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
enter an opening balance for a second warehouse; §10 phase E.

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

`inventory.show`; `stock.show` / `.create` / `.update` / `.delete`; `stock.transfer.show` /
`.post` / `.delete`; `stock.count.show` / `.create` / `.post`. The write paths ask through
`require` or through their `DeleteRule`, and every read worth guarding asks too - which is what
§7.7 was about and what `V74` ended.

**Two of them existed only as other keys until then.** Reading the transfer history asked for the
right to *post* a transfer, and saving a count sheet asked only for the right to *see* one while
discarding a draft asked for the right to post it. Each new key is granted to whoever held the key
that stood in for it, so nobody loses an ability on upgrade - and on a fresh install `V13` grants
no default role `stock.transfer.post` at all, so `stock.transfer.show` starts with no holders
either. That is not something `V74` introduced: warehouses and transfers have never been in a
default role, and only the administrator reaches them.

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

**C - permissions and evidence. Delivered 2026-09-20, see §14.**

**D - what a transfer and a count are owed.** Split in two when it was started, because it is the
largest phase and every part of it is a screen - one pull request each:

- **D1 - the transfer** (`V76`). **Delivered 2026-09-21, see §15.** The history by period, a slip
  for one transfer, a note on the header.
- **D2 - the count and the stocks screen.** **Delivered 2026-09-21, see §16** - and it began
  with a defect that produced wrong figures, found by reading and reproduced on CI before it was
  fixed. A list of past counts with a `RowDetailDrawer` of their lines, a variance report, a
  printed count record. The stocks screen rebuilt in code with row actions and a `Task`.

**E - the warehouse as a record** (`V77` at the earliest - this line said `V75`, which phase C
took). `stocks.is_active` with a scope argument and **no
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
- Most of §12: phase A is unit-tested, and only its reversal warning has been watched on a screen.
- §13 was watched on 2026-09-20 - see the section itself for what that covered and what it did not.

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

### The defect the transfer test found on its first run

**A transfer into a warehouse that already held the item failed outright**, with
"هذه البيانات موجودة بالفعل", before it moved anything. That is the ordinary case: a new
warehouse backfills an `items_stock` row for every item and a new item one for every warehouse, so
the destination almost always has the row already.

`StockTransferService` gives the destination a row for each item first - the arriving quantity
needs something to add itself onto - and writes it as `INSERT IGNORE`, because most items already
have one. But `before_items_stock_insert` signalled SQLSTATE 45000 for a duplicate (item, stock),
and **an error a trigger signals is not suppressed by `INSERT IGNORE`**, while the duplicate-key
error from `items_stock_uk` is. The trigger enforced nothing the unique key did not, and broke the
one statement written to rely on that key's error being ignorable.

It is dropped, not recreated - the same way `V5` replaced `before_items_units_insert` - and the
`DROP` stays in `R__triggers.sql` so an install that ran an older copy loses it too.

**And it would have broken the stock count next.** The count's post now gives every counted item a
row through the same `INSERT IGNORE`, added earlier in this same phase - so with the trigger in
place, every posted count would have failed the same way, in code that had not shipped yet and had
no test of its own. `StockCountPostAcceptanceTest` is that test now: a count moves the balance by
the difference it found, a sale made while the sheet was open survives the post (23, not 28 - the
case that says the adjustment is a difference and not a target), an item with no row in the
warehouse is given one, a user without `stock.count.post` is refused, and a posted sheet cannot be
posted twice.

**Nothing in a green build could see this, and no amount of reading found it**: the review read the
trigger, wrote down that it "conflicts with `INSERT IGNORE`", and ranked it fifteenth. It took
running a transfer against a real database, which §11 said had never happened, and it is the answer
to why the plan insists on that.

### Watched on a screen, 2026-09-20

