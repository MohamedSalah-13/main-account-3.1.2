# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A JavaFX 21 desktop accounting application for Arabic-speaking businesses (invoicing, inventory,
customers/suppliers, treasury, reporting), backed by MySQL. User-facing strings are Arabic and the UI is RTL.

## Build and run

```bash
mvn clean compile -DskipTests      # build both modules
mvn clean test                     # run the tests
mvn -pl account javafx:run         # run the app (needs a reachable MySQL + a config.xml)
mvn clean package -DskipTests      # shaded jar in account/target/

mvn -o -pl controlsfx test -Dtest=CryptoDatabaseConfigTest              # one class
mvn -o -pl controlsfx test -Dtest='CryptoDatabaseConfigTest$KeyFile'    # one nested class

# account depends on controlsfx, so -pl account needs -am or it compiles against
# whatever stale controlsfx is in ~/.m2 and fails on anything added since:
mvn -o -pl account -am test -Dtest=ScheduledBackupTest -Dsurefire.failIfNoSpecifiedTests=false
```

`mvn -o` (offline) works — the local repository is populated.

**Coverage is real but uneven — know which half you are in.** JUnit 5 and Mockito are declared in the
root pom and inherited by both modules; surefire needs no configuration. `mvn clean test` currently runs
**888 tests across ~90 classes** — 98 in `controlsfx`, 790 in `account` — with 29 skipped (below). What is
genuinely covered:

- **The declarative specs, pinned character for character** — `DocumentDaoStatementsTest`,
  `PartyDaoStatementsTest`, `PartyLedgerStatementsTest`, `CardItemDaoStatementsTest`,
  `DocumentTableSpecTest`, `WipeCatalogTest`, `ItemMergeStatementsTest`,
  `ItemReferenceRegistryTest`. These fail the build on a wrong column, so they are the
  safety net for anything touching SQL. The last two read the foreign keys straight out of the
  migration files, so the schema itself is what they check against.
- **Architecture rules** — `AuthorizationArchitectureTest`, `ErrorHandlingArchitectureTest`,
  `DefaultRoleAcceptanceTest`. They fail when a new service skips the permission guard or a new
  exception escapes the error boundary.
- **The invoice logic** — the `features/invoice` package has a test per class, all without a JavaFX
  toolkit.

What still has none: the controllers, the FXML screens, the reports, the trial logic, and most of the
`model/dao` write paths.

**Six classes do not run by default.** `InvoiceStockDatabaseAcceptanceTest`,
`DocumentLineDatabaseAcceptanceTest`, `StockLedgerReconciliationAcceptanceTest`,
`TotalDocumentDeleteReversesStockLedgerAcceptanceTest`, `PartyLedgerViewAcceptanceTest` and
`ItemMergeDatabaseAcceptanceTest` are gated on
`-Daccount.db.acceptance=true` and need a reachable MySQL. A green `mvn clean test` has not run them.
`PartyLedgerViewAcceptanceTest` is the only check that the accounting views say what
`DocumentLedgerEffect` says, so **run it after touching `R__views.sql`** — the whole return-ledger defect
lived where no test could see it. `ItemMergeDatabaseAcceptanceTest` is the only check that a merge leaves
the surviving item holding both histories, and **it has never been run** — no MySQL was reachable when it
was written.

So a passing build now means more than it did, but still not that a screen works: state what was and was
not checked, and remember that verifying UI behaviour means running the app against a database.

**Always `clean` when verifying a change.** Incremental builds frequently report
`Nothing to compile - all classes are up to date` and silently skip your edits, so a plain `mvn compile`
can "succeed" without ever compiling them.

**Lombok error cascades.** One unrelated compile error aborts annotation processing, and every generated
getter/setter then reports `cannot find symbol` — hundreds of errors across untouched files. Fix the first
genuine error and the rest disappear; do not chase them individually.

## Plans and conventions

Two documents govern work here and are kept current — read them before large changes:

- **[`docs/new-code-rules.md`](docs/new-code-rules.md)** — the contract every new model, DAO or service
  must follow so the planned Spring Boot and SaaS moves stay cheap. **Read it before creating anything
  in `model/domain/`, `model/dao/` or `service/`.** The short version: new models are plain POJOs (no
  `javafx.beans.property`, no `DForColumnTable`), DAOs never read `CurrentUser`/`Preferences`/
  `ServiceRegistry`, dependencies arrive through the constructor, transaction boundaries go in the
  service via `TransactionTemplate` (no new `insertMultiData` sites), filtering and paging happen in
  SQL, and no table ever gets a `tenant_id` column.

  **§5 of that file is the one to read before editing anything at all, not just before creating.**
  It is the "one touch" contract: any file you open for any reason leaves compliant. Table columns
  are built in code, never with `@ColumnData` (`PropertyValueFactory` is reflection by string — a
  renamed field yields a silently empty column); a model you touch leaves without `javafx`; an FXML
  you touch declares `fx:controller` and is loaded with a `ResourceBundle`; icons are Ikonli, not
  `InputStream` fields; a service throws a message *key*, never an Arabic literal. Each rule is
  meant to be pinned by an architecture test the way `AuthorizationArchitectureTest` already is —
  a rule without a test is a wish.
- **[`docs/erp-roadmap.md`](docs/erp-roadmap.md)** — the governing roadmap (§0 carries a measured
  status update). `docs/spring-migration-plan.md` is superseded and kept for reference only.

## Modules

- **`account`** — the application. Depends on `controlsfx`.
- **`controlsfx`** — an in-repo shared library (not the public ControlsFX project): DAO base classes,
  connection management, alerts, table/column helpers, i18n, the observer `Publisher`, config encryption.
  Changes here affect every screen.
