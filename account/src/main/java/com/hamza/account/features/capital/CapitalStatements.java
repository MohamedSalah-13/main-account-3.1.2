package com.hamza.account.features.capital;

import com.hamza.account.features.items.ItemCatalogSql;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.party.PartyTableSpec;

/**
 * The statements the owner's equity is read from. Pinned character for character with their
 * parameter counts by {@code CapitalStatementsTest}.
 *
 * <p><b>The direction is read from {@code deposit_or_expenses}, never from the category's name.</b>
 * {@code category <> 'NORMAL'} is deliberate, as in {@code TreasuryStatements.SELECT_CAPITAL_MOVEMENTS}:
 * a category added later is the owner's until someone says otherwise. Reading "paid in" as
 * {@code category = 'CAPITAL_IN'} would drop such a category from both sides at once; reading the
 * direction column keeps it on the side its money actually moved, which V21's CHECK ties to the
 * category anyway. The {@code (category, date_inter)} index V21 added is what these ride on.</p>
 */
public final class CapitalStatements {

    /** One row per day and treasury that holds an owner's movement. */
    public static final String BY_DAY_AND_TREASURY = """
            SELECT d.date_inter AS day,
                   d.treasury_id AS treasury_id,
                   t.t_name AS treasury_name,
                   COALESCE(SUM(IF(d.deposit_or_expenses = 1, d.amount, 0)), 0) AS paid_in,
                   COALESCE(SUM(IF(d.deposit_or_expenses = 2, d.amount, 0)), 0) AS drawn,
                   COUNT(*) AS movements
            FROM treasury_deposit_expenses d
                     JOIN treasury t ON t.id = d.treasury_id
            WHERE d.category <> 'NORMAL'
              AND d.date_inter BETWEEN ? AND ?
            GROUP BY d.date_inter, d.treasury_id, t.t_name
            ORDER BY d.date_inter, t.t_name""";
    public static final int BY_DAY_AND_TREASURY_PARAMETERS = 2;

    /** Everything the owner paid in and drew before a day - what the period opens on. */
    public static final String BEFORE = """
            SELECT COALESCE(SUM(IF(d.deposit_or_expenses = 1, d.amount, 0)), 0) AS paid_in,
                   COALESCE(SUM(IF(d.deposit_or_expenses = 2, d.amount, 0)), 0) AS drawn
            FROM treasury_deposit_expenses d
            WHERE d.category <> 'NORMAL'
              AND d.date_inter < ?""";
    public static final int BEFORE_PARAMETERS = 1;

    /**
     * What the business held when it started using the program - every "opening" figure, which
     * {@code docs/reports-plan.md} §3.1 treats as equity brought forward and never as a movement.
     * The treasuries' opening balances, the customers' opening balances (owed to the business)
     * less the suppliers' (owed by it), and the opening stock valued at each item's buy price.
     * <p>
     * The stock is valued at <b>today's</b> buy price: there is no costing method yet
     * ({@code docs/warehouse-plan.md} §9), so it is a valuation and not a cost, and the screen says
     * so on the line - the caveat {@code ValuationReport} already carries.
     */
    public static final String BROUGHT_FORWARD = """
            SELECT (SELECT COALESCE(SUM(t.amount), 0) FROM treasury t) AS treasuries,
                   (SELECT COALESCE(SUM(c.first_balance), 0) FROM custom c) AS customers,
                   (SELECT COALESCE(SUM(s.first_balance), 0) FROM suppliers s) AS suppliers,
                   (SELECT COALESCE(SUM(st.first_balance * i.buy_price), 0)
                    FROM items_stock st JOIN items i ON i.id = st.item_id) AS stock""";
    public static final int BROUGHT_FORWARD_PARAMETERS = 0;

    /**
     * What the business holds and owes today, and the two kinds of recorded movement the profit does
     * not see - the figures {@link EquityReconciliation} sets against the equity. {@code docs/reports-plan.md}
     * §13.2.
     * <p>
     * <b>Each figure is read where it lives.</b> The treasuries from {@code treasury_current_balance},
     * the one definition of a balance; the parties by the balances screen's own expression, per party
     * over the ledger view and then split by its sign; the parties' non-cash movements as the account
     * tables' {@code purchase} column alone - those tables have no {@code discount}, which the ledger view
     * supplies as a zero for their rows, and the first draft read it and failed on MySQL; the stock by the
     * items screen's balance times
     * the buy price, which is the item reports' "stock valuation". {@code CapitalDatabaseAcceptanceTest}
     * holds each to the screen that shows it.
     */
    public static final String RECONCILIATION = """
            SELECT (SELECT COALESCE(SUM(b.balance), 0) FROM treasury_current_balance b) AS treasuries,
                   (SELECT COALESCE(SUM(GREATEST(x.balance, 0)), 0) FROM (%1$s) x) AS customers_owe,
                   (SELECT COALESCE(SUM(GREATEST(-x.balance, 0)), 0) FROM (%1$s) x) AS customers_in_credit,
                   (SELECT COALESCE(SUM(GREATEST(x.balance, 0)), 0) FROM (%2$s) x) AS suppliers_owed,
                   (SELECT COALESCE(SUM(GREATEST(-x.balance, 0)), 0) FROM (%2$s) x) AS suppliers_in_advance,
                   (SELECT COALESCE(SUM(items.buy_price * %3$s), 0)
                    FROM items JOIN %4$s ip ON items.id = ip.item_id) AS stock,
                   (SELECT COALESCE(SUM(a.purchase), 0) FROM %5$s a) AS customers_non_cash,
                   (SELECT COALESCE(SUM(a.purchase), 0) FROM %6$s a) AS suppliers_non_cash,
                   (SELECT COALESCE(SUM(IF(d.deposit_or_expenses = 1, d.amount, -d.amount)), 0)
                    FROM treasury_deposit_expenses d
                    WHERE d.category = 'NORMAL') AS ordinary_cash""".formatted(
            partyBalances(PartyLedgerSpec.CUSTOMER, PartyTableSpec.CUSTOMER),
            partyBalances(PartyLedgerSpec.SUPPLIER, PartyTableSpec.SUPPLIER),
            ItemCatalogSql.BALANCE, ItemCatalogSql.MOVEMENTS,
            PartyLedgerSpec.CUSTOMER.table(), PartyLedgerSpec.SUPPLIER.table());
    public static final int RECONCILIATION_PARAMETERS = 0;

    /** One row per party with its balance: {@code PartyBalanceQuery}'s expression, with no date on it. */
    static String partyBalances(PartyLedgerSpec ledger, PartyTableSpec party) {
        return "SELECT m.%1$s, ROUND(SUM(m.purchase - m.discount - m.paid), 2) AS balance FROM %2$s m JOIN %3$s p ON p.%4$s = m.%1$s GROUP BY m.%1$s"
                .formatted(PartyLedgerSpec.PARTY, ledger.view(), party.table(), PartyTableSpec.KEY);
    }

    private CapitalStatements() {
    }
}
