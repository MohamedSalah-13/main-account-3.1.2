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
**1,827 tests across 247 test source files** with 101 skipped (below) — the figure `mvn clean test`
reports, measured on 2026-09-11. What is
genuinely covered:

- **The declarative specs, pinned character for character** — `DocumentDaoStatementsTest`,
  `PartyDaoStatementsTest`, `PartyLedgerStatementsTest`, `CardItemDaoStatementsTest`,
  `DocumentTableSpecTest`, `WipeCatalogTest`, `ItemMergeStatementsTest`,
  `ItemReferenceRegistryTest`. These fail the build on a wrong column, so they are the
  safety net for anything touching SQL. The last two read the foreign keys straight out of the
  migration files, so the schema itself is what they check against.
- **Architecture rules** — eighteen `*ArchitectureTest` classes now (twenty files: `ErrorHandlingArchitectureTest`
  and `TableColumnArchitectureTest` exist once per module), plus `DefaultRoleAcceptanceTest`:
  `AuthorizationArchitectureTest`, `ErrorHandlingArchitectureTest`, `DocumentPackageArchitectureTest`,
  `DefaultStockUsageArchitectureTest`, `LocalizationArchitectureTest`, `FxmlArchitectureTest`,
  `ModelPurityArchitectureTest`, `TableColumnArchitectureTest`, `StocksChangedArchitectureTest`,
  `FxmlWiringArchitectureTest`, `KeyboardNavigationArchitectureTest`, `ShiftGateArchitectureTest`,
  `MessageKeyArchitectureTest`, `ConnectionTimeZoneArchitectureTest`, `MultiDeviceRefreshArchitectureTest`,
  `PasswordChangeArchitectureTest`, `ProductProfileWiringArchitectureTest` and
  `TreasuryStatementScreenArchitectureTest`. They
  fail when a new service skips the permission guard, a new exception escapes the error boundary,
  `account.document` starts importing one of the two packages that import it, or a new stock-aware
  operation reaches for `DefaultStock.ID` instead of taking a `stockId`. Two of them carry an explicit
  allow-list of files, reviewed once at the point the rule was written: adding a file to it is a
  decision made in the same review that adds the reference.

  **A translation key named in Java is checked the same way an FXML `%key` is.**
  `MessageKeyArchitectureTest` scans the argument list of every `getString`/`text`/`tip`/`Columns.*`
  call in both modules — 985 distinct keys — and fails when one is absent from any of the three
  bundles. It was written because ten keys shipped in the master-data work with no translation
  anywhere, two of them table column headings: `LanguageManager.getString` answers a missing key
  with the key itself and a log warning, so the screen reads `masterdata.count.items` and nothing
  in the build notices. The one rule it imposes on new code: **write a key as one whole literal** —
  a ternary between two keys is fine, `"prefix." + suffix` is invisible to any static check.
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

**Twenty-two classes do not run by default.** `InvoiceStockDatabaseAcceptanceTest`,
`DocumentLineDatabaseAcceptanceTest`, `StockLedgerReconciliationAcceptanceTest`,
`StockMovementBackfillAcceptanceTest`, `StockTransferDatabaseAcceptanceTest`,
`TotalDocumentDeleteReversesStockLedgerAcceptanceTest`,
`PurchaseDeleteReversesNonDefaultWarehouseBalanceAcceptanceTest`, `PartyLedgerViewAcceptanceTest`,
`PartyStatementViewAcceptanceTest`, `PartyMovementDatabaseAcceptanceTest`,
`ReturnSourceAcceptanceTest`, `ReturnableRepositoryAcceptanceTest`, `ItemMergeDatabaseAcceptanceTest`,
`TreasuryBalanceViewAcceptanceTest`, `ProfitDefinitionDatabaseAcceptanceTest`,
`ShiftAccountingDatabaseAcceptanceTest`, `ItemGroupMoveDatabaseAcceptanceTest`,
`MasterDataDuplicateAcceptanceTest`, `ItemsCatalogBalanceAcceptanceTest`,
`AuditLogDatabaseAcceptanceTest`, `PasswordChangeDatabaseAcceptanceTest` and
`TreasuryStatementDatabaseAcceptanceTest` are gated on
`-Daccount.db.acceptance=true` and need a reachable MySQL. A green `mvn clean test` does not run them.
**`PartyStatementViewAcceptanceTest` and `PartyMovementDatabaseAcceptanceTest` are the newest**,
written 2026-09-10 with the party statement and payment work and **first run the same day against a
scratch schema built from nothing** - 23 cases with `PartyLedgerViewAcceptanceTest`, green twice
running, no residue in either database. That run found three real defects a green build could not
see, one of which would have failed every install; see `docs/party-plan.md` §12 and the migration
paragraph below.

**On 2026-08-31 the first thirteen were run together for the first time, and after one fixture fix
all pass: 1022 tests, nothing skipped.** Before that day the honest statement was that most of them
had never been run at all. **`ShiftAccountingDatabaseAcceptanceTest` is the fourteenth, added after
that run and first run on its own on 2026-09-04** — seven cases now, green three times running, against a scratch schema
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

`ItemGroupMoveDatabaseAcceptanceTest` is the newest of the family and the only thing that says a group
move lands and that a refused batch moves nothing: the rest of `features/itemgroups` is tested against a
mock repository, where the optimistic check passes by construction. **Run for the first time on
2026-09-05, where it caught a defect in itself** — it signed in as user 1, and
`UserSessionContext.isSystemAdministrator()` is `currentUserId() == 1` and bypasses every permission,
so its permission case could not fail and its other nine were being allowed by the bypass rather than by
the keys they name. **A test that signs in as user 1 is not testing authorization** — that is the trap
to remember, and it is not specific to this class. Ten cases green three times running since, and it
queries the four tables afterwards for its own `GRP-%` marks rather than trusting the rollback, which is
what makes it safe on a database with data in it. Two things it deliberately does not claim are in its
javadoc: the `moved != effective.size()` guard is unreachable from one connection, and the `FOR UPDATE`
is not proven to block a second writer.

**The merge one and the group-move one are safe to run on a working database; do not assume that of the
other twelve.** Each opens one transaction and rolls it back in a `finally`, so even the audit triggers'
rows go with it - and that was checked rather than trusted: querying afterwards, `item_merge`,
`item_merge_lines` and every `MRG-%` barcode the merge fixtures create all counted zero, and the
group-move one now makes the same check on itself in an `@AfterAll`, over `items`, `sub_group`,
`main_group` and `audit_log`. `ProfitDefinitionDatabaseAcceptanceTest` was checked the
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
- **[`docs/multi-device-plan.md`](docs/multi-device-plan.md)** — what changes when a second computer
  points at the same MySQL: why the trial is now a row per machine, why an out-of-date build is
  refused before Flyway is called at all, which settings became the shop's rather than the
  computer's, and which machine takes the backups. §8 lists what has not been run on two machines
  yet — which is all of it. **Read it before touching `TrialManager`, `DatabaseMigrationService`,
  `PreferencesSetting`, anything under `features/backup` or `features/workstation`, or the
  `V38`-`V42` tables.**
- **[`docs/users-and-recovery-plan.md`](docs/users-and-recovery-plan.md)** — accounts,
  the forced first password change, and emergency recovery: why `V44` hashes the credential
  `V1` seeds rather than replacing it, why there is no `delete`, and §6 the one decision still
  open — where the support key comes from. **Read it before touching `UsersService`,
  `features/users`, the login path or the `V44`-`V45` tables.**
- **[`docs/agent-worktree-rules.md`](docs/agent-worktree-rules.md)** - the contract for an AI agent
  working in a worktree, whatever tool it is: never commit, merge or push; always `clean`; never
  run the database acceptance classes without a disposable schema; never create a `config.xml`.
  It is tool-neutral on purpose - `docs/multi-agent-development.md` is the runner that enforces
  it, but a person opening any agent by hand is bound by the same rules.
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
initialise the Hikari pool, verify the database is reachable, run Flyway, run the trial/licence check,
load and verify the signed product profile, then register every service in `ServiceRegistry`.
`LogApplication` (login) opens from `start()`.

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

**The JDBC URL says `connectionTimeZone=LOCAL`, and it has to.** This MySQL runs on the machine's
own local time, so `NOW()` and every `DEFAULT CURRENT_TIMESTAMP` store local wall clock. The URL used
to claim `serverTimezone=UTC`, and a driver told that converts in both directions — which produced
three different bugs from one word, only the first of them visible:

