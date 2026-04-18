# TASK_ROUTER (open only when scenario type is unclear)

> Quick scenario → first files to inspect. For full feature file lists, use `FILE_INDEX.md`.

| Scenario | First file(s) to open |
|---|---|
| Button/UI doesn’t work in screen X | `controller/<feature>/XController.java` + matching FXML in `resources/.../view/<feature>/` |
| Wrong calculation (price/total/balance) | `service/<RelatedService>.java` → then `model/dao/` if data source suspected |
| Data not saved / not loaded / duplicated | `model/dao/<Related>Dao.java` + `config/ConnectionToDatabase.java` + `config/ConnectionToMysql.java` |
| Permissions disabled/enabled wrongly | `type/UserPermissionType.java` → `service/UserPermissionService.java` → `controller/main/DisableButtons.java` |
| Login / password / registration | `security/AuthService.java` / `PasswordService.java` / `RegistrationService.java` / `UserRepository.java` |
| Print / Jasper report broken | `reportData/Print_Reports.java` + `reportData/JasperReportPaths.java` + `controller/reports/<Related>.java` |
| Excel export / Barcode | feature controller + `view/barcode/*` + `features/export/*` |
| Theme / colors / fonts | `resources/.../css/*.css` + FXML + `config/ThemeManager.java` + `config/Style_Sheet.java` |
| FXML not loading / window not opening | `openFxml/*` + `view/<Screen>Application.java` + the FXML path annotation |
| Shift / session issue | `session/ShiftContext*.java` + `service/UserShiftService.java` |
| Logging / error tracking | `resources/log4j2.xml` + `resources/log4j2-1.properties` + `/logs` |
| Config / build / version | `pom.xml` (root + `account/`) + `config.xml` + `resources/version.properties` |

## Golden workflow
1. Read listed file(s) only.
2. If cause not found → go ONE layer down (Controller → Service → DAO).
3. For financial/permission changes: state before/after impact + manual verify steps.