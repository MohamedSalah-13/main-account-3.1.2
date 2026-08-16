package com.hamza.controlsfx.language;

public class Setting_Language {

    public static final LanguageManager INSTANCE = LanguageManager.getInstance();

    public static final String salary = INSTANCE.getString("setting.salary");
    public static final String ERROR = INSTANCE.getString("error");

    /**
     * Used as an annotation constant ({@code @ColumnData(titleName = string_birth)} in
     * {@code Employees}), which requires a compile-time constant expression - a call to
     * {@link LanguageManager#getString} does not qualify. Left as a raw literal for that
     * reason, not an oversight.
     */
    public static final String string_birth = "تاريخ الميلاد";
    /** See {@link #string_birth}: same annotation-constant requirement. */
    public static final String string_hire = "تاريخ التعيين";
    public static final String generate = INSTANCE.getString("setting.generate");


    public static final String WORD_REPORT_CUSTOMER = INSTANCE.getString("setting.report.customer");
    public static final String WORD_REPORT_SUPP = INSTANCE.getString("setting.report.supplier");

    public static final String TOTAL_PUR = INSTANCE.getString("setting.total.purchase");
    public static final String TOTAL_PUR_RE = INSTANCE.getString("setting.total.purchase.return");
    public static final String TOTAL_SALES_RE = INSTANCE.getString("setting.total.sales.return");
    public static final String TOTAL_SALES = INSTANCE.getString("setting.total.sales");
    public static final String AREA = INSTANCE.getString("setting.area");

    public static final String TOTAL_DISCOUNT = INSTANCE.getString("setting.total.discount");

    public static final String THE_AMOUNT = INSTANCE.getString("setting.amount");

    public static final String CHANGE_PASS = INSTANCE.getString("nav.change.password");

    public static final String NOTES = INSTANCE.getString("setting.notes");

    public static final String THE_PASSWORD_IS_INCORRECT = INSTANCE.getString("password.incorrect");


    public static final String PROGRAM_TITLE = "AccountK / حساباتك";
    public static final String PROGRAM_TEL = "01002937820";

    public static final String PROGRAM_NAME_EN = "Mohamed Salah";
    public static final String TREASURY = INSTANCE.getString("setting.treasury");


    public static final String E_MAIL = INSTANCE.getString("setting.email");
    public static final String JOP = INSTANCE.getString("setting.job");
    public static final String SELECT_ALL = INSTANCE.getString("setting.select.all");
    public static final String CANCEL_SELECT_ALL = INSTANCE.getString("setting.cancel.select.all");

    /**
     * {@code UsersType} resolves an employee's stored job back to a constant with
     * {@code getUserTypeByType(String)}, matching by value against
     * {@code employees.type} - the same class of DB-identity landmine documented on
     * {@link #SALARIES} and friends below. Making these language-dependent would mean
     * a row saved as "مدير" stops resolving the moment the app is switched to English.
     */
    public static final String ADMIN = "المسئول";
    public static final String MANAGER = "مدير";
    public static final String EMPLOYEE = "موظف";
    public static final String DELEGATE = "مندوب";

    public static final String PLEASE_SELECT_ROW = INSTANCE.getString("msg.select.row");

    public static final String PLEASE_INSERT_ALL_DATA = INSTANCE.getString("msg.insert.all");


    public static final String MONTHS = INSTANCE.getString("setting.months");
    public static final String LIMIT = INSTANCE.getString("setting.credit.limit");
    public static final String WORD_CARD_ITEM = INSTANCE.getString("item.card.title");


    public static final String WORD_REPORT_ITEMS = INSTANCE.getString("setting.report.items");


    public static final String PRICE = INSTANCE.getString("setting.price");
    public static final String TOTAL = INSTANCE.getString("setting.total");
    public static final String PURCHASE = INSTANCE.getString("setting.purchase");
    public static final String SALES = INSTANCE.getString("setting.sales");
    public static final String RETURN = INSTANCE.getString("setting.return");
    public static final String BALANCE_NOW = INSTANCE.getString("setting.balance.now");


    public static final String BARCODE_PRINT_SEL_PRICE = INSTANCE.getString("setting.barcode.print.sel.price");
    public static final String BARCODE_PRINT_NAME = INSTANCE.getString("setting.barcode.print.name");
    public static final String TWO_BARCODE = INSTANCE.getString("setting.barcode.two.label");
    public static final String NAME_ITEM = INSTANCE.getString("setting.name.item");
    public static final String PRINT_BARCODE = INSTANCE.getString("setting.print.barcode");


    public static final String RIGHT = INSTANCE.getString("setting.right");

    public static final String LEFT = INSTANCE.getString("setting.left");
    public static final String OK = INSTANCE.getString("ok");


    public static final String DEPOSIT = INSTANCE.getString("deposit");
    public static final String EXPENSES = INSTANCE.getString("expenses");

    public static final String WORD_ADDRESS = INSTANCE.getString("address");
    public static final String WORD_CLOSE = INSTANCE.getString("common.close");

