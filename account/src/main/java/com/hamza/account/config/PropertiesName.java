package com.hamza.account.config;

public class PropertiesName extends PreferencesSetting {

    public static final String BARCODE_LABEL_SHOW_DOUBLE = "barcode.label.show.double";
    public static final String BARCODE_LABEL_PRINT_PRICE = "barcode.label.print.price";
    public static final String BARCODE_LABEL_PRINT_BARCODE = "barcode.label.print.barcode";
    public static final String BARCODE_LABEL_PRINT_NAME = "barcode.label.print.name";
    private static final String PANE_INDEX = "pane.index";
    private static final String SETTING_EXPANDED = "setting.expanded";
    private static final String ITEMS_SUB_GROUP = "items.sub.group";
    private static final String ITEMS_TYPE_GROUP = "items.type.group";
    private static final String SETTING_SAVE_NAME_CUSTOMER = "setting.save.name.customer";
    private static final String SETTING_SAVE_NAME_DELEGATE = "setting.save.name.delegate";
    private static final String PATH_IMAGE_MAIN_SCREEN = "setting.path.image.main.screen";
    private static final String SETTING_PRINTER_THERMAL = "setting.printer.thermal";
    private static final String SETTING_PRINTER_BARCODE = "setting.printer.barcode";
    private static final String SETTING_PRINTER_NORMAL = "setting.printer.normal";
    private static final String DEFAULT_PRINTER_NAME = "Microsoft Print to PDF";
    private static final String INVOICE_BACKUP_AFTER_SAVE = "invoice.backup.after.save";
    private static final String INVOICE_UPDATE_PRICE = "invoice.update.price";
    private static final String INVOICE_SHOW_SCREEN_PAID = "invoice.show.screen.paid";
    // -------------------

    private static final String SETTING_CURRENCY = "setting.currency";
    private static final String SETTING_PRINT_REPORT_TITLE = "setting.print.report.title";
    private static final String SEL_WITHOUT_BALANCE = "item.sel.without.balance";
    private static final String ITEM_SHOW_ALERT = "item.show.alert";
    private static final String ITEM_SHOW_IMAGE_HINT = "item.show.image.hint";
    private static final String ITEM_EDIT_FROM_TABLE = "item.edit.from.table";
    private static final String COUNT_PRINT_PRINTER_THERMAL = "count.print.printer.thermal";
    private static final String DATABASE_USE_PATH_VARIABLE = "database.use.path.variable";
    private static final String PRINT_PAPER_RECEIPT_INVOICE = "print.paper.receipt.invoice";
    private static final String PRINT_PAPER_RECEIPT_ACCOUNT = "print.paper.receipt.account";
    private static final String PRINT_PAPER_DIRECT = "print.paper.direct";
    private static final String SERIAL_RECORD_MODIFICATION_NUMBER = "serial.record.modification.number";
    private static final String INVOICE_INCREASE_ITEM_ONE_TABLE = "invoice.increase.item.one.table";
    /*-------------------------------------- Barcode Setting --------------------------------------*/
    private static final String SETTING_BARCODE_COUNT_SCALE = "setting.barcode.count.scale";
    private static final String SETTING_BARCODE_COUNT_ITEM = "setting.barcode.count.item";
    private static final String SETTING_BARCODE_START = "setting.barcode.start";
    private static final String SETTING_BARCODE_LENGTH = "setting.barcode.length";
    private static final String SETTING_BARCODE_SCALE_ACTIVE = "setting.barcode.scale.active";
    private static final String SETTING_BARCODE_VALIDATE_CHECK_DIGIT = "setting.barcode.validate.check.digit";
    private static final String SETTING_BARCODE_MAX_WEIGHT = "setting.barcode.max.weight";
    private static final String SETTING_BARCODE_MIN_WEIGHT = "setting.barcode.min.weight";
    private static final String BACKUP_DATABASE_SAVE_FOLDER = "backup.database.save.folder";