- a column MySQL wrote read back **late** by the whole offset (a shift opened at 08:00 displayed as
  11:00) — the ~20 `getTimestamp(...).toLocalDateTime()` sites;
- a column written with `setTimestamp(Timestamp.valueOf(...))` was stored **early** by the offset and
  read back late, so the two errors cancelled *on screen* while the value on disk — the one every
  report, view and `BETWEEN` reads — was wrong. Fixing only the read would have broken these;
- the bounds of a range query were converted while the column they filter was not, so
  `UserShiftDao.calculateShiftSummary` counted a window three hours out of line with its own rows.
  Measured against real data, a Z-report missed **24% of the day's takings** and showed the till short
  by that much. That is the one that mattered, and it looked like a date-formatting bug.

`ConnectionTimeZoneArchitectureTest` pins every JDBC URL in both modules, and
`V43__timestamp_timezone_correction.sql` repairs the rows the second case left behind — the seven
columns ever written with `setTimestamp`. So putting `serverTimezone=UTC` back would not merely
reintroduce the bug; it would make already-corrected rows wrong the other way. The migration converts
with `FROM_UNIXTIME(TO_SECONDS(v) - 62167219200)` rather than `CONVERT_TZ`: **`CONVERT_TZ` with named
zones answers `NULL` when the `mysql.time_zone` tables are not loaded**, which is the default on
Windows, and it would have emptied the columns instead of failing.

Layering is `Controller → Service → DAO → AbstractDao`, and it is in the middle of a deliberate shift.
The older services under `service/` are thin `record X(DaoFactory)` wrappers with the real logic sitting
in controllers and DAOs. The newer work puts the logic in a `features/<area>/` package that has no
JavaFX at all and a test per class — `features/invoice`, `features/stockcount`, `features/inventory`,
`features/rbac`, `features/masterdata`, `features/itemgroups`. **New behaviour goes there, not into a
controller.** The test for whether it is in the right place: can it be tested without starting a
JavaFX toolkit?

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
they represented is paid: `WRITES_WITHOUT_A_GUARD` holds only entries that are legitimate — a read
that seeds the company row, the wallet fee whose only callers guard first, emergency recovery which
runs when there is no session to ask, and the presence column written on sign-in and sign-out. The
list fails the build in both directions, so it cannot become fiction: a new unguarded write fails
it, and so does an entry that has quietly been fixed.

**Both rules used to look for a write called exactly `update` or `deleteById`.** So every write
named anything else was invisible to them — `updateCase`, `updateImage`, `updateList`,
`updateAvailable`, `deleteByReference`, `deleteRangeIds` — while the insert half already matched
`insert` followed by anything. That was not a narrower rule, it was the same rule with holes in
it, and one of the holes was real: `LogApplication` and `ApplicationNavigator` wrote
`users.user_available` straight through `UsersDao` from `view/`, which is exactly what the first
rule forbids, for as long as the rule could not see it. The detector now matches
`(?:insert|update|delete)` followed by anything, the two view writes go through
`UserPresenceService`, and a write is a write whatever it is called.

Roles live in `auth_role` / `auth_role_permission` / `auth_user_role`, resolved by `RbacService` over
`JdbcRbacRepository`, with per-user overrides in `auth_user_permission_override`. The schema arrived in
`V11__rbac.sql` (import of every legacy grant), `V12__modern_authorization.sql` (rename to `auth_*`,
keys become dotted strings) and `V13__default_rbac_roles.sql`. `user_permission` is retained as
read-only legacy evidence — nothing reads it for decisions.

`CurrentUser.get()/getOrNull()` reads the signed-in user from `UserSessionContext` in `ServiceRegistry`.
It is **process-wide**, which is correct for a desktop app and is one of the things that has to change
before anything is served over a network — see `docs/new-code-rules.md`.

### Product profiles

The product profile is independent of authorization. RBAC answers who may use a capability;
`ProductFeatureAccess` answers whether that capability exists in this customer's edition at all.
An absent product feature is hidden/unmanaged (so it owns no shortcut) and is also guarded at the
service or screen-opening boundary. A present feature can still be disabled by ordinary permissions.

`ProductFeatureCatalog` is the one declarative inventory used by both runtime and the standalone
`AccountK-Product-Setup` launcher. Profiles use the release RSA public key, are stored as the original
signed envelope in the singleton `product_profile` row, and are copied into
`product_profile_history` on every apply. A database with no row is deliberately `LEGACY_FULL`, so
installing the migration cannot remove screens from an existing customer. The private key is selected
by the technician for one signing operation and must never be stored or shipped. See
`docs/product-profile.md` for the operating workflow and extension rule.

### Users, sign-in and support recovery

`features/users` holds the management screen's queries; `service/UsersService` holds the rules.
Four things to know before touching any of it:

**`V1` seeds `admin/admin` in plain text, and `V44` is what ends that.** It replaces the value
in place with a bcrypt hash of the same password and sets `must_change_password`, so an existing
install is not locked out — it is asked to change at the next sign-in, before the main window
opens. `BootstrapPasswordMigrationTest` reads both values out of the two migrations and checks the
hash really is a hash of what `V1` seeded: asserting only that the file contains `$2a$12$` passes
just as happily on a hash of something else, which would lock every install out of the one account
that can sign in. `WipeCatalog.USERS` restores the same hash and the same flag, never the literal.

**A change the system demands does not go through the permission that governs changing by
choice.** `UsersService.updateOwnPassword` asks `requireCurrentUser` first — the check that
actually matters, since nothing lets anyone touch a password but their own — and requires
`SETTING_UPDATE_PASS` only when `must_change_password` is not set. A user carrying the flag
without that permission could otherwise satisfy neither the demand nor the login screen, with no
screen anywhere able to clear it.

**There is no `UsersService.delete`.** An account is retired with `updateActive(id, false)`;
`users.user_name` is UNIQUE, so a deleted name could never be issued again, and a method called
`delete` that deactivated instead was a trap for the next caller. For the same reason `update`
carries the **stored** `user_activity` over rather than taking one from its caller: `UsersDao.update`
writes that column, and the edit screen has no control for it, so it was sending `true` and quietly
reactivating every deactivated account it touched.

**Emergency recovery is the one write path with no permission guard, deliberately.**
`--support-recovery` opens `SupportRecoveryView` before any login, because it exists for the case
where nobody can sign in and there is no session to ask a permission of. What stands in for the
guard is a **signed challenge**: the machine issues `HAMZA_RECOVERY|<machine>|<nonce>|<issued>`
(`support_recovery_challenge`, `V45`) and accepts only a `BASE64(payload).BASE64(signature)`
response — the same shape as `license.dat` — signed by the private key that issues licences,
which this repository has never held. Around it: five refused responses per fifteen minutes, and
a row in `support_recovery_audit` for **every** attempt including the refused ones. The window is
asked of those rows, not held in memory, because an attempt costs one relaunch of the program.
Both methods are listed in `WRITES_WITHOUT_A_GUARD` with that reason.

**It replaced a shared support key, and why is the lesson.** The first draft of `V45` seeded
that key's bcrypt hash into `app_setting`, which would have shipped it in every copy of this
repository — and the attempt limit never touches the attack that matters: the hash is worked on
offline, unlimited and unlogged, and the right key then arrives on the first try. One key for
every installation, so breaking it once opens the administrator account everywhere. It was
folded out before the migration ever shipped, and `V45` now deletes such a row on the way past.

Three things are bound into the signed text and each earns its place: the **machine**, so a
response for one customer is inert at another; the **nonce**, so it answers once (`UPDATE …
WHERE redeemed_at IS NULL` is the whole race); and the **issue time**, so an unused one expires.
**The `HAMZA_RECOVERY` tag is load-bearing** — `license.dat` is signed by the same key over
`HAMZA_ACCOUNT|<machine>`, so without a tag of our own every customer's own licence file would
verify here. `SupportRecoveryChallengeTest` pins it. The public key lives once, in
`ReleaseSigningKey`; `TrialManager` reads it from there. Full contract, including what support
runs to sign: `docs/users-and-recovery-plan.md` §6.

**Three contracts in `DialogApplication`/`OpenApplication` break a new screen silently**, and the
user-management screen broke on all three before it was ever opened:

- **`save()` must return exactly `1`.** Anything else is a failed save: the dialog stays open and
  `afterSaved()` never runs. `AddUserController.insertData` returned the generated user id, so
  every created user was reported as a failure — and saving again collided with `users_pk`.
