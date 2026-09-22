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
**3,490 tests** with 256 skipped (below) — the figure `mvn clean test`
reports, measured on 2026-09-23 after the quick invoice became a screen of its own. What is
genuinely covered:

- **The declarative specs, pinned character for character** — `DocumentDaoStatementsTest`,
  `PartyDaoStatementsTest`, `PartyLedgerStatementsTest`, `CardItemDaoStatementsTest`,
  `DocumentTableSpecTest`, `WipeCatalogTest`, `ItemMergeStatementsTest`,
  `ItemReferenceRegistryTest`. These fail the build on a wrong column, so they are the
  safety net for anything touching SQL. The last two read the foreign keys straight out of the
  migration files, so the schema itself is what they check against.
- **Architecture rules** — twenty-two `*ArchitectureTest` classes now (twenty-four files:
  `ErrorHandlingArchitectureTest` and `TableColumnArchitectureTest` exist once per module), plus
  `DefaultRoleAcceptanceTest`:
  `AuthorizationArchitectureTest`, `PermissionCatalogArchitectureTest`, `ErrorHandlingArchitectureTest`,
  `DocumentPackageArchitectureTest`,
  `DefaultStockUsageArchitectureTest`, `LocalizationArchitectureTest`, `FxmlArchitectureTest`,
  `ModelPurityArchitectureTest`, `TableColumnArchitectureTest`, `StocksChangedArchitectureTest`,
  `FxmlWiringArchitectureTest`, `KeyboardNavigationArchitectureTest`, `ShiftGateArchitectureTest`,
  `MessageKeyArchitectureTest`, `ConnectionTimeZoneArchitectureTest`, `MultiDeviceRefreshArchitectureTest`,
  `PasswordChangeArchitectureTest`, `ProductProfileWiringArchitectureTest`,
  `TreasuryStatementScreenArchitectureTest`, `ListToolbarArchitectureTest`,
  `LicensingArchitectureTest` and `ThemeTokenArchitectureTest` — the last three were already in the
  tree and missing from this list, which is what a count written by hand does. They
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
**That list is itself out of date** - forty `*AcceptanceTest` files exist, and the later areas' own
sections name theirs. The reports work added four, each building a scratch schema of its own from
nothing and dropping it, and each run with `ACCOUNT_DB_ACCEPTANCE_CONFIG`:
`PartyProfileDatabaseAcceptanceTest`, `CapitalDatabaseAcceptanceTest`,
`CustomerRfmDatabaseAcceptanceTest` and `ParetoDatabaseAcceptanceTest` (`docs/reports-plan.md` §12
and §13.3).
**They read `account/config.xml` for the credentials alone**, and `ExpenseDatabaseAcceptanceTest` also
takes `ACCOUNT_DB_ACCEPTANCE_CONFIG` to name that file outright - which is how it runs from a worktree,
where there is deliberately no database configuration to copy a secret into.
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
- **[`docs/employees-plan.md`](docs/employees-plan.md)** — the employees contract: why the job is a
  row rather than an enum, why the salary carries a date, why the cash an employee is paid has one
  writer (`expenses_details`) and the employee ledger records only what is not cash, and why an
  advance is deducted once - on the day it leaves the drawer. §1.2 lists the twelve defects the
  review found; §12 the order the phases are built in. **Read it before touching anything under
  `features/employee`, `controller/employee` or the `V57`-`V58` tables.** §13 records what phase A
  delivered and §14 phase B, each with what was actually run and what was not.
- **[`docs/expenses-plan.md`](docs/expenses-plan.md)** — the expenses contract: why
  `expenses_details` stays the one table an expense lives in (nine readers depend on it), why a
  heading is a row with a `system_key` for the one the wallet fee needs (it was found by name, and
  renaming a heading is what the headings screen does), why an employee is paid only through the
  employee payment screen, and why a payment above the till's balance is a warning rather than a
  refusal. §10 records what phase A delivered, what differed from §3, and that **V64 and
  `ExpenseDatabaseAcceptanceTest` passed against MySQL twice running on 2026-09-17** (the first run
  found a defect in the test itself), and that the four screens were opened on a copy of the
  development database the same day, where three layout defects no test could see were found and fixed.
  §11 records phase B, the reports: every report is built from the list's own `FROM`/`WHERE` and binder
  (`ExpenseQuery.fromSql`/`whereValues`), and `ExpenseDatabaseAcceptanceTest` holds the report by heading,
  the profit and loss's expenses column and the filtered list to one figure on MySQL. **`V65`'s first draft
  failed on the first database it met** - a 62-character description for `auth_permission.description`,
  which is `VARCHAR(50)` - and `ExpenseReportsMigrationTest` now reads the length. §12 records phase C,
  the budget and the recurring expenses (`V66`): a main heading's own budget is the family's ceiling and
  its children's are not added to it, a template **reminds and records nothing** because the cash leaves a
  drawer by a person's hand and `ShiftGate` has nobody to ask in a scheduled task, and
  `expenses_details.recurring_id` is both the link and the definition of "already recorded".
  **V66 and the thirteen cases of `ExpenseDatabaseAcceptanceTest` passed against MySQL twice running on
  2026-09-18**, from nothing and over a V63 database with data in it, leaving no schema and no row behind -
  and that run found three defects, all three in the test rather than in the code. **Both screens were then
  opened on a copy of the development database**, where the app applied V66 itself on start-up, and three
  more defects turned up that no test could see (§12.8): a recurring template opened with its end date
  seeded to today, so a new one was born already expired (`DateSetting.dateAction` seeds today,
  `dateFilter` does not - the entry-field-versus-blank-means-none distinction, at a `DatePicker`); a
  `FlowPane` wrapped between a caption and the control it names, so "الفترة" read as though it labelled
  the combo before it; and the due list did not refresh after "record now", leaving an answered reminder
  on screen for somebody to record twice. §12.9 lists what is still unseen: the budget tab's layout, the
  notification actually firing, and the buttons hidden from a user without the two new permissions.
  **Read it before touching anything under `features/expense`, `controller/expense`, `WalletFeeService`,
  `V64` or `V66`.**
- **[`docs/returns-plan.md`](docs/returns-plan.md)** - the returns contract: why a return's price,
  cost and discount share all come from the source line, the eight refusals and the two warnings, and
  §8 what is still open. **Read it before touching anything under `features/returns`,
  `ReturnEntryCoordinator` or the return half of `InvoiceSaveService`.**
- **[`docs/warehouse-plan.md`](docs/warehouse-plan.md)** - the warehouse contract: a balance is
  derived and `quantity_items_table` is the one place it comes from, the four things that may move
  one and the rows they lock, and why `stock_movements` is a consistency check that **nothing may
  read for a figure** until the roadmap's step 8.6. §7 is what the 2026-09-20 review found - first
  that the item card does not know transfers exist, so it and the inventory sheet answer two
  balances for one warehouse - **read and not reproduced**; §9 the five decisions nobody has taken
  (the costing method first); §10 the phases. **Read it before touching anything under
  `features/inventory`, `features/stocktransfer`, `features/stockcount`, `features/stockledger`,
  `features/itemcard`, `InvoiceStockGuard` or the stock half of `R__views.sql`.**
- **[`docs/installer-plan.md`](docs/installer-plan.md)** - **phase A of four is built** (the
  provisioning, in Java); the Inno Setup script, the upgrade path and the signing are not. One Setup file
  that wraps the jpackage image, carries MySQL, creates the service, the schema and a per-machine
  `config.xml`. The decisions that matter: the data lives in `%ProgramData%\AccountK\mysql-data` and
  neither an upgrade nor an uninstall ever touches it; nothing secret is shared between two installs
  (the 4.1.3 installer shipped one populated `data` folder and one `config.xml` to everybody); the
  provisioning is Java (`--provision-local`), not Pascal; and the service is ours on **3307**, because
  3306 is taken on half the machines it will meet. **Read it before touching `packaging/` or
  `features/dbsetup`.**

  What phase A is: `LocalServerProvisioner`, reached with no window through
  `AccountK-Database-Setup.exe --provision-local`. Three rules carry it, each pinned by a test. **It is
  inert over existing data** - a data directory with anything in it means no initialization, no account
  and no `config.xml`, which is what lets an upgrade run the same command. **A failure removes what the
  run made and nothing else**, because a half-made data directory would make the next run inert over a
  server that never worked; a `license.dat` in the same folder is not the installer's and is never
  listed, moved or deleted. **No secret leaves the JVM**: both passwords are generated inside, used over
  JDBC, and there is deliberately no option that takes one - an argument is visible in the process list.
  The server is shut down *before* `config.xml` is written, so one that will not stop is a failure with
  nothing pointing at it. The root password goes to a file restricted with `icacls` **by SID**, since
  `BUILTIN\Administrators` is a localized name and this ships to a Windows that is not in English.
  `LocalServerProvisioningAcceptanceTest` is gated on `-Daccount.installer.acceptance=true` with
  `-Daccount.installer.mysqlHome=<a MySQL distribution>`, and is the only thing that says any of it
  works: it initializes a real `mysqld` under JUnit's temporary folder on a free port from 3307, starts
  it again from `my.ini` alone as the service will, and signs in as the restricted account through the
  `config.xml` that was written. **It touches no database that exists.** Green three times on MySQL
  8.0.31; what it has not been run as - elevated, from inside the jpackage image, on a second machine -
  is in the plan's §8.4. And `MysqlTools` now finds the bundled `mysqldump` beside the launcher
  (`jpackage.app-path`) rather than in the working directory, where a program started from anywhere but
  its own folder failed every backup it was asked for.
- **[`docs/product-plan.md`](docs/product-plan.md)** - the order the large items are built in and why
  (one developer, so one item open at a time), the hybrid selling model, and §4 the ideas log: an idea
  that arrives mid-item is written there in one line, not built. Two plans hang off it, **each with
  its phase A built and nothing after it**: [`docs/licensing-server-plan.md`](docs/licensing-server-plan.md) - the licence stays
  verified offline and the server only issues it, the server gets a **second key** because
  `ReleaseSigningKey` also signs emergency recovery, and an expiry never reaches `failAndExit` - and
  [`docs/delegates-plan.md`](docs/delegates-plan.md) - the dated commission rule, the frozen monthly
  run, and why a collection's delegate is written at entry rather than derived. **Read the first before
  starting any large item, and the matching one before touching `TrialManager` or anything under
  `features/delegate`.**
- **[`docs/reports-plan.md`](docs/reports-plan.md)** - the reports contract, written 2026-09-22:
  every "opening" figure is equity brought forward and never a period movement,
  the profit on the equity statement is `ProfitLossDao`'s and nothing recomputes it, a report
  explains a figure a screen already shows and is held to it on MySQL, and the hub (phase C) opens
  existing screens rather than hosting copies. §2 is what the review found in the existing reports -
  two of its ten findings were wrong and say so there, which is what checking before fixing is for;
  §4 is phase 0, **built**: the dead day-details button and three unreachable screens gone, the
  payments report printing, and the dashboard's best sellers in base units net of returns through
  `document.ItemNetLines`, which the party profile shares. Phases A (the party profile), B (the
  owner's equity) and C (the reports hub, the shared trend chart and the customers' recency, frequency
  and value) are **built, green on MySQL and seen on a copy of the development data** - see
  **A party's profile**, **The owner's equity** and **The reports hub** below; §12 is what differed
  from the plan and what only the screen and the rendered paper found. Phase D's first three items -
  the return on equity, the reconciliation of assets against equity, and the items' Pareto - were
  decided in §13 and are **built, green on MySQL twice and seen on a copy** (§13.3, and **Items by
  Pareto** below); the other three (a warehouse filter on the item reports, stock turnover, the
  periodic tax report) wait on the decisions §13.1 names. **The item is closed and shipped in 4.10.0**
  (§14, item «2أ» in `docs/product-plan.md` §1), and closing it fixed the item movement report.
  **Read it before adding a report or a chart anywhere, and before touching `controller/reports`,
  `features/party/profile`, `features/capital`, `features/itemreports` or `TreasuryCapitalController`.**
- **[`docs/permissions-plan.md`](docs/permissions-plan.md)** - the authorization contract: why a
  permission is a string key with no database id, why a declared key must be read by something (eight
  were not), why a permission's name comes from the bundles and not from
  `auth_permission.description` (which holds the key itself for 108 of the 162 rows), and why removing
  a key needs no migration. §2 is what the 2026-09-20 review found; §3 the four keys granted to a
  default role and read by nothing; §4 the six decisions nobody has taken - the administrator-only
  behaviours, the roles screen's grouping and risk badge, the report keys, refreshing a running
  session, `update.data.before.month`, and which settings tabs deserve a key. §5 says what was not
  verified, which is every screen. **Read it before touching `account.authorization`,
  `features/rbac`, `V11`-`V13`, or any migration that adds a key.**
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
`findItemById`. The bulk editor is the one write that takes list rows by design, so
`ItemsDao.updateBulk` names only the columns that screen changes, writes the picture only when
asked (`ItemsBulkUpdateTest`), and writes an opening balance only to items nothing has moved - a
batch holding one that has moved and would change is refused whole, before anything is written
(`BulkOpeningBalance`, the item screen's `OpeningBalanceGuard` rule; the option used to be dropped
while the dialog reported the save as done). It used to write every column from the row, and a list row has no
picture, so every bulk edit blanked the picture of each item in it. And **the page query and its `COUNT` are built from one `WHERE`**
(`ItemsDao.catalogQuery`, pinned by `ItemsCatalogQueryTest`): filter them separately and the
pagination control starts describing a different set of rows than the table shows.

