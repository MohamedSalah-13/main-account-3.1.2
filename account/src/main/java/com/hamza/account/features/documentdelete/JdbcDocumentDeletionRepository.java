package com.hamza.account.features.documentdelete;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.JdbcInvoiceStockRepository;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.returns.ReturnLinkGuard;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftDocumentDeletionJournal;
import com.hamza.account.features.treasury.WalletFeeService;
import com.hamza.account.features.treasury.WalletFeeSource;
import com.hamza.account.features.stockledger.StockMovementAssembler;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;

import com.hamza.controlsfx.database.ConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link DocumentDeletionRepository} over the four document tables. */
final class JdbcDocumentDeletionRepository implements DocumentDeletionRepository {

    private final DaoFactory daoFactory;

    JdbcDocumentDeletionRepository(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @Override
    public void requirePeriodOpen(DocumentType type, List<Integer> ids) throws DaoException {
        PeriodLock.require(type.periodLock(), ids);
    }

    @Override
    public void requireNoReturns(DocumentType type, List<Integer> ids) throws DaoException {
        // A return has nothing returned against it, and the guard answers for that itself.
        ReturnLinkGuard.requireNoReturns(type, array(ids));
    }

    @Override
    public List<DocumentDeleteStockCheck.StockLine> stockLinesOf(
            DocumentType type, List<Integer> ids) throws DaoException {
        if (ids.isEmpty()) {
            return List.of();
        }
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        String placeholders = "?" + ",?".repeat(ids.size() - 1);
        // Per item AND warehouse: one document names one warehouse, but a batch of them can name
        // several, and an item's balance is per warehouse.
        String sql = "SELECT l." + spec.lineItem() + " AS item_id, i.nameItem AS item_name,"
                + " d.stock_id AS stock_id, s.stock_name AS stock_name,"
                + " SUM(l.quantity * l.type_value) AS removed"
                + " FROM " + spec.lineTable() + " l"
                + " JOIN " + spec.table() + " d ON d." + spec.key() + " = l."
                + DocumentTableSpec.LINE_DOCUMENT
                + " LEFT JOIN items i ON i.id = l." + spec.lineItem()
                + " LEFT JOIN stocks s ON s.stock_id = d.stock_id"
                + " WHERE d." + spec.key() + " IN (" + placeholders + ")"
                + " GROUP BY l." + spec.lineItem() + ", i.nameItem, d.stock_id, s.stock_name";

        record Removed(int itemId, String itemName, int stockId, String stockName, double base) {
        }
        List<Removed> removed = new ArrayList<>();
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < ids.size(); index++) {
                    statement.setInt(index + 1, ids.get(index));
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        removed.add(new Removed(rows.getInt("item_id"), rows.getString("item_name"),
                                rows.getInt("stock_id"), rows.getString("stock_name"),
                                rows.getDouble("removed")));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not read what deleting these documents would take off the shelf", e);
        } finally {
            ConnectionManager.release(connection);
        }
        if (removed.isEmpty()) {
            return List.of();
        }

        // The balance is read through the one definition there is of it, rather than a second
        // one written here - the mistake the treasury and the party ledger each paid for once.
        JdbcInvoiceStockRepository balances = new JdbcInvoiceStockRepository();
        Map<Integer, List<Removed>> byStock = new LinkedHashMap<>();
        removed.forEach(row -> byStock.computeIfAbsent(row.stockId(), key -> new ArrayList<>()).add(row));
        List<DocumentDeleteStockCheck.StockLine> lines = new ArrayList<>();
        for (Map.Entry<Integer, List<Removed>> entry : byStock.entrySet()) {
            List<Integer> itemIds = entry.getValue().stream().map(Removed::itemId).distinct().toList();
            Map<Integer, Double> current = balances.currentBaseBalances(entry.getKey(), itemIds);
            for (Removed row : entry.getValue()) {
                lines.add(new DocumentDeleteStockCheck.StockLine(
                        row.itemName() == null ? "#" + row.itemId() : row.itemName(),
                        row.stockName() == null ? "#" + row.stockId() : row.stockName(),
                        current.getOrDefault(row.itemId(), 0.0), row.base()));
            }
        }
        return List.copyOf(lines);
    }

    @Override
    public int deleteDocuments(DocumentType type, List<Integer> ids, String correctionReason)
            throws DaoException {
        Integer[] numbers = array(ids);
        // Read before anything goes: the reversal is of what the document held in cash.
        var journal = new ShiftDocumentDeletionJournal(daoFactory).capture(type, numbers);
        daoFactory.stockMovementDao().deleteByReferences(
                StockMovementAssembler.referenceTypeFor(type), numbers);
        int rows = deleteHeaders(type, numbers);
        journal.appendReversals(rows, correctionReason);
        removeWalletFees(type, numbers, rows, correctionReason);
        if (rows > 0) {
            ChangeAnnouncer.jdbc().announce(new InvoiceSaved(type.side()));
        }
        return rows;
    }

    /**
     * A document's wallet fee was paid for that document and goes with it (V67). Asked of each
     * number rather than assumed from the count: a delete that took fewer rows than it was handed
     * must not take the fee of a document that is still there.
     */
    private void removeWalletFees(DocumentType type, Integer[] numbers, int deletedRows,
                                  String correctionReason) throws DaoException {
        if (deletedRows <= 0) {
            return;
        }
        var fees = new WalletFeeService();
        var reader = new JdbcShiftCashEffectReader();
        for (Integer number : numbers) {
            if (deletedRows == numbers.length || reader.document(type, number) == null) {
                fees.removeFor(WalletFeeSource.document(type, number), correctionReason);
            }
        }
    }

    private int deleteHeaders(DocumentType type, Integer[] numbers) throws DaoException {
        return switch (type) {
            case SALES -> daoFactory.totalsSalesDao().deleteInvoicesInRange(numbers);
            case SALES_RETURN -> daoFactory.totalsSalesReturnDao().deleteInvoicesInRange(numbers);
            case PURCHASE -> daoFactory.totalsPurchaseDao().deleteInvoicesInRange(numbers);
            case PURCHASE_RETURN -> daoFactory.totalsBuyReturnDao().deleteInvoicesInRange(numbers);
        };
    }

    private static Integer[] array(List<Integer> ids) {
        return ids.toArray(Integer[]::new);
    }
}
