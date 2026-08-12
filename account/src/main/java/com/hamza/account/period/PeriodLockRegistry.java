package com.hamza.account.period;

/**
 * The dated documents a closed period protects.
 * <p>
 * Every one of these carries a business date that a report has already been drawn from.
 * Editing or deleting one after the period it belongs to has been closed changes a
 * figure someone has already signed, and nothing on the signed page says so - which is
 * exactly what closing a period is meant to prevent.
 * <p>
 * The stock count is here too although it is not money: posting one moves every balance
 * on the sheet at its own date, so a count posted into a closed month rewrites a stock
 * valuation that has already been reported.
 */
public final class PeriodLockRegistry {

    public static final LockedDocument SALES_INVOICE =
            new LockedDocument("فاتورة بيع", "total_sales", "invoice_number", "invoice_date");

    public static final LockedDocument SALES_RETURN =
            new LockedDocument("مرتجع بيع", "total_sales_re", "id", "invoice_date");

    public static final LockedDocument PURCHASE_INVOICE =
            new LockedDocument("فاتورة شراء", "total_buy", "invoice_number", "invoice_date");

    public static final LockedDocument PURCHASE_RETURN =
            new LockedDocument("مرتجع شراء", "total_buy_re", "id", "invoice_date");

    public static final LockedDocument CUSTOMER_ACCOUNT =
            new LockedDocument("حركة حساب عميل", "customers_accounts", "account_num", "account_date");

    public static final LockedDocument SUPPLIER_ACCOUNT =
            new LockedDocument("حركة حساب مورد", "suppliers_accounts", "account_num", "account_date");

    public static final LockedDocument EXPENSE =
            new LockedDocument("مصروف", "expenses_details", "id", "date");

    public static final LockedDocument STOCK_COUNT =
            new LockedDocument("جرد فعلي", "stock_count", "id", "count_date");

    // ---- treasury -----------------------------------------------------------
    //
    // Declared, and only one of them is enforced anywhere - because at the time of
    // writing nothing in the application writes the other two.
    //
    //   treasury_transfers        - no writer in Java at all; the table appears only in
    //                               WipeCatalog, DeleteRegistry, and a read in UserShiftDao
    //   treasury_deposit_expenses - the same
    //   treasury_movements        - written by TreasuryMovementDao.insertMovement, which
    //                               is itself unreachable: no DaoFactory method returns
    //                               it and nothing constructs it
    //
    // They are declared rather than left out so that whoever revives a treasury screen
    // finds the rule already written down, and enforcing it is one line at the write.
    // A rule declared and not reached is honest; a rule nobody remembered to write is
    // how a closed month quietly moves.

    public static final LockedDocument TREASURY_TRANSFER =
            new LockedDocument("تحويل خزينة", "treasury_transfers", "id", "transfer_date");

    public static final LockedDocument TREASURY_DEPOSIT =
            new LockedDocument("إيداع/سحب خزينة", "treasury_deposit_expenses", "id", "date_inter");

    public static final LockedDocument TREASURY_MOVEMENT =
            new LockedDocument("حركة خزينة", "treasury_movements", "id", "movement_date");

    private PeriodLockRegistry() {
    }
}