### Authorization

`docs/permissions-plan.md` is the contract, and §4 is what is still undecided. **Read it before
touching `account.authorization`, `features/rbac` or any migration that adds a key.**

`UserPermissionType` — the ~130-entry enum with ids hand-matched to table rows — **is gone**.
Permissions are now string keys: `AppPermissions.SALES_CREATE` is `key("sales.create")`, and adding one
is a single constant plus a `permission.<key>` line in each of the three bundles. No database id, no
switch, no permission-screen edit, **and no migration**; the metadata (module, resource, action, risk)
is derived from the key itself and synchronized on startup.

Three rules about the catalogue itself, all three pinned by `PermissionCatalogArchitectureTest` and all
three written for something that had shipped:

- **A declared key must be read by something.** Eight were not — four settings tabs, both price-tier
  keys, a treasury balance and the read half of the month rule — so they were tick boxes in the roles
  screen that changed nothing a user could do, and a role built out of them granted nothing. They are
  gone. Four more were *granted by `V13`* and read by nothing, which is worse because
  `DefaultRoleAcceptanceTest` pins those grants. Three of them - `reports.show.customers.account.area`,
  `.day.details` and `.delegate` - were removed with the reports work (`docs/reports-plan.md` §4), and
  that test now resolves a grant the catalogue no longer declares to nothing, as `synchronizeCatalog`
  does, and lists those keys so a removal is as visible in review as a grant. `items.add.excel` is the
  one left in `DECLARED_BUT_UNREAD`, and that list fails in both directions.
- **A permission's name comes from the bundles, never from the database.** `V1` seeds the permission
  rows with names and no descriptions, and `synchronizeCatalog` writes `description = permission_key`
  for a row it inserts and never returns to it — so `COALESCE(description, permission_key)` put
  **108 of 162 keys on the Arabic screen as their Latin key**, `treasury.capital` and
  `shift.force.close` among them, while English derived `Total · Sales · Re · Show` from the key.
  `PermissionLabels` reads `permission.<key>` and falls back to a stored description only where the
  bundle has none.
- **Removing a key needs no migration.** `synchronizeCatalog` disables every system permission and
  re-enables the declared ones, so a constant that goes leaves its row `enabled = 0`: out of the screen
  and out of `findEffectivePermissions`, with the grants that named it dormant rather than deleted. Put
  the key back and they work again.

**The risk is derived from the key's last word, and is declared where that is wrong.** DELETE, BYPASS,
MANAGE, POST and RESTORE derive `CRITICAL`; CREATE and UPDATE `HIGH`; everything else falls through to
`LOW` — which covered CAPITAL, OPENING, TRANSFER, DEPOSIT, PAY, APPROVE, ADJUST and MERGE. The owner's
capital and a treasury's opening balance, both documented as the owner's alone, were `LOW`. Worse, two
places said two things: `V55` inserts `customer.account.adjust` as `HIGH` and
`ON DUPLICATE KEY UPDATE risk_level = VALUES(risk_level)` overwrote it with the derived `LOW` on the
next startup, so the migration's answer never survived a restart. Seventeen keys now pass their risk to
`key(value, risk)` beside the sentence that justifies it, the four `audit.*` keys' `equals` chain inside
`definition()` moved there too, and a declaration that merely restates what derivation already gets
right fails the build.

**The section a permission is shown under is `PermissionGroup`, and used to be decided twice.**
`AppPermissions` derived the module from the key's first word - `TOTAL`, `SHOW`, `UPDATE`, `SEL` -
while `UserPermissionController.categoryLabel` matched that against a `switch` over eight words it
had chosen itself, four of which could never match: the derivation gives `PURCHASE` where the switch
said `PURCHASES`, `SETTING` where it said `SETTINGS`, `CUSTOMER`/`SUPPLIERS` for `PARTIES` and
`USERS`/`ROLES` for `SECURITY`. Two differ by one letter, and **134 of the 162 keys read "عام"** -
invisible to every test and every query, because each half did exactly what it was told, and obvious
the moment the screen was opened. Thirteen groups own the key prefixes now, and four rules hold them:
a key belongs to **exactly one** group (a group may claim a key **by name** where a prefix would reach
into another family's - `DELEGATES` does that with `sales.discount.override`, because V73 grants it
with `commission.rule.update` and not with selling); a key claimed by name has to exist, or the claim
silently does nothing; every group owns a key; and every group has a label in all three bundles. It
needs no migration - `synchronizeCatalog` rewrites `module_key` on every start-up.

**The roles dialog folds what is secondary and puts the count on the header.** It opened 735 points
tall on a 768 screen, and a `SplitPane` split what was left between the inheritance table and the
permissions table, which came out at **two rows of 162**. It is not a `RowDetailDrawer` case - the two
tables do not depend on each other's selection, as **A row's detail** says - so it uses the other
idiom this repository has: the role's fields and the inheritance table are collapsed `TitledPane`s,
the inheritance header carries how many roles are inherited and follows a tick live, the `SplitPane`
is gone and the permissions table is the `center`. Two rows became six at a real 1366x768. **Nothing
opens a section on the user's behalf**: the first draft expanded the inheritance when a role inherited
something, which cost the table five of its six rows the moment such a role was picked - the count is
what says there is something inside, and opening it as well spends the height to say it twice.

**"Is this the administrator" is `CurrentUser.isSystemAdministrator()`, asked in one place.** Three
screens wrote `CurrentUser.get().getId() == 1` themselves — the invoice lines table's column menu, the
sidebar's role caption, the help button — which is the numbered-administrator test this whole system
replaced, and is wrong on any install where the owner is not user number one.
`theAdministratorIdIsAskedInOnePlace` fails on a fourth, and it subsumes
`AuthorizationArchitectureTest`'s older rule, which hunted `usersVo.getId() == 1` in six named files and
could not see the `CurrentUser` spelling at all. Whether those three should be permissions is
`docs/permissions-plan.md` §4.1.

`AuthorizationGuard` is the single gateway, and it answers two different questions with two methods —
using the wrong one is the mistake to avoid:

- `isGranted(key)` returns a boolean and is for **UI hints** — hiding a button, disabling a menu.
- `require(key)` throws `BusinessRuleException` and is for **enforcement**. Its sentence names the
  permission through `PermissionLabels.describe` - it passed the key, and "stock.count.create" sat in
  an Arabic refusal until an ordinary user met it on screen. It belongs in the service
  layer, and there are ~57 calls to it in `service/` today.

**A sidebar section shows when one of its commands opens, and each command asks its own key.** The
settings section used to be hidden without `setting.show`, and with it Home, About, Delete data and
the section itself all asked that key - so a cashier given only their own shift screen
(`shift.self.view`) could not reach it, and one given `setting.show` to reach it could also open the
delete-data screen. `setting.show` now opens the settings **screen** and nothing else; Home, About and
Close are `PUBLIC_ACCESS` (Close asked `user.shift.manage` since `1efc3b03`, the key meant for the
shift administration button, which was `PUBLIC_ACCESS` - the two the wrong way round); the shift
administration opens for any of the five keys its tabs read; and wiping the tables is
**`setting.data.delete`**, granted by no migration - `SYSTEM_ADMIN` gets it from the start-up
synchronisation and nobody keeps it by accident. `WipeService.run` asks it before anything, where
until then it asked nothing at all and the only lock was a password written into the program.
`MainScreenController.applySectionVisibility` shows a section when the edition carries it **and** a
button in it is enabled, so a section with nothing this user can open - treasury for a cashier - is
gone rather than a column of grey buttons. **It leaves the accordion's list rather than being made
invisible**: the `Accordion` skin lays out every pane it holds and reads neither `visible` nor
`managed`, so the employees section, hidden by one key, had been leaving an empty slot in every
cashier's sidebar - seen on screen, as was every part of this. `SettingButtonsPermissionTest` pins each
button's key and the wipe's refusal.

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
The login screen's "forgot the administrator's password" link (and `--support-recovery`) opens
`SupportRecoveryView` before any login, because it exists for the case
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

**The challenge shown is the row read back, never Java's clock.** `redeem` rebuilds the signed text
from `support_recovery_challenge`, whose `issued_at` is MySQL's `CURRENT_TIMESTAMP`; the window used
to display `LocalDateTime.now()`, so a till one second away from the server - or an insert crossing a
second - made every correctly signed response unanswerable. Support signs from a GUI tab in
`AccountK-Product-Setup` (`SupportRecoverySignerPane`), which checks the response against
`ReleaseSigningKey` before handing it over and will not sign until a customer is named and the caller
is confirmed verified - the weak point of a signed challenge is the phone call, since anyone at the
customer's login screen can issue a request. The script stays as a byte-identical fallback. The review,
and what is still unrun: `docs/users-and-recovery-plan.md` §11.

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
stopped. The other nineteen tables keep `date_insert`: they are not paired with anything.
`PartyDaoStatementsTest` pins every statement, character for character.

**Neither party joins `table_area` any more, and the join's history is the lesson.** It was there so
`map` could read `area_name`, and nothing else - no statement filtered or ordered by it. The
customer's was an INNER join once, which dropped a customer whose area row had been deleted out of
every list and every search; that was corrected to `LEFT`. **The supplier's listing was still INNER**
- so a supplier in that state was missing from `loadAll` while answering `getDataById` perfectly well
- and it survived the first correction because the supplier's *searches* never joined at all, which
was mistaken for the whole story. `PartyLookups` ended both: it reads `table_area` and `type_price`
**once per query** and the mapper takes the names from it, so the join had nothing left to do.

**That snapshot is `ItemsCatalogLookups` applied to the parties, for the same defect.**
`CustomerDao.map` resolved the price tier with a `getDataById` of its own per row, and
`SuppliersDao.map` the area - so reading the party list cost one query plus one per row, over a
`type_price` holding **three** rows. Measured on a schema migrated from nothing, same machine, same
harness: **88 ms → 16 ms at 147 parties, and 529 ms → 55 ms at 2,000**, which is the shape of it -
the old cost grows with the table and the new one does not. Build one per query and let it go with
the list it mapped; it is a snapshot, not a cache, and the one-row `map` fetches its own.

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

Two screens, and since 2026-09-23 two classes: `BuyController2` (`buy-view2.fxml`) has a
barcode/name/unit/price/quantity form above the table, and `QuickInvoiceController`
(`quick-invoice.fxml`) has none - **the table itself is the only entry surface**. Both extend
`InvoiceScreenController`, which holds everything they share: the header and its pins, the lines
table, the payment footer, and the whole save, print and return path. A subclass answers only how a
line is entered (`configureItemEntrySurface`, `placePartyField`, `focusItemEntry`, `resetItemEntry`,
`openCurrentItem`). They used to be one class switching on a mode, which hid fourteen controls and
left the till screen with the standard header whole; the quick FXML now lays out one header row, a
status line and a large amount to pay from the same `fx:id`s, **every one of which it must declare** -
a field the loader cannot find stays null and fails on the first click that reaches it.

**The quick screen exists for a new sale and a new purchase, to the holder of its key.**
`DocumentType.quickEntryPermission()` answers `sales.quick` or `purchase.quick`, and empty for both
returns - a return is written against a source invoice through a picker the entry row has no room
for - and a saved document always reopens on the standard screen. The key chooses a screen and grants
no write: saving still asks the create key inside `InvoiceSaveService`. `QuickInvoiceController`
`require`s it when it opens, the F6 switch and the two sidebar entries hide on it, and `V79` grants it
to every role and every `ALLOW` override holding the create key, so no till loses its screen on
upgrade. **The screen chosen with F6 is remembered per document** (`invoice.screen.mode.<type>`), with
the one global value written before as the fallback, and a user without the key opens the standard
screen whatever the computer remembers - `features/invoice/QuickInvoiceAccess` decides all of it.
A scan the quick table cannot take - an unknown barcode, a refusal - is written in its status line with
a beep instead of a dialog, because a dialog is dismissed by the next scan's Enter unread; a fault that
is not a `UserFacingException` still gets the dialog and its reference code.

**The unit is chosen on the line, on both screens** (`InvoiceLineCells.unit`,
`InvoiceLineEditService.editUnit`): a list of the line's own item's units, repriced through
`InvoiceItemSelectionService.selectUnit` and held to the unit's cost on a sale. Before it the unit
could only be set on the form above the table, so on the quick screen an item whose carton had no
barcode of its own could not be sold by the carton at all. The editable cells open on a line of the
invoice only - on the entry row a price or a quantity used to open and then be refused as "the
invoice line is not valid".

