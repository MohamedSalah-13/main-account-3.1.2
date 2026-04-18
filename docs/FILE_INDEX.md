# FILE_INDEX

> **الهدف:** عند ذكر اسم ميزة، اذهب مباشرة لملفاتها بدون استكشاف.
> **القاعدة:** Controller → Service → DAO → Domain → FXML.
> **اختصارات:**
> - `J:` = `account/src/main/java/com/hamza/account`
> - `R:` = `account/src/main/resources/com/hamza/account`

---

## 🟢 Core Infrastructure (مهم — معروف عالميًا)
| الغرض | الملف |
|---|---|
| نقطة الدخول | `J:/Main.java` |
| Base loader للـ Controllers | `J:/controller/main/LoadData.java` |
| Event bus | `J:/controller/main/DataPublisher.java` |
| تحميل البيانات الأولية | `J:/controller/main/LoadDataAndList.java`, `LoadOtherData.java` |
| الشاشة الرئيسية | `J:/controller/main/MainScreenController.java`, `MainMenuController.java`, `MainToolbarController.java`, `MainRightPaneController.java` |
| Split / Items main | `J:/controller/main/MainSplit.java`, `MainItems.java` |
| Disable buttons by perm | `J:/controller/main/DisableButtons.java`, `ButtonWithPerm.java` |
| Tasks/Background | `J:/controller/main/DataTask.java`, `BackgroundSlideshow.java` |
| Menu button setting | `J:/controller/main/MenuButtonSetting.java` |
| Data list helper | `J:/controller/main/DataList.java` |
| DAO factory | `J:/model/dao/DaoFactory.java` |

---

## 🛒 1) Items (الأصناف)
**Controllers:** `J:/controller/items/`
- `ItemsController.java` — الشاشة الرئيسية للأصناف
- `AddItemController.java` — إضافة/تعديل صنف
- `CardController.java` — كرت الصنف
- `InventoryController.java` — الجرد
- `UnitsController.java`, `TableUnitsSetting.java`, `TableUnitsSettingProperty.java` — الوحدات
- `ItemsPackageController.java` — الباكدج
- `UpdateSomeItems.java` — تحديث جماعي
- `ColumnImage.java` — عمود الصورة في الجدول

**Service:** `J:/service/ItemsService.java`, `UnitsService.java`, `ItemPackageService.java`, `ItemMiniQuantityService.java`, `SelPriceItemService.java`, `CardItemService.java`, `MainGroupService.java`, `SupGroupService.java`, `StockService.java`

**DAO:** `J:/model/dao/ItemsDao.java`, `ItemsUnitDao.java`, `ItemsPackageDao.java`, `ItemMiniDao.java`, `Items_StockDao.java`, `MainGroupsDao.java`, `SubGroupsDao.java`, `StockDao.java`, `TypeSelPriceDao.java`, `UnitsDao.java`, `CardItemDao.java`

**Domain:** `J:/model/domain/ItemsModel.java`, `ItemsUnitsModel.java`, `Items_Package.java`, `ItemsMiniQuantity.java`, `Items_Stock_Model.java`, `MainGroups.java`, `SubGroups.java`, `Stock.java`, `UnitsModel.java`, `SelPriceTypeModel.java`, `CardItems.java`

**FXML:** `R:/view/items/items-view.fxml` (+ بقية FXML داخل `R:/view/items/`)

---

## 🧾 2) Invoice — Sales (المبيعات)
**Controllers:** `J:/controller/invoice/`
- `BuyController2.java` — الشاشة الموحدة للفواتير (بيع/شراء/مرتجع)
- `BuyData.java`, `ActionTextBuy.java` — منطق مساعد للفاتورة
- `TotalsController.java` — شاشة الإجماليات
- `ShowInvoiceController.java`, `ShowInvoiceDetails.java`, `ShowInvoiceNameData.java` — عرض فاتورة محفوظة
- `UpdateInvoiceRow.java` — تعديل سطر داخل فاتورة
- `DialogCashPaid.java` — حوار الدفع
- `ChoiceItemExpireDate.java`, `ExpireDateInterface.java` — تواريخ الصلاحية

**Service:** `J:/service/SalesService.java`, `SalesReService.java`, `SalesPackageService.java`, `TotalSalesService.java`, `TotalSalesReturnService.java`, `TotalsService.java`

**DAO:** `J:/model/dao/SalesDao.java`, `SalesReturnDao.java`, `SalesPackageDao.java`, `TotalsSalesDao.java`, `TotalsSalesReturnDao.java`

**Domain:** `J:/model/domain/Sales.java`, `Sales_Return.java`, `Sales_Package.java`, `Total_Sales.java`, `Total_Sales_Re.java`

