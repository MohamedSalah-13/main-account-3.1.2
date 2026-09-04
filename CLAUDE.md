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
**1,257 tests across 141 test source files** — 98 in `controlsfx`, 1,159 in `account` — with 63 skipped (below). What is
genuinely covered:

- **The declarative specs, pinned character for character** — `DocumentDaoStatementsTest`,
  `PartyDaoStatementsTest`, `PartyLedgerStatementsTest`, `CardItemDaoStatementsTest`,
  `DocumentTableSpecTest`, `WipeCatalogTest`, `ItemMergeStatementsTest`,
  `ItemReferenceRegistryTest`. These fail the build on a wrong column, so they are the
  safety net for anything touching SQL. The last two read the foreign keys straight out of the
  migration files, so the schema itself is what they check against.
- **Architecture rules** — eleven `*ArchitectureTest` classes now, plus `DefaultRoleAcceptanceTest`:
  `AuthorizationArchitectureTest`, `ErrorHandlingArchitectureTest`, `DocumentPackageArchitectureTest`,
  `DefaultStockUsageArchitectureTest`, `LocalizationArchitectureTest`, `FxmlArchitectureTest`,
  `ModelPurityArchitectureTest`, `TableColumnArchitectureTest`, `StocksChangedArchitectureTest`,
  `FxmlWiringArchitectureTest`, `KeyboardNavigationArchitectureTest`. They
  fail when a new service skips the permission guard, a new exception escapes the error boundary,
  `account.document` starts importing one of the two packages that import it, or a new stock-aware
  operation reaches for `DefaultStock.ID` instead of taking a `stockId`. Two of them carry an explicit
  allow-list of files, reviewed once at the point the rule was written: adding a file to it is a
  decision made in the same review that adds the reference.
- **The invoice logic** — the `features/invoice` package has a test per class, all without a JavaFX
  toolkit.

What still has none: the controllers, the FXML screens, the reports, the trial logic, and most of the
`model/dao` write paths.

**The treasury acceptance tests were run for the first time on 2026-08-30**, against the developer's
own MySQL, and found two real defects that no unit test could have: the wallet-fee split was wrong
because *every new party payment had been silently discarded since `f2b4baf`* (see
`AccountCustomerService.isNew`), and one of the new cases was asserting a rollback it could not
observe from inside an enclosing transaction. Fifteen cases pass now, twice in a row, and the class
checks for its own residue rather than trusting the rollback.

**Fourteen classes do not run by default.** `InvoiceStockDatabaseAcceptanceTest`,
`DocumentLineDatabaseAcceptanceTest`, `StockLedgerReconciliationAcceptanceTest`,
`StockMovementBackfillAcceptanceTest`, `StockTransferDatabaseAcceptanceTest`,
`TotalDocumentDeleteReversesStockLedgerAcceptanceTest`,
`PurchaseDeleteReversesNonDefaultWarehouseBalanceAcceptanceTest`, `PartyLedgerViewAcceptanceTest`,
`ReturnSourceAcceptanceTest`, `ReturnableRepositoryAcceptanceTest`, `ItemMergeDatabaseAcceptanceTest`,
`TreasuryBalanceViewAcceptanceTest`, `ProfitDefinitionDatabaseAcceptanceTest` and
`ShiftAccountingDatabaseAcceptanceTest` are gated on
`-Daccount.db.acceptance=true` and need a reachable MySQL. A green `mvn clean test` does not run them.

**On 2026-08-31 the first thirteen were run together for the first time, and after one fixture fix
all pass: 1022 tests, nothing skipped.** Before that day the honest statement was that most of them
had never been run at all. **`ShiftAccountingDatabaseAcceptanceTest` is the fourteenth, added after
that run and first run on its own on 2026-09-04** — five cases, green twice, against a scratch schema
built from nothing, with both databases queried afterwards rather than the rollback trusted. It is
the shift system's only check against a real database. What the runs are worth knowing for:

- **It was run against a schema built from nothing**, not against the developer's database - which is
  also how the fresh-install defect behind `V1_1__audit_log_procedure.sql` was found. Build a scratch
  schema, migrate it, run, drop it; then query the real database for the fixture's own marks rather
  than trusting the rollback. It costs one throwaway class and removes the whole question.