- **`addLastPane()` defaults to `false`**, which adds no `ButtonType` at all, and a `Dialog`
  without a `CANCEL_CLOSE` button ignores the window's close control. A screen that does not
  override it needs a close button of its own; `DeleteDataController` is the worked example.
- **`pagination.setPageFactory` owns the node it returns.** Declaring a `TableView` in the FXML
  *and* returning it gives it two parents, so the Pagination tears it out of its own slot.
  `InventoryController` and `UserController` build the table in code and give the Pagination the
  `VBox.vgrow`; nothing else is safe.

`UserPresenceService` writes `users.user_available` — the "online" column — for the login screen
and the navigator's sign-out. It has no guard either, and that is not debt: presence is a
consequence of authenticating rather than an operation anyone is authorized to perform.

### Errors

`controlsfx.error` classifies what the user is allowed to see. `UserValidationException` (bad input) and
`BusinessRuleException` (a rule refused it, including every permission denial) carry messages meant for
the user and are shown as-is. Anything else is technical: `ErrorReporter` logs it behind a reference code
and shows a generic sentence, so a stack trace or a SQL fragment never reaches a screen.
`GlobalExceptionHandler` is the last boundary.

**No test enforces the split, and this file said one did.** `ErrorHandlingArchitectureTest` exists
once per module - `controlsfx` and `account`, each scanning its own `src/main/java` - and both check
two other rules entirely: the legacy dialog and double-logging. Nothing has ever failed a build for throwing a raw `RuntimeException` at a
user-facing path, and `MainGroupService.insert` did exactly that: it caught the `DaoException` it
already declared and rethrew it wrapped, so a duplicate group name, a permission refusal and a lost
connection all reached the screen as the same technical sentence and a reference code. It was found by
a test written for something else, which is the only way it could have been found. Twelve more raw
throws remain under `service/` and `features/`, most of them in the migration and backup services
where the process is being aborted rather than a user answered — a guard here needs that distinction,
which is why one has not simply been bolted on.

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

**The payment screen is the save's confirmation, not an afterthought to it.** With
`settings.checks.showPaidScreen` on (per computer, off by default), saving a **new sales** invoice
opens `InvoicePaymentDialog` in place of "do you want to save?": the amount due, what the customer
handed over, the change. Going back writes nothing, and no "saved" alert follows it - the screen
clearing for the next customer is the answer. It used to be a change calculator shown *after*
the save, whose OK and Cancel did the same thing. The rules are `features/invoice/InvoiceTender`:
**what is handed over is not what is paid** - the change leaves the drawer, so `paid` never exceeds
the net and the change is never stored; a cash invoice handed less is refused rather than saved short;
a deferred one takes the amount as its advance payment through `txtPaid`, the one field the save reads.

**The lines table and its footer write amounts as money** (`Columns.asMoney`, `Columns.money`) and
quantities through `Columns.quantity`; the editable cells use `NumberTextConverter`, which reads back
what it writes, separators and Arabic digits included. **Nothing may read a figure back out of the
footer** - the save button's "total is positive" used to `Double.parseDouble` it, which a formatted
`1,050.00` would have broken. The columns are bound to the line's properties (`Columns.observable`):
a column built from a getter is a snapshot, and editing a quantity left the row's total unchanged.

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

**A `shift_cash_ledger` row can belong to no shift, and `NULL` there means one specific thing.**
`ensureBaseline` writes a `CREATE` row for a document the journal has not seen, so the deltas after
it add up to the document's live value — the per-document invariant
`ShiftReconciliationDao.countSourceMismatches` enforces. A document created inside a shift carries
`origin_shift_id` and its baseline goes there. A document created **before shifts were switched on**
has no such shift, and the baseline used to be filed under whichever shift happened to be open when
somebody edited it: editing an August invoice inside today's shift wrote `CREATE +505` then
`UPDATE -50`, so the drawer was expected to hold 455 collected a month earlier and would have
counted short by all of it. Since `V53` that baseline is written with `shift_id NULL` — recorded,
reconciled, owned by no drawer — and every per-shift read filters `shift_id = ?` and passes over it.
`V53` is forward-only on purpose: rewriting old rows would move the expected balance of shifts
already closed and settled against a counted drawer, and `docs/shift-plan.md` §11 carries the query
that finds them.

**Everything the system records is append-only**, enforced by triggers that refuse `UPDATE` and
`DELETE`: `shift_cash_ledger` (with a numeric `ShiftCashSource`, never a translated label - the
`MovementLabel` lesson), the close snapshot, the close request and its decision, and the
handover/override/variance tables. They live in `V26`, `V27`, `V28` and `R__triggers.sql`, so hunting
one means checking all four.

**The two halves of that are not guarded the same way, and the difference matters.** The `DELETE`
trigger checks `@app_bulk_wipe`, so a wipe can take these rows; the `UPDATE` trigger is
**unconditional** and takes no escape hatch at all - nothing in the running system, and nothing
holding that flag, can ever change one of these rows. A migration that has to correct a stored value
must therefore drop the `UPDATE` trigger, write, and recreate it; `V43` is the worked example, and
this paragraph used to claim `@app_bulk_wipe` covered both, which is how `V43` came to fail on its
first run against a database that had a closed shift in it.

A new fact table here gets the same triggers and a `WipeCatalog` entry, and `WipeCatalogTest` reads
the migrations to check you did.

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

### A party's statement

`features/party/statement` is where a customer's or a supplier's account statement comes from -
one place, reading `account_customer_table` / `account_suppliers_table` through
`PartyLedgerSpec`. It has no JavaFX and a test per class, and it is built on
`features/treasury/statement` class for class: a `Filter` record with its checks in the
constructor, a `Repository`, a `Service`, a `Page`, a `Summary`, a `PrintData`, an `Options`.

**It replaced a second definition of what a party owes, and that is the whole point.**
`AccountDetailsWithItemsController` used to assemble the statement itself from four queries -
the opening balance off the party's row, the invoice totals, the return totals, the payments -
and that assembly carried `if (invoiceType == CASH) total = totalAfterDiscount;`, which is the
`IF(invoice_type = 1, total, 0)` that `V15__return_cash_split.sql` had already removed from the
views. So a **deferred** sales return reached the statement as a debit of zero and a credit of
zero: an empty row, and a customer who had returned 1000 of goods on account still appeared to
owe the 1000 - while the totals screen, reading the view, knew they did not. Two screens, two
balances, and the one a customer signs was the wrong one. `PartyStatementAgreesWithLedgerEffectTest`
asserts every row against `DocumentLedgerEffect.balanceChange()` rather than against a number,
so the two cannot drift again.

Four things to know before changing anything there:

- **A filter narrows the rows; the two balances answer the dates alone.** `openingBalance` and
  `closingBalance` ignore every other filter, exactly as
  `TreasuryStatements.SELECT_STATEMENT_SUMMARY` does. A "balance before the period" measured
  over payments only is not anybody's balance, and it is the figure a customer is asked to agree
  with. It is pinned in `PartyStatementQueryTest` and in the acceptance test; a new filter goes
  in `narrowsRows()`, never near the opening query.
- **The running balance is accumulated in SQL, over every movement of the period.** A window
  function seeded with one scalar read of what came before `from`. The screen used to filter a
  loaded list and restart the total at zero, so a statement for one month was printed as though
  the party began it owing nothing. The other filters therefore sit in an outer query over the
  CTE: a balance that skips the rows a filter hid is not a balance.
- **The page, the summary and the print extract share one `WHERE`** (`rowFilterSql`), so the
  totals and the exported file cannot describe a different set from the table. Same rule as
  `ItemsDao.catalogQuery`. Printing reads `forPrint(filter)` rather than the rows on screen.
- **It is proven against MySQL, not only reasoned about.** `PartyStatementViewAcceptanceTest`
  seeds a balance before a period and asserts the carried-in figure, the per-row running balance
  and the closing balance; asserts a deferred return is on the statement with its whole value;
  and sums the fetched rows in Java to check the SQL summary agrees - because the summary restates
  `PartyStatementRow.debit()`/`credit()` in SQL, and two statements of one rule is the shape of
  defect this package exists to remove.
- **A movement's kind is a number, never a label.** `PartyMovementKind` carries the
  `information` codes 1-4; `hasDocumentLines()` is what decides whether a row expands into its
  invoice lines. That decision used to be `information.equals("المبيعات")` - the `MovementLabel`
  mistake again, and this application ships an English bundle. The kind's `messageKey()` is for
  display only, and because the screen resolves it through a *variable*,
  `MessageKeyArchitectureTest` cannot see those four keys - `PartyStatementTest` checks them
  against the three bundles instead.