The quick screen keeps a trailing **entry row** for the operator to scan into. Two rules make that
safe, and both were missing when it was first written - the screen could not be saved at all:

- **The entry row is a control, not a sale.** `InvoiceLineTotals.isPlaceholder` (a row naming no
  item) is what the totals, the line count and `linesForSave()` filter it out by. It used to be
  counted, so `hasInvalidLine` - which `btnSave.disableProperty()` is bound to - was permanently
  true, and `comboStock` was permanently disabled for the same reason (`isNotEmpty(getItems())`).
  Anything reading a total must go through the summary, never through `table.getItems().size()`.
- **A line is added through `InvoiceScreenController.addLine`, from either screen**, and that is
  `features/invoice/InvoiceLineEntry`: the line validation, the expiry question and its batch check,
  the repeated-item merge (never for a scale barcode's weighing) and the low-stock warning, with a
  test per rule (`InvoiceLineEntryTest`). It lived in the controller, where nothing could test it.
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
  and `QUERY_ITEM_IN_STOCK` is for finders that name one item in one warehouse.

**A query for named items must not join `quantity_items_table`.** Each of its seven CTEs is a
`GROUP BY` over a whole line table, and MySQL builds all of them before it can return one row,
whatever the outer query filters on - so every barcode scan used to aggregate the entire sales
history: 0.8 s a scan on a copy with 106,606 sales lines, on the JavaFX thread, growing with every
invoice saved, and paid again by the save's stock guard and once per line by `SalesDao.map`.
`features/items/ItemStockBalanceSql` computes the view's own columns for named items with
correlated subqueries that reach their lines through the item's index (3 ms a scan, 31 ms for the
item with the most lines). It is the same definition, not a second one: `ItemStockBalanceSqlTest`
rebuilds the view's CTEs, joins and columns from its list and finds each in `R__views.sql`, and the
two were compared row for row on that copy with a second warehouse, transfers and posted and draft
counts seeded in - 2,504 rows, none different. A catalogue-wide query keeps reading the view, where
aggregating everything once is the right plan. A barcode resolves to an item id first, on the three
code indexes, and a code naming two items is still "not found", logged.

The name search behind the invoice's suggestion list (`ItemsDao.getFilterItems`) had the same
defect through the all-stocks aggregate: its `LIMIT 50` could only apply after every item's balance
had been built, about a second per suggestion on that copy. Its three searches now answer ranked
ids only, and the rows are read for those ids with `ItemStockBalanceSql.acrossStocksForItems` -
folded by `ItemCatalogSql.movementsOver`, the same text `MOVEMENTS` is, so the list and a search
cannot fold warehouses two ways. `findItemById`, `getDataById` and `getDataByString` read the same
way. Run old and new code side by side on the copy, thirteen searches plus the finders and the last
fifty returned the same items in the same order with the same balances; the searches went from
0.7-1.4 s to 4-243 ms. What is left of the 243 is `map`'s query per row over fifty rows.
- **A transfer line carries the unit and factor it was entered in**
  (`V19__stock_transfer_units.sql`), converts to base units before checking the source balance, is
  refused inside a closed period (`PeriodLockRegistry.STOCK_TRANSFER`), and is reversed through
  `DeleteRegistry`/`DeletionService` rather than by a delete of its own.

**What the source must cover is the item's total, never a line's.** An item may appear on a transfer
twice - two cartons and three pieces is an ordinary thing to type, and the screen builds it, since it
replaces a pending line only when the item *and* the unit match. `StockTransferCommand` used to refuse
a repeated item with an `IllegalArgumentException`, so that line was accepted at entry and the save
then reached the user as a reference code. **That refusal was protecting a check with a hole in it**:
the balance was compared once per line, so two lines of ten each both passed against a balance of
fifteen. `baseQuantityByItem()` sums them, which is what `InvoiceStockGuard` already does for a
document - it judges the whole document's effect on an item. The same record answers `itemIds()`, the
distinct ids in the order `lockSource` takes them.

**Reversing a transfer warns before it takes the destination below zero**
(`StockTransferService.deleteShortfalls`, feeding `DocumentDeleteStockCheck`). Goods that arrived and
have since been sold left the destination short with no word, while deleting a purchase in the same
state warned. A warning and never a refusal, for the reason that check carries; and a failure to read
it is logged and passed over, because a reference code in front of somebody reversing a transfer reads
as a refusal.

**A quantity typed into these screens is read with `NumberTextConverter`, never `Double.parseDouble`**
(`TransferQuantityInput`). The transfer screen read ٠-٩ as zero and then told the person their
quantity was invalid - the omission that had already cost `ReturnQuantityInput` its existence.

**The item card counts what the inventory sheet counts, and until 2026-09-20 it did not.**
`CardItemDao.balanceSql` added the opening balance, the four document families and posted counts -
and nothing from `stock_transfer_list` - while its own javadoc said "the same three terms" and named
three of four. So on any warehouse a transfer had touched, the card and the sheet beside it reported
two numbers for one shelf. **Reproduced on CI before it was fixed**: the acceptance run on the test's
own commit went red with `expected: <7.0> but was: <3.0>`, and the next commit's run was green.
`card_item_view` now carries both halves of every transfer (one row per warehouse, `name_custom`
being the warehouse at the other end) and the adjustment of every posted count (one row, whose
quantity is the **signed** difference in base units, so its `type_value` is 1 and its unit is the
item's own). `ItemCardTotals.netQuantity()` counts them because the screen shows it between the
opening and closing balances - three figures that have to be one arithmetic - and the adjustment is
summed signed, since a count's rows go both ways and magnitudes would report three missing and three
extra as the same six.

Two seams to know when adding a kind of movement: `CardItemDao.processTypeOf` answers `null` for a
`table_name` it does not know, so the row is drawn with no kind and left out of every total while it
sits on screen; and `CardController.dataInterface` is an exhaustive switch, so the compiler stops
you there. A transfer is not a document, so the row's "open" button is **disabled** through
`RowAction`'s `enabled` predicate rather than pressed into a reference code.

**`WarehouseStockDao` is the one place a warehouse's rows are locked**, in item-id order, and the
one place a named item's balance in one warehouse is read (through `ItemStockBalanceSql`). The
transfer's DAO delegates to it. The lock order is the invariant, and an invariant kept in two files
can be ordered two ways.

**A posted count's `system_qty` is a snapshot taken when the line was scanned, and that is
deliberate and correct.** The adjustment is a *difference*, not a target: book 10, shelf 9, a sale of
2, counted 9 gives `9 - 10 = -1` and a balance of 7, which is what is on the shelf. Re-reading it at
post time is what would swallow the sale. What the post does do now is give every counted item an
`items_stock` row first - without one, `quantity_items_table` has nothing to add the adjustment onto
and the count reports lines moved while nothing moves.

`mini_quantity_view`'s company-wide total is deliberately a *different question* from the per-warehouse
check the sale-time low-stock alert makes, and is documented as such rather than "fixed".

**Who moved what on a shelf is audited since phase C.** Eight triggers in `R__triggers.sql` on
`stocks`, `stock_transfer` and `stock_count` - the audit triggers reached items, parties, documents
and treasuries and stopped at the shelf, so who created a warehouse, who moved stock between two of
them and who turned a count sheet into a posted correction were recorded nowhere. The `UPDATE` on
`stock_count` is the one that matters: `status` moving DRAFT → POSTED is the moment the sheet
becomes a correction to every balance on it. **The two line tables are deliberately not audited**,
the decision `items_units` already carries: `StockCountDao.save` deletes and re-inserts every line
on each save, so a trigger there writes a row per line per save of a sheet that may run to hundreds
of items.

**The transfer history is a period, a warehouse and a text, not "the last two hundred"** (phase D1,
`docs/warehouse-plan.md` §15). A transfer older than those two hundred could be neither found nor
reversed, what a transfer had moved was on no screen at all, and the only paper was a log of a
date range chosen apart from the list. `StockTransferHistoryQuery` is the page, its totals and the
printed log around **one `WHERE`**, pinned with its binder; a warehouse matches **either end**, and
the text matches the note, an item's name by part, or one of the item's three codes **exactly**.
The totals are counts of transfers and lines, never a sum of quantities - cartons of juice and
pieces of soap add up to nothing. The history is the screen's second tab
(`StockTransferHistoryView`), a transfer's lines open in a `RowDetailDrawer`, and each transfer has
a slip (`StockTransferSlipLayout`, through `DocumentPdfPage`) printed with the quantities **as
entered, in their own units**, offered straight after posting in place of the "posted" notice.
The reversal stays in `StockTransferController`, because that file is what announces two moved
balances (`MultiDeviceRefreshArchitectureTest`).

**A count sheet names an item on one line, because each line carries the item's whole book**
(`StockCountLines`, phase D2). `system_qty` is the item's balance when it was scanned, and a post
moves each line's count less that book - so the screen, which kept a line per item *and* unit as an
invoice does, turned two cartons and three pieces against a book of 30 into `24 - 30` plus
`3 - 30`, and booked a shelf of 27 at **-3**. Reproduced on CI before the fix
(`expected: <27.0> but was: <-3.0>`). A second unit of an item already on the sheet now restates
its line in the base unit, and the service refuses a sheet naming an item twice. **Posted sheets
are not rewritten** - `docs/warehouse-plan.md` §16 has the query that finds them. The count's
history, its paper and a variance report by item are the screen's other two tabs, and a difference
there is `adjustment_agg`'s own expression, read out of `R__views.sql` by the test.

**A warehouse is switched off, never deleted** (`stocks.is_active`, V77, phase E1). Deleting one
is refused for any warehouse that has ever held an item, since `items_stock` carries a row per item.
`StockService.stocksForPicker` takes a `StockScope` and has **no default**: `ACTIVE_ONLY` for a
screen writing a new movement, `EVERYONE` for history - the `PartySearchScope` rule a third time.
Hiding it is not the guard: `InvoiceStockGuard` (a new document only - an edit of one saved there
goes through), `StockTransferService` and `StockCountService` each refuse a switched-off warehouse
through `WarehouseStockDao.nameIfInactive`. Switching off is refused for the default warehouse, one
with a draft count open, and one still holding a balance. `Stock` is a plain object now.

**An item's opening balance is one per warehouse, and lives in `items_stock` alone** (V78, phase E2).
There were three places: `items_stock.first_balance`, which every balance is read from;
`items.first_balance`, which the item screen wrote; and `after_items_update`, which copied the second
over warehouse 1 on **every** update of an item - its name, its price - so an opening that reached
the warehouse row any other way was undone by the next price edit. That was shown on a V77 schema
before V78 dropped all of it. Whether an opening may still change is `WarehouseOpeningBalance`: the
lock is **per warehouse** - only that warehouse's own lines close it - and its list of what moves an
item is `ItemStockBalanceSql.MOVEMENTS`, the balance's own terms, less the `POSTED` condition, since a
draft count holds the book the counter was shown. It replaced `OpeningBalanceRegistry.ITEMS`, which
counted lines in every warehouse against the copy. The item screen's field is the default warehouse's
opening and says so; the others are entered from the warehouse's row (`WarehouseOpeningView`, under
`items.update`), one warehouse and many items at a time, refused whole for a warehouse switched off,
a figure changed since it was read, or an item that has moved there.

**A count warns before its post leaves an item below zero, and never refuses it**
(`StockCountPostCheck`, 4.8.1). The adjustment is a difference against the book when the line was
scanned, so goods that left after the scan take the posted balance below zero - and a count is the
one writer for which that is a legitimate answer. The warning reads the balance as it is now,
through `DocumentDeleteStockCheck`, the transfer reversal's check. The count screen also prints a
**blank sheet** to count on (`StockCountBlankSheet`), and it carries **no book quantity on purpose**:
a counter who can read what the system expects writes that down. The transfer screen's item is an
`ItemSuggestionField` that resolves a scanned code on Enter. Its unit combo selects **its own copy**
of the scanned unit by id: `UnitsModel` has no `equals`, and selecting another copy left the box
empty on screen while the value was set. `docs/warehouse-plan.md` §20.

**`InventoryService` asks for `inventory.show`, and the stock count resolves a scanned name in the
warehouse being counted.** The first was a menu hint alone, so a reader without the key could not see
the button and could still reach a sheet carrying every item's cost. The second fell back to
`ItemsService.getFilterItems`, which folds every warehouse, and `StockCountService.lineFor` snapshots
whatever it is handed as `system_qty` - so an item found by name while counting warehouse 2 carried
the whole business's balance as "what the system says", and the difference posted was wrong by
everything held elsewhere. Invisible with one warehouse. Both fixed 2026-09-20; the rest of that
review, including the two defects that still produce wrong figures, is `docs/warehouse-plan.md` §7.

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