On a throwaway MySQL started from the developer's own binaries on port 3399 - their server was not
running and was not started - with a schema migrated from nothing to V73 by the application's own
migrations, two warehouses, and one movement of every kind. The backup folder was redirected first
and restored byte for byte after; the scheduled backup did fire into the sink, which is why that
step is not optional.

**The item card, main warehouse, for an item that had been purchased, sold, transferred twice and
counted:**

| row | party column | quantity | running balance |
|---|---|---|---|
| purchase | مورد عام | 20 | 120 |
| sale | بيع نقدي | 5 | 115 |
| transfer out | **مخزن الفرع** | 30 | 85 |
| transfer out | **مخزن الفرع** | 6 | 79 |
| posted count | **جرد شهري** (the sheet's note) | **-2** | **77** |

Opening 100, net movement -23, closing **77** - and 100 - 23 = 77, the three figures on one row
agreeing for the first time. The quantities tab read purchases 20, sales 5, **transfers out 36,
transfers in 0, count adjustment -2**, which decomposes the net exactly. The inventory sheet beside
it read **77.000** for the same item and warehouse, and the database read 77: **one number on both
screens**, which is the whole of what phase B was for.

**The branch warehouse's card** showed the other half: two rows named **الرئيسي**, 30 then 36,
opening 0, transfers in 36, closing 36. Before this work that card was empty with a balance of zero
while the goods were on the shelf.

**The "open" button is disabled on the three new kinds** and enabled on the purchase and the sale -
zoomed in to be sure rather than read off a full-screen capture. A transfer is not a document, and
the button says so instead of producing a reference code.

**The reversal warning fires with the right arithmetic.** With the branch down to 2 after a sale,
reversing the transfer that brought 30 in was met with *"هذا الحذف سيجعل رصيد 1 صنف بالسالب: - زيت
عافية 1 لتر في مخزن الفرع: -28"* - the same two message keys the totals screen uses for a document
delete, not a new sentence. Cancelling left both transfers and every balance exactly as they were.

**Still unseen:** the transfer screen's own entry (the Arabic-digit quantity and one item in two
units are unit-tested only), the inventory sheet for a reader without `inventory.show`, and all of
it in English.

## 14. What phase C delivered (2026-09-20)

Two migrations, eight audit triggers, and the deletion of a test that proved nothing. Every part of
it was applied to a schema built from nothing before it was pushed - 79 migrations to `V75` - and
then exercised rather than inspected.

- **`V74`: `stock.transfer.show` and `stock.count.create`.** The first because the transfer history
  and its report asked for the right to *post* a transfer: a storekeeper meant only to see what had
  moved had to be given the ability to move it. The second because `save` asked only
  `stock.count.show`, so every reader of the count screen was a writer of it, while `deleteDraft`
  asked `stock.count.post` - throwing away a sheet that has moved nothing needed the right to move
  balances with one. Both granted to whoever held the key that stood in for them; on the scratch
  schema `stock.count.create` landed on exactly the two roles that hold `stock.count.show`.
- **`V75`: a posted count sheet survives its item.** `stock_count_lines.item_id` was
  `ON DELETE CASCADE`, so deleting an item took its lines out of every posted sheet with no refusal
  and no trace - and because this repository's rule is that a cascading key is never declared in
  `DeleteRegistry`, nothing could have refused it. The key is `RESTRICT` now and the rule declares
  it, which was checked by trying: `ERROR 1451` where the row used to vanish. The merge is
  unaffected - `ItemReferenceRegistry.STOCK_COUNT_LINES` sums the two items' lines onto the target
  before the source is deleted, so there is nothing left to refuse.
- **`SchemaForeignKeys` had to be told about `V75`**, and that is worth its own line: it reads the
  migrations for keys and skips the cascading ones, so without the new file in its list both
  catalogs went on seeing `V8`'s cascading key and the declaration failed as "not in the schema".
  A migration that *replaces* a key is invisible to a reader that only knows how to *add* one.