**`view_customer_receivables` is derived from `account_customer_totals` and must stay that
way.** It used to compute a customer's debt from its own subqueries over `total_sales` and
`customers_accounts`, ignoring every sales return and the ledger's `purchase` column - a third
answer to "how much does this customer owe", and the one `CreditLimitSource` raised its
credit-limit warning from. Its three component columns now add up to its fourth, which they did
not before.

**What is still not done here** is in `docs/party-plan.md`: the credit limit is polled hourly by
a notification rather than checked when an invoice is saved, and `CustomerDao.map` and
`SuppliersDao.map` still resolve a lookup row per party with a query of their own. The ageing
report the payment allocation exists to feed has since been written - see **Debt ageing** below -
and the parties list showing no balance is a decision rather than a gap (**The two party screens**).

### A movement on a party's account

`features/party/payment` and the two `Account*Service` classes. A hand-entered movement is a
collection, a debit note or a credit note (`PartyEntryKind`), and the difference between the
first and the other two is not cosmetic.

**`paid` is cash and `purchase` is not, and that is a fact about the schema.** `treasury_balance`
unions `customers_accounts.paid` as money into the till and `suppliers_accounts.paid` as money
out of it; neither view reads `purchase` at all. So a note written through `purchase` moves the
party's account and leaves every treasury balance exactly where it was - which is what a note
means - while a note written through `paid` would claim cash had changed hands. **A credit note
is therefore a negative `purchase`, never a positive `paid`.** Both would reduce the balance by
the same amount and exactly one of them leaves the drawer alone; getting it backwards turns
every correction entered at a desk into a till surplus somebody has to explain at the close of a
shift. It follows that a cashless movement does **not** pass through `ShiftGate` and writes no
row in the shift's cash journal: requiring an open shift for a correction would stop one being
made outside trading hours, which is when corrections are made. A later edit is still accounted
for, because `ShiftCashLedger.ensureBaseline` writes the `CREATE` row for a movement the journal
has not seen.

**`customers_accounts.purchase` had no writer until `V55`, and that was the gap behind a promise
the system could not keep.** `OpeningBalanceGuard` refuses to rewrite `first_balance` once a
party has moved - correctly, since it is the one figure with no date on it - and tells the user
to record a movement on the account instead (`opening.correction.customers`). The only movement
anyone could record was a collection, so an opening balance entered *too low* could never be
corrected by anybody, and the message pointed at a road that did not exist. `*.account.adjust`
(V55) is what opens it, and it is deliberately a separate permission from `account.create`:
collecting money is matched by cash in the drawer, and deciding that a customer owes another
thousand pounds is not. `V55` grants it to whoever already held create, the way `V34` did, so
nobody loses an ability on upgrade.

**`account_num` is the database's to assign.** It is `BIGINT AUTO_INCREMENT PRIMARY KEY` and
always was, but the insert used to write it from a number the collection screen computed as
`max + 1` over the whole ledger - so two tills collecting at the same moment chose the same
number and the second failed on the primary key, which is the defect `InvoiceNumberAllocator`
exists to prevent on the invoice side. It is out of `PartyLedgerSpec.insertColumns`, and
`PartyLedgerStatementsTest` asserts its absence **in both directions** so putting it back to
"keep the screen's numbering" fails the build. `AbstractDao.insertReturningId` reads the
generated key back, which the caller needs: the shift journal files the movement under it.

**Nothing opens a movement for editing, so half of `Add_AccountController` is unreachable.**
It fully supports `movementId > 0` — it disables the party field and the kind combo, calls
`selectData` and `selectAllocatedInvoice`, and asks for a shift-correction reason. But the only
caller that ever passed a movement id was `AccountDetailsController`, which is constructed solely
by `AccountDetailsApplication`, **which nothing constructs at all**; and the statement screen that
replaced it has no edit action. Two things go with that: `ShiftCorrectionReasonPrompt.forUpdate()`,
which exists for this path alone, and the "editing excludes itself" rule below — written, unit
tested, and reachable from no screen. Whether a recorded movement should be editable at all, or
corrected with a note the way `OpeningBalanceGuard` says an opening balance must be, is a decision
the code and the screens currently disagree about: the code assumes the first, the screens enforce
the second by silence.

**`numberInv` is finally written.** A collection may be allocated to one invoice, and
`OpenInvoiceQuery` is what offers the unsettled ones: `total - discount - paid - allocated`.
Three rules there. **Returns are not netted off an invoice** - a return is its own document on
the statement, and guessing which invoice it belongs to would be an invented rule of exactly the
kind this codebase has twice had to undo. **Over-allocation is refused, not clamped**, and
measured inside the saving transaction rather than from the list a dialog is holding, because a
second till can settle the same invoice while that dialog is open. And **editing a payment
excludes itself** from what is allocated, or a payment that settled an invoice in full would
find it already settled by the row being edited. An unallocated payment (`numberInv = 0`) is
"on account" and remains the default and the ordinary case.

**`AccountService` is gone**, and with it `NameAndAccountInterface.accountList()`. It computed a
running balance over every movement of every party in Java - the second definition of a party's
balance, competing with the view - and nothing called it once the collection screen stopped
scanning the ledger. One screen needing one number now asks
`PartyStatementService.currentBalance`.

### The two party screens

Both are built in code, and `accountDetailsTreeTableView.fxml` is gone while `account-totals.fxml`
is down to two nodes. The reason is not taste. The old FXML carried English captions the controller
replaced at runtime, so three `CheckMenuItem`s shipped reading `"Unspecified Action"`; it bound its
columns by field-name string through `PropertyValueFactory`, which answers a renamed field with a
silently empty column; it put a `TreeTableView` with a fixed preferred height inside a `ScrollPane`,
so the table never grew; and it declared a header field (`txtLast`) that nothing ever wrote, beside a
label that said "opening balance" over a field holding the **credit limit**. Two files that have to
agree about one screen did not agree. A screen assembled in one place cannot have a caption nobody
set or a field nobody fills.

**`AccountDetailsWithItemsController`** is the statement: header, filter bar, tree, footer. It reads
`PartyStatementService.forPrint` - the whole filtered extract rather than a page - so the table, the
print, the PDF and the Excel are one set of rows by construction. Loading is off the JavaFX thread
with a `generation` token that discards the answer to a search the user has already replaced, the way
`MasterDataPane` and `ItemSuggestionField` do. The lazy expansion of a document row into its lines is
kept from the old screen: a party with two thousand invoices must not read two thousand line tables
to render.

**`AccountController2`** is the balances list, over the new `features/party/balances`. It used to call
`accountTotalList(null, null)` - every row of `account_customer_totals` on every refresh - and then
filter, search and total it in memory with one checkbox ("show the zeros") as its only filter. Now
every filter is SQL: balance state, balance as at a chosen day, range, area, text, **over the credit
limit**, and **idle for N days**. `PartyLedgerSpec.totalsBetweenDatesSql()` had been written and
documented for the period filter and had no caller anywhere; the period is real now. The four figures
above the table are filters as well as facts - "over limit: 12" was a number somebody then had to go
and find.

**A filter field is not an entry field, and `Utils.setTextFormatter` seeds `0.0`.** That is right
for an amount being entered - a payment starts at zero - and wrong for a filter, where an untouched
box has to mean "no bound". The balances screen opened filtering `balance >= 0 AND balance <= 0`,
showing the 118 parties whose account came to nothing out of 145, with a total owed of zero on a
database owing 13,225 and nobody having typed anything; the statement's amount filter had it too.
Every unit test passed, because the filter record was doing exactly what it was told - **only
opening the screen found it.** Use `Utils.setOptionalNumberFormatter` for a filter, and keep the
distinction the tests pin: no bound adds no condition, and a bound of zero stays a bound somebody
can ask for.

**A read-only amount must not carry a number formatter.** `Utils.setTextFormatter`'s converter
round-trips through `Double`, so a string put into such a field comes back as `Double.toString`:
the collection screen showed `1050.0` for the balance the screen beside it wrote `1,050.00`, and
every figure on it was a decimal short. That is the same field type doing two different jobs - a box
somebody types an amount into, and a box the screen writes an answer into. Only the first takes the
formatter; the second is written with `Columns.money` and styled `.app-readonly-amount`. And do not
compute from what such a field displays: hold the figure (`Add_AccountController.balanceBefore`) and
write the display from it, or the arithmetic is done on whatever survived the rounding.