- **`ItemMergeDatabaseAcceptanceTest` had been failing since 2026-08-28 and nobody could know.** It
  passed on 2026-08-25 when its fixture wrote the opening balance to `items.first_balance`; `fbadd53`
  moved `quantity_items_table` onto `items_stock.first_balance` three days later and the fixture kept
  writing a hard-coded zero there, so it claimed an opening of 5 against a view reading 0. The merge
  itself was right the whole time - it sums `items_stock.first_balance` per warehouse. **A gated test
  is not a passing test.** Run them after anything that moves a view or a balance.

`PartyLedgerViewAcceptanceTest` is the only check that the accounting views say what
`DocumentLedgerEffect` says, so **run it after touching `R__views.sql`** — the whole return-ledger defect
lived where no test could see it. `TreasuryBalanceViewAcceptanceTest` is the same kind of check on the
treasury side: it is the only thing that says `treasury_current_balance` produces the number a person
would reach with a pen. `ProfitDefinitionDatabaseAcceptanceTest` is the third of that family and the
only thing that says the profit and loss screen, the yearly report and both invoice lists report one
number — the text-matching `ProfitDefinitionTest` says they all read `document_profit`, not that the
figure is right. `ItemMergeDatabaseAcceptanceTest` is the only check that a merge leaves the surviving
item holding both histories.

**The merge one is safe to run on a working database; do not assume that of the other twelve.** It opens
one transaction and rolls it back in a `finally`, so even the audit triggers' rows go with it - and that
was checked rather than trusted: querying afterwards, `item_merge`, `item_merge_lines` and every `MRG-%`
barcode its fixtures create all counted zero. `ProfitDefinitionDatabaseAcceptanceTest` was checked the
same way and is the same shape, and it also refuses to run in a year that already holds documents,
since the views it reads group the whole database by date. The others have not been checked, and at
least one acceptance run has left rows behind in a development database before, so read the fixture
before pointing one at data you care about. Two things bite when running any of them: surefire's working
directory is the module, so the config read is `account/config.xml`, **not** the root `config.xml` -
they are different files - and `-pl account` needs `-am` or it builds against a stale `controlsfx`.

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
  `InputStream` fields; a service throws a message *key*, never an Arabic literal; a screen you
  touch leaves with its logic in a testable named class and its Enter order declared once with
  `Utils.whenEnterPressed` — a barcode scanner ends its read with an Enter, and 18 of the 23
  data-entry screens still swallow it. Each rule is
  meant to be pinned by an architecture test the way `AuthorizationArchitectureTest` already is —
  a rule without a test is a wish.
- **[`docs/treasury-plan.md`](docs/treasury-plan.md)** — the treasury and capital contract: what a
  balance is, why it is derived rather than written, and what phase D (wallet fees) still owes.
  Sections 14-16 record what was actually delivered. **Read it before touching anything under
  `account.treasury`, `features/treasury` or the treasury half of `R__views.sql`.**
- **[`docs/shift-plan.md`](docs/shift-plan.md)** — the shift contract: why the default is
  `DISABLED`, why a shift belongs to a till rather than to a person, what `ShiftGate` guards, and
  which of the append-only journals may never be written twice. §8 lists what is deliberately still
  undecided and §10 what the system has no test for. **Read it before touching anything under
  `features/shift`, `UserShiftService` or the `V22`-`V33` tables.**
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

`Main` → `DownLoadApplication`. Its **`start(Stage)`** does the wiring in order, on a background
`Task` named `application-bootstrap` behind a loading screen — not in a constructor, and not on the
JavaFX thread: read and decrypt `config.xml`,
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

**A screen that lists many items must not build its rows with `ItemsDao.map`.** That mapper
resolves an item's sub group (which resolves its main group), its base unit, its unit list (whose
own mapper resolves a unit and a user per row), its extra barcodes and its warehouse — each with a
query of its own, per row. A page of fifty items cost several hundred round trips, paid on whichever
thread asked for the page. `getCatalogProducts`/`getCatalogItem`/`getCatalogCount` map through
`ItemsCatalogLookups`, a snapshot of the three small lookup tables read once per query, and leave
out what a list does not show. `map` stays for the finders, which load one item and need all of it.