**A fee knows the movement it was paid for** (`V67`: `expenses_details.fee_source_type`, a
`ShiftCashSource` code 1-6, and `fee_source_id`, unique together). It used to be an expense row with
nothing saying whose: deleting a collection left its fee standing, and the correction the plan itself
prescribes - delete the payment and enter it again - charged the fee twice.
`WalletFeeService.removeFor` goes with the delete of a payment or a document, in that delete's own
transaction. There is no foreign key (the column points at six tables), and rows written before V67
stay unlinked on purpose: matching them by date and amount is a guess, and a fee linked to the wrong
payment is deleted with a payment it has nothing to do with.

**A document paid on a wallet pays the fee too, automatically** (`features/invoice/InvoiceWalletFee`,
inside `InvoiceSaveService.persist`). The two kinds of fee differ deliberately: a collection's is
*typed* off the wallet's receipt, so it is written on insert only; a document's is *computed*, so
`syncDocument` keeps it in step - rewritten when the cash, the treasury or the date moves, removed when
the document moves to a drawer or is deleted. Two rules keep that from surprising anyone: a fee already
posted is **not re-rated** when nothing about the cash changed (fixing a note on an old invoice must not
rewrite an expense of a month already reported), and a document saved before V67 is **not charged
retroactively**. Proven on MySQL through `ExpenseDatabaseAcceptanceTest` (16 cases, twice, from nothing
and over a V63 database); what is *not* proven is a whole invoice save on a wallet end to end -
`docs/treasury-plan.md` §19.3.

**A transfer between treasuries may carry a fee** (`TreasuryTransferCommand.fee`, `V68`): what a
wallet or a bank charged for it, posted as an expense on the **sending** treasury and tied to the
transfer with `fee_source_type = 11` (`TRANSFER_OUT`), so deleting the transfer takes it. The
destination receives the amount in full and the source must cover `amount + fee`. It is typed,
not computed: a wallet's withdrawal fee is not its collection percentage, and a wrong suggestion is
a figure people learn to accept. `V68` only replaces V67's CHECK - it does not touch V67.

**All of it was then watched on a copy of a real database** (`docs/treasury-plan.md` §21): the app
applied V66-V68 itself over 1,205 invoices, and a transfer with a fee, a cash sale on a wallet, its
edit and its delete each did on screen and in MySQL what the tests said. **And the screen found what
2,615 green tests could not: opening a saved document for editing did not restore its treasury.**
`BuyController2.selectData` put back the date, the party, the delegate and the warehouse and left the
treasury on the screen's default, so re-saving an invoice paid on a wallet - to fix a note - moved its
cash to the main drawer without a word. It predates the fee work and affected every document on a
non-default treasury. `selectStoredTreasury` fixes it (adding a treasury since closed rather than
leaving it unselected), and `InvoiceEditRestoresTreasuryTest` reads the screen's source so the line
cannot be lost in a merge - crude, and the only check possible without a toolkit.

**The transfers, deposits and capital screens list a period, not "the last fifty"**
(`TreasuryHistoryFilter`/`TreasuryHistoryPage`, `docs/treasury-plan.md` §22). A movement older than
the fifty could not be found, and so could not be corrected either - the delete acted on a row of
that list. The page and its totals are read with **one `WHERE`**, the totals over the whole filtered
set; a treasury matches either end of a transfer, inside one bracket. `TreasuryHistoryBar` and
`TreasuryHistoryTable` give all three the shared bar, content-sized columns, a delete button in the
row, and a PDF and a spreadsheet of the columns on screen over the whole extract. **The deposits and
capital reports carry no totals line on purpose**: both directions share one amount column, and their
sum is a number that means nothing - the two totals go in the subtitle.

**A deposit, a withdrawal and a transfer each have a paper** (`TreasuryVoucherLayout`,
`docs/treasury-plan.md` §23): a receipt voucher, a payment voucher and a transfer slip, built on
`ExpenseVoucherLayout` through `DocumentPdfPage`. The slip carries the fee and what **left the
source** (`amount + fee`), or it would not agree with the source treasury's statement; a capital
deposit says so on the paper. `forVoucher(id)` reads the stored row again with who entered it, and
the button is in the row. There is no amount in words on purpose. **Drawing the three to images found
what no test could**: a shadda in a label splits the word in the PDF font. The label lost its
diacritics; the same happens to a user's own note with tanween on any document, and
`ArabicTextHelper` does not handle it - that one is not fixed.

**What the wallets kept is a report** (`WalletFeeReport`, opened from the treasuries screen): fees
per treasury and per kind of movement, grouped in SQL. It reads the expense rows under
`system_key = 'WALLET_FEE'` and nothing else, so its total **is** the heading's total in the expenses
reports - not a second computation - and a fee tied to no movement is listed rather than hidden, or
the two would disagree. The printed treasury statement now says which statement it is: the treasury,
whatever narrows the rows, and the balance brought forward.

**A treasury may carry its wallet or account number and a minimum balance** (`V69`). The minimum is
a **warning, never a refusal** - a withdrawal is still refused only above the balance itself - zero
means none set, and every existing treasury has none, so an upgrade raises no notification.
`min_balance` is deliberately **not** in `treasury_current_balance`: what a balance is has one
definition and a warning level is not part of it; `SELECT_BELOW_MINIMUM` joins the table to the view
itself. The dead `TreasuryMovementDao` family - eight classes and the four `@Deprecated` methods that
mutated the *opening* balance under a name suggesting the current one - is gone; the
`treasury_movements` table stays, deliberately dead.

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

### Returns

`features/returns` with `docs/returns-plan.md` as the contract. One sentence carries the area: **a
return gives back exactly what a document took, and nothing else** - so its price, its cost, its
share of every discount and its quantity all come from the source line, and where there is no source
the system says so rather than pretending.

**`ReturnGuard` and `ReturnCostResolver` run inside `InvoiceSaveService.persist`** and refuse, in
order: a source that does not exist, a party different from the source's, a deferred return of a
**cash** invoice, a line the screen typed in by hand on a return that names an invoice, a price or
unit or line-discount share different from the sale's, more of an item than the source sold, more of
a **line** than that line sold, and a header discount that is not the source's proportional share.
The source is locked with `SELECT … FOR UPDATE` before "how much is left" is read; until that lock
existed two tills returning one invoice were kept apart only by accident, because `InvoiceStockGuard`
happens to lock the item rows first.

**The per-line cap is not the per-item one.** An invoice listing one item twice - five at 100 and
five at 60 - let all ten come back against the line at 100: ten of ten sold, every price matching the
line it named, and 200 refunded that nobody ever paid. The price belongs to a line, so the quantity it
may be refunded for belongs to that line too (`alreadyReturnedBySourceLine`).

**A document's own discount is shared by value** (`ReturnHeaderDiscount`): the fraction of the
source's `total` that the return's `total` is, since a document discount has no owner among the lines
and any other allocation would be invented. Nothing carried it until 2026-09-19 - a sale of 1000 with
100 off was paid 900, and returning all of it refunded 1000. The screen fills the box and **locks** it
while a source is named, for the reason a picked line's price is locked. Editing a line's quantity
recomputes its share from the **source line**, never by scaling the rounded share on the row: a
rounded share scaled up lands a piastre off what the save expects, which is a save nobody can finish.

**Two things are warnings and not refusals** (`ReturnSettlementAdvice`, the shape
`ExpenseBalanceCheck` established): a deferred free return on the party cash sales land on - a bucket
rather than a person, so the credit sits there for ever - and cash handed back to a party who still
owes. Both are legitimate and usually a mistake, and the person at the counter knows which. The cash
party is read from the default-customer setting, **never a literal `1`**. `DocumentDeleteStockCheck`
is the same answer on the delete path: deleting a purchase or a sales return takes goods back off the
shelf, and a shop that sells before entering the supplier's bill is already below zero on paper.

**A free return is where the reason is asked for.** The reason combo lives in the picker, which a
free return never opens, and `ReturnSourceWriter` used to return before writing anything when there
was no source - so every one of them reached the reasons report as "none given". Two settings narrow
free returns and both ship **off**: `return.require.source.invoice` and `return.free.limit`.

**Editing a saved return is the same path, and `restoreSource` is what makes it so.** Without it
`ReturnGuard` reads a source of `0`, treats the document as a free return it has nothing to compare
against, and every rule above is silently off.

**The source invoice is found, not recited** (`ReturnSourceSearch`): digits are a document number and
the rest is a party's name, either or both, read through `NumberTextConverter` so ٠-٩ count - the same
omission that made `ReturnQuantityInput` necessary after a quantity typed the ordinary way on an
Arabic keyboard was silently a zero and its line silently left off the return.

What is still open - the till is never checked on a cash refund, `InvoiceSaveService`'s ten
telescoping constructors, and the Arabic literals under `features/invoice` - is `docs/returns-plan.md`
§8.

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

### Employees

`features/employee` is the record, `controller/employee` the screens, and `docs/employees-plan.md`
the contract for everything still to come (the employee's account, the payroll run, attendance,
commission). Phase A is what is shipped: the job, the dated salary and the status.

**The job is a row in `jobs`, and the delegate is a flag on it.** It used to be `UsersType` — four
constants whose ids were matched to that table by hand — and `getUserTypeById` answered `null` for
any other id, so **adding a row to an editable table broke the employees screen twice over**: once
in `EmployeesController` when it drew the job column, once in `EmployeesDao.getData` when it saved.
A delegate was `job == 4`, written into a DAO constant and two queries, so a shop could not have a
delivery delegate and a collections delegate. `jobs.is_delegate` is what fills the delegate combos
now; `V57` sets it on the seeded row 4 alone, so nothing changes on upgrade. The jobs are managed
from the employees screen rather than through `MasterDataPane`: that editor is a name and one
numeric slot, and a job carries a flag today and a suggested salary as well.

**The salary is dated, and `employees.salary` means the salary at hire.** One number with no date
made every calculation of August read a figure raised in September, with nothing recording that it
had moved — the `first_balance` and `treasury.amount` lesson, twice learned. `employee_compensation`
carries `(employee, effective_from, kind, rate)`, `employee_current_compensation` is the one place
that answers "what is he paid now", and the old column keeps a fixed meaning stated in its COMMENT.
A row dated in the future is the ordinary case: a raise agreed in March to start in April is entered
when it is agreed, and the view begins reading it on the day.

**`SalaryChangeGuard` is `OpeningBalanceGuard` applied to that figure.** While one dated rate exists
it and the column say the same single thing, so the form may still correct both. Once there are two,
the history has begun and the form is refused — and the message names a road that exists, which
`opening.correction.customers` did not for months: `EmployeeService.changeSalary`, reachable from the
same screen that shows the refusal. `EmployeeService` keeps `employees.salary` equal to the earliest
dated rate in one place, so it cannot come to mean two things again.

**A salary is not fetched for a reader who may not see one.** `employees.show.salary` used to hide a
column — by index, `getColumns().get(4).setVisible(...)` — while the figures crossed the connection
anyway and sat in the memory of a screen opened by somebody with no right to them. The permission is
answered once, in `EmployeeService.salaryVisible()`, and it decides whether the columns are
**selected at all**. It follows that **filtering on a rate requires the same permission**: a bound
you can move is a way of reading the figure it filters on, one comparison at a time.

**A stopped employee is marked, not hidden.** `DeleteRegistry` rightly refuses to delete anybody
named on an invoice or an expense, and `employees.column_name` is UNIQUE, so before `is_active` the
people who had left stayed in every combo for ever. `EmployeeScope` is an argument and never a
default — `ACTIVE_ONLY` for a document being written, `EVERYONE` for one being re-opened and for the
employees list, which is the only screen the flag is turned back on from. The same rule, learned the
same way, as `PartyTableSpec.PartySearchScope`.

**What is left of `model/domain/Employees` is a seam.** `Total_Sales`, `Total_Sales_Re` and
`ExpensesDetails` hold their delegate as that class, so `EmployeeService.delegates`/`delegateByName`/
`delegateById` still answer with it — built from `EmployeeRef`, an id and a name, so no salary is
read to fill a dropdown. It dies with the single `Document` model, not before.

**`V57` was found wrong by running it, not by building it.** `ALTER TABLE jobs MODIFY COLUMN id INT
AUTO_INCREMENT` fails with error 1833 on every install, new or upgrading, because `employees.job` is
a foreign key pointing at that column — MySQL refuses to change a column a key points at. A green
build saw nothing. The key is now taken off and put back, and it is **looked up by its columns in
`information_schema`** rather than by the name `V1` gives it, since a database converged by `V4` may
carry another. This is the same class of defect as `V55`'s and `V56`'s helper-procedure calls, and
the same answer: **migrate a schema from nothing before believing a migration**.


### The employee's account

`features/employee/statement` is where one employee's account comes from, and `V58` is the schema
under it. It is `features/party/statement` class for class — a `Filter` whose checks are in its
constructor, a `Repository`, a `Service`, a `Page`, a `Summary`, a `Query` pinned character for
character — and it carries the same four rules, for the same reasons.