**A value written once does not need a listener; it needs the recompute on the same line.** That
screen's remainder was recomputed from a listener on the balance field - and the balance is written
by `addPartyField()`, which runs at the *top* of `otherSetting()` while the listeners were added at
the bottom. So it opened on a customer owing 1,050 with the remainder reading `0.0`, correcting
itself only once somebody typed. The same method also has to be called when anything else it depends
on changes: `applyKind` did not call it, so switching a 500 collection to a 500 debit note left the
screen reading 550 where the answer is 1,550 - wrong by twice the amount, in the direction that
flatters the customer. **Neither was visible to any unit test, and both were obvious within seconds
of opening the screen.**

**Columns.money is the one definition of how an amount is written** - two decimals, thousands
separated, right-aligned, negatives red through a `PseudoClass` styled in the theme. Screens printed
`String.valueOf(double)`, so a balance that had been through arithmetic showed as
`1234.5600000000002`, and the same figure appeared with and without a separator on two screens of one
ledger. Use it for every money column and for every money label, including footers, so a total
matches the column it sums.

**`StatementPeriod` holds the period arithmetic** rather than eight buttons wiring eight date pairs.
The week runs **Saturday to Friday** - a week starting Monday reports Saturday's and Sunday's takings
in the week before, which are two of the busiest days of an Arabic-market shop. `StatementPeriodTest`
asks about a January, a leap February and the 31st, because a quarter that starts in the wrong month
still looks like a quarter on screen.

**The opening balance and the day it is as at are one entry, and the lock takes both.**
`OpeningBalanceGuard` refuses to rewrite `first_balance` once a party has moved, because it is the
one figure on the row with no date on it. V56 gave it a date - so the entry has two halves, and a
guard holding one while leaving the other typable is a way round itself, reachable from the guard's
own screen: a balance of 1,000 as at January is a different fact from 1,000 as at September, and
moving the date moves the entry through the history exactly as changing the amount would.
`PartyTableSpec.openingColumns()` names both, `updateWithoutOpeningSql()` drops both, and
`OpeningBalanceGuard.withoutAll` removes both values - **refusing indexes given lowest first**,
because dropping index 5 shifts the date down to 5 and a following drop of 6 takes the price tier
and leaves the date in the statement. That is a perfectly valid `UPDATE` writing a price tier into a
date column, invisible until a customer's row is wrong.

**`is_active` decides who may be sold to, not who may be collected from.** The migration says a
stopped party leaves the combos, and applying that literally would have made an old debt
uncollectable: the collection screen searches through the same picker, so a party you stopped
dealing with while they owed you money could not be reached. `PartyTableSpec.PartySearchScope` is
therefore an argument and not a property of the statement - `ACTIVE_ONLY` for the invoice screen and
for the default customer in settings, `EVERYONE` for the collection screen and for the parties list,
which is **the screen a stopped party is switched back on from**, so hiding it there would make the
flag a one-way door. There is deliberately no no-argument form: the two screens want opposite
answers, and a default would silently give one of them the other's. Note the brackets in those three
statements - `a OR b AND c` is `a OR (b AND c)`, so an unbracketed pair would filter the telephone
match and leave the name match open, which reads on screen as a filter that works sometimes.

**Both party screens are built in code now, and `addName.fxml` is down to a root node.** Every
caption in it was an English placeholder (`Code`, `Name`, `area`) the controller replaced at runtime,
the rows were numbered in one file and referred to by number in another, and V56's six fields would
have meant renumbering nine rows in one place and matching them in the other. Rewriting it found
three things a green build had never objected to: `selectData` read `nameList()` - every customer in
the database, each resolving its area and price tier with a query of its own - to edit one row;
`checkDataToEnableButton` called `binding.or(...)` on a line of its own and returned `binding`, so
**the result was computed and thrown away** and the save button asked about the name alone; and
`Double.parseDouble` on an empty limit box reached the user as a reference code. A fourth is
structural and is why `carryForwardWhatIsNotOnScreen` exists: the update writes the whole row, so a
column the form has no control for is written back as the model's default - the same trap that had
`UsersService.update` reactivating every account it touched.

**The parties list deliberately has no balance column.** A party's balance has one definition and
one screen, `AccountController2` over `features/party/balances`, which already carries the area, the
credit limit, who is over it and the four figures above the table. A second computation in the list
would be exactly the defect the party work exists to remove - two screens, two answers, and the one a
customer signs decided by which was opened. What the list gained instead is what lives on the party's
own row: a status column (a stopped party is marked, never hidden), an email column, and
`Columns.moneyOfDouble` in place of `Columns.number` on the opening balance.

**`V56` adds the fields a party record was missing** - email, tax number (the e-invoice needs it),
payment terms in days (the ageing report needs it, or "ninety days overdue" counts from the invoice
rather than from when it fell due), a default delegate, an opening-balance date, and `is_active`. Two
notes on it. `opening_balance_date` is backfilled with `DATE(created_at)`, which is what the view
already used, so **no figure moves** - and **nothing reads it yet**: re-dating the opening balance
moves a movement in every existing party's history, which is a decision taken on its own rather than
as a side effect of a migration that adds a column. And `is_active` exists because `DeleteRegistry`
rightly refuses to delete a party with one invoice while `custom.name` is UNIQUE, so a party you have
stopped dealing with stayed in every combo for ever - the same reasoning as `UsersService.updateActive`.

### Row actions and paging

`account.table.RowAction` + `RowActionsColumn` are the one way a table gets buttons that act on
their own row, and `PageJumpBox` the one way it gets a page number you can type.

**A toolbar button that acts on "the selected row" is two gestures and one invented error.** It
has to be able to say "choose a row first", a message that exists only because the control is in
the wrong place. A button in the row cannot be pressed without naming its row - proven on screen:
with row 53 selected, pressing a button in row 42 opened 42. The balances screen's collect and
statement buttons moved into the row this way, and the parties list's edit and delete joined the
show button that was already there. Refresh, print and export stay in the toolbar, because they
act on the list.

**Three things a button cell gets wrong, and this repository had shipped two of them.** A
`TableView` recycles cells rather than building one per row, so `updateItem` must clear the graphic
when empty or buttons appear on blank rows and act on whatever row the cell last held.
`getTableView().getItems().get(getIndex())` throws during a reload, and an `IndexOutOfBounds` out
of a cell factory reaches the user as a reference code with no screen in it - use
`getTableRow().getItem()`, which is the row's own answer. And the buttons are built once, not
inside `updateItem`. The permission on a `RowAction` is a **hint** in the `isGranted` sense: the
action is left out rather than shown and refused, and the service behind it still calls `require`.

**The actions column goes first, not last.** These tables are wider than the window and carry a
horizontal scroll bar; a column appended to the end lands behind that scroll, which for buttons
meant for "the row in front of you" is the same as not being there.

**`PageJumpBox` reuses `features/totals/PageJump`** - the same clamping (500 in a 24-page list
means the last page, refusing an obvious intention is worse than answering it) and the same
alphabet, **including the ٠-٩ an Arabic keyboard actually produces**. It is in the balances screen
and in `TableController`, so every list screen has it. Its jump goes through
`pagination.setCurrentPageIndex`, so the typed number and the pager's own numbers are one route
and cannot disagree.

**Two layout traps found only by opening the screen.** A control placed after a field with
`hgrow` gets nothing: an `HBox` hands the growing child every spare pixel and then squeezes what
is left, so the page box was simply not on screen until it was moved *before* the search field and
given `minWidth="-Infinity"` as a floor. And a bare `Label` inherits whatever its container sets -
in the table toolbar that came out invisible, so a caption wears the same class as the captions
beside it (`form-label`) rather than trusting the default.

**`.summary-card` was declared twice in `app-theme.css` and the two disagreed.** Once as a dark
navy card with white bold labels, and again five hundred lines later under "Capital Management
Styles" as a white one. The later rule wins on the properties it names, so every card was white
while its labels went on being painted white - **the footers of five screens had invisible text**:
party balances, party statement, comprehensive sales, customer receivables and user management.
The invoice screens escaped only because they scope their own `.invoice-sales .summary-card` in
the theme files, which is what the capital-management block should have done instead of claiming a
shared class name. One declaration now, with colours that answer the background it actually has.
A screen-specific style must never redefine a shared class.

### Column views and widths

