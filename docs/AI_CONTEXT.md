
# AI_CONTEXT — START HERE (and only here by default)

> **SYSTEM NOTE TO AI:** Read ONLY this file by default.
> Open other docs (`FILE_INDEX.md`, `PROJECT_MAP.md`, `TASK_ROUTER.md`, `MODULE_INDEX.md`) ONLY when this file tells you to.
> Never read: `/controlsfx`, `/backup_data`, `/logs`, `/out`, `/reports`, `/fonts`, `.idea`, `.claude`, `target`, `*.jar`, `*.dat`, `*.class`.

---

## 1. Tech Baseline (fixed — do not re-verify)
- **JDK 21** | **Java** | **JavaFX (FXML)** | **Maven** | **Lombok** | **Log4j2** | **Apache POI** | **ControlsFX (local)**
- Main module: `account` | Root pkg: `com.hamza.account`

---

## 2. Architecture (memorize — do not re-explore)
- > When user mentions a feature by name, open `docs/FILE_INDEX.md` first and jump to the listed files. Do not explore packages.
  UI (FXML + Controller) → Service (business logic) → DAO (model/dao) → Domain (model/domain

> - Base classes: `model/base/` (`BasePurchasesAndSales`, `BaseTotals`, `BaseNames`, `BaseAccount`) → invoice Generic strategy.
- Event bus: `controller/main/DataPublisher.java` (Publisher/Observer).
- Controller base: `controller/main/LoadData.java` (many controllers extend it).
- DAO factory: `model/dao/DaoFactory.java`.
- Current user: `LogApplication.usersVo`.
- i18n: `com.hamza.controlsfx.language.Setting_Language`.
- Alerts: `com.hamza.controlsfx.alert.AllAlerts`.

---

## 3. When to open other docs

| Situation | Open |
|---|---|
| User mentions a **feature by name** (items / invoice / pos / treasury / reports / users …) | `docs/FILE_INDEX.md` → jump to the feature section |
| User asks about **project overview / architecture / build** | `docs/PROJECT_MAP.md` |
| Issue type is unclear; need **routing by scenario** | `docs/TASK_ROUTER.md` |
| Need **package-level overview** | `docs/MODULE_INDEX.md` |

> Do NOT load more than ONE of these per task unless strictly necessary.

---

## 4. Golden Rules
1. **Minimal Safe Change** — no unasked refactors.
2. Do not change public method signatures without explicit request.
3. Read only files needed for the task. If you need extra files, state why first.
4. Heavy work **off** JavaFX UI thread.
5. Financial code (`service/*`, `model/dao/*`, invoice/treasury/totals) = **HIGH RISK** → state before/after impact.
6. On duplicate/DB errors → show user-friendly Arabic message via `AllAlerts`.
7. Log exceptions with `log.error(e.getMessage(), e.getCause())`.

---

## 5. Response Format (strict)


---

## 6. Token Saving
- Don’t re-read files you already saw in the session.
- Don’t re-list directories already listed.
- Ask ONE clarifying question instead of exploring blindly.
- Prefer `find_text` / `exact_search` over reading whole files.