**Cash has one writer, and it is `expenses_details`.** Every pound that leaves a till for an
employee — a salary, an advance, a bonus handed over, a settlement — is a row there with an
`emp_id`, written through `ExpensesDetailsService`. Three things follow, and the third is the one
worth knowing: `treasury_balance` already reads that table, so the till needs no second ledger;
`ShiftGate`, `PeriodLock` and the shift cash journal are inherited rather than rebuilt beside four
chances to forget one; and **the years of wage payments already sitting in customers' databases
appear on the new statement with no data migration at all**, because it reads the table they were
written to. That last one is proven rather than assumed: an expense row with no purpose beside it
comes through the view as a `SALARY`, which is what those rows have always meant.

**So `employee_ledger` holds no cash — a row there with a cash amount is a defect, not a feature.**
It holds what is *not* cash: an entitlement earned, a bonus awarded, a deduction decided, a
commission approved, a balance carried in. `employee_account_table` unions the two exactly as
`account_customer_table` unions a party's payments with their invoices, and `employee_balance` is
derived from that view rather than summed a second time — the mistake `view_customer_receivables`
carried for years.

**An advance is a debit on the day the cash leaves, and is never deducted again.** The payroll run
of phase C enters the whole entitlement into the ledger and hands over the difference; the balance
squares because each side is recorded once, in its own place. Deducting the advance a second time
would charge the employee twice for one payment — the line `docs/employees-plan.md` calls its most
important.

**The direction lives in `EmployeeEntryKind.sign()` and nowhere else.** `amount` is stored unsigned
with a CHECK, the view restates the direction as a `CASE`, and `V58` restates the list of names as
a second CHECK — so `EmployeeLedgerAgreesWithEntryKindTest` reads both files and fails the build
when any of the three drifts. Two definitions of a direction is what cost the party ledger a data
migration (`V15`) and a screen that had been showing customers the wrong balance. The opening
balance is two kinds rather than one signed amount: a screen asking "in the employee's favour or
against them" is answerable, while a box that quietly accepts a minus sign is a place to make a
mistake worth twice the figure.

**`EmployeeMovementSource` exists because both enums carry a `BONUS`** and they mean opposite
things — one awarded increases what is owed, one paid reduces it. Resolving a row's kind without
its source gets the sign backwards on exactly that row.

**A movement with no cash does not pass through `ShiftGate`**, and that is the rule rather than an
omission: a deduction takes nothing out of a drawer, and requiring an open shift for one would stop
a correction being made outside trading hours, which is when corrections are made. The same line
this file draws for a party's debit and credit notes. And there is **no update** of a recorded
movement — it is corrected with an opposing entry, decided at the start here rather than enforced
by the silence of a screen.

**No expense heading is seeded, and no purpose maps to one in code.** The first draft of `V58`
seeded "رواتب وأجور"; migrating a schema from nothing showed that `V1` already seeds "مرتبات" and
"سلف", so it would have shipped a third heading meaning what two others mean. The payment screen
offers the headings from `expenses` and the person paying chooses — a constant like
`SALARY -> 1` is the `UsersType` and `DELEGATE_JOB` mistake at a different editable table.

**`employee.pay` is the additional permission, not a replacement for `expenses.create`.** The base
act is creating an expense, so a payment needs both; `V58` grants the new one to whoever holds the
old, so nobody loses an ability on upgrade, but a role given only `employee.pay` cannot pay anybody.

### Delegates and commission

`features/delegate` and `docs/delegates-plan.md`. Phases A, B and C are shipped: the **dated commission
rule** (`V70`, `employee_commission_rule`), the arithmetic over its tiers and the rules screen opened
from a delegate's row; then the **delegate of a collection** (`V71`), the two commission bases in SQL
and the monthly performance report; then the **frozen monthly run** (`V72`) and its posting, once, by
one of two roads; then D1, the delegate's commission statement and two reminders; then D2, the
discount ceiling (`V73`), the month-in-detail report, the delegate filter on the balances and ageing
screens and the delegate trend - **the item is closed** (`docs/delegates-plan.md` §15). The screens
open from the employees screen.

**It replaces `targeted_sales`/`target_delegate`, which nothing new may read.** That row had no period,
so the view joined it to every month in history and changing a target changed last January's
commission; its `IF` chain ended in an unconditional third rate, so a delegate at 1% of target was
paid it on everything. The rule now carries `effective_from` exactly as `employee_compensation`
does, and **a month is judged by the rule in force on its first day** - a rule starting on the 10th
governs from the next month, so no month is split between two targets and none is raised after its
sales are in. The old rows are **not** translated: their defaults cannot be read as rising
thresholds without guessing.

**`CommissionTiers` is the arithmetic and has no database.** One to three tiers, lowest threshold
first; **below the lowest there is nothing**. A threshold is compared as an *amount*
(`target x percent / 100`), never as the rounded percentage on screen - 99,996 of 100,000 shows
100.00% and is not the target. `WHOLE` pays the reached rate on everything, `MARGINAL` each tier
on the part inside it; a base of zero or less earns zero, never a negative commission; with no
target only a tier starting at zero can be reached, and the rule accepts nothing else.

**An empty box is not a zero** (`CommissionRuleForm`): an empty pair is "no such tier", `0` and `0`
is a tier from zero. The second and third tiers therefore take `setOptionalNumberFormatter` - the
seeding one would make every rule three tiers at zero, refused for having no order, on a form nobody
typed into. The filter-field lesson, at an entry form.

**A MySQL CHECK passes when it evaluates to NULL.** V70's first draft wrote
`tier2_from > tier1_from AND tier2_rate BETWEEN 0 AND 100`, which accepts a tier with a threshold
and no rate. Every branch comparing a nullable column says `IS NOT NULL` itself, and
`CommissionRuleDatabaseAcceptanceTest` inserts the bad rows and checks the refusal came from this
table's own constraint. That class builds a scratch schema from nothing, runs from a worktree with
`ACCOUNT_DB_ACCEPTANCE_CONFIG`, and drops the schema; nine cases, green twice on 2026-09-19.

**A month's commission is frozen by approving it, and there is no draft** (`CommissionRunService`,
`V72`). The preview is computed live and writes nothing; approval creates `commission_run` and its
lines in one transaction, so a run is born `APPROVED`, and only once its month is over. A line
carries **everything that produced the figure** - basis, target, the tiers as they stood, the month's
three figures, the base, the tier reached - so it can be worked out again from the line alone after
the rule has been replaced. A delegate with a rule who reached nothing gets a line of zero; one with
no rule gets no line. **One approved run per month** is a unique index over a *generated* column
(`active_key`, NULL once cancelled), so cancelled runs repeat freely - V66's `month_key` trick turned
round. A run is cancelled only while nothing of it is posted, and a rule a month was computed under
is history: amending it in place or deleting it is refused with the road that exists, a new rule from
a later month.

**The commission reaches the employee's ledger once, by one of two roads, and "once" is a primary
key.** `PayrollService.approve` writes one `ENTITLEMENT` = basic + allowances + **commission**, so a
run that also wrote a `COMMISSION` row - as the plan's first draft did - would pay every delegate twice
on paper. `commission_posting(line_id PRIMARY KEY, payroll_run_id, ledger_entry_id)` with a CHECK of
exactly one road: the payroll marks the lines it pays inside its approval, a shop without payroll
posts them as `COMMISSION` movements from the run screen, and whoever comes second meets the key.
The payroll side is `CommissionSource`, a seam shaped like `AttendanceSource` for the same two reasons -
`NONE` is every shop that approves no runs, so nobody's payroll changed the day it arrived. **What the
payroll sums and what it marks are two statements sharing everything from `FROM` on**, compared as text
by `CommissionRunQueryTest`; it is owed what was approved for its period **or an earlier one**, since
October is approved in November. A draft whose commission is not the approved figure is refused at
approval (`payroll.error.commission.differs`): the entitlement is built from the line while what is
marked is the approved amount, so a draft built too early would mark 500 paid and pay nothing.

**The freeze is the database's.** Six triggers, **kept as the last section of `R__triggers.sql`** under
`-- commission run (V72)`: a line and a posting refuse every UPDATE and a DELETE outside a wipe; the run
moves only `APPROVED -> CANCELLED`, and not once a line is posted. They are last because
`DelegateActivityDatabaseAcceptanceTest` builds a V70 schema and cuts the file there - a trigger cannot
be created on a table that does not exist yet. `CommissionRunDatabaseAcceptanceTest` (12 cases on MySQL)
proves the unique key, the freeze with and without `@app_bulk_wipe`, that a late invoice moves the
preview and not the approved line, both roads through the real `PayrollService`, and that SQL behind
the service is refused too. **Not built, on purpose** (`docs/delegates-plan.md` §12.4): refusing a change
of an invoice's delegate in an approved month - the line carries its own figures - and an adjustment
run, whose negative amounts collide with two `>= 0` CHECKs and need a decision of their own.

**A collection's delegate is written at entry and never derived** (`customers_accounts.delegate_id`,
`V71`). Read off the customer at report time, moving a customer between delegates would rewrite both
delegates' history. `AccountCustomerService.save` writes it inside the insert's own transaction with
`DelegateActivityQuery.ATTRIBUTE_COLLECTION_SQL`: the delegate of the invoice the collection is
allocated to, else the customer's default - read *through* `employees`, because
`custom.default_delegate_id` has no key (V56, on purpose: a preference) while this column does (a
fact, like `total_sales.delegate_id`, and declared in `DeleteRegistry.EMPLOYEES`). Only a row that
moved cash, and only one naming nobody yet. **Everything before V71 stays NULL and no migration may
guess it** - `DelegateActivityQueryTest` fails on a backfill - and the report shows "collected with
no delegate" as a figure of its own rather than dropping it. It is a second statement rather than a
column of the insert because that insert is shared with suppliers and pinned in `PartyLedgerSpec`.

**The two bases are one statement, each side grouped before the join** (`ACTIVITY_SQL`): joining the
three tables and grouping after multiplies an invoice by every collection of its delegate. Sales are
`total - discount`; a return counts in the month of the *return*; collected is cash in both
directions - the invoices' `paid_up`, plus attributed collections, **less cash refunded on returns**.
`purchase` is never read, which is the whole of how "a credit note is nobody's collection" is kept.
**"بيع مباشر" (employee 1) is the no-delegate row for invoices**: `total_sales.delegate_id` has been
NOT NULL since V1, so the rows add up to the month's sales with no synthetic row.
`DelegateActivityDatabaseAcceptanceTest` works October out by hand - including a return dated October
of a September invoice - drives the collections through the real `save`, and upgrades a V70 schema
holding collections; eight cases, green twice on 2026-09-19.

**The report is by the month, and that is the rule rather than a limit**: the rule, the target and the
run are all a month's, and an achievement beside a fortnight's sales means nothing. Its commission is
a **preview**, computed when read and stored nowhere. `commission.reports` opens the activity;
a target, a rate and a commission need `commission.show` as well, and without it the rules are **not
fetched** and the screen does not build those columns.

**A frozen figure and live data are shown side by side rather than kept in step**
(`CommissionStatementService`, opened from the rules screen). Phase C declined to refuse a change of an
invoice's delegate in an approved month; the statement is what it promised instead: each approved month
with the same month **computed again today**, by the run's own two steps. A difference is not an error
and corrects nothing - the screen says so in a sentence, because a column of non-zero numbers beside
approved figures reads as an accusation. A delegate the month no longer names has a live figure of
**zero**, not "none"; "none" is kept for the one case of no rule in force on that day any more. A
cancelled run is not on the statement.

**The two reminders decide nothing themselves** (`CommissionSources`, in `NotificationBootstrap`): the
day and the threshold are `DelegateAlerts`', over plain values, with a test per boundary. "Last month is
not approved" fires only when approving it would write a line; "will not reach his lowest tier at this
pace" projects `base / days elapsed x days in the month` and judges **only from the 20th**, by the
month's own length, for an active delegate with a target. Both are silent for a shop that gives no
delegate a rule - so upgrading raises nothing - and each is enabled only for a reader who may see what
it says. **Neither has been seen firing.**

**A delegate may carry a discount ceiling** (`employees.max_discount_percent`, `V73`; NULL is no ceiling,
which is everybody on upgrade, and zero is a real one). `DiscountCeiling` judges **the lines' discounts
and the header's together** against the lines before discount - a ceiling on the header alone is walked
round through a line's discount column - and compares **amounts, never the rounded percentage**.
`DelegateDiscountGuard` is asked inside `InvoiceSaveService.persist` **before the number is allocated**
(the counter does not roll back), of a **sale** alone. `sales.discount.override` is granted to whoever
holds `commission.rule.update`, **not** to whoever may sell - a deliberate departure from "nobody loses an
ability on upgrade": no ceiling exists until somebody sets one, and granted to every cashier a ceiling
would stop nobody. It is read with `isGranted` on purpose - it guards no write, it picks which of two
answers a rule gives. The ceiling is edited on the commission rules screen, in a card of its own that the
rule's save does not touch.