- **`fx-commons`** — an *external* dependency (`com.codejava.commons:fx-commons`) built from a separate
  GitHub repository, which CI checks out and installs before building. It is not in this tree.

## Architecture

### Startup

`Main` → `DownLoadApplication`. Its **constructor** does the wiring in order: read and decrypt `config.xml`,
initialise the Hikari pool, verify the database is reachable, run the trial/licence check, then register
every service in `ServiceRegistry`. `LogApplication` (login) opens from `start()`.

`ServiceRegistry` is a static `Map<Class<?>, Object>` service locator — there is no DI framework.
Controllers pull collaborators with `ServiceRegistry.get(SomeService.class)`, which returns null if
registration order ever changes.

### Database access

`DaoFactory` (an enum singleton) creates DAOs; it holds **no** connection. Every `AbstractDao` helper
borrows a connection from the HikariCP pool for the length of one call and returns it.

`ConnectionManager` binds a connection to the **calling thread** for the duration of a transaction. This is
what lets `insertMultiData` span several DAO objects — `TotalsSalesDao.insert` writes the header and then
calls `salesDao.insertList(...)` — while keeping them on one connection. Consequences worth knowing:

- Work reached through `AbstractDao` helpers automatically joins an open transaction on that thread.
- A nested `insertMultiData` joins the outer transaction and leaves the commit to it.
- Code needing the raw `Connection` must go through `AbstractDao.withConnection(...)`, never hold one.

**`TransactionTemplate.execute(...)` is the same thing said from the service side, and is what new code
uses.** `insertMultiData` puts the boundary inside a DAO, which is why `TotalsSalesDao.insert` opens a
transaction and then calls into other DAOs. That works, but the boundary belongs to the operation, not
to a table — so do not add an eighteenth `insertMultiData` site; wrap the service method instead. Both
routes share `ConnectionManager`, so they nest safely with each other.

Layering is `Controller → Service → DAO → AbstractDao`, and it is in the middle of a deliberate shift.
The older services under `service/` are thin `record X(DaoFactory)` wrappers with the real logic sitting
in controllers and DAOs. The newer work puts the logic in a `features/<area>/` package that has no
JavaFX at all and a test per class — `features/invoice`, `features/stockcount`, `features/inventory`,
`features/rbac`. **New behaviour goes there, not into a controller.** The test for whether it is in the
right place: can it be tested without starting a JavaFX toolkit?

### Authorization

`UserPermissionType` — the ~130-entry enum with ids hand-matched to table rows — **is gone**.
Permissions are now string keys: `AppPermissions.SALES_CREATE` is `key("sales.create")`, and adding one
is a single constant. No database id, no switch, no permission-screen edit; the metadata (module,
resource, action, risk) is derived from the key itself and synchronized on startup.

`AuthorizationGuard` is the single gateway, and it answers two different questions with two methods —
using the wrong one is the mistake to avoid:

- `isGranted(key)` returns a boolean and is for **UI hints** — hiding a button, disabling a menu.
- `require(key)` throws `BusinessRuleException` and is for **enforcement**. It belongs in the service
  layer, and there are ~57 calls to it in `service/` today.

**Hiding a button is not enforcement.** The old system only hid buttons, so anything that reached a
service another way was unguarded. `AuthorizationArchitectureTest` is what keeps that from coming back:
it fails the build when a service write path has no guard. Add the guard when you add the method.

Roles live in `auth_role` / `auth_role_permission` / `auth_user_role`, resolved by `RbacService` over
`JdbcRbacRepository`, with per-user overrides in `auth_user_permission_override`. The schema arrived in
`V11__rbac.sql` (import of every legacy grant), `V12__modern_authorization.sql` (rename to `auth_*`,
keys become dotted strings) and `V13__default_rbac_roles.sql`. `user_permission` is retained as
read-only legacy evidence — nothing reads it for decisions.

`CurrentUser.get()/getOrNull()` reads the signed-in user from `UserSessionContext` in `ServiceRegistry`.
It is **process-wide**, which is correct for a desktop app and is one of the things that has to change
before anything is served over a network — see `docs/new-code-rules.md`.

### Errors

`controlsfx.error` classifies what the user is allowed to see. `UserValidationException` (bad input) and
`BusinessRuleException` (a rule refused it, including every permission denial) carry messages meant for
the user and are shown as-is. Anything else is technical: `ErrorReporter` logs it behind a reference code
and shows a generic sentence, so a stack trace or a SQL fragment never reaches a screen.
`GlobalExceptionHandler` is the last boundary. `ErrorHandlingArchitectureTest` enforces the split, so
throwing a raw `RuntimeException` at a user-facing path fails the build.

### The generic invoice seam

`DataInterface<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>`
is the central abstraction. Four implementations in `interfaces/impl_dataInterface` — `CustomData`,
`CustomDataReturn`, `SuppliersData`, `SuppliersDataReturn` — let one set of controllers
(`BuyController2`, `TotalsController`, `AccountController2`) serve customer/supplier × sale/return. When
changing invoice behaviour, check all four implementations. The signatures above the interface are no
longer heavily generic - see the next paragraph for what replaced them and why.

**No consumer names T1 or T2 any more.** The four used to be copied onto every class that so much as
touched a `DataInterface` — 27 of them. Today every screen declares at most `<T3, T4>`:
`BuyController2`, `BuyData`, `ShowInvoiceController`, `TotalsController`, `TotalsService`,
`TotalsButton`, `NameController`, and the `view/*Application` classes name none at all. The line and
header types are held through wildcards (`DataInterface<?, ?, T3, T4>`) and never correlated, and
constructing the next controller down works because Java captures the wildcards — `new
NameController<>(...)` needs no help. **`MainItems` is the exception and has to be**: it constructs the
four implementations, so it names all four parameters.

