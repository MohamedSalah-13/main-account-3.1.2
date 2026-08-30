package com.hamza.account.features.profitloss;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The profit and loss statement, read from the one definition of a document's profit.
 * <p>
 * The revenue and the cost come from {@code document_profit}, which states the rule
 * once for both sales and their returns and signs a return negative. This class used
 * to spell that rule out itself in four {@code UNION ALL} branches - correctly, as it
 * happens, while {@code total_sales_names_table} and {@code view_yearly_monthly_report}
 * spelled out two different ones and disagreed with it by exactly the discounts. Being
 * the right answer written in the wrong place is what let the other two drift; there is
 * nothing to drift from now.
 * <p>
 * Expenses are still read here, straight from {@code expenses_details}, because they
 * are not a property of any document. The table holding the owner's capital and
 * drawings is deliberately not named anywhere in this file: neither is income or an
 * expense, and {@code ProfitLossExcludesCapitalTest} fails the build on the mere
 * mention of it here - bluntly, on the file's text, which is why this paragraph
 * describes it rather than naming it.
 */
public final class ProfitLossDao extends AbstractDao<ProfitLossRow> {

    private static final String SQL_TEMPLATE = """
            SELECT report_date,SUM(revenue) net_sales,SUM(cost) cost_of_sales,SUM(revenue)-SUM(cost) gross_profit,SUM(expense) expenses,SUM(revenue)-SUM(cost)-SUM(expense) net_profit FROM (
            SELECT document_date report_date,net_revenue revenue,cost_of_sales cost,0 expense FROM document_profit
            UNION ALL SELECT date,0,0,amount FROM expenses_details) entries %s GROUP BY report_date ORDER BY report_date DESC
            """;

    /**
     * The statement over the period, with each bound standing on its own.
     * <p>
     * Both bounds used to be required together - {@code from != null && to != null} -
     * and a single bound was dropped without a word. A user who set "from 1/1/2026"
     * and left "to" empty got the whole history back and read it as the year's
     * profit: the one number the owner acts on, silently answering a different
     * question than the one asked. An open-ended period is a perfectly ordinary
     * request ("everything since the shop reopened"), so it is answered rather than
     * ignored, and only a period with no bounds at all reads the whole ledger.
     * <p>
     * The range itself is checked in {@link ProfitLossService}, where a message the
     * user can read belongs.
     */
    public List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException {
        List<String> conditions = new ArrayList<>(2);
        List<Object> parameters = new ArrayList<>(2);
        if (from != null) {
            conditions.add("report_date >= ?");
            parameters.add(from);
        }
        if (to != null) {
            conditions.add("report_date <= ?");
            parameters.add(to);
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions) + " ";
        return queryForObjects(SQL_TEMPLATE.formatted(where), this::map, parameters.toArray());
    }

    public ProfitLossRow map(ResultSet r) throws DaoException {
        try {
            return new ProfitLossRow(r.getObject("report_date", LocalDate.class),
                    r.getBigDecimal("net_sales"), r.getBigDecimal("cost_of_sales"),
                    r.getBigDecimal("gross_profit"), r.getBigDecimal("expenses"),
                    r.getBigDecimal("net_profit"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