- **Eight audit triggers** on `stocks`, `stock_transfer` and `stock_count`. Who created a
  warehouse, who moved stock between two of them, and who turned a sheet into a posted correction
  were recorded nowhere: the audit triggers reached items, parties, documents and treasuries and
  stopped at the shelf. The `UPDATE` on `stock_count` is the one that matters and it was watched
  firing - `"DRAFT"` before, `"POSTED"` after.
- **The line tables are deliberately not audited**, the decision `items_units` already carries.
  `StockCountDao.save` deletes and re-inserts every line on each save, so a trigger there would
  write a row per line per save of a sheet that may run to hundreds of items.
- **`StockCountService` throws message keys.** Three Arabic sentences left a service that ships with
  an English bundle, and the file left `LocalizationArchitectureTest`'s allow-list - which fails in
  both directions, so it cannot go back quietly.
- **`StockTransferDatabaseAcceptanceTest` is deleted.** It had no fixture, asserted one boolean and
  finished in four milliseconds, and `StockTransferEndToEndAcceptanceTest` now says what it only
  appeared to. A class that runs and proves nothing is worse than no class: the green beside its
  name reads as coverage.
- **Three `cp.txt` files were committed by accident** in phase B - `dependency:build-classpath`
  output from running the app by hand. Removed and ignored.

**Not seen on a screen.** The two permissions were proven on a scratch database, not by signing in
as a user who holds one and not the other; and the refusal `V75` now produces has been seen as
MySQL's error, not as the sentence `DeletionService` turns it into. Phase D1 proved the first of
those through the service instead - see §15.

## 15. What phase D1 delivered (2026-09-21)

What a transfer was owed: a history that reaches back further than two hundred rows, a paper to go
with the goods, and a line saying why they moved. One migration, `V76`.

### What changed

- **The history is a period, a warehouse and a text** (`StockTransferHistoryFilter`), opening on the
  month so far. It was "the last two hundred" - `recent(200)` - so a transfer from March could not
  be found in September, and could not be reversed either, since the reversal is a button on a row
  of that list. The warehouse matches **either end** of a transfer: "what moved through my
  warehouse" means in and out alike. The text matches the note, an item's name by part, or one of
  the item's three codes (`items.barcode`, `item_barcodes`, a unit's barcode) **exactly** - a partial
  code matches half a catalogue - and its `%` and `_` are escaped, so a percent sign typed into the
  box is a percent sign.
- **One `WHERE` for the page, its totals and the printed log** (`StockTransferHistoryQuery`, pinned
  with its binder by `StockTransferHistoryQueryTest`). The totals are **counts** - transfers and
  lines - and never a sum of quantities: a transfer's lines are in different items and different
  units, and cartons of juice plus pieces of soap is not a quantity of anything. The page's line
  count is a subquery, so a transfer is one row whatever it carries; the totals join the lines, so
  the transfers are counted `DISTINCT` and the join is `LEFT`, or a transfer with no line would be
  on the page and missing from the total.
- **The history is the screen's second tab** (`StockTransferHistoryView`). It was a 640-point drawer
  over the entry form - too narrow for a period, a warehouse, a search and a pager - and before that
  the lower half of the screen. The two are two jobs, the transfer being written and every transfer
  already posted. **A transfer's lines open in a `RowDetailDrawer`** from the row, which is a master
  and its detail - the case the drawer exists for. What a transfer had moved used to be on no
  screen at all.
- **A slip for every transfer** (`StockTransferSlipLayout`, through `DocumentPdfPage`, upright): the
  letterhead, the number and date, from and to, who entered it, the lines numbered with their codes,
  how many lines, the note, and the receiver's signature. **The quantities are the ones entered, in
  their own units, and nothing adds them up** - the receiver counts two cartons and three pieces
  against the paper, and "27" is a number nobody on the van can check. It is offered **straight
  after posting**, as one question in place of the "posted" notice it replaces - the moment goods
  are posted is the moment their paper is wanted - and from the row's own button afterwards.