Three things made that possible, and they are the pattern to reuse:

- **A generic collaborator is built inside the implementation and exposed through a plain method.**
  `DataInterface.saveInvoice(InvoiceSaveCommand): InvoiceSaveResult` — `InvoiceSaveService` stays fully
  generic, but the four classes construct it where T1/T2 are still `Sales`/`Total_Sales`, so the
  concrete type is never reconstructed from a wildcard and no cast is needed.
- **Two questions that had to be correlated are answered once, together.** Through wildcards, each call
  captures an independent type the compiler will not relate to another call's. `loadInvoiceHeader(int)`
  returns an `InvoiceHeaderView` with the party name, delegate, source invoice and entry time already
  resolved — a `default` method, since T2 is in scope inside the interface itself.
- **Where neither works, a checked cast, in the file that already knows the answer.**
  `TotalsDataInterface` and `TotalDesignInterface` are fixed on `BaseTotals`: `getCustomers()`,
  `getCustomer()`, `getSuppliers()` and `getSupplierData()` are four different methods on four different
  classes, and `TableColumn<S, ?>` is invariant in `S`, so there is no covariant escape the way there is
  for `List`. The eight classes in `interfaces/totals/` and `interfaces/impl_totalDesgin/` each carry one
  private `cast(BaseTotals)`. That is a JVM-verified cast on an ordinary class — not the unchecked
  generics cast the warning below is about.

**`DataInterface` itself still declares all four, and cannot stop until the models are one.** Each
parameter is bounded by exactly one base class, so widening them compiles at every *use* site - but the
four implementations override methods that take the concrete type (`InvoiceBuy.object_Totals` returns
`Total_Sales` and calls `setSalesList(List<Sales>)`), and a widened parameter no longer overrides.
Making it work would mean unchecked casts inside the implementations, which is the safety the generics
are there for. A single `Document` model removes the parameters for free; nothing short of it does.

**What the four are is declared in `account.document`, not spread over the screens.** `DocumentType`
(SALES, SALES_RETURN, PURCHASE, PURCHASE_RETURN) answers what a document *means* — whose account it
moves (`partyKind`), which half of the ledger it is on (`side`), which way it moves the stock and the
treasury (`stockSign`/`cashSign`), whether it carries a delegate, which period lock guards it, and its
five permissions. `DesignInterface.documentType()` is the one thing the four `impl_design` classes now
answer for themselves; `show()`, `update()`, `delete()`, `show_totals()`, `show_totals_invoice()` and
`showDataForCustomer()` are defaults that read it. That is why `BuyController2` no longer identifies a
sale by comparing its permission against `SALES_SHOW` — a permission was the only field that differed
between `DesignCustom` and `DesignCustomReturn`, and using it as an identity check is what a new
document type would have broken.

`DocumentTableSpec` is the other half: where a document's rows *live*. The four tables answer the same
questions with different words — the key is `invoice_number` on the two invoices and `id` on the two
returns, the party is `sup_code` or `sup_id`, what was settled in cash is `paid_up`,
`paid_from_treasury` or `paid_to_treasury`, and the item on a line is `num` or `item_id` — and every
statement over them is built from one place. The eight DAOs keep their `map` and their parameter
arrays, which are the parts that know a model; they no longer write their own SQL.

**The parties have the same seam.** `account.party.PartyTableSpec` says where a customer or a supplier
lives and what its columns are called, and builds every statement over it —
`CustomerDao` and `SuppliersDao` were the same file twice, down to the sixty-line three-phase search
(exact id or telephone → names starting with the text → names containing it). A supplier is a customer
without a credit limit and a price tier; everything else that differed was accident. Two asymmetries are
kept deliberately and are commented as such: the supplier's date column is `date_insert` where the
customer's is `created_at` — **was**, until `V10__supplier_created_at.sql` renamed the supplier's to
match, finishing what `V4` started when it renamed `custom`, `customers_accounts` and `items` and
stopped. The other nineteen tables keep `date_insert`: they are not paired with anything. The supplier's
searches do **not** join `table_area`. The customer's
join is a **`LEFT` join**: it is there to read the area's name, and it was an inner join, which dropped a
customer whose area row had been deleted out of every list and every search while a supplier in the same
state stayed. `PartyDaoStatementsTest` pins all of it.

`PartyLedgerSpec` does the same for the two account tables. Only **payments** live in
`customers_accounts` and `suppliers_accounts`; the invoice side of a statement comes from the view
(`account_customer_table` unions the payments with `total_sales`), which is why saving an invoice never
reaches the period lock there and is guarded at `TotalsSalesDao` instead. **`user_id` on a movement
records who *entered* it**, so both inserts write it and neither update does — the customer's update used
to, which meant the same edit restamped one payment and left the other alone. Who changed a row
afterwards is `audit_log`'s answer, written by a trigger whether the application asks or not.

A summary of a ledger comes in two forms and both are in the spec: `totalsSql()` reads the totals view,
which has already summed everything there has ever been, and `totalsBetweenDatesSql()` sums one period
itself — a total cannot be filtered after the fact. Both sides answer both now; the supplier screen used
to take the period filter and drop it, and the customer's dated statement selected seven columns while
its mapper read nine, so it failed in the mapper and the service logged it and returned an empty list.
An unknown `information` value is refused by `TableName.requireById` with the value in the message,
rather than reaching a caller as a bare `NullPointerException` from inside a row mapper.