Two things follow from that split and both have bitten. **A catalog row must never be handed to
`ItemsDao.update`**: it carries no units and no extra barcodes, and that method replaces both from
the model, so saving one deletes them — which is what setting an item's picture from the list used
to do. Edit a list row through `quickUpdate` or `updateImage`, or load the item again through
`findItemById`. And **the page query and its `COUNT` are built from one `WHERE`**
(`ItemsDao.catalogQuery`, pinned by `ItemsCatalogQueryTest`): filter them separately and the
pagination control starts describing a different set of rows than the table shows.

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
service another way was unguarded. `AuthorizationArchitectureTest` is what keeps that from
coming back, and it now says so in two independent ways: `controllersDoNotWriteBusinessRowsDirectlyThroughDaos`
fails the build when a screen reaches a DAO write without crossing a service, and
`serviceWritePathsAskPermissionFirst` fails it when a service method writes a row without calling
`require` — per method, so a class where `delete` is guarded and `open` is not still fails. Add the
guard when you add the method.

**Only the first of those two existed until 2026-08-31, while this file described the second.** A
service method with no `require` passed every check there was, and two of them did: `openShift` and
`closeShift`, which any signed-in user could call for anyone. **Both are guarded now**, and the debt
they represented is paid: `WRITES_WITHOUT_A_GUARD` is down to the two entries that are legitimate —
a read that seeds the company row, and the wallet fee whose only callers guard first. The list fails
the build in both directions, so it cannot become fiction: a new unguarded write fails it, and so
does an entry that has quietly been fixed.

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

**`InvoiceBuy` and `TotalsAndPurchaseList` live there too**, and moved out of
`interfaces.api` for a reason worth keeping: `DataInterface` exposes the save operation as
`saveInvoice(InvoiceSaveCommand)`, so `interfaces.api` has to see `features.invoice`'s two
records - and `InvoiceSaveService` used to need these two interfaces back out of
`interfaces.api`, which made the two packages depend on each other. `account.document`
imports neither and both already imported it, so it is the one place that breaks the cycle
rather than moving it. It also happens to be where they belong: how a document family
builds a line and a header, and how it reads its own rows, is the same kind of per-family
declaration as `DocumentType` and `DocumentTableSpec`. **Keep `account.document` free of
imports from `interfaces.*` and `features.invoice` - that is the property the whole
arrangement rests on.**

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

### The quick invoice

The same four document families open in one of two screens: `InvoiceScreenMode.STANDARD`, which
has a barcode/name/price/quantity form above the table, and `QUICK`, which hides that form and makes
**the table itself the only entry surface**. F6 switches; both are `BuyController2` with the same
save path, the same guards and the same `DataInterface`.

The quick screen keeps a trailing **entry row** for the operator to scan into. Two rules make that
safe, and both were missing when it was first written - the screen could not be saved at all:

- **The entry row is a control, not a sale.** `InvoiceLineTotals.isPlaceholder` (a row naming no
  item) is what the totals, the line count and `linesForSave()` filter it out by. It used to be
  counted, so `hasInvalidLine` - which `btnSave.disableProperty()` is bound to - was permanently
  true, and `comboStock` was permanently disabled for the same reason (`isNotEmpty(getItems())`).
  Anything reading a total must go through the summary, never through `table.getItems().size()`.
- **A line is added through `BuyController2.addLine`, from either screen.** That method is where the
  line validation, the expiry-batch dialog, the repeated-item merge and the low-stock alert live.
  The quick screen used to set the fields on its entry row directly and so had none of them: an item
  with `item_has_validity` could be scanned onto a quick invoice and was then refused at save with no
  way to supply the date.

