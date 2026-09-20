# The permissions contract

What authorization is in this application, what the 2026-09-20 review found, and what it deliberately
left undecided. Read it before touching `account.authorization`, `features/rbac`, `V11`–`V13`, or any
migration that adds a permission key.

`CLAUDE.md` § **Authorization** is the short version and stays the first thing to read. This file is
the longer one: the rules that are not obvious from the code, and the decisions nobody has taken.

---

## 1. What the system is

A permission is a **string key**. `AppPermissions.SALES_CREATE` is `key("sales.create")`, and that
constant is the whole declaration: there is no database id, no `switch`, and no row for a technician to
add. `PermissionKey` validates the shape; `AppPermissions.definitions()` derives the module, the
resource and the action from the key itself; `JdbcRbacRepository.synchronizeCatalog` writes the
catalogue into `auth_permission` on every start-up and grants every key to `SYSTEM_ADMIN`.

That replaced `UserPermissionType`, a ~130-entry enum whose ids were matched to table rows by hand.

**`AuthorizationGuard` answers two different questions and using the wrong one is the mistake:**
`isGranted(key)` is a **hint** - hide a button, leave a `RowAction` out, pick which of two answers a
rule gives - and `require(key)` is **enforcement**, which belongs in a service.
`AuthorizationArchitectureTest` pins the second in both directions: a service method that writes a row
without calling `require` fails the build, and so does an entry in `WRITES_WITHOUT_A_GUARD` that has
quietly been fixed.

**Roles carry the keys, and a role may inherit another's.** `auth_role` / `auth_role_permission` /
`auth_user_role`, resolved by one recursive CTE in `findEffectivePermissions`, with per-user
`ALLOW`/`DENY` overrides in `auth_user_permission_override` - each carrying a reason and an optional
expiry, and `DENY` winning over every grant. `RbacService.validateRoleInheritance` refuses a cycle.
Every management mutation writes `auth_audit_log` inside its own transaction, and since V48 database
triggers record the same changes made from a SQL client.

**The session is a snapshot.** `UserSessionContext` holds an immutable `Set<PermissionKey>` replaced
atomically at sign-in and by `RbacService.refreshCurrentSession()`. It is process-wide, which is right
for a desktop program and is one of the things that has to change before anything is served over a
network.

### 1.1 Three rules that are easy to get wrong

**A key declared here must be read by something.** Eight were not, and appeared in the roles screen as
tick boxes that changed nothing a user could do - four settings tabs, two price-tier keys, a treasury
balance and the read half of the month rule. A role built out of them granted nothing, and no build
objected. `PermissionCatalogArchitectureTest.everyDeclaredKeyIsReadBySomething` is what stops a ninth.

**A permission's name comes from the bundles, never from the database.** `permission.<key>` in all
three, pinned by the same test. `auth_permission.description` holds the key itself for most rows - see
§2.1 - so a name written only there is a name most installs do not have.

**Removing a key is safe and needs no migration.** `synchronizeCatalog` disables every system
permission and re-enables the declared ones, so a constant that goes leaves its row with
`enabled = 0`: it drops out of the screen and out of `findEffectivePermissions`, while the grants that
named it stay dormant rather than being deleted from a customer's database. Put the key back and those
grants work again. **What is not safe is editing a shipped migration** - `V13`'s grant lists name keys
by string, so a key removed stays named there, harmlessly.

---

## 2. What the review found

Measured on 2026-09-20 over 162 keys (170 before this change), by counting every reference to every
constant in both modules' `src/main/java` and every migration.

### 2.1 The names: 108 of 162 keys displayed their Latin key on an Arabic screen

`V1` seeds the `permission` table with **names and no descriptions**. `synchronizeCatalog` writes
`description = permission_key` for a row it inserts and then never updates that column, so only a
migration can put Arabic there and about two dozen ever have. `PERMISSIONS_SQL` reads
`COALESCE(description, permission_key)`, which turns the remaining NULLs into `treasury.capital`,
`items.merge` and `shift.force.close` - in an RTL Arabic table, beside role names in Arabic.