    public static final String WORD_NAME = INSTANCE.getString("name");

    public static final String WORD_PRINT = INSTANCE.getString("print");
    public static final String WORD_SAVE = INSTANCE.getString("common.save");
    public static final String WORD_SEARCH = INSTANCE.getString("search");
    public static final String FIRST_BALANCE = INSTANCE.getString("firstBalance");
    public static final String EMPLOYEES = INSTANCE.getString("employees");
    public static final String WORD_DATE = INSTANCE.getString("date");
    public static final String WORD_CODE = INSTANCE.getString("code");
    public static final String WORD_BARCODE = INSTANCE.getString("barcode");
    public static final String WORD_PUR = INSTANCE.getString("pur");
    public static final String WORD_SALES = INSTANCE.getString("sales");
    public static final String WORD_TOTAL = INSTANCE.getString("total");
    public static final String WORD_STOCK = INSTANCE.getString("stock");
    public static final String WORD_PAID = INSTANCE.getString("paid");

    public static final String WORD_NUM = INSTANCE.getString("num");
    public static final String WORD_RE_PUR = INSTANCE.getString("RePur");
    public static final String WORD_RE_SALES = INSTANCE.getString("ReSal");
    public static final String WORD_ITEMS = INSTANCE.getString("items");
    public static final String WORD_SUP_ACC = INSTANCE.getString("supAcc");
    public static final String WORD_CUSTOM_ACC = INSTANCE.getString("cuAcc");
    public static final String WORD_REST = INSTANCE.getString("rest");
    public static final String WORD_ADD = INSTANCE.getString("add");

    public static final String WORD_REFRESH = INSTANCE.getString("refresh");
    public static final String WORD_UPDATE = INSTANCE.getString("update");
    public static final String WORD_INSERT = INSTANCE.getString("insert");
    public static final String WORD_DELETE = INSTANCE.getString("delete");
    public static final String WORD_NEW = INSTANCE.getString("new");
    public static final String WORD_SHOW = INSTANCE.getString("show");


    public static final String WORD_DISCOUNT = INSTANCE.getString("discount");
    public static final String WORD_CUSTOM = INSTANCE.getString("customers");
    public static final String WORD_SUP = INSTANCE.getString("suppliers");
    public static final String WORD_ACCOUNT = INSTANCE.getString("account");


    public static final String WORD_ADD_GROUP = INSTANCE.getString("add_group");



    public static final String WORD_ADMIN = INSTANCE.getString("Administrator");

    public static final String WORD_CANCEL = INSTANCE.getString("cancel");
    public static final String WORD_USERS = INSTANCE.getString("users");

    public static final String WORD_TEL = INSTANCE.getString("tel");
;
    public static final String WORD_CASH = INSTANCE.getString("cash");
    public static final String WORD_DEFER = INSTANCE.getString("defer");

    public static final String WORD_FROM = INSTANCE.getString("from");
    public static final String WORD_TO = INSTANCE.getString("to");
    public static final String WORD_ALL = INSTANCE.getString("all");
    public static final String WORD_TYPE = INSTANCE.getString("type");

    public static final String WORD_MAIN_G = INSTANCE.getString("mainGroup");
    public static final String WORD_SUB_G = INSTANCE.getString("subGroup");

    public static final String WORD_OLD_PASS = INSTANCE.getString("oldPass");
    public static final String WORD_NEW_PASS = INSTANCE.getString("newPass");

    public static final String WORD_SEL_PRICE = INSTANCE.getString("selPrice");


    public static final String PASS_OK = INSTANCE.getString("password.confirm");

    public static final String PASS_NO_RIGHT = INSTANCE.getString("password.mismatch");
    public static final String ABOUT = INSTANCE.getString("nav.about");
    public static final String RENTALS = "ايجارات";
    public static final String SALARIES = "مرتبات";
    public static final String ELECTRICS = "الكهرباء";
    public static final String WATERS = "المياه";
    public static final String PRED = "السلف";
    public static final String OTHERS = "أخرى";

    public static final String MESSAGE = INSTANCE.getString("setting.not.updated");
    public static final String PROCESS = INSTANCE.getString("setting.user.operations");

    public static final String DATA = INSTANCE.getString("setting.data");

    public static final String Unit = INSTANCE.getString("unit");
    public static final String UNITS = INSTANCE.getString("setting.units");

    public static final String CHOOSE_PRINTER = INSTANCE.getString("setting.choose.printer");


    public static final String COMPANY_NAME = INSTANCE.getString("setting.company.name");

    public static final String STORE_TRANSFERS = INSTANCE.getString("setting.store.transfers");


    public static final String TREASURY_TRANSFERS = INSTANCE.getString("setting.treasury.transfers");
    public static final String REPORT_DELEGATE = INSTANCE.getString("setting.report.delegate");
    public static final String PLEASE_WAIT = INSTANCE.getString("setting.please.wait");

    public static final String company = INSTANCE.getString("setting.company");



    private Setting_Language() {

    }


}