**What a document does to an account and to a till is `DocumentLedgerEffect`, and it is one rule, not
four.** The cash column (`paid_up`/`paid_from_treasury`/`paid_to_treasury`) is what the treasury moved,
in the direction `DocumentType.cashDirection()` declares; whatever it did not cover (`net - paid`) went
onto the party's account, in the direction `DocumentType.ledgerSign()` declares. **It does not branch on
`invoice_type`** — a cash document simply stores `paid = net`, so its account effect works out to zero on
its own. Writing that branch by hand is what went wrong: the two account views and `treasury_balance`
each decided a deferred return for themselves and ended up reading its cash column with two opposite
meanings, one as the refund and one as the account credit. They were self-consistent under an
*undocumented* convention that the stored rows also followed, so client books balanced — but nothing said
so anywhere, and a partial refund produced nonsense. `V15__return_cash_split.sql` swapped the two halves
of every deferred return so the column finally means what its name says, and
`PartyLedgerViewAcceptanceTest` holds the three views to the rule against a real database.
`totalsBetweenDatesSql()` filters `purchase <> 0` rather than `> 0` for the same reason: a return's
`purchase` is negative, and `> 0` would report a period's receivables without the credits raised in it.

**Changing a column means changing the spec, and `DocumentDaoStatementsTest` will tell you.** It pins
every statement of all eight DAOs character for character, and pins the array bound to each against the
statement's parameter count. A repository merge that swaps two adjacent columns still produces valid
SQL — it just saves the discount as a stock id — so the pinning is the only thing standing between that
and a customer's database.

### Saving an invoice

The save path is no longer in the controller. `features/invoice` holds it, with no JavaFX anywhere in
the package and a test per class. The pieces worth knowing before changing anything there:

- **`InvoiceSaveService`** owns validation, calculation, construction and persistence — it is the
  coarse operation, and the right size for an endpoint if this is ever served over a network. Around it:
  `InvoiceSaveValidator`, `InvoiceLineAssembler`, `InvoiceLineTotals`, `InvoicePaymentTerms`,
  `InvoicePostSaveService`, `InvoicePrintService`.
- **`InvoiceNumberAllocator`** replaced "read the max and add one", which handed two users the same
  number. `JdbcInvoiceNumberAllocator` advances `document_sequences` (added by
  `V14__document_sequences.sql`) with `LAST_INSERT_ID(current_value + 1)`, which is atomic per
  connection. There is one counter per `DocumentType`; a missing row is an error, not a silent zero.
- **`InvoiceStockGuard`** is the last check before persistence. It serializes the whole stock effect of
  the document — every line, converted to base units — and refuses the save as a whole. It reads through
  `InvoiceStockRepository`, and `JdbcInvoiceStockRepository` already takes a `stock_id`.

### One warehouse

`stocks`, `items_stock` and the `stock_id` column on all four invoice tables still exist and every write
still carries a warehouse id — but **the multi-warehouse screens were removed** (commit `0853cf4`), and
nothing lets a user create a second stock. The id written is always `DefaultStock.ID`, the seeded
`'الرئيسي'` row.

**Every DAO that writes a `stock_id` must use that constant.** A different id produces rows no screen can
reach. The views that group by `stock_id` keep working precisely because every row carries the same one.

Three places would break the moment a second stock existed, and are documented in
`docs/erp-roadmap.md` §11: `mini_quantity_view` sums `items.first_balance` once per warehouse row, and
`ItemsDao.QUERY_ITEMS` joins `quantity_items_table` without `stock_id` so item rows would multiply.
Fix those before restoring anything.

### Expiry batches

An item with `items.item_has_validity` tracks expiry, and `InvoiceExpiryService` decides where the date
on a line comes from — the answer differs by direction, which is the part that surprises people:

- A document that moves stock **in** (purchase, sales return) is `MANUAL_ENTRY`: the user types the date.
- A document that moves stock **out** (sale, purchase return) is `EXISTING_BATCH`: the user picks from
  the batches actually on hand, and the service computes each batch's remaining quantity by subtracting
  what the unsaved document already consumes. No batch with stock left means the line is refused.
- An item without `item_has_validity` is `NOT_REQUIRED` and the column stays null.

Editing a saved document restores its own original quantities first (`captureOriginalLines`), otherwise
a line would be judged against a balance it is itself responsible for.

### Period locks and stock counts

`accounting_lock` (`V9`) closes a period, and `PeriodLockRegistry` declares every dated document the
lock protects — table, key column and date column for each: the four documents, both account ledgers,
expenses, and the stock count. `DocumentType.periodLock()` returns the right one, so a screen never
names a table.

The stock count is in that list although it is not money: posting one moves every balance on the sheet
at its own date, so a count posted into a closed month rewrites a valuation already reported.

`stock_count` / `stock_count_lines` (`V8`) with `features/stockcount`: a count is a dated document that
posts its differences once and is then read-only (`isEditable()` is the single answer every disabled
control hangs off). Only `POSTED` counts affect `quantity_items_table`, so counting in progress moves
nothing. This is what replaced correcting a balance by editing `items.first_balance` — which rewrote
what the opening balance *was*, silently changing every earlier report and recording nothing about who
did it.

### Units

An item is stocked in one **base unit** and may be bought or sold in others — قطعة, كرتونة, لفة, متر,
whatever the business uses. `service.ItemUnits` is the only place that answers what a unit means for an
item: `unitsFor`, `unitByName`, `baseUnit`, `toBase`/`fromBase`, `factor`. Nothing should read a factor
off a `UnitsModel` directly — `factor()` guards the zero and negative that would zero or reverse a stock
movement.