`QuickInvoiceTable` owns the entry row, the cell editors and the keyboard flow (its javadoc carries
the key table); the barcode and name cells open **only** on the entry row, so there is one code path
for adding a line and none for changing one in place. `ItemSuggestionField` is the name search on
both screens - a debounced, background, keyboard-driven suggestion popup that replaced the read-only
field plus modal table. Its `chosenName` property, not its text, is what
`InvoiceItemEntryCoordinator` listens on: the listener resolves a whole line, so it must fire once
per choice and not once per keystroke.

**The decisions that class makes are not in it.** `features/invoice/QuickEntryRules` answers them over
a plain list — is this row the entry row, may it be deleted, does Ctrl + reach it, which cell does
Enter open, where does the caret land after a line is added — and `QuickEntryRulesTest` pins each. A
table, a focus model and a cell editor all need a running toolkit, so a rule written inline in
`QuickInvoiceTable` is a rule nothing can check, and every one of these was a defect at some point in
this screen's short life. What is left there is the JavaFX: the editors, the accelerators, and the
two nested `runLater`s that an edit queued behind a closing cell editor needs. `ItemSuggestionField`
is still uncovered, and deliberately: its one testable rule is a token comparison that drops answers
to superseded queries, woven into a focus check.

### Warehouses

There are several again. The screens were removed once (commit `0853cf4`) and **came back in
`fbadd53`, backed by invariants the first pass had not had**: the stocks screen, warehouse transfers,
and per-warehouse awareness in the invoice, inventory, card and stock-count screens. `stocks`,
`items_stock` and the `stock_id` column on all four invoice tables were never dropped, which is why
restoring the screens needed no data migration.

**`DefaultStock.ID` no longer means "the only one" — it means "which one, if nothing else says".** An
operation that reads or writes a specific warehouse's balance takes a `stockId`; the constant answers a
combo's initial selection, a compatibility overload kept for an old caller, or the one opening-balance
field the item screen has never had a picker for. That distinction cannot be checked by a regex, so
`DefaultStockUsageArchitectureTest` carries the list of files allowed to reference it at all: a new file
that reaches for it instead of threading a real `stockId` through fails the build.

Three things had to be true before any of this was safe, and all three now are:

- **A new warehouse backfills `items_stock` for every existing item, and a new item for every existing
  warehouse** (`StockDao`, `ItemsDao`, `Items_StockDao`). `quantity_items_table` is driven by
  `items_stock`, so a missing row is a silently dropped balance. `V18__warehouse_opening_balances.sql`
  backfills the warehouses that predate the change.
- **A catalog query must not multiply rows.** `quantity_items_table` is keyed by (item, stock), so
  joining it to `items` returns one row per warehouse. `ItemsDao` now has two joins and the choice is
  the point: `QUERY_ITEMS_ALL_STOCKS` pre-aggregates by `item_id` for every query that names no stock,
  and the raw `QUERY_ITEMS` is only for finders that already scope with `ip.stock_id = ?`.
- **A transfer line carries the unit and factor it was entered in**
  (`V19__stock_transfer_units.sql`), converts to base units before checking the source balance, is
  refused inside a closed period (`PeriodLockRegistry.STOCK_TRANSFER`), and is reversed through
  `DeleteRegistry`/`DeletionService` rather than by a delete of its own.

`mini_quantity_view`'s company-wide total is deliberately a *different question* from the per-warehouse
check the sale-time low-stock alert makes, and is documented as such rather than "fixed".

### The treasury

Several treasuries are an everyday case - a cash drawer, an e-wallet (فودافون كاش، انستاباي), a bank
account - and every cash document has always carried a `treasury_id`. What was missing until
`docs/treasury-plan.md` was worked through is everything around that column.

**A balance is derived, never stored, and there is exactly one place it comes from:**
`treasury_current_balance` = the opening balance + everything in - everything out. There used to be
three answers - `treasury.amount` (written once at insert and never updated), `treasury_balance` (the
documents, without the opening balance or the transfers) and `treasury_balance_after_convert` (the
opening balance and the transfers, without the documents) - and the screens read different ones. The
third view is dropped; do not reintroduce a fourth.

- **`treasury.amount` is the opening balance.** Not the current one. The column carries a COMMENT
  saying so since `V20`, and editing it needs `treasury.opening` on top of `treasury.update`, checked by
  comparing against the stored row rather than by trusting a screen to ask.
