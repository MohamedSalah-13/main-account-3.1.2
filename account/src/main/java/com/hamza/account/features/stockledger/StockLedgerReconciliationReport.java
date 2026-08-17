package com.hamza.account.features.stockledger;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers the question §8.5 of {@code docs/erp-roadmap.md} calls the gate for the whole
 * phase: does {@code stock_movements} agree with {@code quantity_items_table} for every
 * item in every warehouse, not just a scripted scenario?
 * <p>
 * One query, not a Java-side comparison of two loaded lists: {@code quantity_items_table}
 * already carries {@code first_balance} (read once from {@code items}, per its own long
 * comment on why), so the ledger side only has to add the sum of its own movements to
 * that same number rather than re-deriving the opening balance a second way.
 * <p>
 * A tolerance of {@value #TOLERANCE} rather than exact equality: both sides are decimal
 * sums built from different row sets, and floating-point drift here would report a
 * mismatch that is not one.
 */
public final class StockLedgerReconciliationReport extends AbstractDao<Void> {

    private static final double TOLERANCE = 0.001;

    public record Mismatch(int itemId, int stockId, double viewBalance, double ledgerBalance) {
        public double difference() {
            return viewBalance - ledgerBalance;
        }
    }

    public List<Mismatch> run() throws DaoException {
        String sql = """
                SELECT v.item_id,
                       v.stock_id,
                       v.first_balance + v.quantityPurchase + v.quantitySalesRe + v.toStock + v.adjustment
                           - v.quantitySales - v.quantityPurchaseRe - v.fromStock AS view_balance,
                       v.first_balance + COALESCE(m.qty_in, 0) - COALESCE(m.qty_out, 0) AS ledger_balance
                FROM quantity_items_table v
                         LEFT JOIN (SELECT item_id, stock_id,
                                           SUM(quantity_in) AS qty_in, SUM(quantity_out) AS qty_out
                                    FROM stock_movements
                                    GROUP BY item_id, stock_id) m
                                   ON m.item_id = v.item_id AND m.stock_id = v.stock_id
                HAVING ABS(view_balance - ledger_balance) > ?
                """;
        return withConnection(connection -> {
            List<Mismatch> mismatches = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDouble(1, TOLERANCE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        mismatches.add(new Mismatch(
                                rows.getInt("item_id"), rows.getInt("stock_id"),
                                rows.getDouble("view_balance"), rows.getDouble("ledger_balance")));
                    }
                }
            }
            return mismatches;
        });
    }
}