English was worse in a quieter way: `UserPermissionController` derived words from the key, so
`total.sales.re.show` read `Total · Sales · Re · Show`.

**Fixed.** `PermissionLabels` asks the bundle first, falls back to a stored description that says more
than the key, and only then to the derived words. 162 × 3 entries added, and a test that fails on a
missing one - so adding a permission is a constant plus three lines of translation, and never a
migration.

### 2.2 Eight keys nobody read

`treasury.balance.show`, `sel.price.show`, `sel.price.delete`, `setting.company.show`,
`setting.other.show`, `setting.items.show`, `setting.shows.show`, `show.data.before.month`.

**Removed**, with the reason recorded where each used to be. Two of those groups are worth knowing
about: all four settings tabs and the sidebar button ask `setting.show` (four places in
`SettingButtons`, one in `MainScreenController`), and the price-tier screen asks `sel.price.update`
alone. Which settings tabs a shop wants apart is a decision nobody has taken, and four tick boxes that
do nothing are worse than none.

### 2.3 A risk derived from the last word of a key answered LOW for paying an employee

`risk(action)` reads the key's final word: DELETE, BYPASS, MANAGE, POST and RESTORE are `CRITICAL`,
CREATE and UPDATE `HIGH`. Everything else falls through to `LOW` - which covered CAPITAL, OPENING,
TRANSFER, DEPOSIT, PAY, APPROVE, ADJUST and MERGE. The owner's capital and a treasury's opening
balance, both documented in `AppPermissions` as the owner's alone, were `LOW`.

**And two places said two things.** `V55` inserts `customer.account.adjust` as `HIGH`;
`synchronizeCatalog`'s `ON DUPLICATE KEY UPDATE risk_level = VALUES(risk_level)` overwrote it with the
derived `LOW` on the next start-up. The migration's answer never survived a restart.

**Fixed.** `key(value, risk)` states the risk at the declaration, beside the sentence that justifies
it, and 17 keys use it. The four `audit.*` risks that had been a chain of `equals` inside
`definition()` moved there too.
`PermissionCatalogArchitectureTest.aDeclaredRiskIsOnlyUsedWhereTheDerivationIsWrong` refuses a
declaration that restates what derivation already gets right, so the two cannot drift.

The level is metadata today: it is written to `auth_permission.risk_level` and read by nothing but two
tests. §4.2 is the screen that should use it.

### 2.4 "Is this the administrator" was written out in three screens

`BuyController2` (the lines table's column menu), `MainScreenController` twice (the sidebar's role
caption, the help button). `CurrentUser.get().getId() == 1` - the numbered-administrator test this
permission system replaced, copied back in by hand, and wrong on any install where the owner is not
user number one. `AppPermissions.MAIN_TOTALS_MANAGE`'s javadoc documents the same mistake having been
removed from the settings tab.

**Fixed as far as it can be without a decision**: `CurrentUser.isSystemAdministrator()` is the one
question, `UserSessionContext` the one answer, and
`PermissionCatalogArchitectureTest.theAdministratorIdIsAskedInOnePlace` fails on a fourth. That test
also subsumes `AuthorizationArchitectureTest`'s older rule, which hunted `usersVo.getId() == 1` in six
named files and could not see the `CurrentUser` spelling at all. Whether these three should be
permissions is §4.1.

### 2.5 About 23 keys are UI hints with no `require` anywhere

Legitimate for the most part - a `*.show` key that opens a screen, and `sales.discount.override` and
`accounting.lock.bypass`, which are read with `isGranted` on purpose inside a service. Two are not:

- **The five report keys** (`reports.show.summary`, `.items`, `.sales`, `.purchase`, `.returns`) hang
  off a sidebar button and nothing else. `reports.show.profit` is the one exception -
  `ProfitLossService` calls `require`, and its javadoc says why.
- **`invoice.profit.show` hides a column while the figures are fetched**, in three places. The rule
  this repository already wrote for `employees.show.salary` - *a salary is not fetched for a reader who
  may not see one*, answered once in `EmployeeService.salaryVisible()` so the columns are not
  **selected at all** - was never applied to the profit column.