`account.table.TableColumnViews` is the "العرض" menu - a compact view, a full view, a
hand-picked set of columns and the way back to the default, remembered per table - and
`ContentSizedColumns` sizes each column to what it holds instead of stretching every column
across the window. The parties list and the party accounts screen both use them. **A list that
wants either takes these two classes, not a copy of them** - they were one private class inside
`PartyNamesTable` until the second screen asked for the same thing.

To give another table the same menu and widths:

- **Give every column an id.** The compact set names columns by id, and
  `TableSetting.tableMenuSetting` keys each column's saved visibility and width by it - without
  one it falls back to the column's index, so inserting a column hands its neighbour's saved
  choice to it.
- **Call `tableMenuSetting` before `install`, then hide JavaFX's own header menu**
  (`setTableMenuButtonVisible(false)`), which would offer the same choices a second time. A custom
  view *is* the columns' own visibility, which `TableSetting` already saves; the menu stores only
  the preset, and restores "custom" by leaving the columns alone.
- **Leave the row actions and the selection box out of the menu** (`fixedColumnIds`). Both have a
  fixed width (min = max), which is also how `ContentSizedColumns` knows not to measure them.
- **Call `layout` every time rows are placed in the table.** The widths are measured from the
  loaded rows: a column blank on every row becomes a thin divider rather than a share of the empty
  width, and an empty result falls back to the headings.
- **Choose the default preset for what the screen already showed**, so nobody's screen loses
  columns on upgrade: the parties list opens compact, the accounts screen opens full.

A screen hosted by `TableController` answers `supportsColumnViews`/`configureColumnViews` and
`usesContentSizedColumns`/`layoutColumns` on its `DataTable` - `PartyNamesTable` is the example. A
screen built in code puts `TableColumnViews.menuButton()` in its own bar - `AccountController2`.

### Debt ageing

`features/party/ageing` is phase D's first report and the first thing built on the payment
allocation: without `numberInv` being written, "ninety days overdue" can only be guessed from the
age of a balance, and **a balance has no age** — it is one number with the oldest and newest debt
folded together.

**It must never become another answer to "how much does this party owe".** It does not add its
columns up and call the result a balance. It reads the balance from the ledger view — the same
expression `PartyBalanceQuery` uses — then splits it, ages what it can, and puts the rest in a
column of its own, so `current + 1-30 + 31-60 + 61-90 + over 90 + unallocated = balance` holds **by
construction**. `PartyAgeingRow`'s constructor refuses a row that does not reconcile, because a
wrong ageing report looks exactly like a right one: five plausible columns of money.

**Only an invoice can be aged.** It has a date, a due date and a remaining amount. The other three
things in the ledger cannot be: an opening balance is settled by nothing in particular, a return is
its own document and is deliberately not netted off any invoice, and a payment left on account
names none. Guessing which invoice they belong to — oldest first, say — is an invented rule of the
kind this codebase has twice had to undo. So `unallocated` is *defined* as the balance less what
the open invoices account for. **A real run showed why that column has to exist**: on a copy of a
customer database the open invoices came to 320,148 against a ledger balance of 13,225, because
three movements in the whole book carried an allocation. Without the column the report would have
announced 320,148 overdue on a book owing 13,225.

**Overdue is measured from the due date, not the invoice date** — `invoice_date +
payment_terms_days`, the V56 column that nothing had read. For a customer on thirty days an invoice
written sixty days ago is thirty days overdue, and calling it sixty puts it in the wrong band.
`CURRENT` is owed but not yet due and is not counted as overdue; a party can owe nothing on balance
and still hold a ninety-day-old invoice offset by an unallocated payment, which is exactly the row
the report is opened to find.

It opens from the balances screen's toolbar rather than the main menu: same data, same permission,
and a menu entry would need a feature in the signed product catalogue. Exporting requires
`reports.show.customers` — **the first use of one of the three report keys that were defined,
granted, and reached by no screen** — deliberately on the export rather than the view: a list on a
screen is looked at, a file leaves the building.

### Printed reports

The `.jrxml` templates live in **`reports/` at the repository root**, and `Configs.FILE_REPORTS`
is a *relative* `File`. So `mvn -pl account javafx:run` — whose working directory is `account/` —
looks in `account/reports/` and finds nothing, exactly as surefire reads `account/config.xml`
rather than the root one. That is a property of how the app is launched from source, not a defect;
a packaged install has `reports/` beside the executable.

**A printed figure has to be written the way the screen it came from writes it.** The party totals
report printed `2905.0` and `16450.0` beside a screen reading `2,905.00` — the detail fields
carried no `pattern` at all. The printed document is the one a customer is handed, so this is the
same rule as `Columns.money`, not a matter of taste.

**Money rounds HALF_UP, in a report as everywhere else.** Those templates summed with
`BigDecimal.ROUND_CEILING`, which always rounds away from zero — so a printed total could exceed
the sum of the very rows printed above it.

Two traps when fixing this, both paid for:

- **`pattern="#,##0.00"` is resolved against the report's locale.** With an Arabic locale it
  printed `٢٬٩٠٥٫٠٠` next to a customer code written `164` — money in one script and identifiers
  in another on one row. Format explicitly with `DecimalFormat` and `Locale.US` symbols rather
  than relying on the attribute.
- **Formatting belongs to the text field that shows a value, never to the expression that
  computes it.** Replacing `$F{amount}` everywhere it appeared also hit the
  `<variableExpression>` of a `<variable calculation="Sum">`, which then summed *strings*:
  `ClassCastException: String cannot be cast to Number`, thrown from `JRDoubleSumIncrementer`
  while filling. Never text-replace an expression in a `.jrxml` without looking at the element it
  sits in.

One consequence is accepted rather than fixed: on the 80mm receipt layout the amount columns are
35px, so a six-figure value now wraps onto two lines where the unformatted one fitted. The number
is complete and readable, and widening those columns would narrow the name column on every row to
help a handful. The A4 template's 62-64px columns are unaffected.

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

**The generic toolbar is gone.** `ToolbarAccountInt` / `ToolbarAccountController` /
`ApplicationDataWithToolbarIndexApp` (with `Disable`, `toolbar-account.fxml` and its stylesheet) were a
dialog that wrapped a screen in a record-navigation toolbar and published the screen's `changeEvent()`
after a save or a delete. Its last two implementors were `AddAreaController` and
`AddSubGroupController`, and both went with the old group/area screens when the master-data editor
replaced them — leaving four classes in `controlsfx` that referenced only each other, so they were
deleted too. **A screen that owns its own buttons and publishes its own event is the pattern now**;
`MasterDataPane` is the worked example, and nothing needs a generic toolbar to say that its data
changed.

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

### Master data: groups, areas and units

Four screens said the same thing four ways. `features/masterdata` says it once, and
`MasterDataKind` is the whole idea: each of the four sections (MAIN, SUB, AREA, UNIT) declares its
table, its id and name columns, and its four permissions, and everything else is derived from that.
`MasterDataPane` is one reusable editor built four times over; a section whose `show` permission is
missing loses its tab rather than its buttons.

**Only identifiers the enum owns ever enter SQL.** `MasterDataQuery` concatenates table and column
names from `MasterDataKind` and binds every user value as a parameter, including the search, whose
wildcards are escaped (`pattern()` escapes `!`, `%` and `_` with `ESCAPE '!'`). That property is what
makes string-built SQL safe here, and it is the one thing to preserve when adding a section.

**The form's limits are the schema's**, and they were read off it rather than guessed: 50 characters
for a group or unit name, 100 for an area, and a unit factor within `DECIMAL(14,3)` — `0.001` to
`99999999999.999`. `MasterDataForm` throws message *keys*, never sentences.

**Uniqueness is the index's decision, not the check's.** `nameExists` is a courtesy that produces a
good message in the ordinary case; two people saving the same name both pass it, and
`main_group_pk` / `sub_group_pk` / `table_area_pk_2` / `units_pk` refuses the second. `MasterDataService`
translates that refusal into the same message rather than letting it surface as a reference code —
which needs the cause `AbstractDao` now keeps. A sub-group's name is unique **globally**, not per
parent, which is why the check does not filter on the parent.

**Deletes still go through `DeletionService`**, so the reference protection in `DeleteRegistry` is
unchanged: this package added an editor, not a second way to delete a group.

`EmptyGroupsSource` polls the same repository for groups holding nothing and is the one place that
notification comes from. Its keys are written out as whole literals on purpose — see
`MessageKeyArchitectureTest`.