**One delegate's month in detail explains a figure and never offers a second one**
(`features/delegate/report`, opened from his row of the performance report). By customer and by area are
read off the documents (`total - discount`, `ACTIVITY_SQL`'s own expression); by item and by group off
the lines (`total_sel_price - discount`), and **a discount taken on a whole invoice is not shared out
among items** - that would be an invented rule - but shown as a figure of its own, so lines less header
discounts is the same net. `DelegateDetailDatabaseAcceptanceTest` holds all four breakdowns to the
activity query's net, read rather than typed twice. No profit column: a line's `total_profit` is not
this system's definition of profit.

**"The customers a delegate follows" is `features/party/CustomerDelegateCondition`, one condition for
the balances list and the ageing report** - derived from `custom.default_delegate_id` as it is today,
which is right for "who follows this customer now" and wrong for a commission, where the delegate is
written at the event. **"No delegate" means no delegate stands behind the default** - `jobs.is_delegate`,
not a stored zero: the column has no key, so it can name an employee since deleted or no longer a
delegate, and the customer's own form shows nobody for both. With the combo listing every delegate
(`EmployeeScope.EVERYONE`), the choices cover each customer exactly once; the first draft said "no
employee" and left such a customer under no choice at all. It narrows which parties are listed and
touches no aged invoice, so every ageing row still reconciles. **The delegate trend defines nothing**
(`features/delegate/trend`): its four sums and its date conditions are `ACTIVITY_SQL`'s own text,
which `DelegateTrendQueryTest` finds there, and `DelegateActivityDatabaseAcceptanceTest` holds each
month of it to that month's performance row on MySQL. Its periods are `TrendGranularity`'s - one
definition of a week for both charts - and it is a screen of its own because the party trend is built
on a `DataInterface` a delegate has none of.

**D2 was then watched on a copy of a real database** (§14.5), signed in as an ordinary user - user 1
bypasses the override like every permission. A sale of 300 discounted 50 against a ceiling of 10% was
refused with its sentence, the caret went to the discount box, and **the number counter did not move**;
discounted 20 it saved as the very next number. The detail report's by-item view met a month with a
real header discount: lines 10,213.50 less 10.00 is the performance report's 10,203.50. **What only
the paper showed:** the PDF's totals line read "net 10,213.50" and the true net was nowhere on the
page - a screen says it on a card and a page has no cards - so the subtitle now carries it, written
from the held summary rather than read back out of a label. Found on the way, and both fixed
since: a user whose grants were imported from the legacy system sold but held no `stock.show`, so the
sales screen raised a refusal and opened with an empty warehouse combo (`596e23f8`, the pickers read
`StockService.stocksForPicker`); and a hyphenated product code was reversed in every PDF
(`owala-5250` printed `5250-owala`) - 4.8.2, see **Printed reports**.

Permissions are `commission.run.create` / `.update` / `.post` for the run (V72, granted to whoever holds
`commission.rule.update`; `POST` derives `CRITICAL`), `commission.reports` (V71), and
`commission.show` and `commission.rule.update`, granted by V70 to whoever holds
`employees.show.salary` and `employee.salary.change`. **`update`, not `manage`**: the risk is derived
from the key's last word and `MANAGE` is `CRITICAL`. A rate is a figure about a person, so
`history`/`ruleForMonth` call `require` before any query. **All four screens were opened on 2026-09-19 on a copy of a real database**, where the app applied
V66-V72 itself: a rule saved and tried out, the performance figures equal to MySQL's, August approved
and posted (`commission_posting` -> one `COMMISSION` ledger row), the statement showing approved beside
computed-today, and a rule a month was computed under refusing its delete. What only the screen showed:
**a dialog takes its size from its root node**, and three of these had none, so the last column - the
commission itself - opened behind a scroll bar; each root now sets a preferred size that fits 1366x768.
Still unseen: the two reminders firing, the payroll road on screen, English, and a reader without
`commission.show`. **And a launch from a worktree is an unlicensed launch**: `license.dat` is
git-ignored, so the app took the trial path and charged this machine a failure in `trial.dat`. Put the
licence beside the `config.xml` that `ACCOUNT_CONFIG_DIR` names before running a worktree build.

### A list screen's bar

`account.table.ListToolbar` places the controls above a list, and the order is its decision, not
the screen's: **search, filters, clear all | refresh, print, export, view | everything else**. A
screen names what each control *is* (`.search(...)`, `.refresh(...)`, `.extra(...)`) and calls
`installIn(row)`, which works on a row declared in FXML as well as one built in code. The screens
had each written their own row and no two agreed - the totals screen had the view menu before
refresh and print after the bulk delete - and none of those differences had been decided.

- **The filters panel is closed until opened, and the toggle carries a count** (`showActiveFilters`).
  Each filter record answers `panelConditionCount()`, tested apart from the screen. What stays in
  the bar is not counted: the text, and whatever says *which* list this is - a statement's period,
  the inventory's warehouse, the treasury statement's treasury. A closed panel must never hide
  that, nor the fact that the list is narrowed.
- **A single filter sits beside the search** (users, unit prices): a panel holding one combo is a
  click for nothing.
- `ListToolbarArchitectureTest` fails the build when a controller using `TableColumnViews` or
  `PageJumpBox` does not go through `ListToolbar`, and when anything else shows or hides a
  `filter*Pane`/`filter*Panel`. Its exemption list fails in both directions.

A master and its detail are one table and a `RowDetailDrawer` (merge items, stock transfers, the
audit log and its administration journal), never two tables stacked. Tables that do not depend on each other's selection - the
shift administration, the permissions editor, the item picker's basket - are not that case.

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

### A row's detail, and a screen that has to fit 1366x768

**A detail table stacked under its master table does not fit the screen this ships to.**
`account.table.RowDetailDrawer` is the panel a row opens over its own screen instead:
`installIn(AnchorPane)` once, `setContent(node)` once, `show(title, subtitle)` per row. The
merge screen (دمج الأصناف) is the worked example - its candidates and the operations of the
selected one were two tables sharing one column of height, which on a 768px screen is four
rows each.

Four things it settles, and each was a decision rather than a default:

- **No scrim, and the table stays live underneath.** The gesture is running down a list -
  open a row, read it, open the next - so an open panel follows the selection instead of
  having to be closed first. That is why the panel is only made invisible rather than
  removed: an invisible node is not pickable, so the clicks reach the table.
- **The row's own button opens it, not the selection.** The merge table is multi-select,
  because that selection *is* the list of merge sources; a panel opening on every ctrl-click
  would be in the way of the screen's own gesture. `RowActionsColumn` first, double-click as
  the shortcut, and `showOperations` deliberately never calls `select` - selecting a row to
  "show" it would add a source nobody asked to merge.
- **The edge is the trailing one, and it does not depend on the language.** The panel covers
  the columns a table puts last, never the ones naming the row. JavaFX renders an RTL node's
  children mirrored, so the logical right *is* the trailing edge in both directions - visually
  left in Arabic, visually right in English - and one anchor answers both. The first draft
  flipped it with the language and was right only in Arabic: **opened in English, the panel
  sat over `View`, `Role` and `Item`**, and the unit test written for the flip passed, because
  it pinned the wrong rule. Seeing a screen work in one reading direction says nothing about
  the other.
- **It takes the whole width below 760 points** rather than splitting it into two unusable
  halves.

**The sidebar squeezed rather than scrolled, and nothing said so.** Each accordion section
had a `ScrollPane` of its own inside a fixed-height column, so on a 1366x768 screen the nine
headers, the brand row, the user menu and the footer left the expanded section a sliver: the
items section showed *one* of its ten buttons, with a scroll arrow above and below it. The
accordion and the footer now sit inside one `ScrollPane` (`sideMenuScroll`), so a menu taller
than its window scrolls **with its headers**, and `MainScreenController.applySidebarDensity`
adds `sidebar-compact` below `COMPACT_SIDEBAR_HEIGHT` - paddings, spacing and the logo, worth
about a hundred points on that screen. Nothing is hidden: an entry that disappeared because a
window was resized is a worse bug than the one being fixed. Spacing is set in the controller
rather than the stylesheet because the FXML sets it, and a value set on the node wins over CSS.

**`TableSetting` stored the window size and called it the user's column width.**
`TableAppearance` defaults to filling the available width - a constrained resize policy, under
which JavaFX computes every column from the table's own width - and `TableSetting` persisted
each change of that computed width. The merge screen's operations table had stored 1613 points
across seven columns that way; restored into a 690-point panel it showed two of them. A width
is now written only while the policy is unconstrained, the one state in which a drag is the
only thing that can change it (`TableSettingTest`). **That stops new garbage and does not clean
the old:** a width stored while filling was on is restored as soon as a user turns filling off,
which is the state of the development machine this was found on - so a table moved into a
narrower place still needs an id of its own, and the merge panel's `mergeOperationsPanel` is
what actually fixed it there.

**A table with no id shares its saved widths with every id-less table in its package.**
`TableSetting` keys a column by index under the prefix `table_` when the table has no id, and
`Preferences.userNodeForPackage` makes the node the package, not the class. The stock transfer
lines table opened with 862 and 751 points on its first two columns - another table's - and its
quantity column off the edge. Give every table passed to `tableMenuSetting` an id.

### Column views and widths

`account.table.TableColumnViews` is the "العرض" menu - a compact view, a full view, a
hand-picked set of columns and the way back to the default, remembered per table - and
`ContentSizedColumns` sizes each column to what it holds instead of stretching every column
across the window. The parties list, the party accounts screen and the items list use them. **A list that
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

**Printing and exporting follow the same columns.** `VisibleColumns` is the one reading of a table
for a report: its visible columns in display order, minus the controls, and a row's value through
the column's own value factory. **Not through `getCellObservableValue`**, which answers null for
every row of a column not placed in a table - a silent empty report, found by the first test of
it. `TablePdfLayout` builds the PDF from it (with `TablePdfReport` choosing and writing the
file - both were `PartyListPdfLayout`/`PartyPdfReport` until the items list printed the same way;
a column holding a plain `double` is money or a quantity only when `NumberFormats` names it) and
`VisibleColumnsExcelWriter` the spreadsheet,
so what is hidden on screen is off the paper and out of the file too. The accounts screen uses
both; its old writer, with a fixed list of nine columns, is gone.

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

### Collections trend

`features/party/trend` is the chart of what was charged and what was collected (or, for
suppliers, owed and paid) by year, month or week, with the same dates a year earlier beside them.
It opens from the accounts screen, next to the ageing report, and asks the same permission.

- **Its two figures are the balances screen's.** `PartyTrendQuery.DEBIT`/`CREDIT` are the
  expressions `PartyBalanceQuery` sums into the period-debit and period-credit columns, per row of
  the same view, and `PartyTrendQueryTest` fails the build if the text of either drifts - a chart
  that disagrees with the column beside it is the defect this whole area exists to remove. It
  follows that an opening balance is debit on the day its party was created, and a return is
  credit rather than a collection, exactly as those columns count them.
- **The periods are counted in Java, not SQL.** The query answers one row per day and
  `TrendGranularity` files each into its period. The week starts on
  `StatementPeriod.FIRST_DAY_OF_WEEK` - one definition of a week for the statement and the chart,
  not a second one written in MySQL's `WEEK()` modes.
- **"The year before" is the same calendar dates, filed into this year's periods.** Exact by the
  month; by the week the dates fall on other weekdays, so a day at a week's edge can land in the
  neighbouring week. By the year there is no comparison - last year is the point beside this one.
- **A percentage with nothing to divide by is absent, not zero** (`PartyTrendSummary`): a
  collection rate of nothing charged, or a change against a year with no movement, is not a number.
- **It prints the chart as drawn** - the ticked lines, the year before when it is showing - above
  the table of its figures (`PdfExportService.exportChartReport`). The snapshot is taken under a
  `trend-print` class that paints the chart dark on white whatever the theme, and removed straight
  after: a dark theme's light axis labels would otherwise print as nothing on a white page.

The ageing report has had the same treatment as the accounts screen it opens from: identity
header, list actions after the filters, the view menu, content-sized columns, and a PDF and a
spreadsheet of the columns on screen. Both old fixed-column Excel writers are gone.

### A party's profile

`features/party/profile` and `PartyProfileController`, the row's "show" in the parties list and a row
action in the balances screen. It replaced the "items purchased" window, which listed raw invoice lines
with no period, no units and no returns. `docs/reports-plan.md` §5 and §12.2.

- **Quantities are base units, net of returns, and each side is grouped before the union**
  (`account.document.ItemNetLines`, shared with the dashboard's best sellers and pinned by
  `ItemNetLinesTest`). A carton of twelve and three pieces, less a returned piece, is fourteen - the
  window it replaced said three of something.
- **The items and the days are two readings of one set of documents and are held to each other**:
  the lines less the documents' own discounts are the headers' net, which is what the ledger view
  says for the same documents. A discount on a whole invoice is shown, never shared among items.
  `PartyProfileSummary.unexplained()` is whatever does not add up, and the screen says it rather than
  hides it - on the development data all 1,207 sales reconcile to the piastre and 7 of 150 purchases
  differ from their lines by 38 piastres between them.
- **Viewing asks what opening the party asks** (`customer.show`/`suppliers.show`), because the window
  it replaced was reached that way; **a file asks `reports.show.customers`/`.suppliers` on top**
  (`forExport`), the ageing report's rule. The balance card is the statement's figure, for a reader
  who may see accounts, and absent - not zero - for anybody else.
- The periods are `TrendGranularity`'s and the week starts on Saturday; "stopped buying" compares the
  same length of time straight before. `PartyProfileDatabaseAcceptanceTest` (gated, seven cases,
  scratch schema) holds all of it to `account_customer_table` and `account_suppliers_table`.

### The owner's equity

`features/capital` and the capital screen's two new tabs (`TreasuryCapitalController`).
`docs/reports-plan.md` §3 and §6 are the contract, §12.3 what the screen and the paper found.

- **Every "opening" figure is equity brought forward, never a period movement**: the treasuries'
  opening balances, the customers' less the suppliers', and the opening stock at today's buy price
  (a valuation, and the screen says so). A treasury's opening balance may be capital or years of
  earlier profit in a drawer, and nothing recorded says which - so it is never "capital paid in".
- **The profit is the profit and loss's own rows**, read through `ProfitLossService`, which requires
  `reports.show.profit` itself: the statement needs that key on top of `treasury.capital`, and the
  movements tab needs only the capital. Nothing in `features/capital` recomputes a profit, and
  `ProfitLossExcludesCapitalTest` still guards `ProfitLossDao`.
- **The side a movement is on is its direction column, never its category's name**
  (`CapitalStatements`, `category <> 'NORMAL'`), so a category added later is not dropped from both
  sides at once. The totals are summed in SQL by day and treasury - the row per treasury
  `docs/treasury-plan.md` §4.3 specified.
- It is **not a balance sheet**: without a ledger nothing proves the assets equal this figure, and the
  screen says that in a sentence. `CapitalDatabaseAcceptanceTest` (gated, eight cases) works a quarter
  out by hand and checks the profit and loss for it is exactly the trading profit.
- **The return on equity is the period's profit over the mean of its opening and closing equity**
  (`EquityPeriod.returnOnEquity`), and it is **absent, not zero**, when that mean is zero or less -
  dividing by it means nothing. The opening is the closing less the period's change, so the column
  needs no query of its own.
- **The reconciliation sets what the business holds today against the equity, and does not call
  itself a balance sheet** (`EquityReconciliation`, the fourth tab). **Each figure is read where it
  lives** (`CapitalStatements.RECONCILIATION`): the treasuries from `treasury_current_balance`, each
  party by the balances screen's own expression and then split by its sign, the stock by the items
  screen's balance times the buy price - the item reports' valuation, which a copy of real data showed
  equal to the piastre. What explains part of the difference is shown by name: the parties' non-cash
  movements, and the treasury's ordinary deposits and withdrawals, which `ProfitLossDao` never reads.
  The rest is **unexplained**, on the table and beside it. Two traps it met: **the account tables have
  no `discount` column** - the ledger view supplies a zero for their rows - so the non-cash line is
  `SUM(purchase)` alone, and the first draft failed on the first MySQL it met; and **employees are
  left out on purpose**, since the profit counts a salary on the day it is paid. As at today only: a
  past day would need a second definition of an item's balance.

### The reports hub

`features/report` and `ReportsHubController`, the first button of the sidebar's reports section.
`docs/reports-plan.md` §7 and §12.4.

- **It hosts nothing.** A card runs the report's existing entry point - the sidebar's own button, or
  the constructor the button on its own screen calls - after asking for the product feature the way
  `MenuButtonSetting` does. So a report cannot say one thing in the hub and another where it has always
  been. `ReportsHubWiringTest` fails the build when a `ReportEntry` has no opener in `ReportsButtons`.
- **An entry names every key its existing road asks, and adds none.** A report behind a button on
  another screen asks that screen's key as well - the expense reports need `expenses.show` and
  `expenses.reports` - so the hub never becomes a road to a report the sidebar and the screens would
  not have shown. `ReportCatalog.visible` takes the permission and feature checks as arguments and has
  no session, so it is tested without a toolkit.
- **It has no product feature of its own, on purpose.** A key new to the catalogue is absent from every
  profile already signed, and would have taken the hub from every shop that has one; each card asks
  its report's feature instead.
- **A label that does not name its colour is white** under the theme: the first draft's descriptions
  used `stat-subtitle`, which only `dashboard.css` colours, and were invisible on a white card.

**`account.table.TrendChart` is the one line chart of an amount over periods** - the collections, the
delegate and the expenses trends draw through it, and a fourth trend takes it rather than a copy. It
carries the four decisions the three copies shared: time runs left to right in either language, the
axis is whole amounts in Latin digits, a line is coloured by its classes (the checkboxes wearing the
same markers are the legend), and every point says its figure on hover. The move was checked by
photographing the three screens before and after on one copy: not one pixel differed.

**The customers' recency, frequency and value** (`features/party/rfm`, `CustomerRfmController`, from the
customer balances screen and the hub) are the party profile's figures for every customer at once, and
`CustomerRfmDatabaseAcceptanceTest` holds each row to that customer's profile on MySQL. A score is the
fifth of the listed customers a figure falls in (`RANK()`, ties share one), worked out **before** the
text condition, so a search never moves a score. **The cash-sales customer is left out only when the
settings name one** (`PropertiesName.getChosenDefaultCustomer`): the setting's own getter falls back to
`1`, and on a real database customer 1 was a person while the bucket was number 48 - excluded on that
guess, a real customer vanished from the table while the bucket headed it. The screen and the paper
say who was left out, or that nobody was.