- **`treasury_movements` is deliberately dead.** It is a complete cash ledger with `balance_after`,
  designed and never wired - and wiring it now would be a fourth definition of a balance to reconcile.
  It belongs to the general ledger (§9 of the roadmap), not to this. Nothing may write a row to it.
- **The Arabic literals `treasury_balance` writes into `information` are `MovementLabel`**, and
  `MovementLabelTest` reads them out of `R__views.sql` and fails both ways. The statement screen compares
  that column with `equals()`, so translating either side silently empties every filter on it.

**What writes:** `TreasuryTransferService` and `TreasuryCashService`, each refusing in a fixed order -
permission, then the period lock, then the arithmetic, then the balance. The balance is derived, so
checking it and then inserting is a read-then-write on a number nothing holds still: the source treasury
is locked with `SELECT … FOR UPDATE` first, the way `StockTransferDao.lockSource` does.

Both are **reintroductions, not new features**, and the history is the point. Screens for both existed
until `8376368` (2026-08-10, shipped in v4.3.0) removed them - controller, DAO, service, domain and
FXML - and kept the tables deliberately: "a migration that dropped them because no Java reads them any
more would take the rows of every install that already has them". What went was a transfer service
whose whole body was `getById` and `delete`: no permission, no period lock, no balance check, and the
insert done from the controller. So between 4.3.0 and this work the views, the delete rules and the
period-lock rules stood over tables nothing could write - which is why the shift report was showing
"total deposits" over rows no screen could create any more. The rules are what the second attempt adds;
the data is what the first one was careful to keep.

**The owner's money is not the business's.** Capital paid in is not income and drawings are not an
expense: counted as either, the treasury still balances and the profit - the number the owner reads - is
wrong by the whole amount. `treasury_deposit_expenses.category` (`NORMAL`/`CAPITAL_IN`/`OWNER_DRAW`,
`V21`) says which, a CHECK ties each category to its only possible direction, and
`ProfitLossExcludesCapitalTest` fails the build if `ProfitLossDao` ever reaches into that table. That
last one was a structural accident before it was a rule - one `UNION ALL` added "so deposits show up"
would have ended it.

**Statements live in `account.treasury.TreasuryStatements`** and are pinned character for character,
including each one's parameter count: a delete with the wrong count is a delete of everything.

**An e-wallet fee is an expense, never a deduction.** A customer settling 1000 on فودافون كاش has paid
1000 and their account closes by all of it; the wallet keeps its percentage
(`treasury.fee_percent`), and that is posted as an expense on the same treasury under the heading
`V21` seeds. Netting it off the collection instead would leave that customer owing the fee for ever, on
every wallet payment they make. The payment and the fee are written in **one transaction** by
`AccountCustomerService.save(account, fee)` / `AccountSupplierService.save(account, fee)`, and only on
insert - editing a payment leaves its fee row alone.

### Shifts

A cash drawer answered for by whoever is on it. `docs/shift-plan.md` is the contract; the four things
to know before touching any of it:

**It is off by default and that is load-bearing.** `ShiftPolicy.mode` is `DISABLED` / `OPTIONAL` /
`REQUIRED`, seeded `DISABLED`, and `ShiftGate` returns `OptionalInt.empty()` immediately in that
mode - so an existing install upgrades through twelve migrations and notices nothing. Per treasury
there is a second switch, `TreasuryShiftPolicy.trackingMode` (`NONE` / `TRACK_ONLY` / `RECONCILE`),
and `NONE` disables shifts on that till whatever the global mode says.

**A shift belongs to a treasury, not to a user.** `user_shifts.treasury_id`; two tills open at once
are two independent shifts, and one till may never carry two. The expected balance is *computed*
from the movements, never stored - the same rule as `treasury_current_balance`, for the same reason.

