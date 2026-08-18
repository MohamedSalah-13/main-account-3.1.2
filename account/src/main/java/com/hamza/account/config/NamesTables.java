package com.hamza.account.config;

/**
 * Bundle keys for {@code @ColumnData(titleName = ...)} across every annotated table -
 * an annotation attribute must be a compile-time constant, so it cannot call {@link
 * com.hamza.controlsfx.language.LanguageManager} directly; these constants hold the
 * key, and {@link com.hamza.controlsfx.table.TableColumnAnnotation} resolves it
 * through the active bundle when the column is built. See {@code column.*} in
 * {@code i18n/messages*.properties} for the translations.
 */
public class NamesTables {

    public final static String CODE = "column.code";
    public final static String NAME = "column.name";
    public final static String ADDRESS = "column.address";
    public static final String SALARY = "column.salary";
    public static final String EMAIL = "column.email";
    public static final String TEL = "column.tel";
    public static final String QUANTITY = "column.quantity";
    /**
     * The units screen's column for units.value_d. It is a default, not the
     * factor an item converts by - that is per item, in items_units.
     */
    public static final String DEFAULT_FACTOR = "column.default_factor";
    public static final String PRICE = "column.price";
    public static final String DATE = "column.date";
    public static final String NOTES = "column.notes";
    public static final String LIMIT = "column.limit";
    public static final String STRING = "column.barcode_str";
    public static final String EXPIRY_DATE = "column.expiry_date";
    public static final String ALERT_DATE = "column.alert_date";
    public static final String DEBTOR = "column.debtor";
    public static final String CREDITOR = "column.creditor";
    public static final String AMOUNT = "column.amount";
    public static final String PURCHASE = "column.purchase";

    public static final String TOTAL = "column.total";
    public static final String TYPE = "column.type";
    /** The kind of document a movement came from, as the item card shows it. */
    public static final String PROCESS_TYPE = "column.process_type";
    public static final String DISCOUNT = "column.discount";
    public static final String TOTAL_AFTER = "column.total_after";
    public static final String REST = "column.rest";
    public static final String TOTAL_AMOUNT = "column.total_amount";
    public static final String PASS = "column.password";
    public static final String SEL_PRICE = "column.sel_price";
    public static final String BUY_PRICE = "column.buy_price";
    public static final String BARCODE = "column.barcode";
    public static final String FIRST_BALANCE = "column.first_balance";
    public static final String BALANCE = "column.balance";
    public static final String IN_BALANCE = "column.in_balance";
    public static final String OUT_BALANCE = "column.out_balance";
    public static final String OTHER_REVENUES = "column.other_revenues";
    public static final String OTHER_EXPENSES = "column.other_expenses";
    public static final String ACCOUNT_SUPPLIERS = "column.account_suppliers";
    public static final String ACCOUNT_CUSTOM = "column.account_custom";
    public static final String SALES_RETURN = "column.sales_return";
    public static final String SALES = "column.sales";
    public static final String PURCHASE_RETURN = "column.purchase_return";
    public static final String RETURN_QUANTITY = "column.return_quantity";
    public static final String NAME_ITEM = "column.name_item";
    public static final String MINI_QUANTITY = "column.mini_quantity";
    public static final String CODE_INVOICE = "column.code_invoice";
    public static final String DELEGATE = "column.delegate";
    public static final String RECEIPT = "column.receipt";
    public static final String DAMAGED = "column.damaged";
    public static final String TOTAL_BUY_PRICE = "column.total_buy_price";

    public static final String SUM_ALL_BALANCE = "column.sum_all_balance";
    public static final String DATE_INSERT = "column.date_insert";
    public static final String ITEM_NAME = "column.item_name";
}