### Items by Pareto

`features/itemreports` (`ParetoRanking`, `ParetoReport`, `JdbcItemSalesRepository`), two reports in the
item reports screen: by net sales and by margin. `docs/reports-plan.md` §13.

- **Per item, sales less returns, each side grouped before the union**; a line's amount is
  `ItemNetLines.lineAmount`, so the rows and the party profile read a line one way. **A discount on a
  whole invoice is not shared among items**: it is its own line under the table, shown only when
  nothing narrows the report, and items less it is `document_profit`'s net and profit -
  `ParetoDatabaseAcceptanceTest` holds both on MySQL.
- **The class is decided by the cumulative share *before* the item**: under 80% A, under 95% B, the rest
  C, so the first item is always A. An item whose figure is zero or less has no share and no class
  ("—"). Net asks `reports.show.items`; margin asks `reports.show.profit` on top, being a profit.
- **Both reports fit the table at 1366x768** (`ParetoReport.FITS_THE_SMALLEST_SCREEN`, 18 width units):
  drawn first, the margin report was nine columns and its class - the answer - sat behind the
  horizontal scroll bar. The class is one letter wide (`ItemReportColumn.mark`) and the margin report
  leaves the quantity to the net one.
- **A period report and a dated one read the first date box as two questions**, so each kind keeps
  its own value (`ItemReportsController.periodStart`/`singleDay`): shared, a Pareto run left its start
  in the box and "expiring" then listed what had expired by that day, under the word "until".
- **An item report's paper keeps each total beside its label** (`ItemReportPdf.totalsLine`). The
  labels used to be joined in the wide cell and the figures in the last column's - the width of one
  letter on Pareto - so a reader paired them by position. Every item report with several totals
  printed that way.
- **The item movement report ("تقرير حركة الأصناف") reads these same figures** (`ItemSalesRankDao`
  over `JdbcItemSalesRepository`), month or year, most sold first. It read `view_item_sales_rank`,
  which summed `quantity` across units, subtracted no return and took a line before its own discount;
  the view is gone, its `DROP` kept, and `ProfitDefinitionTest` refuses it back - it would be a second
  definition of what an item sold. On a copy of real data 13 item-months were overstated by their
  line discounts and a month sold by the carton changed its first item.

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

**`PdfExportService` (the non-Jasper PDFs) had the script trap the other way round.**
`ArabicTextHelper` runs the Unicode bidi pass itself, and under its rules digits after an Arabic
word become "Arabic numbers" that a hyphen or a percent sign does not join: every PDF printed the
dates in its subtitle backwards (`01-10-2025`), its rates as `%113.44`, and every negative amount
as `11,995.00-`. Numbers are now isolated left-to-right before the pass - **but not digits touching a Latin
letter**: isolated alone, the digits of a product code are a run of their own, and "نوته NC7013"
printed on every invoice as "7013NC" (45 of 1,836 items on a real database). **Nor a number a Latin
word reaches**: a Latin letter begins a left-to-right run that takes everything up to the next Arabic
letter, which is what the screen does, so "owala-5250" and "Pepsi 330" print as the screen shows them
- they printed "5250-owala" and "330 Pepsi", the digits isolated on their own and set apart. Digits
*before* a Latin word stay the Arabic line's, again as on screen, so a code typed "74-AY" prints
"AY-74" the way every screen draws it. `ArabicTextHelperTest` holds the paper to plain Unicode bidi
over real names; on a database of 1,840 items the change moved 76 names, each to what the screen shows,
and moved none away from it. The paragraph's direction is still read from the original text - asked
of the isolated one it would skip the run, and "Total: 5" would come out "5 :Total". And the bundled bold Naskh
has **no glyph for the minus sign**, so a negative total - the one bold row - printed as a
positive number beside an empty box; `boldFontFor` falls back to the regular face. And the totals
line ran left to right under a right-to-left table - the headers and rows were reversed on their
way in and it was not - so every label and figure on it sat under another column's heading, in the
totals screen's reports as much as the party ones. All three were found only by rendering a report
to an image: the text extracted from the PDF was right all along. `PdfExportServiceLayoutTest` now
reads cell positions out of a real PDF, which is the only check that can see the last one.

**An invoice is a document, not a report, and prints through its own path.** The A4 invoice
first went to PDF through `TablePdfReport`, which turns any table of more than five columns
sideways, and a seven-column table had no room for the letterhead, the payment type, the
additional discount, what was paid or what was left - it printed the total *before* the
additional discount under the word "total". `features/invoice/InvoicePdfLayout` builds a
`DocumentPdfPage` from an `InvoicePrintDocument` (the saved header, never the screen's fields),
and `PdfExportService.exportDocument` draws it on the configured paper, always upright. A
deferred document also prints the party's balance before and after it: `after` is
`PartyStatementQuery.balanceAfterMovementSql` - the running balance on **that document's own
row** of the statement, so a reprint a month later still says what it was then - and `before` is
`after` less `DocumentLedgerEffect.balanceChange()`. A reader without the account permission
gets the invoice without the balance. **Print it from the JavaFX thread**: `chooseTarget` may
open a dialog, and the post-save print used to call it from the masker pane's worker.

**The 80mm receipt stays on Jasper, printed straight to the thermal printer by name, and reads the
same `InvoicePrintDocument`.** Converting it to PDF was weighed and declined: a PDF on a roll needs its
length worked out per receipt and has to reach a thermal driver at actual size, neither checkable from
a build, and Jasper stays in the project for the shift and label templates anyway.
`features/invoice/InvoiceReceiptLayout` writes every figure and label as text (`Columns.money`), so
`invoice-80mm.jrxml` carries no pattern and no arithmetic; its summary is a table of rows, so a row that
does not apply (no discount, no balance on a cash sale) leaves no gap; and `isIgnorePagination` makes
the page as long as what is on it - it was a fixed 850 points, a whole page for four lines and a second
page after forty. Both invoice screens decide receipt-or-A4 by `getPrintPaperReceiptInvoice`; the saved
invoice screen read the *account* thermal setting. `ReceiptTemplateFillTest` fills the real file with
`Print_Reports.receiptParameters` - compiling alone passes a field whose class no longer matches its bean.

**`JasperData` compiles each template once** (`CompiledReports`), recompiling a file in `reports/` only
when it changes on disk. Every print used to load and compile its `.jrxml` first: 1.3 s for the first
receipt after the program opened and 110-210 ms for each one after, against a 40-70 ms fill.

**The X and Z shift reports are rows, the receipt's way** (`features/shift/ShiftReportLayout`, one
template `shift-report-80mm.jrxml` for both). The two templates it replaced placed every label on the
left and its figure on the right - an Arabic paper printed as if it were English - passed the amounts
as doubles through a `pattern`, so the Arabic report locale printed `١٠٫٠٠` beside a shift number
written `2`, left a blank where the X report has no counted cash, drew the treasury row through the
rule under it, and titled the paper with the English literal `X-Report`. The layout writes every row
as text (`Columns.money`) in the drawer's arithmetic - in, total in, out, total out, opening, expected,
counted, difference - and a row that does not apply is not in the list; the template is a detail band
over those rows with the label on the right. Three things only rendering or running found:
**the sign of a difference goes in its label** ("الفرق - عجز") and the figure prints unsigned, because
a minus in a right-to-left line lands on the far side of the number; **a date never follows an Arabic
word in one field** - "وقت الطباعة 2026-09-22" printed `22-09-2026`, so the label and the date have
columns of their own; and **a Jasper bean's package must be exported in `module-info.java`** - the
first X report printed from the running program failed with `IllegalAccessException` while every test,
filling the template on the class path, passed. `ShiftReportTemplateFillTest` fills the real file under
an Arabic locale, checks no two elements overlap and each label is right of its figure, and reads
`module-info.java` for the export.

