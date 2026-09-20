# The warehouse contract

What a stock balance is, who may move one, what the 2026-09-20 review found, and the order the
rest is built in. Read it before touching anything under `features/inventory`,
`features/stocktransfer`, `features/stockcount`, `features/stockledger`, `features/itemcard`,
`InvoiceStockGuard`, `StockService`, `Items_StockDao`, or the stock half of `R__views.sql`
(`quantity_items_table`, `stock_transfer_view`, `card_item_view*`, `mini_quantity_view`).

Sections 1-6 are the contract as it stands. §7 is what the review found wrong, §8 what does not
exist, §9 the decisions nobody has taken yet, §10 the phases, §11 what has never been run.
**The review was a reading of the code, the migrations and the tests. Nothing was run** - so every
defect in §7 is read, not reproduced, and the first act of fixing one is reproducing it.

The history in one paragraph: the warehouse screens were removed in `0853cf4` and came back in
`fbadd53`, over a schema that was never dropped. `docs/erp-roadmap.md` §11 is the record of what had
to be true before they could come back (its 11.4 and 11.5 are still unticked there although the
screens shipped - that document is stale on this point, this one is current). Roadmap step 8 is the
stock ledger, and §5 below is where the two meet.

## 1. A balance is derived, and there is one place it comes from

**`quantity_items_table`, keyed by (item, warehouse)** = the warehouse's opening balance
(`items_stock.first_balance`) + purchases - sales - purchase returns + sales returns - transfers out
+ transfers in + the adjustments of **posted** stock counts. Every quantity is
`quantity * type_value`, the factor stored on the line, so a balance is always in base units and a
later change of an item's factor never rewrites it.

The same rule as `treasury_current_balance`, for the same reason: a stored balance is a second
answer waiting to disagree with the first. Three consequences:

- **The view has no balance column.** It answers the eight components and each caller adds them up.
  `ItemStockBalanceSql` is the same definition for *named* items (`ItemStockBalanceSqlTest` holds
  the two together); a catalogue-wide read uses the view, a read for named items must not.
- **The driver is `items_stock`.** A missing (item, warehouse) row is a silently dropped balance, so
  a new warehouse backfills a row per item and a new item a row per warehouse (`StockService`,
  `ItemsDao`, `Items_StockDao`), `V18` backfilled the warehouses that predate that, and
  `StockTransferDao.ensureDestination` covers a row still missing on the receiving side.
- **A query that names no warehouse must aggregate before it joins** (`QUERY_ITEMS_ALL_STOCKS`), or
  it returns one row per warehouse, each describing one warehouse's movements as the total.

**The warehouse of a document is its header's.** `stock_id` is on `total_buy`, `total_sales` and the
two returns; the line tables carry none. A document moves one warehouse, and the invoice screen
disables `comboStock` once lines exist, which is what keeps an edit from reversing its original
lines against a different warehouse than the one they left.

**`mini_quantity_view` is company-wide on purpose** - "should we reorder" is asked of the business,
"can this till sell it" of a warehouse. `StockLevelAlert` asks the second. The two are different
questions and are documented as such in the view.

## 2. What may move a balance

Four things, and nothing else:

| Writer | Where | Refuses, in order |
|---|---|---|
| A document (four families) | `InvoiceSaveService.persist` → `InvoiceStockGuard` | permission, period lock, then the stock effect of the **whole** document |
| A transfer | `StockTransferService.transfer` | `stock.transfer.post`, period lock (`STOCK_TRANSFER`), source rows locked, source balance |
| A posted stock count | `StockCountService.post` | `stock.count.post`, editable, period lock (`STOCK_COUNT`), not empty, `WHERE status = 'DRAFT'` |
| An item's opening balance | `ItemsDao` → `Items_StockDao.updateOpeningBalance` | `OpeningBalanceGuard` / `BulkOpeningBalance`: only while nothing has moved the item |

**There is no fifth.** Correcting a balance is a stock count - dated, posted once, read-only after,
recorded under whoever posted it. Editing `first_balance` to make a number come right rewrote what
the opening balance *was* and every report before it; that is what `V8` ended.