    /*-------------------------------------- Other Setting --------------------------------------*/
    private static final String BACKUP_DATABASE_SAVE_AUTOMATIC = "backup.database.save.automatic";
    private static final String BACKUP_DATABASE_SAVE_BEFORE_CLOSE = "backup.database.save.before.close";
    private static final String BACKUP_DATABASE_TIME_BACKUP = "backup.database.time_backup";
    private static final String SPLIT_PANE_DIVIDER_POS = "pos.splitPane.divider";
    private static final String POS_SPLIT_PANE_DIVIDER_SEARCH_ITEMS = "pos.splitPane.divider.search.items";
    private static final String POS_PRINT_CUSTOMER = "pos.print.customer";
    private static final String APP_LAST_RUN_VERSION = "app.lastRunVersion";
    private static final String SETTING_LOGIN_SHOW = "setting.login.show";
    private static final String SETTING_SERVER_START = "setting.server.start";
    private static final String SETTING_SHOW_INVOICE_SCREEN_SEPARATE = "setting.show.invoice.screen.separate";
    private static final String POS_INVOICE_ITEMS_SIZE_HEIGHT = "pos.invoice.items.size.height";
    private static final String POS_INVOICE_ITEMS_SIZE_WIDTH = "pos.invoice.items.size.width";
    private static final String POS_INVOICE_SHOW_SELECT_PRICE = "pos.invoice.show.select.price";
    private static final String POS_INVOICE_FONT_NAME_SIZE = "pos.invoice.font.name.size";
    private static final String POS_INVOICE_FONT_PRICE_SIZE = "pos.invoice.font.price.size";
    private static final String MENU_SEARCH_SIZE_HEIGHT = "menu.search.size.height";
    private static final String MENU_SEARCH_SIZE_WIDTH = "menu.search.size.width";
    private static final String ACCOUNT_CONTROLLER_ROW_COLOR = "account.controller.row.color";
    private static final String NUMBER_GENERATOR_LAST_NUMBER = "number.generator.last.number";
    private static final String FONT_COLOR_ACTIVE = "font.color.active";
    private static final String SHOW_MAIN_TOTALS = "setting.show.main.totals";



    public static String getItemsSubGroup() {
        return getString(ITEMS_SUB_GROUP, "1");
    }

    public static void setItemsSubGroup(String value) {
        putString(ITEMS_SUB_GROUP, value);
    }

    public static String getItemsTypeGroup() {
        return getString(ITEMS_TYPE_GROUP, "1");
    }

    public static void setItemsTypeGroup(String value) {
        putString(ITEMS_TYPE_GROUP, value);
    }

    public static boolean getSettingExpanded() {
        return getBoolean(SETTING_EXPANDED, false);
    }

    public static void setSettingExpanded(boolean value) {
        putBoolean(SETTING_EXPANDED, value);
    }

    public static String getSettingSaveNameCustomer() {
        return getString(SETTING_SAVE_NAME_CUSTOMER, "1");
    }

    public static void setSettingSaveNameCustomer(String value) {
        putString(SETTING_SAVE_NAME_CUSTOMER, value);
    }

    public static String getSettingSaveNameDelegate() {
        return getString(SETTING_SAVE_NAME_DELEGATE, "1");
    }

    public static void setSettingSaveNameDelegate(String value) {
        putString(SETTING_SAVE_NAME_DELEGATE, value);
    }

    public static String getPathImageMainScreen() {
        return getString(PATH_IMAGE_MAIN_SCREEN, "");
    }

    public static void setPathImageMainScreen(String value) {
        putString(PATH_IMAGE_MAIN_SCREEN, value);
    }

    public static String getSettingPrinterThermal() {
        return getString(SETTING_PRINTER_THERMAL, DEFAULT_PRINTER_NAME);
    }

    public static void setSettingPrinterThermal(String value) {
        putString(SETTING_PRINTER_THERMAL, value);
    }

    public static String getSettingPrinterBarcode() {
        return getString(SETTING_PRINTER_BARCODE, DEFAULT_PRINTER_NAME);
    }

    public static void setSettingPrinterBarcode(String value) {
        putString(SETTING_PRINTER_BARCODE, value);
    }

    public static String getSettingPrinterNormal() {
        return getString(SETTING_PRINTER_NORMAL, DEFAULT_PRINTER_NAME);
    }

    public static void setSettingPrinterNormal(String value) {
        putString(SETTING_PRINTER_NORMAL, value);
    }

    public static String getSettingCurrency() {
        return getString(SETTING_CURRENCY, "ar_EG");
    }

    public static void setSettingCurrency(String value) {
        putString(SETTING_CURRENCY, value);
    }

    public static boolean getSettingPrintReportTitle() {
        return getBoolean(SETTING_PRINT_REPORT_TITLE, false);
    }

    public static void setSettingPrintReportTitle(boolean value) {
        putBoolean(SETTING_PRINT_REPORT_TITLE, value);
    }

    public static boolean getBarcodeLabelShowDouble() {
        return getBoolean(BARCODE_LABEL_SHOW_DOUBLE, true);
    }

    public static void setBarcodeLabelShowDouble(boolean value) {
        putBoolean(BARCODE_LABEL_SHOW_DOUBLE, value);
    }