**FXML:** `R:/view/invoice/` (تأكد من الأسماء)

---

## 🛍️ 3) Invoice — Purchase (المشتريات)
**Controllers:** نفس مجلد `J:/controller/invoice/` (الشاشة موحدة `BuyController2`)

**Service:** `J:/service/PurchaseService.java`, `PurchaseReService.java`, `TotalBuyService.java`, `TotalBuyReturnService.java`

**DAO:** `J:/model/dao/PurchaseDao.java`, `PurchaseReturnDao.java`, `TotalsBuyDao.java`, `TotalsPurchaseReturnDao.java`

**Domain:** `J:/model/domain/Purchase.java`, `Purchase_Return.java`, `Total_buy.java`, `Total_Buy_Re.java`

**Data Interfaces (Generic Strategy):** `J:/interfaces/impl_dataInterface/` — `SuppliersDataReturn.java`, ... (انظر القسم 14)

---

## 💰 4) Treasury (الخزينة)
**Service:** `J:/service/TreasuryService.java`, `TreasuryTransferService.java`, `TreasuryBalanceService.java`, `DepositService.java`

**DAO:** `J:/model/dao/TreasuryDao.java`, `TreasuryTransferDao.java`, `TreasuryBalanceDao.java`, `DepositDao.java`

**Domain:** `J:/model/domain/TreasuryModel.java`, `TreasuryData.java`, `TreasuryTransferModel.java`, `TreasuryBalance.java`, `AddDeposit.java`

**Controllers:** `J:/controller/convert_treasury/` (تحويلات الخزينة)

---

## 🏪 5) POS (نقاط البيع)
**Controllers:** `J:/controller/pos/`
- `PosController.java` — الشاشة الرئيسية
- `ButtonSetting.java`, `DialogButtons.java` — أزرار سريعة
- `PosInvoiceSetting.java`, `PosInvoiceSettingData.java` — إعدادات الفاتورة
- `PosPaymentMethods.java` — طرق الدفع

**Service:** يستخدم `SalesService` + `TotalSalesService` + `TreasuryService`

**FXML:** `R:/view/pos/`

---

## 👥 6) Users & Permissions (المستخدمون والصلاحيات)
**Controllers:** `J:/controller/users/`
- `UserController.java` — قائمة المستخدمين
- `AddUserController.java` — إضافة/تعديل مستخدم
- `UserPermissionController.java` — إدارة الصلاحيات
- `UserShiftController.java` — ورديات المستخدم

**Service:** `J:/service/UsersService.java`, `UserPermissionService.java`, `UserShiftService.java`

**DAO:** `J:/model/dao/UsersDao.java`, `UserPermissionDao.java`, `UserShiftDao.java`

**Domain:** `J:/model/domain/Users.java`, `Users_Permission.java`, `UserShift.java`

**Type/Enum:** `J:/type/UserPermissionType.java`

**Disable by perm:** `J:/controller/main/DisableButtons.java` (`PermissionDisableService`)

---

## 🔐 7) Security / Auth (تسجيل الدخول والأمن)
**Path:** `J:/security/`
- `AuthService.java` — تسجيل الدخول
- `RegistrationService.java` — التسجيل
- `PasswordService.java` — كلمات المرور (BCrypt)
- `UserRepository.java` — مستودع المستخدمين
- `Validation.java` — تحقق من المدخلات

**Related:** `J:/view/LogApplication.java` (الجلسة الحالية: `LogApplication.usersVo`)

---

## 📊 8) Reports (التقارير)
**Controllers:** `J:/controller/reports/`
- `SummaryController.java` — الملخص
- `ProfitLossController.java` — الأرباح والخسائر
- `ReportItemsController.java` — تقارير الأصناف
- `ReportByTreeController.java` — تقرير شجري
- `DayDetailsTreeController.java`, `DayDetailsTableController.java` — تفاصيل اليوم
- `ReportTotalByYearController.java`, `ReportTotalsByYearAndMonthController.java` — تقارير سنوية/شهرية
- `ReportPaid.java` — المدفوعات
- `ToolbarReportsNameController.java`, `ToolbarReportsNameInterface.java` — شريط الأدوات

**Print/Jasper:** `J:/reportData/` (الملفات الكاملة TODO — لكن الدخل المعروف:)
- `Print_Reports.java` — منسق الطباعة
- `JasperReportPaths.java` — مسارات Jasper

**Service:** `J:/service/ShiftReportService.java`, `EarningsService.java`, `ExpensesService.java`, `ExpensesDetailsService.java`