**The factor is per item, and lives in `items_units.quantity`.** `units.value_d` is one number for the
whole database and cannot say that a carton of juice is 12 while a carton of cigarettes is 200; it
survives only as the fallback for a unit with no row of its own, and as the default the item screen
offers when you pick a unit. The invoice screens used to scale by it, so every item shared one meaning
of "carton" — `V5__item_units.sql` is the migration that ended that, and it rebases any item whose own
unit had a factor above 1 (quantities on past lines are untouched; the balances and prices counted
against them move by the same factor, so the stock is the same, restated in the unit it is sold in).

`ItemsDao` prepends the base unit to `itemsUnitsModelList` with a factor of 1 — it is `items.unit_id`,
not a row in `items_units`, and a row there for the same unit is a duplicate (V5 deletes them and adds
`items_units_item_unit_uk` to stop more). `ItemUnits.unitsFor` therefore returns the base first, which
is what the invoice combo selects.

**A unit may also carry its own prices** — `items_units.buy_price`, `sel_price`, `sel_price2`,
`sel_price3` (the last two added by `V6__item_unit_prices.sql`; the tiers mirror the item's, since the
customer's price tier has to answer for a carton as much as for a piece). Zero means "no price of its
own" and falls back to the item's price × the factor, which is what every row held before the columns
were readable — so nothing was repriced by the migration. `ItemUnits.sellPrice`/`buyPrice` resolve it;
`hasOwnSellPrice` is what stops the invoice's "update the item's price as you type" option from
dividing an outright carton price by twelve and dragging the piece price down with it.

The base unit never has an override: it is `items.unit_id`, its row in the loaded list is the one
`ItemsDao` synthesizes, and `ItemsDao.saveUnits` filters it back out on the way to the database — which
is also why `items_units` rows are replaced wholesale on every item save, empty list included.

**A unit may carry its own barcode** in `items_units.items_barcode` (nullable since V5, so several
units of an item can go without one). An item therefore answers to three kinds of code — `items.barcode`,
`item_barcodes` (V3), and its units' — and `ItemsDao.findItemByStockIdAndBarcode` and the three
`getFilterItems` queries search all three. Which unit was scanned is answered from the item's already
loaded list by `ItemUnits.unitByBarcode`, not by another query; `BuyController2` selects it in the
combo, and selecting it is what fills in the price and balance. Each barcode table has its own unique
index and none can see the others, so `ItemsService.isBarcodeTakenByAnotherItem` is what stops one code
from belonging to two items — the item screen calls it for every code before saving.

An invoice line stores the factor it used in `type_value`, and `quantity_items_table` computes the
balance as `quantity * type_value`. That is deliberate: changing an item's factor later must not
silently rewrite what past invoices meant. Anything comparing quantities across rows — stock checks,
`StockLevelAlert` — has to convert with `toBase` first, since two rows of one item can be in different
units.

Two units of the same item may hold the same number of base units (a roll and a carton of twelve are
different things to sell); it is the unit that must not repeat.

**The units screen manages names.** `value_d` is presented there as "المعامل الافتراضي" — the number the
item screen offers when you pick that unit, nothing more — and left blank it is 1. A unit may be renamed
freely (lines reference it by id and carry their own factor), and may be deleted when nothing points at
it: `UnitsDao.isInUse` checks `items`, `items_units` and the four invoice tables. Unit 1 is exempt, being
the `DEFAULT` on every `type` column. The old rule — ids 1 and 2 can never be renamed or deleted — said
nothing about whether anyone relied on them, and left a business that sells nothing by the carton stuck
with the seeded "كرتونه".

### Cross-screen refresh

`controlsfx.observer.Publisher` + `DataPublisher` (a bag of publishers). Saving fires
`publish(message)`, or `publish()` where the event carries nothing — which is most of them. The
publisher keeps no state, so `publish()` hands observers `null`: an observer that reads its message
has to tolerate that, since the same publisher is usually fired both ways. `publisherAddUser` is the
one to remember — the users screen fires it as a bare signal, while the login path publishes the
user's name for the toolbar greeting. All observers are UI updates, so `Publisher`
dispatches on the JavaFX thread itself — background callers do not need `Platform.runLater`.
`AllAlerts` marshals to the FX thread the same way, so alerts are safe from any thread.

**Subscribe with `Subscriptions`, not `addObserver`.** `DataPublisher` lives as long as the main
screen — `MainItems extends DataPublisher`, and `MainScreenController extends MainItems` — while the
screens subscribing to it are rebuilt on every open. An observer left registered keeps its whole
controller and scene graph alive and re-runs its refresh (and its queries) once per past opening. So a
screen collects its handles and ends its setup with the node its life is tied to:

```java
subscriptions.subscribe(dataPublisher.getPublisherAddItem(), message -> btnRefresh.fire());
subscriptions.disposeWith(stackPane);   // last line of initialize()
```

**New events go on the `EventBus`, not into `DataPublisher`.** `controlsfx.observer.EventBus` is
registered in `ServiceRegistry` by the `DownLoadApplication` constructor and keyed by event type;
events are records implementing `AppEvent`, under `account.features.events`. A screen pulls the bus
from the registry rather than having a publisher threaded through its constructor, and the compiler
checks the payload — where a dozen `Publisher<String>` fields can only be told apart by which field
the caller picked. `UserRenamed`, `UsersChanged`, `InvoiceSaved`, `ItemSaved`, `ItemsChanged`,
`NameChanged`, `AccountChanged`, `GroupsChanged`, `AreasChanged`, `UnitsChanged`, `EmployeesChanged`
`ExpensesChanged`, `TreasuriesChanged`, `CompanyChanged` and `SelPriceNamesChanged` are migrated —
every domain event has moved. What is left in `DataPublisher` is five window signals (logout, the
login-screen setting, the totals box, the background image, the shift), which are window control
rather than something that happened to the business, and whose life is exactly the main screen's.
They are staying. `LoadDataAndList.updateData()` — the wholesale "everything was
replaced" announcement fired after a restore — is now a list of events and needs no publisher bag.
A table declares `refreshOn()` (its event) or `publisherTable()` (the old way), and `TableController`
subscribes to whichever is set; a table seeing only one side of an event narrows it with
`refreshFor(event)`.

The generic toolbar takes the same idea one step further. `ToolbarAccountInt` answers `changeEvent()`
and `eventBus()`, and `ToolbarAccountController` publishes the event after a save or a delete while
`ApplicationDataWithToolbarIndexApp` subscribes to `changeEvent().getClass()`. It is the event
*instance* rather than its type because the bus publishes instances and a generic component cannot
build one; the events are records, so a fresh one per call costs nothing. `eventBus()` is asked of the
screen because `controlsfx` cannot reach `ServiceRegistry`, which lives in `account`.

`InvoiceSaved` carries an `InvoiceSide` (PURCHASE or SALES) and replaced
`DataInterface.publisherPurchaseOrSales()`, which routed to one of two publishers to say the same
thing; implementations now answer `invoiceSide()` and listeners filter on it. The side is two-valued
on purpose: a return shares the side of what it reverses, exactly as it shared a publisher, so a
purchases screen still reloads when a purchase return is saved.

`NameChanged` and `AccountChanged` carry a `PartyKind` (CUSTOMER or SUPPLIER) and replaced the four
publishers that were one per event × side; `NameAndAccountInterface` answers `partyKind()` and every
listener filters on it — a customers screen must not reload because a supplier changed.

`ItemSaved` carries the item and always has one; a bulk change (the Excel import, or a full reload) is
`ItemsChanged` and carries nothing. That split is the point of the migration in miniature: one
`Publisher<ItemsModel>` served both, so every listener had to guard against a null whose meaning was
not written down anywhere.

`Publisher` and `EventBus` take an `Executor` for tests (`Runnable::run`), the way `NotificationCenter`
does — `PublisherTest` and `EventBusTest` need no JavaFX toolkit.

The bus lives for the whole process, and that removes a safety net worth knowing about: `DataPublisher`
belonged to the main screen and was thrown away at logout, so observers nobody unsubscribed died with
it. A bus listener does not — closing its subscription is mandatory, which is why the toolbar greeting
now keeps a `Subscriptions` and the publisher-based listeners beside it do not.

`Subscriptions.disposeWith` unsubscribes when the node leaves the scene graph (a closed tab) or its
window is hidden (a dialog or stage) — both are needed, since closing a stage leaves the scene attached
to its root, and a tab is detached without any window closing. Controllers extending `LoadData` inherit
the `subscriptions` field; the rest declare their own. The only classes that may still call
`addObserver` are the main screen and its toolbar, which are the publisher bag, and are commented as
such.

### Deleting

Two packages, and the rule for both is that what may be deleted is **declared**, not written out at
each delete.

`account.delete` handles one row. `DeleteRegistry` declares a `DeleteRule` per entity — the permission,
the ids that are never deletable, and the tables that would still point at the row — and
`DeletionService` applies it: permission first (no query at all if the user may not), then the protected
ids, then one counted query through `ReferenceScanner`, then the delete. It answers a `DeleteOutcome`
(`Deleted`/`Blocked`/`Protected`/`Denied`/`NotFound`), each carrying its own Arabic sentence;
`rowsOrThrow()` is the bridge for the screens that still expect a row count and an exception.

`account.wipe` handles whole tables — the "delete data" screen. `WipeCatalog` declares a `WipeTarget`
per option (its tables in delete order, its seed rows, and the targets that must go with it), `WipePlan`
resolves the closure into an ordered list of statements, and `WipeService` runs them **inside one
transaction with the foreign keys left on**. `DeleteDataController` generates its checkbox tree from
`requires`; there is no dependency graph written out in the controller and none in SQL.

**The rule that governs both catalogs: only declare a foreign key that is not `ON DELETE CASCADE`.** A
cascading key takes its rows with it, so declaring it refuses a delete the database performs happily —
`expense_salary.expenses_details_id` and `targeted_sales.delegate_id` are the two that catch people
out. `WipeCatalogTest` reads the keys straight out of the migration files and checks the declarations
and the ordering against them, so a wrong declaration fails the build rather than the customer's
database.

What this replaced is worth knowing, because the old shape still shows in places not yet moved: deletes
answered `0` from a `DaoList` default when a DAO had no delete at all, protections were spread across
`IllegalArgumentException` in some DAOs and `DaoException` in others and an `if (id == 1)` in a
controller, and the wipe was four stored procedures taking sixteen booleans that each switched
`FOREIGN_KEY_CHECKS` off — on a pooled connection, without restoring it on failure. The DAO-level
`id == 1` guards that remain are a last line for direct DAO callers, not the rule.

Deletes are audited by triggers, not by the application: `V2` and `V7` write the whole row into
`audit_log.old_data`. `write_audit_log` skips its insert while `@app_bulk_wipe` is set, which is how a
wipe avoids copying the database into the log on its way out — `WipeService` sets it and clears it
before the connection goes back to the pool.

### Merging items

`features/itemmerge` folds one item into another and deletes it: every line it ever appeared on is
repointed at the survivor first. It exists because before `item_barcodes` (V3) an item had exactly one
barcode, so five flavours of one packet were five items — and each carries years of real invoices that
cannot be deleted with the row. `docs/item-merge-plan.md` is the agreed plan and the decisions behind it.

**It writes no figure.** A document line carries its own price, buy price, profit and unit factor, and
the stock balance is a sum over those same lines (`quantity_items_table`), so moving a line changes
nothing but which item it is filed under. The single value written is the source's `items.first_balance`,
added to the target's — it is the only number never derived from a line, and its row is about to go.

**`ItemReferenceRegistry` is the whole correctness of it.** Twelve places name an item, and the schema
calls the column `num` on `sales` and `purchase`, `item_id` on their returns, `items_id` on
`items_units`, and twice on `items_package` — spread over four migrations. Missing one is not a visible
failure: four of them cascade, so those rows are destroyed with the source and that item's history in
that table is simply gone. `ItemReferenceRegistryTest` reads the foreign keys out of the migration files
and fails the build both ways — nothing in the schema undeclared, nothing declared that the schema does
not have. **Add a table with an item column and that test tells you, before a customer's database does.**

Four references cannot take a plain `UPDATE`, and each has a step of its own: `stock_count_lines` is
summed into the target's row (both `system_qty` and `counted_qty`, so the difference the counter found
survives), `items_stock` gains a row per warehouse the target lacks, `items_units` rows are **moved not
copied** (`UNIQUE(items_barcode)` is global, so a copy collides with the row it came from), and
`items_package` is repointed on both columns and then de-duplicated. Every code the source answered to
is kept on the target before the cascade takes it — the code printed on the old packet still has to find
something, which is the point of the exercise.