Not fixed here: both mean changing what a service selects, which is a behaviour change per screen
rather than a tidy-up. §4.3.

### 2.6 The session snapshot never refreshes on its own

It is replaced at sign-in, and by `refreshCurrentSession()` when **this** machine edits roles. So an
override that expires at 3 o'clock stays in force until the user restarts the program, and a
permission taken away from a cashier on another till never reaches them. `data_change` /
`RemoteChangeRelay` and the `UsersChanged` event both already exist and neither calls
`refreshCurrentSession`. Not fixed - §4.4.

### 2.7 Smaller things, found and not fixed

- **`RbacService.saveConfiguration` turns `targetUserId == 1` into `0` silently** and answers `1`, so a
  caller is told a save happened that did not. The screen disables the control, so nothing shows it
  today; a refusal is the honest answer.
- **Four read methods have no guard** - `roles()`, `permissions()`, `roleIdsForUser()`,
  `permissionIdsForRole()`. Low risk, but `users.show` is the obvious floor.
- **The section column said "عام" for 134 of the 162 keys — fixed, see §2.8.**
- **The key names are not consistent**: `employee.*` beside `employees.show.salary`, `customer.*`
  beside `suppliers.*`, `user.shift.manage` beside `shift.*`. Renaming one costs a migration and a
  grant transfer, so it is only worth doing with something else.
- **`synchronizeCatalog` disables every system key and re-enables the declared ones on every start-up
  of every machine.** Inside one transaction, so it is safe; but a machine running an older build with
  the same migration head would disable the newer keys until an updated till starts. Unlikely, since a
  new key almost always arrives with a migration, and `refuseADatabaseNewerThanThisBuild` catches that.

### 2.8 The section column said "عام" for 134 of the 162 keys

Two halves decided it and neither could see the other. `AppPermissions.definition()` derived the
module as **the key's first word uppercased** - so `TOTAL`, `SHOW`, `UPDATE`, `MAIN`, `SUB` and `SEL`
were section names - while `UserPermissionController.categoryLabel` matched that against a `switch`
over **eight words it had chosen itself**. Four of the eight could never match anything the
derivation produces:

| the switch says | the derivation produces |
|---|---|
| `PURCHASES` | `PURCHASE` |
| `SETTINGS` | `SETTING` |
| `PARTIES` | `CUSTOMER`, `SUPPLIERS` |
| `SECURITY` | `USERS`, `ROLES`, `AUDIT` |

Two of them differ by a single letter. Only `SALES`, `REPORTS`, `TREASURY` and `INVENTORY` ever
matched - 28 keys - and everything else fell to the `default`. **Found by opening the screen**: the
treasury family named its section and the purchases family beside it said "general", which no test
and no query would ever have reported, because each half was doing exactly what it was told.

`PermissionGroup` is the one declaration: thirteen sections, each owning the key prefixes that belong
to it, used by `AppPermissions` for the module and by the screen for the label. All 162 keys land in
a named section and none in "general" - SALES 12, PURCHASES 12, ITEMS 23, STOCK 9, PARTIES 22,
TREASURY 7, EXPENSES 9, EMPLOYEES 21, DELEGATES 7, SHIFTS 9, REPORTS 12, SECURITY 9, SETTINGS 10.

Four rules hold it together, and the first two caught real mistakes while being written:

- **A key belongs to exactly one group.** `sales.discount.override` matched both `sales.` and a
  `sales.discount.` prefix, and the first draft settled it by prefix length - which means the section
  a key appears under is worked out rather than read. A group may instead claim a key **by name**,
  which is what `DELEGATES` does with that one: it is granted with `commission.rule.update` and not
  with selling (V73), so that is where somebody building a role will look for it.
- **A key claimed by name has to exist.** A typo there does nothing visible: the key falls back to
  whichever prefix covers it and quietly lands in the neighbouring section.
- **Every group owns at least one key**, or it is a heading over an empty list.
- **Every group has a label in all three bundles**, and the stored `module_key` always resolves to a
  group - otherwise the column falls back to "general" again by a different road.