**The lock is the `items_stock` rows of the warehouse being drawn from, `FOR UPDATE`, in item-id
order.** `InvoiceStockGuard` and `StockTransferDao.lockSource` take the same rows, which is why a
sale and a transfer out of one warehouse are serialized against each other. A new writer that takes
stock out locks the same rows in the same order, or it deadlocks with these two or races them.

**A transfer line carries the unit and factor it was entered in** (`V19`) and is converted to base
units before the source balance is judged. **An item may appear on more than one line, in more than
one unit, and what the source must cover is the sum of them** (`StockTransferCommand
.baseQuantityByItem`) - the same thing `InvoiceStockGuard` does for a document, which judges the
whole document's effect on an item rather than a line at a time. Reversing a transfer goes through
`DeletionService`/`DeleteRegistry.STOCK_TRANSFERS`, whole, never partly, and **warns before it takes
the destination below zero**.

**A count in progress moves nothing.** Only `POSTED` sheets are in `adjustment_agg`; a shop has at
most one open draft per warehouse, and opening the screen continues it.

## 3. Below zero

- **A sale may go below zero only when the shop says so**: `item.sel.without.balance`
  (`SharedSettingKeys`, so it is the shop's and not the computer's), off by default, and it lifts
  the check for `SALES` alone.
- **A purchase return, a transfer out, and any edit that takes stock out are always refused** below
  the balance. An expiry batch is never oversold whatever the setting says.
- **Deleting a purchase or a sales return is a warning** (`DocumentDeleteStockCheck`): a shop that
  sells before entering the supplier's bill is already below zero on paper, and refusing would stop
  it correcting the bill. **Reversing a transfer is the same warning**, read by
  `StockTransferService.deleteShortfalls` over the destination warehouse and asked after the
  reversal is confirmed. A failure to read it is logged and passed over: the check is a courtesy,
  and a reference code in front of somebody reversing a transfer reads as a refusal.
- Nothing at the database level refuses a negative balance, and nothing should: the balance is not a
  column.

## 4. `DefaultStock.ID` is "which one, if nothing else says"

Never "the only one". `DefaultStockUsageArchitectureTest` carries the sixteen files allowed to name
it, and the review sorted them: nine are a combo's initial selection or the protected row of
`DeleteRegistry.STOCKS`; six are compatibility overloads for callers that predate the parameter
(`CardItemDao`, `JdbcInvoiceStockRepository`, `InvoiceSaveCommand`, `StockCountService`,
`StockService`, `DataInterface`); and **one is a real gap** - `ItemsDao` (insert, update, bulk edit)
and `AddItemController` write an item's opening balance to the default warehouse only. No screen can
enter an opening balance for a second warehouse; §10 phase D.

## 5. The ledger: `stock_movements`

Declared in `V1` - typed, signed by an in/out pair with a CHECK that exactly one is positive,
carrying the unit, the factor and a `(reference_type, reference_id, reference_line_id)` - and
written since 2026-08-17 by `features/stockledger` as a **dual write**: the documents and the posted
count write it while `quantity_items_table` goes on being the answer. **Nothing reads it.**

**The decision is already taken, in `docs/erp-roadmap.md` step 8: the ledger becomes the source.**
8.6 rewrites `quantity_items_table`, then `card_item_view` and `mini_quantity_view`, to read from
it - *the same numbers exactly* - and 8.1 gives it `unit_cost`, which is the only road to a cost
that is not today's price (§9.1). This document does not reopen that; it records how far from it
the code is:

- **Five of six producers write it. The transfer does not**, and the opening balance does not.
  `MovementType`'s javadoc still says transfers "have no live write path" - they have had one since
  `fbadd53`. `StockLedgerReconciliationReport` counts transfers on the view's side only, so it
  should report a mismatch for every item a real transfer ever moved. Not run.
- **An edit and a delete rewrite the ledger's rows rather than reversing them**
  (`deleteByReference`). Acceptable while nothing reads it, and the roadmap says it must become a
  reversal before 8.6. **No trigger makes the table append-only**, unlike `shift_cash_ledger`; when
  it becomes the source it gets the same pair of triggers and the same `@app_bulk_wipe` rule.
- **`WipeCatalog`'s `SALES`, `PURCHASES` and `STOCK_COUNTS` targets do not list it**, so wiping
  documents without wiping items leaves their movements behind, pointing at nothing. Its javadoc
  also still says multi-warehouse was removed.