Two refusals, both cases where the moved lines would be arithmetically valid and still mean the wrong
thing: **a different base unit**, and **expiry tracking the target does not do**. A closed accounting
period is not one of them — no figure in it changes — but the preview says how many lines fall inside
one, and the log records it.

The source is deleted through `DeletionService` with `DeleteRegistry.ITEMS` rather than a `DELETE` of
its own, so a table this feature forgot refuses the delete and rolls the whole merge back. That is a
free second check on the registry, on every run.

`item_merge` / `item_merge_lines` (V17) are the record. Nothing else would remember: the audit triggers
are on `items`, `custom`, `suppliers`, `total_sales`, `total_buy` and `treasury`, **not** on the line
tables, so the rows that change hands leave no trace at all. Neither table has a foreign key to `items` —
the source is deleted by definition, and a key on the target would refuse to let that item be deleted
later on the strength of a log entry.

### FXML

Controllers carry `@FxmlPath(pathFile = "...")`; `OpenFxmlApplication` loads the FXML for a controller
instance. The ~56 FXML files live under `account/src/main/resources/com/hamza/account/view/`, and the
annotation's path is relative to that directory.

### Notifications

Engine in `controlsfx.notifications`, business rules in `account.features.notification`.

`NotificationCenter` (process-wide `getInstance()`) is the inbox. Publishing is safe from any thread —
it marshals to the FX thread like `Publisher` does. `NotificationPolicy` decides what happens *before*
anything reaches the inbox: a repeat of a key already there is folded into that entry (counter bumped,
moved to the top) rather than appended, and is not re-announced inside its cooldown. `CRITICAL` is exempt.
Mute is per-category, snooze is per-key. This is why the same low-stock condition polled every 30 minutes
produces one row, not one row per poll.