It needs **no migration**: `synchronizeCatalog` rewrites `module_key` for every declared key on every
start-up. Watched on a copy of a real database - the 34 old module values (`TOTAL`, `SHOW`, `SEL`, …)
were replaced by the 13 group names the moment the new build started.

---

## 3. The four keys granted to a role and read by nothing

`items.add.excel`, `reports.show.customers.account.area`, `reports.show.day.details`,
`reports.show.delegate`.

These are worse than §2.2's eight, because `V13` grants them to default roles and
`DefaultRoleAcceptanceTest` pins those grants - so they look real from every direction except the one
that matters. They are in `PermissionCatalogArchitectureTest.DECLARED_BUT_UNREAD` with a reason each,
and the list fails in both directions.

**The decision, per key, is wire or remove:**

| key | what wiring it would mean |
|---|---|
| `items.add.excel` | `ItemsService`/the import screen requires it beside `items.create`. The clearest of the four: the ability exists, the screen exists, only the check is missing. |
| `reports.show.day.details` | there is no day-details screen to guard. Remove, or the key waits for a report nobody has asked for. |
| `reports.show.customers.account.area` | same; the accounts-by-area report is not built. |
| `reports.show.delegate` | superseded. The delegate reports that exist ask `commission.reports` (V71) and `commission.show`. Removing it is the honest answer, and it costs an upgrade nothing because nothing reads it. |

Removing a key does **not** need a migration (§1.1) but does need `DefaultRoleAcceptanceTest` and the
`DECLARED_BUT_UNREAD` entry updated in the same change.

---

## 4. Decisions nobody has taken

### 4.1 Should the three administrator-only behaviours be permissions?

The invoice lines table's column menu, the sidebar's role caption, the help button. A new key is
granted to `SYSTEM_ADMIN` alone by the start-up synchronisation, which reproduces today's behaviour
exactly - the precedent is `main.totals.manage`, whose javadoc records the same conversion.

The caption is the odd one out: it is a **label**, not an ability, and the right fix is to show the
user's actual role names rather than a two-valued guess. That needs `roleIdsForUser` + `roles()` at
`setupUser` time, on the FX thread, which is why it was not done as part of a tidy-up.

### 4.2 The roles screen: using the groups, and the risk badge

The sections exist now (§2.8) but the table is still 162 flat rows that merely *carry* a section
name. What is left: collapsible headers per group with "select all" for each, a coloured risk badge
per row from `risk_level` - already stored, still shown nowhere - an extra confirmation on granting
`CRITICAL`, and a "copy role" button, since building "cashier + returns" today means ticking twenty
boxes from scratch.

And the layout: the dialog gives its permission table about two rows at 1366x768, under the
parent-roles table stacked above it (§5). That is the case `CLAUDE.md` § **A row's detail** describes,
on the screen this actually ships to.

### 4.3 The report keys and the profit column

Whether the five report keys should be enforced in their services, and whether
`invoice.profit.show` should stop the figures being **selected** the way `employees.show.salary`
already does. Both change what a screen fetches.

### 4.4 Refreshing the session

An expiring override and a revoked permission should reach a running program.
`RemoteChangeRelay` + `UsersChanged` are the channel; the missing piece is a listener calling
`refreshCurrentSession`, and a timer for the nearest `expires_at`. Nothing about it is hard; it was
left out of this pass because it changes when a user loses an ability mid-session, which wants to be
watched on screen rather than reasoned about.

### 4.5 `update.data.before.month`

It predates `accounting_lock` (V9), which answers the same question properly and is enforced in the
service through `PeriodLock`. This key is read in exactly one place -
`TotalsController.permissionButtons` - so it is silently off on every path that does not go through
that screen. Its read-only twin `show.data.before.month` was read nowhere and is gone. Removing this
one changes what existing installs allow, so it is a decision rather than a tidy-up.

### 4.6 Which settings tabs deserve their own key

§2.2 removed four that nothing read. If a shop should be able to open the company tab without the
scale-barcode tab, the keys come back **and are wired** in the same change.

---

## 5. What was and was not verified

**This pass** (2026-09-20, `worktree-permissions-review`):

