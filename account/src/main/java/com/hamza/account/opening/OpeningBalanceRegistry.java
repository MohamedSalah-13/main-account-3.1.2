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
 * and the third copy is where the tables get missed. The parties' openings live on their
 * own rows; an item's lives on each of its warehouse rows, and is
 * {@code WarehouseOpeningBalance}'s.
 */
public final class OpeningBalanceRegistry {

    /*
     * The item's rule was here until V78, and it counted an item's lines in every warehouse against
     * items.first_balance - a copy of warehouse 1 that V78 drops. An item's opening is per warehouse,
     * and so is the question of whether it has moved: features/items/WarehouseOpeningBalance.
     */

    /** A customer has moved once they have an invoice, a return, or a payment. */
    public static final OpeningBalanceRule CUSTOMERS = OpeningBalanceRule.forEntity("delete.entity.customer", "custom")
            .movedBy("total_sales", "sup_code", "delete.ref.sales_invoice")
            .movedBy("total_sales_re", "sup_id", "delete.ref.sales_return")
            .movedBy("customers_accounts", "account_code", "delete.ref.account_movement")
            .correctedBy("opening.correction.customers")
            .build();

    public static final OpeningBalanceRule SUPPLIERS = OpeningBalanceRule.forEntity("delete.entity.supplier", "suppliers")
            .movedBy("total_buy", "sup_code", "delete.ref.purchase_invoice")
            .movedBy("total_buy_re", "sup_id", "delete.ref.purchase_return")
            .movedBy("suppliers_accounts", "account_code", "delete.ref.account_movement")
            .correctedBy("opening.correction.suppliers")
            .build();

    private OpeningBalanceRegistry() {
    }
}