**`ShiftGate` is the single gate, and six services pass through it**: `InvoiceSaveService`,
`TreasuryCashService`, `TreasuryTransferService`, `AccountCustomerService`,
`AccountSupplierService`, `ExpensesDetailsService`. **A new service that moves treasury cash goes
through it too** - nothing fails the build if you forget, which is the gap `shift-plan.md` §10
names first. Its `requireCashCorrection`/`requireTreasuryCorrection` pair exists so a movement
already attributed to a shift cannot be deleted unattributed once the mode relaxes to `OPTIONAL`.

**Everything the system records is append-only**, enforced by triggers in `R__triggers.sql` that
refuse `UPDATE` and `DELETE` outside `@app_bulk_wipe`: `shift_cash_ledger` (with a numeric
`ShiftCashSource`, never a translated label - the `MovementLabel` lesson), the close snapshot, and
the handover/override/variance tables. A new fact table here gets the same triggers and a
`WipeCatalog` entry, and `WipeCatalogTest` reads the migrations to check you did.

Two things that are separate and were once wrongly coupled: **settling the till's variance** depends
only on there being a difference, while **declaring a handover** depends on an enabled handover
policy. A treasury that reconciles but never hands its cash on still has to square its own drawer.

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

**A sold line records its cost through `ItemUnits.buyPrice` as well, and that is the figure the
profit is.** `SalesInvoice`/`SalesInvoiceReturn` used to write `items.buy_price * type_value` into
`buy_price`, so a unit bought at its own price was costed at a number the business never paid and
`document_profit` was wrong by the difference on every line sold that way - while the same sale was
validated against `ItemUnits.buyPrice`, so the floor and the recorded cost disagreed.
`SalesLineCostTest` pins it. The factor is guarded on the same principle: `InvoiceLineAssembler`
normalises every persisted line's unit through `ItemUnits.factor`, because the four DAOs write
`type_value` straight from `getUnitsType().getValue()` and `quantity_items_table` multiplies a
balance by it - a hand-built `UnitsModel` carries zero, which would persist a line that moves no
stock at all.

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

### Scale barcodes

A shop scale prints its own barcode with the item and a weight inside it, and
`features/scalebarcode` is where that is read: `ScaleBarcodeFormat` (the layout),
`ScaleBarcodeParser`, `ScaleBarcodeCheckDigit`, `ScaleBarcodeAmounts`, and `ScaleBarcodeService` for
the part needing the database. It has no JavaFX and a test per class, which is the point — the same
logic used to sit in `otherSetting/BarcodeProcessor` holding the parsing, the arithmetic and the
lookup at once, so none of it could be tested while it decides a line's quantity and price.

Two settings are easy to misread, and the first was misread on screen for a long time:

- **`setting.barcode.count.scale` is how many digits the scale's prefix occupies, not the digits of
  the weight.** The settings tab labelled it as the weight and the parser read it as the prefix, so
  a user who set it to what the label asked for pushed the prefix from `27` to `27000` and no scale
  barcode was recognised again. Read it through `getSettingBarcodeScaleCodeDigits`, which is named
  for what it is; the stored key keeps its old name so existing installs keep their value.
- **Carrying a check digit and verifying it are different questions.**
  `getSettingBarcodeHasCheckDigit` is a position in the layout and defaults to true, because the
  parser used to subtract one unconditionally; `getSettingBarcodeValidateCheckDigit` is whether its
  value is checked. Conflating them cost the last digit of every weight on a scale that prints none.

The weight's width is derived, not stored: the parts must add up to the barcode's length, so
`ScaleBarcodeFormat.deriveValueDigits` takes the total and works the rest out, and `problemKey()`
reports a layout that cannot add up rather than throwing out of a `substring`.

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
registered in `ServiceRegistry` during `DownLoadApplication`'s bootstrap task and keyed by event type;
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

**A MySQL password was committed to this repository and is in the pushed history.**
`scripts/main/RunAllSqlScripts.bat` carried `set "PASS=m13ido"` as its default from `01c7ac8`
until 2026-08-31. The script no longer has it — it requires a password and takes it from
`MYSQL_PWD`, and no longer passes `--password=` on any command line, where it was visible in the
process list — but removing it from the file does not remove it from git. **Treat that password as
public and change it on any server where it was ever used.** This is the second credential of this
kind, after the `config.xml` encrypted with the built-in key above; both are in history, and the
lesson is the same one twice.