- **The printed log and its spreadsheet are one definition** (`StockTransferLog`): every line of the
  transfers the list shows, with each transfer's note beside its lines. It used to print a date
  range chosen in two pickers of its own, apart from the list above it. A transfer matched by one
  of its items is printed whole - the log describes the transfers on screen, not a second selection
  of lines - and more than 10,000 lines is refused rather than cut short, since a log cut without
  saying so looks complete.
- **`V76`: `stock_transfer.notes`**, `VARCHAR(255)`, NULL when nothing was written - a blank note is
  NULL too, so "nothing" has one spelling. The field stops at 255 and the service refuses a longer
  one with a sentence, for a caller that is not that screen. **The audit triggers do not carry the
  note, on purpose**: `DelegateActivityDatabaseAcceptanceTest` runs `R__triggers.sql` over a V70
  schema up to the commission section, and MySQL refuses a trigger naming a column that does not
  exist yet. A transfer is never edited, so its note never changes.
- **Enter in the quantity adds the line** (`Utils.whenEnterPressed(comboUnit, txtQuantity)` and the
  field's own action), and the lines table writes a quantity as one - it showed `7.0`.

### How it was checked

- **Unit tests, no database:** the filter and the page (`StockTransferHistoryTest`), the statements
  and their binder (`StockTransferHistoryQueryTest`), the slip with its labels left as keys
  (`StockTransferSlipLayoutTest`), the log and its spreadsheet (`StockTransferLogTest`), and the
  note on the command.
- **Against MySQL:** six new cases in `StockTransferEndToEndAcceptanceTest` - the note kept and the
  slip read back as the stored row with a carton printed as one carton; a note too long refused with
  nothing moved; the history by period and by a warehouse at either end, totalled over the whole set
  across a page of one; the text by note, by part of a name and by an exact code, with a partial
  code and a `%` finding nothing; the log holding the footer's lines; and `stock.transfer.show`
  reading the history while posting nothing, and nothing readable without it. **Twelve of twelve
  green on a schema migrated from nothing to `V76`** (80 migrations) on a private MySQL 8.0.31, with
  the card and the count classes beside them.
- **Watched on a screen** - the app on that scratch schema, seeded with 57 transfers this month
  over two warehouses, one in two units, one back from the branch and one two months old. The footer
  read 57 transfers and 59 lines, page two held the remaining seven, the old transfer joined when the
  period was widened (58 and 60), `owala-5250` found its transfer and `owala` found none, a transfer
  was posted with an Arabic note through the keyboard and offered its slip, and reversing it took it
  off the list and closed its lines panel. At **1366x768** the bar wraps onto a second line rather
  than running off the window. The slip and the log were **drawn to images** from that database -
  the product code printed the right way round, the note under the table, the log sideways over
  five pages.

### What only the screen found

Three defects, fixed before the pull request, none of them visible to a test:

- **The captions sat above the fields they named.** `ListToolbar` places controls and leaves the
  row's alignment to the row, and the first draft built the row as an `HBox` left at its default -
  top. It is a `FlowPane` now, centred, which is also what lets it wrap at 1366.
- **The lines panel's date read `21-09-2026`.** After an Arabic word, a date's digits are "Arabic
  numbers" to the bidi algorithm and a hyphen does not join those - the defect `PdfExportService`
  already handles for paper. A left-to-right mark before the date is the fix on screen.
- **"عدد الأصناف" counted lines.** Transfer 80 - one item in two units - read "2 items" under a
  heading that meant lines, beside a footer and a slip that said lines. The heading says lines now.

### Found and not fixed

- **A scanned code cannot reach this screen.** The item field beside the search button is
  read-only; an item is chosen only through the search dialog, so a barcode scanner - which every
  shop counting stock owns - types into nothing. The invoice's `ItemSuggestionField` is the answer
  and it is a change of its own.
- **Nothing was seen signed in as an ordinary user.** The permission split is proven through the
  service, not by a user who holds `stock.transfer.show` and not `.post` watching which buttons the
  row offers.
- **The Excel file was not opened** - it is the log's own rows, which the log test and the rendered
  log both cover, and the native save dialog was deliberately not driven.

## 16. What phase D2 delivered (2026-09-21)

The count and the warehouses screen. No migration. It opened on a defect that booked shelves at
the wrong figure, and everything else in it came after that was fixed.

### The defect: one item counted in two units took its book twice

**Each line of a count sheet carries the item's whole book balance** as it stood when the item
was scanned (`system_qty`, base units), and a post moves every line's counted quantity less that
book. The screen kept **a line per item and unit** - the invoice's rule, where each line is a thing
sold - so an item counted in cartons and in loose pieces was two lines, and the post subtracted its
book twice. Two cartons of twelve and three pieces against a book of 30 were posted as
`24 - 30` and `3 - 30`: the shelf holding 27 was booked at **-3**.

Found by reading, while designing the variance report. **Reproduced on CI before it was fixed**:
the acceptance run on the reproducing commit went red with `expected: <27.0> but was: <-3.0>`
(`41a60038`), and the fix's run was green (`d4327314`), 231 cases.

**The fix is one line per item** (`StockCountLines`). Scanning a second unit of an item already on
the sheet restates its line in the base unit and adds the scan there - two cartons and a piece read
25 pieces - keeping the book snapshot of the first scan, for the reason §7.3's struck-through entry
and `StockCountService` both give: the adjustment is a difference against the moment the item was
first counted. The service refuses to save or post a sheet naming one item on two lines, with a
sentence naming it - for a draft saved before the fix, or any caller that is not the screen.

**What it does not do: correct what was posted before.** A posted sheet is a correction that was
made, and rewriting it would move balances already reported. A shop that counted an item in two
units has booked that item short by its book, once per extra line. This finds them:

```sql
SELECT c.id, c.count_date, c.stock_id, l.item_id, COUNT(*) AS lines, MAX(l.system_qty) AS book
FROM stock_count c
         JOIN stock_count_lines l ON l.count_id = c.id
WHERE c.status = 'POSTED'
GROUP BY c.id, c.count_date, c.stock_id, l.item_id
HAVING COUNT(*) > 1;
```

Each row is corrected by counting that item again and posting it - the only correction a count
has, and the one that leaves a record.

### The counted cell read nothing an Arabic keyboard typed

It used `DoubleStringConverter`, which is `Double.valueOf` and does not know ٠-٩, so a count typed
the ordinary way on the machines this ships to was refused as nonsense and the cell went back to
what it held - **silently**, since the handler ignores what it cannot read. It uses
`NumberTextConverter` now, and refuses NaN and a negative count the way it refused a blank. The
same omission as `TransferQuantityInput` and `ReturnQuantityInput`, the third time.

### What changed

- **Past counts** (`StockCountHistoryFilter`, `StockCountHistoryQuery`, `StockCountHistoryView`): the
  count screen's second tab, by period (the year so far - a shop counts a few times a year), warehouse
  and status, paged, with the totals of the whole set - sheets, lines, lines with a difference.
  Counts, never quantities. A sheet's lines open in a `RowDetailDrawer`; its record prints from its
  row. **A posted count could not be seen again before this**: `recent` and `findById` had no caller.
- **The variance report** (`StockCountVarianceView`, `StockCountVarianceReport`): the third tab. Per
  item, over the posted sheets of a period and a warehouse: how many counts found it different, the
  surplus, the shortage, the net, largest shortage first - which items keep going missing. It reads
  the history's own `WHERE` with the status forced to posted, and **a difference is
  `adjustment_agg`'s own expression**, read out of `R__views.sql` by `StockCountHistoryTest`. It sums
  a sheet's lines of one item before splitting surplus from shortage, so an old sheet with an item
  on two lines contributes what it really moved - the wrong figure it really posted.
- **A count's record** (`StockCountSheetLayout`, through `DocumentPdfPage`): the warehouse, the
  status - so a draft cannot be filed as a correction that was made - when it was posted and by whom,
  every line with its book, its count and its difference, and the counter's signature. Offered
  straight after posting, in place of the "posted" notice, and from its row afterwards.
- **`StockCountStatus` carries message keys**, not two Arabic words, and left
  `LocalizationArchitectureTest`'s allow-list.
- **The warehouses screen is built in code** (`StocksController`, §7.14): read and written on a
  `Task`, a table with an id, edit and delete as buttons in the row, and **a delete that asks** -
  it was one press. Enter moves from the name to the address and saves there, so the file left
  `KeyboardNavigationArchitectureTest`'s list. `addStock-view.fxml`, loaded by nothing, is gone.
- **The transfers screen opens on `stock.transfer.show`.** It opened on `.post`, so after `V74` a
  reader of the history who may not post could not open the screen at all. The post button is
  disabled for whoever may not post; the service refuses anyway.
- **`CompanyLetterhead`** writes both warehouse papers - the transfer slip and the count record - the
  one way, rather than a copy of the letterhead code in each screen.

### How it was checked

- **Unit tests:** `StockCountLinesTest` (the scan rule, the fold, the repeated item),
  `StockCountHistoryTest` (the filter, the page, the statements and their binder, the view's own
  difference), `StockCountPapersTest` (the record and the variance report), and the status keys
  against the three bundles.
- **Against MySQL:** six new cases in `StockCountPostAcceptanceTest` - the item in two units lands on
  27; a sheet naming an item twice is refused with nothing moved; the history of a warehouse by
  status with its totals across a page of one; the variance of two posted sheets, a correct line
  and a draft, **held against the balance itself** rather than a figure typed twice; a sheet's record
  as stored; and nothing readable without `stock.count.show`. **Eleven of eleven green** on a schema
  migrated from nothing to `V76`, with the transfer's twelve and the card's three.
- **Watched on a screen at 1366x768**, on that schema seeded with posted counts across three months,
  a draft, and an old sheet carrying an item on two lines. The carton's own code scanned onto oil
  already on the sheet in pieces folded to one line of 102; `٥٥` typed into a counted cell read 55;
  the draft posted and offered its record. The history listed four sheets with 9 lines and 8
  differences; the old sheet's lines showed the defect as it was posted, `-2` and `-48` against one
  book of 50. The variance read oil 4 counts, 7 over, 55 short, **net -48 - exactly what the counts
  moved its balance by across both warehouses**, `(102 - 100) + (0 - 50)`. The warehouses screen
  renamed a warehouse through Enter, asked before a delete and refused it with a sentence naming 4
  balance rows and a count, and the count screen's filter showed the new name without being
  reopened. The record and the variance report were **drawn to images** from that database.

### What only the screen found

- **The count screen's top row was squeezed at 1366** into "الم..." and "ترحيل ا...": the
  warehouse, the date, the notes, the status and four buttons do not fit one line, and an `HBox`
  squeezes. It is a `FlowPane` now and wraps.

### Found and not fixed

- **The count table carries this machine's saved column widths**, from a wider screen: at 1366 the
  counted column - the one anybody types into - opened off the edge behind the horizontal scroll.
  `TableSetting` restores what a user left, which is the trap CLAUDE.md describes; a fresh install
  has no such widths. Not reproduced on a clean profile.
- **The shared delete dialog drew "مستخدم في" as one word** in the refusal - the template has the
  space. A rendering matter of that dialog, everywhere it is used.
- **A blank sheet to count on paper** - a warehouse's items with an empty column - is still not
  printable; the record is the sheet as entered.
- **Nothing was seen signed in as an ordinary user.** The permission of each read is proven through
  the service.