Two ways in:

- **A condition someone has to go and check** — implement `NotificationSource` (id, category, interval,
  `poll()`) and add it to `NotificationBootstrap.sources()`. It is then scheduled, mutable from the
  settings tab and listed there, with nothing else to change. `poll()` runs on a background daemon
  thread, may hit the database, must not touch JavaFX.
- **An event the code already knows about** — call `AppNotifications.info/success/warn/error/critical` or
  `withAction`. Constant key means repeats collapse into one entry; unique key means a row each. An event
  has no rule object, so its on/off switch goes in `NotificationPreferences.isEventEnabled(id, default)`
  and it routes by category.

`StockLevelAlert` is the event worth knowing about: it fires from `BuyController2.addData` when an item
goes onto a **sales** invoice at or below its minimum, at zero, or negative. Two things it gets right that
are easy to get wrong when touching it — the balance it judges is what remains *after* everything already
on the unsaved invoice (the call site converts its
rows to base units), and it is sales only, told apart from sales-returns by
`designInterface.show() == SALES_SHOW`, since `showDataForCustomer()` is true for both. Boundary logic
lives in `StockLevel.of(balance, miniQuantity)` and is covered by `StockLevelTest`; a minimum of zero
means "none set", not "everything is low".

Presentation is listeners, so a new channel does not touch the centre. Two ship: `NotificationToaster`
(in-app corner toast) and `WindowsNotifier` (AWT `SystemTray` balloon — tray icon created lazily on first
use, not at startup). `NotificationChannel` picks between them, resolved most-specific-first by
`NotificationPolicy.channelFor`: rule id → category → global default. Rule ids get onto notifications
because `NotificationScheduler` stamps them as they leave the source; anything published through
`AppNotifications` has none and routes by category.

Poll intervals are per rule and user-editable: `NotificationSource.interval()` is only the default, and
`NotificationScheduler.setInterval` overrides it and re-schedules live. Read the effective value with
`effectiveInterval(sourceId)` — `source.interval()` would show the built-in default even where it has been
overridden. Floor is `NotificationScheduler.MINIMUM_INTERVAL`.