**FXML:** `R:/view/reports/`

---

## ⚙️ 9) Settings (الإعدادات)
**Controllers:** `J:/controller/setting/`
- `SettingController.java` — الإعداد الرئيسي
- `SettingCompanyController.java` — بيانات الشركة
- `SettingTabCheckController.java`, `SettingTabBarcodeController.java`, `SettingTabLanguageController.java` — تابات
- `LabelBarcodeController.java` — تنسيق الباركود
- `FontColorController.java`, `FontColorDialog.java` — الخطوط والألوان
- `ComboSetting.java` — مساعد combo

**Config:** `J:/config/`
- `Configs.java`, `PropertiesName.java`, `PreferencesSetting.java`
- `ThemeManager.java`, `Style_Sheet.java`, `Image_Setting.java`
- `FxmlConstants.java`, `NamesTables.java`
- `ConnectionToDatabase.java`, `ConnectionToMysql.java`, `SaveDatabaseFile.java`

---

## 🎯 10) Targets (الأهداف/العمولات)
**Controllers:** `J:/controller/target/` (TODO — لم تُمسح بعد)

**Service:** `J:/service/TargetService.java`, `TargetDetailsService.java`

**DAO:** `J:/model/dao/TargetDao.java`, `TargetDetailsDao.java`

**Domain:** `J:/model/domain/Target.java`, `TargetsDetails.java`, `Target_Delegate.java`

---

## 👤 11) Customers / Suppliers / Employees (الأطراف)
### Customers (العملاء)
- Service: `J:/service/CustomerService.java`, `AccountCustomerService.java`
- DAO: `J:/model/dao/CustomerDao.java`, `CustomerAccountDao.java`
- Domain: `J:/model/domain/Customers.java`, `CustomerAccount.java`

### Suppliers (الموردون)
- Service: `J:/service/SuppliersService.java`, `AccountSupplierService.java`
- DAO: `J:/model/dao/SuppliersDao.java`, `SupplierAccountDao.java`
- Domain: `J:/model/domain/Suppliers.java`, `SupplierAccount.java`

### Employees (الموظفون)
- Service: `J:/service/EmployeeService.java`
- DAO: `J:/model/dao/EmployeesDao.java`, `ExpenseSalaryDao.java`
- Domain: `J:/model/domain/Employees.java`, `ExpensesSalary.java`

### Areas (المناطق)
- Service: `J:/service/AreaService.java` | DAO: `AreaDao.java` | Domain: `Area.java`

### Generic name service
- `J:/service/NameService.java` — خدمة عامة لأي طرف (Generic)
- `J:/service/AccountService.java` — خدمة حسابات عامة

**Controllers:** `J:/controller/name_account/` (TODO — لم تُمسح بعد)
**Data by Name:** `J:/controller/dataByName/` + `J:/controller/dataByName/impl/` (TODO)

---

## 📦 12) Stock & Transfers (المخازن والتحويلات)
- Service: `J:/service/StockService.java`, `StockTransferService.java`, `StockTransferListService.java`
- DAO: `J:/model/dao/StockDao.java`, `StockTransferDao.java`, `StockTransferListDao.java`, `Items_StockDao.java`
- Domain: `J:/model/domain/Stock.java`, `StockTransfer.java`, `StockTransferListItems.java`, `Items_Stock_Model.java`
- Controllers: `J:/controller/convert_stock/`

---

## 💸 13) Expenses & Earnings (المصروفات والإيرادات)
- Service: `J:/service/ExpensesService.java`, `ExpensesDetailsService.java`, `EarningsService.java`
- DAO: `J:/model/dao/ExpensesDao.java`, `ExpensesDetailsDao.java`, `EarningsDao.java`, `ExpenseSalaryDao.java`
- Domain: `J:/model/domain/Expenses.java`, `ExpensesDetails.java`, `Earnings.java`, `ExpensesSalary.java`

---

## 🧩 14) Generic Invoice Strategy (مهم — البنية المعممة)
> الفواتير تستخدم Generic strategy بأربع type parameters:
> `<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>`

**Base classes:** `J:/model/base/`
- `BasePurchasesAndSales.java`, `BaseTotals.java`, `BaseNames.java`, `BaseAccount.java`

**Strategy implementations:** `J:/interfaces/impl_dataInterface/`
- `SuppliersDataReturn.java` (= مرتجع شراء موردين)
- بقية الملفات (TODO — يفترض وجود: `SuppliersData`, `CustomersData`, `CustomersDataReturn`, ...)