### Item groups: moving items between them

`features/itemgroups` is the drag-and-drop tree that reclassifies items, guarded by its own
`items.group.move` (V34) — narrower than `items.update`, because moving an item between groups is not
editing its prices.

**The move is optimistic and all-or-nothing.** Every item in the batch is locked
(`SELECT … FOR UPDATE`), its current `sub_num` compared with the group the screen claims to have read
it from, and a single mismatch refuses the whole batch — not just the stale row. That is the case the
lock exists for: someone else files an item elsewhere while the screen is open.
`ItemGroupMoveDatabaseAcceptanceTest` is the only thing that says any of this works against MySQL;
the rest of the package is tested against a mock repository, where the check passes by construction.

**The tree query aggregates before it joins.** `items` is grouped by `sub_num` — served by the
foreign key's index — and the small result joined to the groups; joining first and grouping after
walked the whole catalogue once per debounced keystroke. `ITEM_MATCHES` is the one string saying what
an item has to match, shared by the tree and the list so the two halves of the screen cannot start
describing different sets.

**Icons belong to the cells, not to the `TreeItem`s.** A `Node` lives in the scene graph once, and a
`TreeTableView` moves an item's graphic between the cells it reuses — which made folder icons vanish
from unrelated rows on expand, and the disclosure arrow land on top of the ones that remained. The
name column's cell owns three icons and swaps them.

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

### Audit log

`audit_log` is written by database triggers through `write_audit_log`; the screen never fabricates
entries. Since V46 an entry snapshots the actor name and workstation as well as the user id, so a later
rename and a second till do not rewrite what the history means. `AuditSessionInitializer` installs those
values through `ConnectionManager.installSessionInitializer`, which refreshes MySQL session variables
on **every pool borrow**. Setting them once at login is wrong: it stamps one physical connection while
Hikari may execute the write on any of ten. Work before login is `SYSTEM`; a statement from a SQL client
has no initializer and is `DATABASE`, with `CURRENT_USER()` as its actor instead of a false user 1.

The browser is `features/audit`: plain `AuditLogEntry` rows, one `AuditLogQuery` shared by the rows,
summary and export, SQL-side filters and paging, and a `LEFT JOIN` to users. Do not bring back
`TableType.valueOf(table_name)`: historical trigger names (`CUSTOMERS_ACCOUNTS`, `TOTAL_SALES_RE`, etc.)
never matched that enum and one such row failed the whole page. Unknown table names remain readable
verbatim. Reading before/after values requires `audit.view`; exporting the active filtered result to
Excel or PDF requires `audit.export` and refuses more than 10,000 rows. Deleting selected rows remains
the separate critical `audit.delete` permission and requires a 5–500 character reason.

V47 separates administration evidence from the business audit rows. Every export, manual deletion,
retention-policy change and retention cleanup appends an `audit_admin_event` through
`write_audit_admin_event`; update and delete triggers make that journal immutable, and it is deliberately
outside `WipeCatalog` so a database reset does not erase the evidence. Retention is disabled by default.
`audit.retention.manage` may enable a 30–3,650 day policy only after showing the affected-row count and
cutoff; the scheduler starts after login, runs as `SYSTEM`, and records every automatic cleanup. Keep
policy reads and writes inside `features/audit` rather than treating these keys as generic shared settings.

V48 adds the read-only administration browser behind the separate high-risk `audit.admin.view`
permission. `AuditAdminEventQuery` is time-bounded, filtered and paged in SQL; do not load the journal
eagerly into JavaFX. The ordinary browser now presents `AuditJsonDiff` field rows instead of two raw JSON
boxes, with unchanged fields optional. The diff utility deliberately has no JavaFX dependency. Repeatable
database triggers also cover roles, role grants, user-role assignments, inheritance and user overrides:
`auth_audit_log` records application management intent, while these triggers close the direct-SQL and
cascade gap and retain the standard `APP` / `SYSTEM` / `DATABASE` attribution.

V49 adds the separate high-risk `audit.admin.export` permission and grants it conservatively only to
existing roles that already hold both administration view and ordinary audit export. The administration
browser can export its complete active filter to Excel or PDF, with the same 10,000-row boundary, and
records the completed file as `ADMIN_EXPORT` in the immutable journal. Its unfiltered activity strip
shows today's changes, seven-day administration events, direct-database changes and authorization
changes using bounded SQL counts. Completed export, deletion and retention operations publish localized
in-app notifications in the `audit` category; zero-row automatic cleanup stays silent and automatic
cleanup is only announced to a user who may view audit administration.

`AuditLogDatabaseAcceptanceTest` is the real-MySQL proof for this seam. It creates a uniquely named
scratch schema, migrates it from empty through V49 plus all repeatables, verifies the snapshot columns,
indexes, permissions, safe retention defaults and administration procedure, then proves both sides of
attribution: pooled application writes retain the signed-in actor/workstation and a direct SQL connection
is labelled `DATABASE` with no fabricated app user. It also proves the administration journal snapshots
its actor/workstation and rejects update and delete, that `ADMIN_EXPORT` remains queryable through the
same repository and activity overview, and that a direct authorization grant is captured by the database
trigger as `DATABASE`. It drops only that generated schema in `@AfterAll`.
When the application account cannot create
schemas, supply `ACCOUNT_DB_ACCEPTANCE_ADMIN_USER` and `ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD` for the
test run; never point this fresh-install test at the business database.

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

### More than one computer

Several tills against one MySQL. `docs/multi-device-plan.md` is the contract; the five things to know
before touching any of it:

**The trial is a row per machine, and used to be one row for the shop.** `trial_machine_state` (`V38`).
A second computer reading another machine's id in `company.trial_machine` called `failAndExit`, which
incremented the **shared** `trial_fail_count` — and `MAX_FAILS` is 1, so one launch of a second machine
locked the first one out permanently, with no screen that could clear it. A new machine inherits the
earliest installation date rather than starting a trial of its own, and a `license.dat` that names
another machine is no longer treated as tampering: copying the program folder is how a second till gets
installed. A bad signature still is.

**An out-of-date build is refused before Flyway is called.**
`DatabaseMigrationService.refuseADatabaseNewerThanThisBuild` compares the highest applied version
against the highest this build carries. Without it, `validateOnMigrate(false)` plus a repeatable whose
checksum differs **in either direction** means an old machine rewrites all 33 views and the triggers
on every launch, silently, while the updated tills query columns that are no longer there.

**`SharedSettingKeys` is the list of settings that are the shop's**, held in `app_setting` (`V40`);
everything else stays in that computer's `Preferences`. `PreferencesSetting` is where the routing
happens, so no call site in `PropertiesName` changed. The test for membership is not "would sharing be
convenient" but "would two machines disagreeing be a *defect*" — the scale barcode's layout is one
sticker read at every till; `price.check.stock` is the warehouse *one* wall display answers for.

**One machine takes the backups** (`BackupPolicy`): the scheduled one belongs to the recorded owner,
and "back up after every invoice" only runs where the database is. Four tills each running a full
`mysqldump` after every sale is what that used to mean. **"Is anyone else connected?" is asked of
`information_schema.processlist`**, never of a table we write — a till switched off mid-sale has no
thread a second later, while a heartbeat row would keep saying otherwise. `workstation_session` (`V41`)
answers the different question of which machines are *ours* and what each is running.

**A refresh event crosses to the other machines through `data_change`** (`V42`) and
`RemoteChangeRelay`. Only events another machine can rebuild exactly are relayed: `ItemsChanged` yes,
`ItemSaved` no — it carries the item, and a listener handed a fabricated model is worse than one
hearing nothing. `NameChanged` is two topics, one per `PartyKind`, for the reason the kind was added
to the event in the first place.

**Each topic declares which half of the system writes its row, and there are exactly two halves.**
`RemoteChangeTopics.Announcer.SERVICE` means the service that performs the write announces it
through `ChangeAnnouncer`, on the write's own connection and inside its transaction — so a save
that is rolled back takes its announcement with it, and no other till is told to reload for a
change that did not happen. `RELAY` means nothing announces it transactionally and
`RemoteChangeRelay`'s bus listener is the only writer; that is the right answer for the
master-data screens, the company row and the users list, whose services do not announce.