    public static boolean getBarcodeLabelPrintPrice() {
        return getBoolean(BARCODE_LABEL_PRINT_PRICE, false);
    }

    public static void setBarcodeLabelPrintPrice(boolean value) {
        putBoolean(BARCODE_LABEL_PRINT_PRICE, value);
    }

    public static boolean getBarcodeLabelPrintBarcode() {
        return getBoolean(BARCODE_LABEL_PRINT_BARCODE, false);
    }

    public static void setBarcodeLabelPrintBarcode(boolean value) {
        putBoolean(BARCODE_LABEL_PRINT_BARCODE, value);
    }

    public static boolean getBarcodeLabelPrintName() {
        return getBoolean(BARCODE_LABEL_PRINT_NAME, false);
    }

    public static void setBarcodeLabelPrintName(boolean value) {
        putBoolean(BARCODE_LABEL_PRINT_NAME, value);
    }

    public static boolean getSelWithoutBalance() {
        return getBoolean(SEL_WITHOUT_BALANCE, true);
    }

    public static void setSelWithoutBalance(boolean value) {
        putBoolean(SEL_WITHOUT_BALANCE, value);
    }

    public static boolean getItemShowAlert() {
        return getBoolean(ITEM_SHOW_ALERT, false);
    }

    public static void setItemShowAlert(boolean value) {
        putBoolean(ITEM_SHOW_ALERT, value);
    }

    public static boolean getItemImageHint() {
        return getBoolean(ITEM_SHOW_IMAGE_HINT, true);
    }

    public static void setItemImageHint(boolean value) {
        putBoolean(ITEM_SHOW_IMAGE_HINT, value);
    }

    public static boolean getItemEditFromTable() {
        return getBoolean(ITEM_EDIT_FROM_TABLE, false);
    }
    public static void setItemEditFromTable(boolean value) {
        putBoolean(ITEM_EDIT_FROM_TABLE, value);
    }

    public static int getCountPrintPrinterThermal() {
        return getInt(COUNT_PRINT_PRINTER_THERMAL, 1);
    }

    public static void setCountPrintPrinterThermal(int value) {
        putInt(COUNT_PRINT_PRINTER_THERMAL, value);
    }

    public static boolean getDatabaseUsePathVariableSetting() {
        return getBoolean(DATABASE_USE_PATH_VARIABLE, true);
    }

    public static void setDatabaseUsePathVariableSetting(boolean value) {
        putBoolean(DATABASE_USE_PATH_VARIABLE, value);
    }

    public static boolean getPrintPaperReceiptInvoice() {
        return getBoolean(PRINT_PAPER_RECEIPT_INVOICE, true);
    }

    public static void setPrintPaperReceiptInvoice(boolean value) {
        putBoolean(PRINT_PAPER_RECEIPT_INVOICE, value);
    }

    public static boolean getPrintPaperReceiptAccount() {
        return getBoolean(PRINT_PAPER_RECEIPT_ACCOUNT, true);
    }

    public static void setPrintPaperReceiptAccount(boolean value) {
        putBoolean(PRINT_PAPER_RECEIPT_ACCOUNT, value);
    }

    public static boolean getPrintPaperDirect() {
        return getBoolean(PRINT_PAPER_DIRECT, true);
    }

    public static void setPrintPaperDirect(boolean value) {
        putBoolean(PRINT_PAPER_DIRECT, value);
    }

    public static boolean getInvoiceIncreaseItemOneTable() {
        return getBoolean(INVOICE_INCREASE_ITEM_ONE_TABLE, false);
    }

    public static void setInvoiceIncreaseItemOneTable(boolean value) {
        putBoolean(INVOICE_INCREASE_ITEM_ONE_TABLE, value);
    }

    public static int getSettingBarcodeCountScale() {
        return getInt(SETTING_BARCODE_COUNT_SCALE, 2);
    }

    public static void setSettingBarcodeCountScale(int value) {
        putInt(SETTING_BARCODE_COUNT_SCALE, value);
    }

    public static int getSettingBarcodeCountItem() {
        return getInt(SETTING_BARCODE_COUNT_ITEM, 5);
    }

    public static void setSettingBarcodeCountItem(int value) {
        putInt(SETTING_BARCODE_COUNT_ITEM, value);
    }

    public static int getSettingBarcodeStart() {
        return getInt(SETTING_BARCODE_START, 27);
    }

    public static void setSettingBarcodeStart(int value) {
        putInt(SETTING_BARCODE_START, value);
    }

