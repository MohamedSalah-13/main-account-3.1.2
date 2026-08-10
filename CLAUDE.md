# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A JavaFX 21 desktop accounting/POS application for Arabic-speaking businesses (invoicing, inventory,
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

**Test coverage is almost nothing.** JUnit 5 and Mockito are declared in the root pom and inherited by both
modules, and surefire needs no configuration, but the whole suite is `CryptoDatabaseConfigTest` and
`NotificationCenterTest` in `controlsfx`, plus `ScheduledBackupTest` and `PasswordHasherTest` in
`account`. Everything else — the DAO layer, the controllers, the trial logic — has none. A passing build
therefore says very little: do not describe a change as verified on that basis, state what was and was not
checked, and remember that verifying most behaviour still means running the app against a database.

**Always `clean` when verifying a change.** Incremental builds frequently report
`Nothing to compile - all classes are up to date` and silently skip your edits, so a plain `mvn compile`
can "succeed" without ever compiling them.

**Lombok error cascades.** One unrelated compile error aborts annotation processing, and every generated
getter/setter then reports `cannot find symbol` — hundreds of errors across untouched files. Fix the first
genuine error and the rest disappear; do not chase them individually.

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

Layering is `Controller → Service (mostly thin records) → DAO → AbstractDao`. Services add little; the real
logic is in controllers and DAOs.

### The generic invoice seam

`DataInterface<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>`
is the central abstraction. Four implementations in `interfaces/impl_dataInterface` — `CustomData`,
`CustomDataReturn`, `SuppliersData`, `SuppliersDataReturn` — let one set of controllers
(`BuyController2`, `TotalsController`, `AccountController2`) serve customer/supplier × sale/return. When
changing invoice behaviour, check all four implementations, and expect heavily generic signatures.

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

The POS screen stays on the base unit: it has no barcode lookup (its search is an in-memory name index)
and its rows carry no unit column.

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

### FXML

Controllers carry `@FxmlPath(pathFile = "...")`; `OpenFxmlApplication` loads the FXML for a controller
instance. The ~69 FXML files live under `account/src/main/resources/com/hamza/account/view/`, and the
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

`StockLevelAlert` is the event worth knowing about: it fires from `BuyController2.addData` and
`PosController.addDataToTable` when an item goes onto a **sales** invoice at or below its minimum, at
zero, or negative. Two things it gets right that are easy to get wrong when touching it — the balance it
judges is what remains *after* everything already on the unsaved invoice (each call site converts its own
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

- `V1__baseline.sql` is the schema as shipped to clients in v4.1.3 — tables, indexes, 32 views, 8
  triggers, 6 procedures and the seed data (including the `admin` user, without which nobody can log in).
  It is the Flyway baseline: an existing client database is **stamped** with it, never executed, because
  it already is that schema. A new database executes it and continues with `V2`, `V3`, …
- Everything after it is one file per change. **Never fold a migration back into `V1`** and never edit a
  migration that has shipped — a client that already ran it will not run it again, so the change would
  reach new installs only.

Adding a schema change is therefore one file: `V<n>__what_it_does.sql`. Both the upgrade path and the
fresh-install path pick it up, and Flyway derives the version — nothing to register in Java.

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