- The backfill tool (`StockMovementBackfillRunner`, dry run by default, refuses to commit over any
  mismatch) **has never been run on a customer's database.**

**Until 8.6, the ledger is a consistency check and must not be read for a figure.** A half-written
ledger that somebody starts reading is the `treasury_movements` mistake with a head start.

## 6. Permissions

`inventory.show`; `stock.show` / `.create` / `.update` / `.delete`; `stock.transfer.post` /
`.delete`; `stock.count.show` / `.post`. The write paths ask through `require` or through their
`DeleteRule`. What is wrong with the set is in §7.6.

## 7. What the 2026-09-20 review found

Ordered by what it costs. **Read, not reproduced** - that was true when this section was written
and is the reason §12 exists: phase A closed items 2, 4, 7, 10, 11, 15 and the stale javadocs, and
says for each whether anything reproduced it. Everything still open here is still only read.

### Figures a user sees

1. **The item card does not know transfers exist.** `card_item_view` unions the four line tables;
   `CardItemDao.balanceSql` adds the opening balance and posted counts and nothing from
   `stock_transfer_list` - the word "transfer" does not occur in `CardItemDao`, `CardItemService`,
   `CardController` or `features/itemcard`. So for any warehouse that sent or received a transfer,
   the card's opening and closing balance disagree with the inventory sheet beside it, and the
   transfer is not a row. A posted count *is* in the card's balance and is not a row either. The
   comment in `R__views.sql` that the card and `quantity_items_table` "cannot drift apart" is true
   of documents only. Two screens, two balances - the defect the party and treasury work removed.
2. **A count's `system_qty` may be the company's balance, not the warehouse's.** A scan resolves
   through `getItemByBarcodeAndStockId(text, stockId)`, which is per warehouse. When that finds
   nothing the screen falls back to `itemsService.getFilterItems(text)` - the invoice's name search,
   which folds every warehouse - and `lineFor` snapshots `item.getSumAllBalance()` from whichever it
   was handed. An item found by name while counting warehouse 2 would carry the total of all
   warehouses as "what the system says", and the difference posted is wrong by everything held
   elsewhere. Invisible with one warehouse.
3. ~~**Posting a count takes no lock and trusts a snapshot.**~~ **This item was wrong, and it is
   left here struck through rather than deleted, because a review that quietly removes what it got
   wrong teaches nothing.** It read that `system_qty` is captured when the line is scanned and
   never re-read, and called that a defect - a sale between the scan and the post being "undone"
   by the adjustment. The arithmetic says otherwise, and `StockCountController`'s own javadoc said
   so all along: **the adjustment is a difference, not a target.** Book 10, shelf 9, a sale of 2,
   counted 9 - the adjustment is `9 - 10 = -1` and the balance lands on `10 - 2 - 1 = 7`, which is
   what is on the shelf. Re-reading the snapshot at post time is what would swallow the sale. The
   snapshot is deliberate and correct.
   <br>What is real in that paragraph is smaller: **an item with no `items_stock` row in the
   warehouse being counted posts an adjustment that `quantity_items_table` never reads** - the
   count reports lines moved and nothing moves - and **nothing checks whether the result is below
   zero.** The first is fixed (§13); the second is open, and a count is the one writer for which
   going below zero is a legitimate answer, so it is a warning at most.
4. **Deleting a transfer checks nothing.** Goods received and since sold leave the destination
   negative without a word; the same situation on a purchase delete warns.
5. **The opening balance is stored twice and a trigger keeps overwriting one copy.**
   `after_items_update` (`R__triggers.sql`) copies `items.first_balance` into the warehouse-1 row of
   `items_stock` on **every** update of an item, whatever was updated. And
   `items_stock.current_quantity` is written at insert in four places, updated by nothing and read
   by no view: a dead column that looks exactly like a live balance.
6. **`stock_count_lines.item_id` is `ON DELETE CASCADE`**, so deleting an item removes its lines
   from posted counts - a recorded correction, gone with no trace. `DeleteRegistry.ITEMS` cannot
   refuse what it does not list, and by the catalogue's own rule a cascading key is not listed; the
   key itself is what is wrong.