    public static int getSettingBarcodeLength() {
        return getInt(SETTING_BARCODE_LENGTH, 13);
    }

    public static void setSettingBarcodeLength(int value) {
        putInt(SETTING_BARCODE_LENGTH, value);
    }

    public static boolean getSettingBarcodeScaleActive() {
        return getBoolean(SETTING_BARCODE_SCALE_ACTIVE, false);
    }

    public static void setSettingBarcodeScaleActive(boolean value) {
        putBoolean(SETTING_BARCODE_SCALE_ACTIVE, value);
    }

    public static boolean getSettingBarcodeValidateCheckDigit() {
        return getBoolean(SETTING_BARCODE_VALIDATE_CHECK_DIGIT, false);
    }

    public static void setSettingBarcodeValidateCheckDigit(boolean value) {
        putBoolean(SETTING_BARCODE_VALIDATE_CHECK_DIGIT, value);
    }

    public static double getSettingBarcodeMaxWeight() {
        return getDouble(SETTING_BARCODE_MAX_WEIGHT, 99.999);
    }

    public static void setSettingBarcodeMaxWeight(double value) {
        putDouble(SETTING_BARCODE_MAX_WEIGHT, value);
    }

    public static double getSettingBarcodeMinWeight() {
        return getDouble(SETTING_BARCODE_MIN_WEIGHT, 0.001);
    }

    public static void setSettingBarcodeMinWeight(double value) {
        putDouble(SETTING_BARCODE_MIN_WEIGHT, value);
    }

    public static String getBackupDatabaseSaveFolder() {
        return getString(BACKUP_DATABASE_SAVE_FOLDER, System.getProperty("user.home"));
    }

    public static void setBackupDatabaseSaveFolder(String value) {
        putString(BACKUP_DATABASE_SAVE_FOLDER, value);
    }

    public static boolean getBackupDatabaseSaveAutomatic() {
        return getBoolean(BACKUP_DATABASE_SAVE_AUTOMATIC, false);
    }

    public static void setBackupDatabaseSaveAutomatic(boolean value) {
        putBoolean(BACKUP_DATABASE_SAVE_AUTOMATIC, value);
    }

    public static boolean getBackupDatabaseSaveBeforeClose() {
        return getBoolean(BACKUP_DATABASE_SAVE_BEFORE_CLOSE, false);
    }

    public static void setBackupDatabaseSaveBeforeClose(boolean value) {
        putBoolean(BACKUP_DATABASE_SAVE_BEFORE_CLOSE, value);
    }

    public static void setBackupDatabaseTimeBackup(int value) {
        putInt(BACKUP_DATABASE_TIME_BACKUP, value);
    }


    public static int getPaneIndex() {
        return getInt(PANE_INDEX, 0);
    }

    public static void setPaneIndex(int value) {
        putInt(PANE_INDEX, value);
    }

    public static boolean getInvoiceBackupAfterSave() {
        return getBoolean(INVOICE_BACKUP_AFTER_SAVE, false);
    }

    public static void setInvoiceBackupAfterSave(boolean value) {
        putBoolean(INVOICE_BACKUP_AFTER_SAVE, value);
    }

    public static boolean getInvoiceUpdatePrice() {
        return getBoolean(INVOICE_UPDATE_PRICE, false);
    }

    public static void setInvoiceUpdatePrice(boolean value) {
        putBoolean(INVOICE_UPDATE_PRICE, value);
    }

    public static double getSplitPaneDividerPos() {
        return getDouble(SPLIT_PANE_DIVIDER_POS, 0.5);
    }

    public static void setSplitPaneDividerPos(double value) {
        putDouble(SPLIT_PANE_DIVIDER_POS, value);
    }

    public static double getPosSplitPaneDividerSearchItems() {
        return getDouble(POS_SPLIT_PANE_DIVIDER_SEARCH_ITEMS, 0.5);
    }

    public static void setPosSplitPaneDividerSearchItems(double value) {
        putDouble(POS_SPLIT_PANE_DIVIDER_SEARCH_ITEMS, value);
    }

    public static boolean getPosPrintCustomer() {
        return getBoolean(POS_PRINT_CUSTOMER, true);
    }

    public static void setPosPrintCustomer(boolean value) {
        putBoolean(POS_PRINT_CUSTOMER, value);
    }

    public static String getAppLastRunVersion() {
        return getString(APP_LAST_RUN_VERSION, "1.0.0");
    }

    public static void setAppLastRunVersion(String value) {
        putString(APP_LAST_RUN_VERSION, value);
    }