`NotificationBootstrap.start()` is called from `MainToolbarController` after login — not from
`DownLoadApplication`, because the rules check the signed-in user's permissions. Re-entering it (logout →
login) clears the inbox, since the entries were produced under the previous user's permissions.

Settings live in Java `Preferences` via `NotificationPreferences`; nothing is persisted to the database.
`NotificationCenterTest` covers the policy, routing and interval resolution without a JavaFX toolkit — the
centre takes its UI executor and clock as constructor arguments for exactly that.

## Configuration and secrets

`config.xml` (database credentials, AES-encrypted) and `config.key` are **git-ignored** and resolved
against the **JVM working directory** — so they belong next to wherever the app is launched from, not
necessarily the repo root. `config.xml.example` documents the format.

Key resolution (`CryptoDatabaseConfig`): `ACCOUNT_CONFIG_KEY` env var → `config.key` file → a built-in
fallback key. **The fallback key is in the source**, and a `config.xml` encrypted with it was committed to
this repository historically, so any credentials it protects should be assumed public. Reading falls back
to it so existing installs keep working; writing refuses it. Values written now are AES/GCM and prefixed
`v2:`; unprefixed values are the older unauthenticated AES/ECB format and are still readable.

Never commit `config.xml`, `config.key`, `private_key.pem`, `license.dat`, or `secret_key.txt`.

## Database schema

Schema changes are **Flyway migrations**, in `account/src/main/resources/db/migration/`, applied by
`DatabaseMigrationService` from the `DownLoadApplication` constructor before anything touches the DAOs.

- `V1__baseline.sql` is the schema as shipped to clients in v4.1.3 — tables, indexes, procedures and the
  seed data (including the `admin` user, without which nobody can log in). It is the Flyway baseline: an
  existing client database is **stamped** with it, never executed, because it already is that schema. A
  new database executes it and continues with `V2`, `V3`, … The current head is `V17`.
- Everything after it is one file per change. **Never fold a migration back into `V1`** and never edit a
  migration that has shipped — a client that already ran it will not run it again, so the change would
  reach new installs only.

Adding a schema change is therefore one file: `V<n>__what_it_does.sql`. Both the upgrade path and the
fresh-install path pick it up, and Flyway derives the version — nothing to register in Java.

**Views, triggers and procedures are repeatable migrations, not versioned ones.** `R__views.sql` (33
views), `R__triggers.sql` and `R__procedures.sql` are re-run by Flyway whenever their checksum changes,
so **changing a view means editing it in place in `R__views.sql`** — do not write a `V<n>` that drops
and recreates one. This is what stops a client on an older schema from being left without a view that
newer code queries. Two conventions inside them:

- `DROP VIEW IF EXISTS` + `CREATE VIEW`, never `CREATE OR REPLACE VIEW`: the latter fails when the name
  is occupied by a base table and cannot change a view's column count.
- They run **after** all versioned migrations, so a view may reference a column added by the latest `V`.

The triggers are split for a reason worth knowing: the audit triggers on `users`, `custom`, `suppliers`,
`total_sales`, `total_buy` and `treasury` stay in `V2__audit_triggers.sql` and `V7__audit_delete_triggers.sql`,
because moving an already-applied versioned migration fails Flyway validation on live clients.
`R__triggers.sql` holds the ones added since. So when hunting a trigger, check all three files.

Three things the service adds around Flyway, all of which have bitten before:

- It creates the database if it does not exist, so a first-ever install needs only a reachable MySQL.
  `DataSourceProvider` sets `initializationFailTimeout(-1)` for the same reason — a fail-fast pool would
  throw on the missing schema before the migration could create it.
- It refuses to baseline a non-empty database that does not carry the v4.1.3 core tables, since stamping
  `V1` over a foreign schema records it as applied without creating anything.
- It runs `mysqldump` first when there is anything to apply to an existing database, and aborts if that
  fails.

Two traps in the tooling. Flyway must stay on the **11.x** line: 12.x pulls Jackson 3, which collides with
the Jackson 2 that `jasperreports` requires over the same `jackson-annotations` coordinates, and Flyway
dies at `Flyway.configure()`. And the shade plugin needs `ServicesResourceTransformer` — `flyway-core` and
`flyway-mysql` both register `META-INF/services/org.flywaydb.core.extensibility.Plugin`, and unmerged, the
packaged jar silently loses MySQL support.

Statements inside a `DELIMITER |` block are sent as one statement, so a bare `DROP PROCEDURE x;` sitting
between procedure definitions arrives glued to the `CREATE` after it. Close the block (`DELIMITER ;`)
around such statements; the old engine only got away with it via `allowMultiQueries=true`.

`scripts/main/*.sql` are the superseded manual bundle, kept for reference; `RunAllSqlScripts.bat` is no
longer the install path.

## Licensing

`TrialManager` (in `account/trial`) enforces a 7-day trial bound to the Windows `MachineGuid`, with trial
state mirrored between `%APPDATA%\HamzaAccount\trial.dat` and the `company` table, HMAC-signed, plus
per-record caps (10 items, 5 customers, 10 sales, 10 purchases) enforced inside the DAOs. A valid
`license.dat` short-circuits all of it at the first line of the check. `README.md` documents the licence
file format and the generation scripts in `scripts/`.

Tampering calls `failAndExit`, and `MAX_FAILS` is 1 — a false positive permanently blocks the install, so
be careful about adding failure paths here.

## Localization

`LanguageManager` (singleton) with bundles at `controlsfx/src/main/resources/i18n/messages*.properties`;
Arabic is the default and the choice persists in Java `Preferences`. Several settings (backup path,
interval, encryption password) also live in `Preferences`, not in files.