### Authorization and evidence

7. `InventoryService` has no `require` - `inventory.show` only hides a menu entry.
   `StockCountService.save` asks `stock.count.show` to write, and `deleteDraft` asks
   `stock.count.post` to delete. There is no `stock.transfer.show`, so reading the transfer history
   needs the right to post one.
8. **No audit trigger** on `stocks`, `stock_transfer`, `stock_transfer_list`, `stock_count` or
   `stock_count_lines`, and none of them has the microsecond `updated_at` that `V50` gave items and
   documents to compare as a version, so two people editing one draft count overwrite each other.
   Who renamed a warehouse or reversed a transfer is recorded nowhere.
9. `StockTransferDatabaseAcceptanceTest` has no fixture and **signs in as user 1**, who bypasses
   every permission - it proves nothing about a transfer. No test on MySQL posts a transfer or a
   count.

### Screens

10. `StockTransferController.parseQuantity` is `Double.parseDouble`: ٠-٩ is a zero and the line is
    refused. The `ReturnQuantityInput` lesson.
11. One item in two units passes the screen (it de-duplicates by item **and** unit) and then
    `StockTransferCommand` throws `IllegalArgumentException` for the repeated item, which reaches
    the user as a reference code. Either the command is right or the screen is; they cannot both be.
12. **The transfer history is "the last 200"** - no period, no filter, no page. An older transfer
    cannot be found and so cannot be reversed; the treasury history had this and
    `TreasuryHistoryFilter` is the answer that exists.
13. **A posted count can never be seen again.** `StockCountService.recent` and `findById` have no
    caller: no list of counts, no variance report, no print. The one document that corrects a
    balance is the one document with no paper.
14. The stocks screen does everything on the JavaFX thread, its table has no id (so its widths are
    shared with every id-less table in the package), its delete is a toolbar button over "the
    selected row", and `addStock-view.fxml` is dead. The transfer screen posts, loads and prints on
    the JavaFX thread.
15. Arabic literals thrown from `StockCountService` (three), and Arabic labels in `StockFilter`,
    `InventoryColumns`, `StockCountStatus` and `LowStockSource` - all on
    `LocalizationArchitectureTest`'s allow-list. `model/domain/Stock` still carries JavaFX
    properties. `StockDao.deleteById` writes `id == 1`. `StockTransferService` ignores the
    `DaoFactory` it is given and builds its DAO itself. `StockTransferDao.balances` reads
    `quantity_items_table` for named items, which is the plan `ItemStockBalanceSql` exists to avoid.

### Documents that say something untrue

`MovementType`'s and `WipeCatalog`'s javadoc (§5); roadmap 11.4/11.5 unticked; and `CLAUDE.md`
described `V51` and `V52` as stock counts and their variance settlement - they are
`V51__data_change_revisions` and `V52__party_optimistic_lock_versions`. **The last migration to
touch a stock table is `V19`**, and a variance settlement for a stock count exists nowhere.

## 8. What does not exist

Each was searched for before being written here.

- An opening balance for any warehouse but the default (§4).
- **A cost.** No average, no FIFO; a sold line snapshots `ItemUnits.buyPrice` and the inventory
  valuation multiplies today's balance by **today's** `items.buy_price` - so last month's valuation
  changes when a price does. `stock_movements` has no cost column. §9.1.
- A write-off document (damage, expiry, samples). A stock count is the only way to lower a balance,
  and it cannot say why.
- A transfer with a state (sent / received), an edit of a transfer, a slip for one transfer, a note
  on a transfer.
- **Expiry batches across a transfer**: a transfer line has no expiry date and
  `expiryBalancesSql` reads the four document families only, so a batch never leaves the warehouse
  it was bought into. Inferred from the SQL; §9.3.
- A user limited to certain warehouses (roadmap 7.3), a minimum quantity per warehouse, a
  warehouse that can be switched off instead of deleted (there is no `is_active`, and a warehouse
  holding an `items_stock` row per item can in practice never be deleted), a keeper or a type.
- In the count: import from a spreadsheet, a count of one group, a sheet pre-filled with a
  warehouse's items, printed count sheets.
