# PROJECT_MAP (open only on explicit request — architecture overview)

## Project
- **Name:** main-account-3.1.2
- **Type:** Desktop ERP / Accounting / Inventory (JavaFX)
- **Java 21 | Maven | JavaFX | Lombok | Log4j2 | Apache POI**

## Repository Structure
- `/account` — main app (code + resources)
- `/controlsfx` — local helper lib (**do-not-read**)
- `/docs` — this docs folder
- `/backup_data`, `/logs`, `/out`, `/reports`, `/fonts` — **do-not-read**
- `pom.xml`, `config.xml`, `backup_script.bat`, `license.dat`, `README.md`

## Architectural Flow
User → FXML + Controller → Service → DAO → Domain → DB ↑ DaoFactory


## High-Risk Zones
- `config/ConnectionTo*.java` — DB connection
- `model/dao/**` — all DB access
- `security/**` + `license.dat` — auth/license
- `service/*` financial (invoice, treasury, totals)

## Safe-Edit Zones
- ✅ CSS / FXML layout / error messages
- ⚠️ Controller internal refactor (keep flow)
- ❌ License / security / DB schema (ask first)

## Build & Run
- `mvn -f account/pom.xml clean package`
- Logs: `/logs` + `account/src/main/resources/log4j2.xml`

> For feature → files mapping, see `FILE_INDEX.md`.
> For scenario-based routing, see `TASK_ROUTER.md`.