**A blind close withholds every amount, on the paper and on the screen** (`ShiftScreenSummary`, and
`ShiftReportLayout`'s blind branch). Both used to hide the expected balance and the difference and show
the five movement figures that add up to them - so the number being kept from the cashier was one
addition away, on the screen they count in front of and on the paper handed to them; the closing
dialog's blind wording recited the same five. What a blind X report and the summary panel now carry is
what the cashier knows anyway: the shift's details, how many invoices were rung up, and the opening
float. The paper's subtitle says why it is bare, or an empty report reads as a broken one.

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

### Unit prices

`features/unitprices` is the screen that lists every item sold in more than one unit with its
units beneath it and the four prices of each, editable in place and saved together
(`UnitPricesController`, opened from the items screen's "other" menu, on the ticked rows when there
are any). Four things to know before touching it:

- **"Automatic" means a stored zero, never a computed figure.** A unit with no price of its own is
  priced at the till as the item's price times the factor (`ItemUnits.sellPrice`), so it follows the
  item for ever. `AutomaticPricing.Mode.AUTOMATIC` clears the field; `FIXED` is the deliberate
  opposite, storing today's figure. The screen shows an automatic price in its own style and
  `UnitPriceLine.effective` is `UnitPriceSuggestion.forFactor` - the item screen's grey hint - so the
  two screens cannot quote a carton differently.
- **A save writes only the fields that changed, and checks only those.** The items and their units
  are locked (`FOR UPDATE OF`, not the unit-name join), each changed figure is compared with what
  the screen read, and one mismatch refuses the whole batch. That is also what makes the masked
  cost safe: a reader without `show.column.buy.price` is handed costs of zero, and writing the whole
  row back would store them.
- **Two permissions, because two different rows.** A unit's own price needs
  `items.unit.price.update` (V62, granted to whoever held `items.update`); the item's own price on
  the same screen still needs `items.update`; changing any cost also needs the cost column.
- **`items_units` is audited on UPDATE only** (`R__triggers.sql`). `ItemsDao.saveUnits` replaces an
  item's unit rows wholesale on every save of the item screen, so insert and delete triggers would
  log every unit of every item each time its name was saved. `audit_items_update` gained the three
  sale prices at the same time - it had recorded the cost and never what an item sold for.

`UnitPriceDraft` holds the screen's unsaved edits and every decision about them, and has no JavaFX:
the tree rows carry two ids and ask the draft on every paint. The filter-wide "automatic prices"
reads every matching item, previews, and saves on confirmation; the ticked and page scopes change
the draft and wait for Save. The items list's "more than one unit" filter and unit-count column
(`ItemCatalogSql.HAS_EXTRA_UNITS` / `UNIT_COUNT`) are the same predicate this screen lists by.

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
nothing but which item it is filed under. The single value written is the source's opening balance,
added **per warehouse** to the target's `items_stock` row for the same warehouse — it is the only
number never derived from a line, and its row is about to go. (It was also added to the target's
`items.first_balance` until V78, where a trigger copied the result over warehouse 1: the same figure
only for as long as the two copies had never drifted.)

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

**A backup's name says why it was taken, and retention prunes each reason apart**
(`features/backup/BackupKind`, `RetentionPolicy`): `backup_` (the timer, the button, the copy on
closing - tiered: the newest 24, then one a day for a week, one a week for four weeks and one a
month for a year, at most 47 files), `after-invoice_` (newest 10), `before-delete_` (before deleting
documents or wiping - newest 30) and `before-restore_` (never pruned). Thirty newest on an hourly
schedule reached back barely more than a day, so the mistake found a week later had no copy from
before it. The periods count back from the newest backup, not the clock, so a schedule that stopped
months ago is not emptied by the first run that works again. Retention used to keep the newest thirty `.enc` files of any
kind, so with "back up after saving an invoice" on the thirty were the last thirty invoices: the
scheduled copies from yesterday and the copy taken before a wipe were gone within the hour. It also
deleted *any* `.enc` in the folder, which defaults to the user's home directory. `backup_` is the
name every backup already had, so an install's existing files stay in the scheduled pool.

**A backup is read back before it counts** (`features/backup/DumpCheck`). `mysqldump` ends its
output with `-- Dump completed`, and only after the last table, so a dump whose last line is
anything else was cut short. `BackupService` checks the dump before encrypting it, then decrypts the
written file into a `DumpCheck` - the plaintext never touches the disk a second time - and deletes a
backup that fails either check rather than leaving it looking like one; retention only runs after
success, so the good copies stay. The restore refuses an incomplete file **before the safety copy
and before touching the database**: its old check looked at the first 64 KB for something like SQL,
which a file truncated in its data passes, and the import drops every table before it reaches the
point where such a file stops. Measured: a truncated dump encrypted with the right password was
refused with nothing written; the read-back cost 240 ms on a 6.2 s backup of 91 tables.

**The after-invoice backup is throttled, not per invoice** (`BackupThrottle`, `AfterInvoiceBackup`):
one run at a time on one thread for the whole process, at most one start every ten minutes, and a
request during a run or inside the gap is owed exactly one more run after it - so the last sale of a
busy spell is always inside a dump that started after it was saved. It used to start a full
`mysqldump` per invoice on the common pool with nothing waiting for the one before, and a failure
reached the cashier as an error dialog after every sale; it is now one folded notification.

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
  new database executes it and continues with `V2`, `V3`, … The current head is `V79`, which gives
  the quick invoice its own two keys, `sales.quick` and `purchase.quick`, granted to every role and
  `ALLOW` override that holds the matching create key (see **The quick invoice**). Before it `V78` leaves
  an item one opening balance per warehouse (`items_stock.first_balance`) and drops the other two
  places one lived - `items.first_balance` and the trigger that copied it over warehouse 1 on every
  update of the item - with the dead `items_stock.current_quantity` (see **Warehouses**). Before it
  `V77` lets a warehouse be switched off instead of deleted (`stocks.is_active`, every existing
  warehouse left on). Before it `V76` gives
  a stock transfer a note (`stock_transfer.notes`, NULL when nothing was written) - why or for whom
  the goods moved, printed on the slip that now travels with them. Before it
  `V75` stops
  `stock_count_lines.item_id` cascading - a posted count sheet is a correction that was actually
  made, and deleting the item took its lines out of one with no refusal and no trace, which
  `DeleteRegistry` could not refuse precisely *because* the key cascaded. Before it `V74` gives the
  warehouses two keys they had been borrowing: `stock.transfer.show`, because reading the transfer
  history asked for the right to post a transfer, and `stock.count.create`, because saving a count
  sheet asked only for the right to see one while discarding a draft asked for the right to post it
  (see **Warehouses** and `docs/warehouse-plan.md` §14). Before them `V73` is a delegate's
  discount ceiling, `V72` the frozen monthly commission run, `V71` is the delegate of a collection and `V70` the dated
  commission rule (see **Delegates and commission** for all three). Before them `V69` gives a
  treasury its wallet or account number and a minimum balance; before it `V68` lets a
  transfer between treasuries carry a fee, and `V67` ties a wallet fee to the movement it was
  paid for (see **The treasury**). Before them, `V66` adds the
  expense budget and the recurring-expense templates: `expense_budget` carries its unique index on a
  **generated** `month_key` (`COALESCE(month, 0)`), because MySQL counts two NULLs in a unique index as
  different values and a unique over a nullable `month` would allow two yearly budgets for one heading;
  `expenses_details.recurring_id` is `ON DELETE SET NULL`, so deleting a template never touches money that
  actually left a drawer. Before it, `V65` adds `expenses.reports`, and `V64` rebuilds the expense headings
  as managed rows. Before them, `V63`
  gives the three columns a barcode can live in one collation. It exists because no migration
  here has ever named a charset: a table a migration creates takes the *database* default, while
  a table restored from a mysqldump keeps the charset the dump names and lands on the *server's*
  default collation instead - and MySQL refuses a UNION of two columns whose collations differ.
  A customer whose data had been restored into a database we created could not save an item at
  all (error 1271, `ItemsDao.takenBarcodesAmong`), on a build working everywhere else. So
  `CREATE DATABASE` no longer names a collation in either of the two places that issue it, and
  `DatabaseMigrationService.alignDatabaseCollationWithItsTables` settles the database's own
  default - which V63 cannot do, because MySQL refuses `ALTER DATABASE` through the
  prepared-statement protocol. Before it, V58 adds
  the employee's account - the non-cash ledger, the purpose beside each payment, and the two
  views that union them (see **The employee's account** above) - and V57 makes the job a row
  rather than four constants, gives the salary a date and the employee a status. Before them,
  V56 adds the
  fields a party record was missing - email, tax number, payment terms, a default delegate, an
  opening-balance date and `is_active` - and V55 adds the debit/credit-note permissions
  (`*.account.adjust`, granted to whoever held `*.account.create`) with the ledger's date indexes;
  see **A party's statement** and **A movement on a party's account**. V54 adds the
  signed singleton product profile and its append-only application history. V53 corrects opening
  shift baselines, V52 gives the customer and supplier rows the microsecond `updated_at` their
  editors compare as a version, V51 gives `data_change` a `revision` counter so the cross-machine
  relay does not compare wall clocks, and V50 gives items and the four document headers the same
  microsecond version. (This sentence used to call V51 and V52 "stock counts" and their "variance
  settlement": the stock count is `V8`, the last migration to touch a stock table is `V19`, and a
  variance settlement exists nowhere - see `docs/warehouse-plan.md`.) V49 adds the audit-administration export permission and the
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

**A migration that *replaces* a foreign key is invisible to the test that reads them.**
`SchemaForeignKeys` parses `CREATE TABLE` bodies and `add_constraint_if_missing(...)` calls from a
named list of migrations, and skips the cascading ones - so when `V75` dropped
`stock_count_lines.item_id` and put it back as `RESTRICT`, both delete catalogs went on seeing
`V8`'s cascading key and `DeleteRegistryTest` failed the new declaration as "not in the schema".
A migration that changes a key belongs in that list as much as one that adds a key, and the
replacement has to be written in the `add_constraint_if_missing` form the reader understands.

**`add_column_if_missing` is the same trap and is worse**, because no baseline creates it at all -
`V20`, `V21`, `V22` and `V23` each define a local copy and drop it again. The first draft of `V56`
called it and would have failed everywhere, an hour after `V55` had been found doing the same thing
and the lesson written down. That is the argument for a test rather than a note:
`MigrationHelperProcedureTest` fails the build when a versioned migration calls a procedure it does
not define in its own file, and **also** when it defines one and leaves it behind - a stray helper is
what the *next* migration calls and finds present on its author's machine and missing in the field.

**Views, triggers and procedures are repeatable migrations, not versioned ones.** `R__views.sql` (32
views; `treasury_balance_after_convert` and `view_item_sales_rank` were removed from it, and the `DROP`
for each stays because a client that ran an older copy still has it), `R__triggers.sql` and `R__procedures.sql` are re-run by Flyway whenever their checksum changes,
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

**A second licence format exists beside that one, and it charges no failure for anything**
(`features/license`, phase A of `docs/licensing-server-plan.md`; no server issues one yet).
`HAMZA_LICENSE2|machine|customer|edition|issued|updatesUntil|expires` is signed by
`LicenseServerKey` - a second key, **blank until the server's pair exists**, and blank is a state
(`SERVER_KEY_MISSING`), not tampering. It is second because `ReleaseSigningKey` also signs emergency
recovery, and a licence server faces the internet. `TrialManager.currentLicense` asks the new package
first and hands the old reader only `LicenseService.filesForOlderReader()`: **a server-format file must
never reach `validateLicense`**, which checks with the release key and ends the install over the
signature that file would always fail. `license.dat` is now read from beside `config.xml` first and the
program folder second, and written only to the first - the program folder is under Program Files.
The rule easiest to break: **an expired subscription (`READ_ONLY`) still skips the trial.** Sent down the
trial path it meets a years-old installation date, which is "trial expired", which is the one failure
an install gets. The question there is `skipsTrial()`, never `mayRecord()`, and
`LicensingArchitectureTest` pins both that and the routing. Nothing asks `mayRecord()` yet - the
read-only guard is phase D.

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

**The default font is El Messiri, because Cairo drops a space.** JavaFX draws Cairo without the space
before «في», wherever the word falls and in every screen and dialog, so "مستخدم في" read
"مستخدمفي". El Messiri, Tahoma and Segoe keep it; Cairo's variable file and a zero-width joiner did
not help. The default is said twice - `FontManager.DEFAULT_FAMILY`, which the screens are stamped
with, and the first family in `app-theme.css`, which a dialog reads because nothing stamps it - and
`FontDefaultTest` holds the two to one answer. A family somebody chose in the settings is kept. **To
reproduce a text defect like this outside the app, call `FontManager.initialize()` first**: without
the bundled fonts registered, JavaFX draws the system's font and the defect is not there.