- A warehouse in `ItemCatalogFilter` or `ItemReportRequest`: valuation, slow-moving, expiring and
  stock-level reports are all company-wide.
- A notification for an expiring batch or for a draft count left open. `items_package` has no screen
  and no stock effect. There are no serial numbers.

## 9. Decisions still open

Written down so they are taken once, on purpose, rather than by whoever gets there first.

1. **The costing method.** The roadmap recommends a moving weighted average and says to document it
   before a line is written, because changing it later changes stored figures. Still to decide:
   whether the average is per warehouse or per item across the business (a transfer at cost makes
   the second simpler and is what a single legal entity wants); what a sale below zero is costed at;
   and whether an edit of an old purchase re-costs the sales after it (it should not - the
   `InvoiceWalletFee` rule about a month already reported).
2. **Which copy of the opening balance survives.** `items_stock.first_balance` is what the view
   reads since `V18`; `items.first_balance` is what the item screen writes and the trigger copies.
   The end state is one column and no trigger, but `OpeningBalanceGuard`, the bulk editor, the Excel
   import and `ItemMergeStatements` all touch it, so it is a phase and not a line.
3. **Whether a transfer moves a batch.** Either a transfer line carries an expiry date and is picked
   from the batches on hand exactly as a sale is (`EXISTING_BATCH`), or expiry stays a
   whole-business question. The first is right and is more than a column.
4. **Whether a transfer has two steps.** One step is right for a shop with a back room; two (sent,
   then received) is what two branches need. If it comes it is a state on the header and goods in
   transit belong to neither warehouse - a third place the balance view has to know about.
5. **Whether a posted count's difference is money.** A shortage is an expense and a surplus is
   income to an accountant; today it is neither. This waits for the general ledger (roadmap step 9)
   and must not be improvised before it - the `treasury_movements` rule.

## 10. The phases

One item open at a time (`docs/product-plan.md`). A and B are defects in figures a user already
sees and come before anything new.

**A - the quick ones, no migration. Delivered 2026-09-20, see §12.**

**B - one balance on two screens. Delivered 2026-09-20, see §13.**

**C - permissions and evidence** (`V74`). Deleting `StockTransferDatabaseAcceptanceTest`, now that
`StockTransferEndToEndAcceptanceTest` says what it only appeared to.
`stock.transfer.show` and `stock.count.create`, granted to
whoever holds the key that stood in for them, so nobody loses an ability on upgrade; the count's
Arabic literals become keys; audit triggers on the five tables, in `R__triggers.sql`;
`stock_count_lines.item_id` stops cascading and `DeleteRegistry.ITEMS` declares it.