- `mvn -o clean test` over both modules - see the commit's report for the figure.
- `PermissionCatalogArchitectureTest`, five rules, green. Two of them caught real defects while being
  written: the administrator-id rule flagged `AddNameController`, where the `1` is the default price
  tier and not a user, and the risk rule needed the derivation restated to tell a needed declaration
  from a redundant one.

**The screen, on a copy of a real database** (same day). A `mysqldump` of the developer's
`account_system_db` was restored into a private `mysqld` started from their own binaries on port
3399, so nothing the run did could reach their server; the app was pointed at it with
`ACCOUNT_CONFIG_DIR`, applied V66-V73 itself, and the copy and the whole data directory were dropped
afterwards. What that proved, in the database and then on screen:

- **The 108 figure was measured, not estimated**, and it is the same defect at a slightly different
  count: that database held 161 enabled permissions and **103 of them had `description` equal to
  their own key**, because `V1` seeds the rows with no description at all.
- **`customer.account.adjust` really was `LOW`** in a live database, although `V55` inserts it as
  `HIGH` - the overwrite described in §2.3 had already happened there. It is `HIGH` after this
  change, and stays `HIGH` across restarts.
- **All eight removed keys became `enabled = 0` and every grant survived**: one grant each, two for
  `show.data.before.month` (`SYSTEM_ADMIN` and `LEGACY_USER_2`, a real non-administrator's role).
  No migration ran, and none was needed.
- **On screen, in Arabic**: `treasury.capital` reads "تسجيل رأس مال ومسحوبات المالك" where it read
  `treasury.capital`; `accounting.lock.manage`, `accounting.lock.bypass`, the whole `shift.*` family
  and `setting.backup.show` likewise. A `sel.price` search returns exactly one row and a `setting.`
  search exactly four - the removed keys are gone from the screen while their grants sit untouched in
  `auth_role_permission`. All three places the name is rendered were checked: the role's permission
  table, the effective-access tab, and the module column beside them.
- **And the screen found what the database could not**: the القسم column read "عام" for 134 of the
  162 keys, four of `categoryLabel`'s eight categories being unreachable. That is §2.8.

**The section fix, read back on the same screen** (second run, a fresh copy of the same database):

- `synchronizeCatalog` replaced the **34** derived module values with the **13** group names on its
  first start-up, with no migration - watched in the table, then on the screen.
- The `purchase.` family reads **"المشتريات"** where the first run had it reading "عام", and
  `total.purchase.show` sits with it rather than in a section called `TOTAL`.
- Unfiltered, the list now opens grouped: every row of "المناديب والعمولة" together, then the next
  section, because `PERMISSIONS_SQL` orders by `module_key` and that column finally means something.
- `sales.discount.override` reads **"المناديب والعمولة"**, which is the key claimed by name rather
  than by prefix - the one decision in §2.8 that a reader could have disagreed with, and it is where
  the rule says it should be.

**Still not verified:**

- **The layout at 1366x768.** The dialog opens at 996x735 and gives the permissions table about
  180 points - two rows - under the parent-roles table stacked above it. It was read by maximising
  the window to 1920x1040, which the shop's screen cannot do. This is the case
  `CLAUDE.md` § **A row's detail** describes, and the screen it ships to is exactly the one that
  cannot show it. **It is the largest thing still wrong with this screen.**
- **English was not opened.** `PermissionLabelsTest` resolves all 162 keys in both languages and
  `PermissionCatalogArchitectureTest` holds all three bundles plus the thirteen section labels, so
  the text exists; what has not been seen is how it sits in the column. Switching the language writes
  the choice into the developer's own Java `Preferences`, which is why it was not done in passing.
- **A fresh install was not migrated.** Both copies were existing databases at V65; the disable path
  and the module rewrite over a schema built from nothing have not been watched.
- **A reader without `roles.manage`** has not opened the screen.
- The gated acceptance classes were not run; this worktree has no database configuration, by design.

**What will happen on the first real start-up of this build**: `synchronizeCatalog` rewrites
`module_key` for all 162 keys and sets `enabled = 0` on the eight that are gone. Nothing else changes,
and nothing is deleted.