**Both were doing it for nine topics, and one invoice save moved `revision` by two** — the service
wrote the row, then the relay wrote it again when the screen published the same event after the
save returned. Measured on a real database, not reasoned about. The relay now subscribes only to
`RELAY` topics, and `MultiDeviceRefreshArchitectureTest` fails the build in **both** directions: a
topic some service announces but declares `RELAY` is the double write, and a topic declaring
`SERVICE` that nothing announces is the quieter defect — the relay will not cover for it, so that
change never leaves the machine. The one place allowed to announce a `SERVICE` topic without a
service is `LoadDataAndList`: a restore replaces the whole database and has no single write to hang
an announcement on.

## Configuration and secrets

`config.xml` (database credentials, AES-encrypted) and `config.key` are **git-ignored**. New installs
store them in `%ProgramData%\AccountK`; `ACCOUNT_CONFIG_DIR` overrides that location. A file in the JVM
working directory remains a read-only compatibility fallback for field installs and development checkouts.
`AccountK-Database-Setup.exe`, packaged as a second jpackage launcher from the same shaded jar/runtime,
tests MySQL and writes a device-specific pair without requiring source, Maven or a separate JDK.
On the main workstation it can also create the schema and an IP/CIDR-restricted MySQL account with
database-scoped privileges; administrator credentials are used only for that operation and are never saved.
Two things there are load-bearing and both were bugs first. **MySQL reads an account host as a literal or
as `address/netmask`, never as a prefix length** — `CREATE USER 'x'@'192.168.1.0/24'` succeeds and then
matches no client at all, which reaches the till as an authentication failure that reads like a wrong
password; `DatabaseServerSetupService.mysqlHostPattern` takes the prefix people know and returns the
netmask MySQL needs, refusing `/0` and an address that does not match its own prefix. And **the account is
shared by every till**, so an existing one keeps its password: the run used to end with an unconditional
`ALTER USER`, which silently signed out every machine already holding the old password in its own
`config.xml`. Replacing it is now an explicit tick, and the result says which of created/reset/unchanged
happened rather than leaving the technician to guess whether the password they typed is the one that
account has.
`config.xml.example` documents the format.

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
`DatabaseMigrationService` from the background startup task before anything touches the DAOs.

- `V1__baseline.sql` is the schema as shipped to clients in v4.1.3 — tables, indexes, procedures and the
  seed data (including the `admin` user, without which nobody can log in). It is the Flyway baseline: an
  existing client database is **stamped** with it, never executed, because it already is that schema. A
  new database executes it and continues with `V2`, `V3`, … The current head is `V56`: V56 adds the
  fields a party record was missing - email, tax number, payment terms, a default delegate, an
  opening-balance date and `is_active` - and V55 adds the debit/credit-note permissions
  (`*.account.adjust`, granted to whoever held `*.account.create`) with the ledger's date indexes;
  see **A party's statement** and **A movement on a party's account**. V54 adds the
  signed singleton product profile and its append-only application history. V53 corrects opening
  shift baselines, V52 adds stock-count variance settlement, V51 adds stock counts, and V50 adds
  treasury statement support. V49 adds the audit-administration export permission and the
  direct-source activity index. V48 adds the
  administration-browser permission and its secondary query indexes. V47 adds the immutable
  audit-administration journal, safe-disabled retention settings, and the dedicated export and retention
  permissions. V46 preserves audit actor/workstation snapshots, adds the time-range indexes
  used by the audit browser and splits `audit.view` from the broad settings permission. Before it, `V44` hashes the
  `admin/admin` credential `V1` seeds and demands a change at the next sign-in, and `V45` adds
  support recovery — the signed challenge, its audit, and the row that makes a challenge
  answerable once. See **Users, sign-in and support recovery**. Before them, `V43`
  repairs the timestamps stored shifted by the machine's UTC offset (see **Database access**
  above). `V38`–`V42` are
  the multi-device work — a trial row per machine, the two backup permissions, the shared `app_setting`
  table, the machine registry and the cross-machine change feed (see `docs/multi-device-plan.md`).
  Before them, `V36` and `V37` are the price-check kiosk, `V34` adds
  `items.group.move` and grants it to every role that could already edit an item, and `V35` gives the
  areas list its own `area.show` instead of the `items.show` it had been borrowing — each granting the
  new key to whoever already held the old one, so nobody loses an ability on upgrade. Before them, shift
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

**And the same rule has a second scar, in the other direction: `V1` takes its own helpers away
with it.** `add_index_if_missing` is created at `V1` line 311, used some eighty times, and
**dropped at `V1` line 994** - so nothing after the baseline can call it, and an install stamped
at `V1` never created it at all. `V16`, `V21`, `V22`, `V23` and `V4` each define a local copy for
that reason. `V55` was written calling the original and **failed on the first line it reached**,
on a schema built from nothing:
`PROCEDURE …add_index_if_missing does not exist`. It would have done that on every install, new
and upgrading. Nothing in a green build could see it - a migration is only wrong when MySQL reads
it - and the test written for that very migration passed, because it checked the *text* of the
call rather than whether the call could work. **So: a versioned migration defines the helpers it
calls, and the only thing that says a migration applies at all is migrating a schema from
nothing.** It costs five minutes.

**`add_column_if_missing` is the same trap and is worse**, because no baseline creates it at all -
`V20`, `V21`, `V22` and `V23` each define a local copy and drop it again. The first draft of `V56`
called it and would have failed everywhere, an hour after `V55` had been found doing the same thing
and the lesson written down. That is the argument for a test rather than a note:
`MigrationHelperProcedureTest` fails the build when a versioned migration calls a procedure it does
not define in its own file, and **also** when it defines one and leaves it behind - a stray helper is
what the *next* migration calls and finds present on its author's machine and missing in the field.

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
state mirrored between `%APPDATA%\HamzaAccount\trial.dat` and **`trial_machine_state`, a row per
machine** (`V38`), HMAC-signed, plus
per-record caps (10 items, 5 customers, 10 sales, 10 purchases) enforced inside the DAOs. A valid
`license.dat` short-circuits all of it at the first line of the check. `README.md` documents the licence
file format and the generation scripts in `scripts/`.

Tampering calls `failAndExit`, and `MAX_FAILS` is 1 — a false positive permanently blocks the install, so
be careful about adding failure paths here. **A failure is charged to the machine that caused it**; it
used to be charged to the one `company` row, which is how a second computer starting once could end the
first one's install for good. The `company.trial_*` columns are still there and nothing reads them for
a decision: they were never created by a migration — `TrialManager` added them itself — and dropping
them would take an install's own history with them. The identity comes from `config.MachineId`, which
is the reader lifted out of this class unchanged so the backup owner and the machine registry answer to
the same value the licence is bound to.

## Localization

`LanguageManager` (singleton) with bundles at `controlsfx/src/main/resources/i18n/messages*.properties`;
Arabic is the default and the choice persists in Java `Preferences`. Several settings (backup path,
interval, encryption password) also live in `Preferences`, not in files.

**Two placeholder conventions coexist, and picking the wrong one fails silently.**
`LanguageManager.getString(key, args…)` formats with **`String.format`**, so its messages take
`%s` and `%d` - 217 keys do, and `%1$d` repeats one argument. A key written with `{0}` is only
right where the *call site* formats it itself with `MessageFormat`, as `TreasureDetailsController`
does for `treasury.statement.page`. Mix them and nothing complains: `String.format` returns the
text with `{0}` still in it and drops the argument, so a sentence reaches the user with a
placeholder in the middle of it. Four keys shipped that way in the party statement work and were
caught only because a gated acceptance test happened to assert on a message's text - and the rule
written to stop it, `MessageKeyArchitectureTest.aKeyGivenArgumentsUsesTheFormatItsFormatterUnderstands`,
immediately found an older one: `user.shift.reconciliation.summary` rendered eight literal braces
on the admin shifts screen with all eight arguments dropped.

**And `KEY_SHAPE` demands a dot, so a single-word key is invisible to the general scan** - while
`name`, `code`, `date`, `search`, `refresh`, `print`, `balance`, `from` and `to` are all real
single-word keys here. `Columns.text("area", …)` therefore passed every build and rendered the word
`area` as a heading in an Arabic table until somebody opened the screen. `columnTitleKeysIn` now
checks the first argument of every `Columns.*` builder whatever it looks like - it can be strict
there because the match is qualified by `Columns.` and all of those take `titleKey` first - and it
accepts only a whole literal, since `NamesTables.SEL_PRICE + "2"` is a key nothing static can
resolve. That rule immediately found twelve **raw Arabic strings** passed as column titles in
`ReportTotalByYearController`, which rendered correctly by accident and could never be translated.