**D - what a transfer and a count are owed.** The transfer history by period
(`TreasuryHistoryFilter`'s shape: one `WHERE` for the page and its totals), a slip for one transfer
through `DocumentPdfPage`, a note on the header. A list of past counts with a `RowDetailDrawer` of
their lines, a variance report, a printed count sheet. The stocks screen rebuilt in code with row
actions and a `Task`.

**E - the warehouse as a record** (`V75`). `stocks.is_active` with a scope argument and **no
default**, the way `PartySearchScope` and `EmployeeScope` have none; an opening balance per
warehouse from the item screen; decision §9.2 carried out - one column, the trigger and
`current_quantity` dropped.

**F - the ledger becomes the source** (roadmap 8.1, 8.4's sixth producer, 8.6). Transfers and
openings write `stock_movements`; an edit and a delete become reversals; the append-only triggers;
`WipeCatalog` lists it; the reconciliation run on a copy of a real database until it reports
nothing; then, and only then, the views read from it - *the same numbers exactly*. `unit_cost`
after decision §9.1. This is the most dangerous step in the whole roadmap and it is last here on
purpose: every phase before it removes a way for the two sides to disagree.

**After F:** a write-off document (the first movement type that is not one of the six, so roadmap
8.2's registered types come with it); a warehouse per user (roadmap 7.3); batches across a transfer
(§9.3); the warehouse filter in the item reports; a minimum per warehouse.

## 11. What has never been run

**`.github/workflows/acceptance.yml` changed what this section can say.** Since 2026-09-20 every
class named `*AcceptanceTest` runs on each push to main and each pull request against it, on a
MySQL container that is thrown away, over a schema **migrated from nothing to the head**. So two of
the things this section used to list are now automatic: a new migration is proved to apply to a
fresh database before it can merge, and a new acceptance class needs no workflow edit to be run.
What it cannot do is open a screen.

That run also measured something this plan had only reasoned about: `StockTransferDatabaseAcceptanceTest`
finishes in **4 milliseconds** and `StockLedgerReconciliationAcceptanceTest` in 47. They are green
and they touch almost nothing - §7.9, in a number rather than an argument.

- **The open items of §7.** Reproduce before fixing.
- A transfer or a count against MySQL under test, *meaning* it. The reconciliation report on a
  database holding a transfer. The backfill tool on a customer's data.
- The roadmap's reference test with two warehouses (11.5).
- The five screens on a copy of a real database with a second warehouse in it, in English as well
  as Arabic, at 1366x768 - where every other area of this system found defects no test could.
- Everything in §12: phase A is unit-tested and has not been opened on a screen.

## 12. What phase A delivered (2026-09-20)

No migration, no schema change, and nothing that needed a database to prove. What each item was,
and what says it is right:

- **A transfer may carry one item in two units, and the source must cover their sum.** The decision
  §7.11 left open, taken the way the invoice already answers it. `StockTransferCommand` no longer
  throws `IllegalArgumentException` for a repeated item - the screen built such a line and the save
  then reached the user as a reference code - and `baseQuantityByItem()` sums the lines per item.
  **That refusal was load-bearing and this is why it could not simply be deleted**: the balance was
  compared once per *line*, so two lines of ten would both have passed against a balance of fifteen.
  `StockTransferService` now compares the item's total. `StockTransferCommandTest`.
- **The transfer quantity is read the way every other number in this application is read.**
  `TransferQuantityInput` over `NumberTextConverter`, out of the controller because a controller
  cannot be tested. It was `Double.parseDouble`, so ٠-٩ - what the keyboard these tills carry
  produces - came back as zero and the screen told the person that what they had just typed
  correctly was not a number. `TransferQuantityInputTest`, six cases.
- **Reversing a transfer warns before it takes the destination below zero.** Goods that arrived and
  have since been sold left the destination short, silently, while deleting a purchase in the same
  state warned. `StockTransferService.deleteShortfalls` feeds the destination's balances to
  `DocumentDeleteStockCheck` - the same pure check, the same two message keys - and the screen asks
  after the reversal is confirmed. A failure to read it is logged and passed over.
- **A scan that finds an item by name now resolves it in the warehouse being counted** (§7.2). The
  fallback was `ItemsService.getFilterItems`, which folds every warehouse, and `lineFor` snapshots
  whatever it is handed as `system_qty` - so an item found by name while counting warehouse 2
  carried the whole business's balance as "what the system says", and the difference posted was
  wrong by everything held elsewhere. Invisible with one warehouse.
- **`InventoryService` asks for `inventory.show` before answering.** The key existed as a menu hint
  alone, so a reader without it could not see the button and could still reach the sheet - which is
  every item's cost and every warehouse's valuation.
- **`StockTransferDao.balances` reads through `ItemStockBalanceSql`** instead of joining
  `quantity_items_table` for named items (§7.15). The same definition, pinned against the view by
  `ItemStockBalanceSqlTest`; it matters more now that a delete warning asks the same question.
- **Three documents stopped saying untrue things.** `MovementType`'s javadoc claimed transfers have
  no live write path because the screens were removed - they came back in `fbadd53`, and the gap is
  now stated as a gap. `WipeCatalog`'s claimed multi-warehouse support was removed. And `CLAUDE.md`
  described `V51` and `V52` as stock counts and their variance settlement; they are the optimistic
  lock and `data_change.revision`, and no variance settlement exists.

**What this phase does not claim.** Nothing here was reproduced against a database or seen on a
screen; the two defects that produce wrong figures - the item card and the count's snapshot at
post - are phase B and are untouched. The transfer screen still posts and prints on the JavaFX
thread, its history is still the last 200 with no period, and a posted count still cannot be opened
again.

## 13. What phase B delivered (2026-09-20)

The two screens that answered a balance differently now answer it once. No migration; the view is
repeatable, so it applies on the next start-up of every install.

**It was reproduced before it was fixed, and the proof is on the cloud.** The first commit of the
branch was a failing test and nothing else: the `acceptance` run on it went red with

> `the card does not count a transfer into the warehouse it arrived in ==> expected: <7.0> but was: <3.0>`

against a MySQL built from nothing - the inventory sheet saying seven and the card saying three for
one shelf - and the run on the next commit was green. That is what `.github/workflows/acceptance.yml`
made possible: before it, this defect could only have been argued about.

- **`CardItemDao.balanceSql` counts both halves of every transfer.** Its javadoc had said "the same
  three terms" while naming three of four, which is how the omission survived - the sentence that
  would have caught it was the one that was wrong. `balanceOn` binds three parameters per branch in
  one loop, so `CardItemDaoStatementsTest` now pins the count as well as the text: seven branches,
  twenty-three parameters. A wrong bound there is a balance computed for another item on another
  day, which reads exactly like a right one.
- **`firstMovementSql` knows the same seven movements**, so a card opened on an item's whole history
  starts at the movement that began it rather than after it.
- **Transfers and posted counts are rows on the card** (`card_item_view`). A transfer is two rows,
  one per warehouse, because the card reads one warehouse at a time; `name_custom` is the warehouse
  at the other end, since a transfer has no party and that column answers "who was this with". A
  posted count is one row whose quantity is the signed difference in base units - which is why its
  `type_value` is 1 and the unit shown is the item's own: there is no "three cartons" to report,
  only what the shelf gained or lost. A line counted exactly right moved nothing and is not a row.
- **The card's three figures are one arithmetic.** `textCountTotals` is labelled "net movement" and
  sits between the opening and closing balances, so `ItemCardTotals.netQuantity()` has to include
  the new kinds or those three contradict each other on screen. The adjustment is summed **signed**,
  because a count's rows go both ways and reading them as magnitudes would report three missing and
  three extra as the same six. The quantities tab gained three cards; it is a `FlowPane`, so it
  wraps rather than needing a layout rewritten.
- **`ProcessType` gained the three kinds and the compiler found the one place that would have gone
  quiet**: `CardController.dataInterface`'s exhaustive switch. A transfer is not a document, so the
  row's "open" button is now *disabled* on those rows through `RowAction`'s `enabled` predicate -
  which existed for exactly this - rather than pressed into a reference code. `processTypeOf`'s
  `default -> null` is the other direction and fails quietly, so it is commented as the thing to
  change when a kind is added: a row with no kind is drawn on screen and left out of every total.
- **`WarehouseStockDao` is now the one place that locks a warehouse's rows.** The transfer's DAO
  delegates to it. The lock order is the invariant - every writer takes the same rows in item-id
  order or they deadlock - and an invariant kept in two files can be ordered two ways.
- **A posted count gives every counted item an `items_stock` row first.** Without one,
  `quantity_items_table` has nothing to add the adjustment onto: the count posts, the screen reports
  lines moved, and nothing moves.

**What this phase set out to do and did not.** It was going to make posting a count re-read its
snapshot under a lock. It does not, because the premise was wrong - see §7.3, left struck through.
Working through the arithmetic while writing the guard is what showed it, and the guard was deleted
rather than shipped. A warning about a balance that had "moved" would have been noise on every sheet
counted during trading hours.

- **The transfer finally has an acceptance class that means it**
  (`StockTransferEndToEndAcceptanceTest`): a transfer posted through the real service moves the
  balance out of one warehouse and into the other, one item in two units is one demand of the
  source, two lines of fifteen are refused against a balance of twenty, a destination with no
  `items_stock` row is given one, and a user **who is not user 1** and does not hold
  `stock.transfer.post` is refused before anything is written. `StockTransferDatabaseAcceptanceTest`
  stays as it was for now: it has no fixture, asserts one boolean, and CI finishes it in four
  milliseconds. It is superseded rather than deleted, and deleting it belongs to phase C with the
  rest of the evidence work: a class that runs and proves nothing is worse than no class, because
  a green run beside its name reads as coverage.

**Still not seen on a screen.** No part of this has been opened. The three new summary cards, the
disabled button on a transfer row, what a count row reads in the kind column, and all of it in
English: unseen.
