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

### Cross-screen refresh

`controlsfx.observer.Publisher` + `DataPublisher` (a bag of publishers). Saving fires
`publish(message)`, or `notifyObservers()` where there is nothing to send — note that the no-argument
form re-sends whatever was published last, which is `null` for most of these publishers, so an
observer that reads its message must tolerate null. All observers are UI updates, so `Publisher`
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

`Subscriptions.disposeWith` unsubscribes when the node leaves the scene graph (a closed tab) or its
window is hidden (a dialog or stage) — both are needed, since closing a stage leaves the scene attached
to its root, and a tab is detached without any window closing. Controllers extending `LoadData` inherit
the `subscriptions` field; the rest declare their own. The only classes that may still call
`addObserver` are the main screen and its toolbar, which are the publisher bag, and are commented as
such.

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