**Other interface impls:**
- `J:/interfaces/impl_invoiceBuy/` — منطق الفاتورة
- `J:/interfaces/impl_namesDao/` — DAO الأسماء
- `J:/interfaces/impl_account/` — الحسابات
- `J:/interfaces/impl_design/` — تصميم الجداول
- `J:/interfaces/impl_totalDesgin/` — تصميم الإجماليات
- `J:/interfaces/names/` — أنواع الأسماء
- `J:/interfaces/spinner/` — Spinners
- `J:/interfaces/treeAccount/`, `treePurchase/` — العرض الشجري
- `J:/interfaces/implReportTotals/` — تقارير الإجماليات
- `J:/interfaces/api/` — تعريفات الواجهات الأساسية

---

## 🔄 15) Shift / Session (الورديات والجلسة)
- Path: `J:/session/` (TODO — يفترض: `ShiftContext.java`, `ShiftContextLoader.java`)
- Service: `J:/service/UserShiftService.java`, `ShiftReportService.java`
- Domain: `J:/model/domain/UserShift.java`, `ShiftSummary.java`
- Doc: `docs/shifts.md`

---

## 🧮 16) Calculator (الآلة الحاسبة)
- Controllers: `J:/controller/calculator/` (TODO)
- View: `J:/view/calculator/` (TODO)

---

## 🔍 17) Search (البحث)
- Controllers: `J:/controller/search/` (TODO)
- Generic table search: `com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText`

---

## 📋 18) Data Setting / Dashboards / Misc
- `J:/controller/dataSetting/` + `impl/` (TODO)
- `J:/controller/others/` (TODO — يحتوي على helpers مثل `SelectedButton`, `ServiceData`)
- `J:/controller/model/` — DTOs لشاشات معينة (مثل `PrintPurchaseWithName`)
- `J:/dash/` (TODO) — الداشبورد
- `J:/otherSetting/` (TODO) — `MaskerPaneSetting.java` معروف
- `J:/perm/` (TODO) — `PermAccountAndNameInt.java` معروف
- `J:/trial/` (TODO) — النسخة التجريبية
- `J:/features/` (TODO):
  - `features/checkbox/`, `features/chart/`, `features/choiceDialoge/`
  - `features/notification/`, `features/key_setting/`, `features/export/`

---

## 🪟 19) View Launchers (فتح النوافذ)
- Path: `J:/view/` (TODO — لم تُمسح بعد)
- Known: `LogApplication.java`, `AddItemApplication.java`, `CardApplication.java`, `ConvertItemsGroup.java`
- Barcode: `J:/view/barcode/` — `PrintBarcodeApp.java`, `PrintBarcodeModel.java`
- Calculator: `J:/view/calculator/`

---

## 📐 20) FXML Loader Infrastructure
- Path: `J:/openFxml/` (TODO)
- Known: `FxmlPath.java` (annotation)

---

## 🗂️ 21) Table Utilities
- Path: `J:/table/` (TODO)
- Known: `EditCell.java`, `TableSetting.java`

---

## 🏷️ 22) Types / Enums
- Path: `J:/type/` (TODO)
- Known: `UserPermissionType.java`

---

## 📁 Resources Index
- `R:/view/` — كل ملفات FXML مجمعة حسب الميزة (items, invoice, pos, reports, setting, users, ...)
- `R:/css/` — الستايل
- `R:/image/` — الأيقونات
- `R:/log4j2.xml`, `R:/log4j2-1.properties` — logging
- `R:/version.properties` — معلومات الإصدار

---

## 🚫 DO-NOT-READ
- `controlsfx/**` (مكتبة محلية خارجية)
- `backup_data/**`, `logs/**`, `out/**`, `reports/**`, `fonts/**`
- `.idea/`, `.claude/`, `target/`
- `*.dat`, `*.jar`, `*.class`

---

## ⚠️ Pending TODO (لم تُمسح بعد — كمّلها عند الحاجة)
- `J:/view/` (و `view/barcode`, `view/calculator` كاملة)
- `J:/openFxml/`
- `J:/reportData/`
- `J:/table/`
- `J:/type/`
- `J:/session/`
- `J:/features/` (كل الـ subpackages)
- `J:/interfaces/` (الفهرسة الكاملة لكل impl_*)
- `J:/otherSetting/`, `J:/dash/`, `J:/perm/`, `J:/trial/`
- `J:/controller/target/`, `controller/calculator/`, `controller/search/`,
  `controller/name_account/`, `controller/dataByName/`, `controller/dataSetting/`,
  `controller/convert_stock/`, `controller/convert_treasury/`,
  `controller/others/`, `controller/model/`
- محتوى `R:/view/`, `R:/css/`, `R:/image/` بالتفصيل
