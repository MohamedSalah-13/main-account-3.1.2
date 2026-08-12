package com.hamza.account.opening;

/**
 * The opening-balance rules, one per kind of row.
 * <p>
 * An opening balance is the only figure on these rows with no date on it. Every screen
 * works out a balance as {@code first_balance + what came in - what went out}, so
 * changing it changes what the balance was at <em>every</em> moment of that row's
 * history: a stock sheet or an account statement printed and signed last month prints
 * differently today, and nothing on either page says why. It is a closed entry once
 * anything has moved, and a correction after that is a new dated movement.
 * <p>
 * Declared here rather than written into each DAO for the reason
 * {@code DeleteRegistry} exists: the same rule was going to be repeated three times,
 * and the third copy is where the tables get missed.
 */
public final class OpeningBalanceRegistry {

    /**
     * An item has moved once it is on an invoice line, a return, an old warehouse
     * transfer, or a stock count.
     * <p>
     * Stock-count lines count although the key cascades - it is not a delete being
     * decided here - and drafts count as much as posted sheets: a draft holds the book
     * balance the counter was shown, and moving it under them makes the sheet post a
     * difference nobody measured.
     */
    public static final OpeningBalanceRule ITEMS = OpeningBalanceRule.forEntity("الصنف", "items")
            .movedBy("purchase", "num", "سطر فاتورة شراء")
            .movedBy("sales", "num", "سطر فاتورة بيع")
            .movedBy("purchase_re", "item_id", "سطر مرتجع شراء")
            .movedBy("sales_re", "item_id", "سطر مرتجع بيع")
            .movedBy("stock_transfer_list", "item_id", "سطر تحويل مخزني")
            .movedBy("stock_count_lines", "item_id", "سطر جرد")
            .correctedBy("لتصحيح الرصيد استخدم شاشة الجرد الفعلي.")
            .build();

    /** A customer has moved once they have an invoice, a return, or a payment. */
    public static final OpeningBalanceRule CUSTOMERS = OpeningBalanceRule.forEntity("العميل", "custom")
            .movedBy("total_sales", "sup_code", "فاتورة بيع")
            .movedBy("total_sales_re", "sup_id", "مرتجع بيع")
            .movedBy("customers_accounts", "account_code", "حركة حساب")
            .correctedBy("لتصحيح الرصيد سجّل حركة على حساب العميل.")
            .build();

    public static final OpeningBalanceRule SUPPLIERS = OpeningBalanceRule.forEntity("المورد", "suppliers")
            .movedBy("total_buy", "sup_code", "فاتورة شراء")
            .movedBy("total_buy_re", "sup_id", "مرتجع شراء")
            .movedBy("suppliers_accounts", "account_code", "حركة حساب")
            .correctedBy("لتصحيح الرصيد سجّل حركة على حساب المورد.")
            .build();

    private OpeningBalanceRegistry() {
    }
}