    public static int getSerialRecordModificationNumber() {
        return getInt(SERIAL_RECORD_MODIFICATION_NUMBER, 100);
    }

    public static void setSerialRecordModificationNumber(int value) {
        putInt(SERIAL_RECORD_MODIFICATION_NUMBER, value);
    }

    public static boolean getSettingLoginShow() {
        return getBoolean(SETTING_LOGIN_SHOW, true);
    }

    public static void setSettingLoginShow(boolean value) {
        putBoolean(SETTING_LOGIN_SHOW, value);
    }

    public static boolean getSettingServerStart() {
        return getBoolean(SETTING_SERVER_START, false);
    }

    public static void setSettingServerStart(boolean value) {
        putBoolean(SETTING_SERVER_START, value);
    }

    public static boolean getSettingShowInvoiceScreenSeparate() {
        return getBoolean(SETTING_SHOW_INVOICE_SCREEN_SEPARATE, false);
    }

    public static void setSettingShowInvoiceScreenSeparate(boolean value) {
        putBoolean(SETTING_SHOW_INVOICE_SCREEN_SEPARATE, value);
    }

    public static boolean getInvoiceShowScreenPaid() {
        return getBoolean(INVOICE_SHOW_SCREEN_PAID, false);
    }

    public static void setInvoiceShowScreenPaid(boolean value) {
        putBoolean(INVOICE_SHOW_SCREEN_PAID, value);
    }

    public static int getPosInvoiceItemsSizeHeight() {
        return getInt(POS_INVOICE_ITEMS_SIZE_HEIGHT, 150);
    }

    public static void setPosInvoiceItemsSizeHeight(int value) {
        putInt(POS_INVOICE_ITEMS_SIZE_HEIGHT, value);
    }

    public static int getPosInvoiceItemsSizeWidth() {
        return getInt(POS_INVOICE_ITEMS_SIZE_WIDTH, 100);
    }

    public static void setPosInvoiceItemsSizeWidth(int value) {
        putInt(POS_INVOICE_ITEMS_SIZE_WIDTH, value);
    }

    public static boolean getPosInvoiceShowSelectPrice() {
        return getBoolean(POS_INVOICE_SHOW_SELECT_PRICE, false);
    }

    public static void setPosInvoiceShowSelectPrice(boolean value) {
        putBoolean(POS_INVOICE_SHOW_SELECT_PRICE, value);
    }

    public static int getPosInvoiceFontNameSize() {
        return getInt(POS_INVOICE_FONT_NAME_SIZE, 12);
    }

    public static void setPosInvoiceFontNameSize(int value) {
        putInt(POS_INVOICE_FONT_NAME_SIZE, value);
    }

    public static int getPosInvoiceFontPriceSize() {
        return getInt(POS_INVOICE_FONT_PRICE_SIZE, 12);
    }

    public static void setPosInvoiceFontPriceSize(int value) {
        putInt(POS_INVOICE_FONT_PRICE_SIZE, value);
    }

    public static int getMenuSearchSizeHeight() {
        return getInt(MENU_SEARCH_SIZE_HEIGHT, 20);
    }

    public static void setMenuSearchSizeHeight(int value) {
        putInt(MENU_SEARCH_SIZE_HEIGHT, value);
    }

    public static int getMenuSearchSizeWidth() {
        return getInt(MENU_SEARCH_SIZE_WIDTH, 100);
    }

    public static void setMenuSearchSizeWidth(int value) {
        putInt(MENU_SEARCH_SIZE_WIDTH, value);
    }

    public static String getAccountControllerRowColor() {
        return getString(ACCOUNT_CONTROLLER_ROW_COLOR, "#ffffff");
    }

    public static void setAccountControllerRowColor(String value) {
        putString(ACCOUNT_CONTROLLER_ROW_COLOR, value);
    }

    public static int getNumberGeneratorLastNumber() {
        return getInt(NUMBER_GENERATOR_LAST_NUMBER, 1);
    }

    public static void setNumberGeneratorLastNumber(int value) {
        putInt(NUMBER_GENERATOR_LAST_NUMBER, value);
    }

    public static boolean getFontColorActive() {
        return getBoolean(FONT_COLOR_ACTIVE, true);
    }

    public static void setFontColorActive(boolean value) {
        putBoolean(FONT_COLOR_ACTIVE, value);
    }
    public static boolean getShowMainTotals() {
        return getBoolean(SHOW_MAIN_TOTALS, false);
    }

    public static void setShowMainTotals(boolean value) {
        putBoolean(SHOW_MAIN_TOTALS, value);
    }

}
