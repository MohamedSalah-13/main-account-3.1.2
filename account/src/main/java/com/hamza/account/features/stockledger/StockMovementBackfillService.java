package com.hamza.account.features.stockledger;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.NumberUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the {@code stock_movements} rows for documents written before the dual write
 * went live (see {@code docs/erp-roadmap.md} §8.5) - everything the ledger missed by
 * simply not existing yet when they were saved.
 * <p>
 * Reads raw SQL rows rather than loading domain models: {@link DocumentTableSpec}'s
 * {@code lineView()} is the exact same view {@code quantity_items_table}'s own CTEs
 * aggregate from, so building a movement straight from its columns guarantees the two
 * sides start from the same facts. This is why {@link StockMovementAssembler} (built for
 * {@code BasePurchasesAndSales} - a loaded JavaFX line, one invoice at a time) is not
 * reused here: backfill runs over every historical line at once, and loading each as a
 * full domain object would mean the N+1 queries that shape carries.
 * <p>
 * Every call clears and regenerates <em>all</em> movements of the type it is backfilling
 * ({@link StockMovementDao#deleteAllByReferenceType}), so running this more than once -
 * for instance after fixing whatever {@link StockLedgerReconciliationReport} found - is
 * safe: idle where nothing changed, corrected where something did. It does not open its
 * own transaction; whoever calls it controls commit and rollback, exactly like every
 * other {@code AbstractDao} write.
 */
public class StockMovementBackfillService extends AbstractDao<Void> {

    private final StockMovementDao movementDao;

    public StockMovementBackfillService(StockMovementDao movementDao) {
        this.movementDao = movementDao;
    }

    /** @return how many movements were (re)written, across the four document types and the stock count. */
    public int backfillAll() throws DaoException {
        int total = 0;
        for (DocumentType type : DocumentType.values()) {
            total += backfillDocumentType(type);
        }
        total += backfillStockCounts();
        return total;
    }

    private int backfillDocumentType(DocumentType type) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        MovementType movementType = DocumentMovementType.of(type);
        String referenceType = movementType.name();
        boolean stockIn = type.stockDirection() == DocumentType.Direction.IN;

        String sql = """
                SELECT lv.invoice_number AS reference_id,
                       lv.%1$s          AS item_id,
                       lv.stock_id,
                       lv.invoice_date,
                       lv.type          AS unit_id,
                       lv.type_value,
                       lv.quantity,
                       t.user_id
                FROM %2$s lv
                         JOIN %3$s t ON t.%4$s = lv.invoice_number
                """.formatted(spec.lineItem(), spec.lineView(), spec.table(), spec.key());

        List<StockMovement> movements = withConnection(connection -> {
            List<StockMovement> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    double quantity = resultSet.getDouble("quantity");
                    rows.add(new StockMovement(
                            resultSet.getInt("item_id"), resultSet.getInt("stock_id"),
                            resultSet.getObject("invoice_date", LocalDate.class), movementType,
                            stockIn ? quantity : 0, stockIn ? 0 : quantity,
                            resultSet.getInt("unit_id"), resultSet.getDouble("type_value"),
                            referenceType, resultSet.getLong("reference_id"),
                            (Integer) resultSet.getObject("user_id")));
                }
            }
            return rows;
        });

        movementDao.deleteAllByReferenceType(referenceType);
        movementDao.insertBatch(movements);
        return movements.size();
    }

    private int backfillStockCounts() throws DaoException {
        String sql = """
                SELECT sc.id AS count_id, sc.stock_id, sc.count_date, sc.user_id,
                       scl.item_id, scl.unit_id, scl.type_value, scl.system_qty, scl.counted_qty
                FROM stock_count_lines scl
                         JOIN stock_count sc ON sc.id = scl.count_id
                WHERE sc.status = 'POSTED'
                """;

        List<StockMovement> movements = withConnection(connection -> {
            List<StockMovement> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    double typeValue = resultSet.getDouble("type_value");
                    double difference = NumberUtils.roundToTwoDecimalPlaces(
                            resultSet.getDouble("counted_qty") * typeValue - resultSet.getDouble("system_qty"));
                    if (difference == 0) {
                        continue;
                    }
                    boolean stockIn = difference > 0;
                    double quantity = Math.abs(difference) / typeValue;
                    rows.add(new StockMovement(
                            resultSet.getInt("item_id"), resultSet.getInt("stock_id"),
                            resultSet.getObject("count_date", LocalDate.class),
                            stockIn ? MovementType.INVENTORY_ADJUST_IN : MovementType.INVENTORY_ADJUST_OUT,
                            stockIn ? quantity : 0, stockIn ? 0 : quantity,
                            resultSet.getInt("unit_id"), typeValue,
                            "INVENTORY", resultSet.getLong("count_id"),
                            (Integer) resultSet.getObject("user_id")));
                }
            }
            return rows;
        });

        movementDao.deleteAllByReferenceType("INVENTORY");
        movementDao.insertBatch(movements);
        return movements.size();
    }
}