The same fix reached the code on 2026-09-04: `BackupService` (both the dump and the restore) and
`DatabaseBackupService` pass the password through `MYSQL_PWD` in the child's environment. Where the
tools live is `MysqlTools`, which is the single answer — there used to be three, and on a machine
without MySQL on the PATH the pre-migration dump worked while every backup the user asked for
failed.

**A restore takes its own copy first.** `BackupService.restoreFromFile` writes
`before-restore_<timestamp>.enc` beside the backup being restored and refuses the restore if it
cannot — the import runs `DROP TABLE` over the live schema, so a run that fails halfway leaves
neither the old contents nor a complete new set. It is encrypted with the password that has just
been proved to open the backup, so it is an ordinary backup file the same screen restores.

**Also: `DatabaseMigrationService` runs `mysqldump` before applying anything, and those dumps land
in `backups/` and `account/backups/` inside the repository.** They are full database contents —
every customer, every invoice, and any row holding a credential. They are git-ignored today; check
that before adding a path, and never relax it.

## Database schema

Schema changes are **Flyway migrations**, in `account/src/main/resources/db/migration/`, applied by
`DatabaseMigrationService` from the `DownLoadApplication` constructor before anything touches the DAOs.

- `V1__baseline.sql` is the schema as shipped to clients in v4.1.3 — tables, indexes, procedures and the
  seed data (including the `admin` user, without which nobody can log in). It is the Flyway baseline: an
  existing client database is **stamped** with it, never executed, because it already is that schema. A
  new database executes it and continues with `V2`, `V3`, … The current head is `V33`. Recent shift
  migrations `V22`–`V33` add treasury-scoped shifts, optional policy, immutable cash journals and close
  snapshots, dual approval, cashier permissions, per-cashier treasury assignments, append-only
  assignment history, optional two-person cash handover, close variance settlement, and audited opening
  overrides. The last four migrations before that work:
  `V18` backfills `items_stock` for warehouses that predate multi-warehouse returning, `V19` gives a
  transfer line its unit and factor, `V20` gives a treasury a type and declares `amount` to be the
  opening balance, and `V21` gives a hand-entered cash movement a category so the owner's capital is
  neither income nor expense.
- Everything after it is one file per change. **Never fold a migration back into `V1`** and never edit a
  migration that has shipped — a client that already ran it will not run it again, so the change would
  reach new installs only.

Adding a schema change is therefore one file: `V<n>__what_it_does.sql`. Both the upgrade path and the
fresh-install path pick it up, and Flyway derives the version — nothing to register in Java.

**A repeatable migration cannot be a prerequisite of a versioned one, and `V1_1` is the scar.**
Repeatables run *after* every versioned migration, so anything a versioned migration can reach —
directly, or through a trigger it fires — has to be created by a versioned one.
`write_audit_log` was not: it lived only in `R__procedures.sql`, while `V1` and `V2` create the
eighteen audit triggers that call it. Nothing noticed for nineteen migrations because none of them
wrote to an audited table; `V20`'s `UPDATE treasury SET opening_date` does, and **a brand-new
database died there**, half-built, with `PROCEDURE write_audit_log does not exist`. Existing clients
were never affected — they are stamped at `V1` and already hold the procedure — so this broke only
a first install, the one case nobody runs twice. `V1_1__audit_log_procedure.sql` is numbered below
`V20` on purpose: a `V22` would run after the migration it is fixing and fix nothing. A client
already past it meets a pending migration below its current version and ignores it, which is
correct. The definition stays in `R__procedures.sql` too, and **that copy is still the one to
edit** — the versioned file is a snapshot, the repeatable has the last word.
`AuditProcedureMigrationTest` pins all of it.

**Views, triggers and procedures are repeatable migrations, not versioned ones.** `R__views.sql` (33
views; `treasury_balance_after_convert` was removed from it, and the `DROP` for it stays because a
client that ran an older copy still has it), `R__triggers.sql` and `R__procedures.sql` are re-run by Flyway whenever their checksum changes,
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
