package com.hamza.account.model.dao;

import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Log4j2
public class StockTransferDao extends AbstractDao<StockTransfer> {

    private final String TABLE_NAME = "stock_transfer";
    private final String ID = "id";
    private final String TRANSFER_DATE = "transfer_date";
    private final String STOCK_FROM = "stock_from";
    private final String STOCK_TO = "stock_to";
    private final String USER_ID = "user_id";

    private final String STATUS = "status";
    private final String CANCELLED_AT = "cancelled_at";
    private final String CANCELLED_BY = "cancelled_by";
    private final String CANCEL_REASON = "cancel_reason";
    private final String REVERSAL_TRANSFER_ID = "reversal_transfer_id";

    private final DaoFactory daoFactory;

    StockTransferDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.connection = connection;
        this.daoFactory = daoFactory;
    }

    @Override
    public List<StockTransfer> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(StockTransfer stockTransfer) throws DaoException {
        validateTransferBeforeInsert(stockTransfer);

        return insertMultiData(() -> {
            int userId = getUserId(stockTransfer);

            String insertTransfer = """
                    INSERT INTO stock_transfer
                    (
                        stock_from,
                        stock_to,
                        transfer_date,
                        status,
                        user_id
                    )
                    VALUES
                    (
                        ?,
                        ?,
                        ?,
                        'POSTED',
                        ?
                    )
                    """;

            executeUpdateWithException(
                    insertTransfer,
                    stockTransfer.getStockFrom().getId(),
                    stockTransfer.getStockTo().getId(),
                    stockTransfer.getDate(),
                    userId
            );

            int transferId = getLastInsertId();

            List<StockTransferListItems> transferListItems = stockTransfer.getTransferListItems();

            for (StockTransferListItems item : transferListItems) {
                item.setStock_transfer_id(transferId);

                int affectedRows = decreaseSourceStock(
                        item.getItem().getId(),
                        stockTransfer.getStockFrom().getId(),
                        item.getQuantity()
                );

                if (affectedRows != 1) {
                    throw new DaoException("الكمية غير كافية للصنف: " + item.getItem().getNameItem());
                }

                increaseTargetStock(
                        item.getItem().getId(),
                        stockTransfer.getStockTo().getId(),
                        item.getQuantity()
                );
            }

            daoFactory.stockTransferListDao().insertList(transferListItems);

            for (StockTransferListItems item : transferListItems) {
                insertStockMovement(
                        item.getItem().getId(),
                        stockTransfer.getStockFrom().getId(),
                        "TRANSFER_OUT",
                        0,
                        item.getQuantity(),
                        transferId,
                        item.getId(),
                        userId,
                        "تحويل صادر إلى مخزن آخر"
                );

                insertStockMovement(
                        item.getItem().getId(),
                        stockTransfer.getStockTo().getId(),
                        "TRANSFER_IN",
                        item.getQuantity(),
                        0,
                        transferId,
                        item.getId(),
                        userId,
                        "تحويل وارد من مخزن آخر"
                );
            }
        });
    }

    @Override
    public int update(StockTransfer stock) throws DaoException {
        throw new DaoException("لا يمكن تعديل تحويل مخزني بعد ترحيله. قم بإلغاء التحويل ثم أنشئ تحويلًا جديدًا.");
    }

    @Override
    public int deleteById(int id) throws DaoException {
        throw new DaoException("لا يمكن حذف تحويل مخزني بعد ترحيله. استخدم إلغاء التحويل بتحويل عكسي.");
    }

    @Override
    public StockTransfer getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Object[] getData(StockTransfer stock) {
        return new Object[]{
                stock.getStockFrom().getId(),
                stock.getStockTo().getId(),
                stock.getDate(),
                getUserId(stock)
        };
    }

    @Override
    public StockTransfer map(ResultSet rs) throws DaoException {
        StockTransfer model = mapBase(rs);
        model.setTransferListItems(daoFactory.stockTransferListDao().loadAllById(model.getId()));
        return model;
    }

    public StockTransfer getDataByIdWithoutList(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::mapWithoutList, id);
    }

    public StockTransfer mapWithoutList(ResultSet rs) throws DaoException {
        return mapBase(rs);
    }

    public int cancelTransfer(int transferId, int userId, String reason) throws DaoException {
        return insertMultiData(() -> {
            StockTransfer originalTransfer = getDataById(transferId);

            if (originalTransfer == null) {
                throw new DaoException("التحويل غير موجود");
            }

            if (!"POSTED".equalsIgnoreCase(originalTransfer.getStatus())) {
                throw new DaoException("لا يمكن إلغاء تحويل غير مرحل أو ملغي مسبقًا");
            }

            int notAvailableItemsCount = getNotAvailableItemsCountForCancel(transferId);

            if (notAvailableItemsCount > 0) {
                throw new DaoException("لا يمكن إلغاء التحويل لأن الكمية غير متاحة في المخزن المستلم");
            }

            String insertReversalTransfer = """
                    INSERT INTO stock_transfer
                    (
                        transfer_date,
                        stock_from,
                        stock_to,
                        status,
                        cancel_reason,
                        user_id
                    )
                    VALUES
                    (
                        CURDATE(),
                        ?,
                        ?,
                        'POSTED',
                        ?,
                        ?
                    )
                    """;

            executeUpdateWithException(
                    insertReversalTransfer,
                    originalTransfer.getStockTo().getId(),
                    originalTransfer.getStockFrom().getId(),
                    "تحويل عكسي لإلغاء التحويل رقم " + transferId,
                    userId
            );

            int reversalTransferId = getLastInsertId();

            List<StockTransferListItems> transferListItems = originalTransfer.getTransferListItems();

            for (StockTransferListItems item : transferListItems) {
                int affectedRows = decreaseSourceStock(
                        item.getItem().getId(),
                        originalTransfer.getStockTo().getId(),
                        item.getQuantity()
                );

                if (affectedRows != 1) {
                    throw new DaoException("الكمية غير كافية لإلغاء الصنف: " + item.getItem().getNameItem());
                }

                increaseTargetStock(
                        item.getItem().getId(),
                        originalTransfer.getStockFrom().getId(),
                        item.getQuantity()
                );
            }

            for (StockTransferListItems item : transferListItems) {
                StockTransferListItems reversalItem = new StockTransferListItems();
                reversalItem.setStock_transfer_id(reversalTransferId);
                reversalItem.setItem(item.getItem());
                reversalItem.setQuantity(item.getQuantity());

                daoFactory.stockTransferListDao().insertList(List.of(reversalItem));
            }

            for (StockTransferListItems item : transferListItems) {
                insertStockMovement(
                        item.getItem().getId(),
                        originalTransfer.getStockTo().getId(),
                        "TRANSFER_OUT",
                        0,
                        item.getQuantity(),
                        reversalTransferId,
                        item.getId(),
                        userId,
                        "تحويل عكسي لإلغاء التحويل رقم " + transferId
                );

                insertStockMovement(
                        item.getItem().getId(),
                        originalTransfer.getStockFrom().getId(),
                        "TRANSFER_IN",
                        item.getQuantity(),
                        0,
                        reversalTransferId,
                        item.getId(),
                        userId,
                        "تحويل عكسي لإلغاء التحويل رقم " + transferId
                );
            }

            String updateOriginalTransfer = """
                    UPDATE stock_transfer
                    SET status = 'CANCELLED',
                        cancelled_at = NOW(),
                        cancelled_by = ?,
                        cancel_reason = ?,
                        reversal_transfer_id = ?
                    WHERE id = ?
                      AND status = 'POSTED'
                    """;

            int affectedRows = executeUpdateWithException(
                    updateOriginalTransfer,
                    userId,
                    reason,
                    reversalTransferId,
                    transferId
            );

            if (affectedRows != 1) {
                throw new DaoException("فشل إلغاء التحويل، ربما تم إلغاؤه مسبقًا");
            }
        });
    }

    private StockTransfer mapBase(ResultSet rs) throws DaoException {
        StockTransfer model = new StockTransfer();

        try {
            model.setId(rs.getInt(ID));
            model.setStockFrom(daoFactory.stockDao().getDataById(rs.getInt(STOCK_FROM)));
            model.setStockTo(daoFactory.stockDao().getDataById(rs.getInt(STOCK_TO)));
            model.setDate(LocalDate.parse(rs.getString(TRANSFER_DATE)));

            try {
                model.setStatus(rs.getString(STATUS));
            } catch (SQLException ignored) {
                model.setStatus("POSTED");
            }

            try {
                var cancelledAt = rs.getTimestamp(CANCELLED_AT);
                if (cancelledAt != null) {
                    model.setCancelledAt(cancelledAt.toLocalDateTime());
                }
            } catch (SQLException ignored) {
                // ignored for old databases
            }

            try {
                int cancelledBy = rs.getInt(CANCELLED_BY);
                if (!rs.wasNull()) {
                    model.setCancelledBy(cancelledBy);
                }
            } catch (SQLException ignored) {
                // ignored for old databases
            }

            try {
                model.setCancelReason(rs.getString(CANCEL_REASON));
            } catch (SQLException ignored) {
                // ignored for old databases
            }

            try {
                int reversalTransferId = rs.getInt(REVERSAL_TRANSFER_ID);
                if (!rs.wasNull()) {
                    model.setReversalTransferId(reversalTransferId);
                }
            } catch (SQLException ignored) {
                // ignored for old databases
            }

        } catch (SQLException e) {
            throw new DaoException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return model;
    }

    private void validateTransferBeforeInsert(StockTransfer stockTransfer) throws DaoException {
        if (stockTransfer == null) {
            throw new DaoException("بيانات التحويل غير صحيحة");
        }

        if (stockTransfer.getStockFrom() == null || stockTransfer.getStockTo() == null) {
            throw new DaoException("يجب اختيار المخزن المصدر والمخزن المستلم");
        }

        if (stockTransfer.getStockFrom().getId() == stockTransfer.getStockTo().getId()) {
            throw new DaoException("لا يمكن التحويل إلى نفس المخزن");
        }

        if (stockTransfer.getDate() == null) {
            throw new DaoException("يجب اختيار تاريخ التحويل");
        }

        if (stockTransfer.getTransferListItems() == null || stockTransfer.getTransferListItems().isEmpty()) {
            throw new DaoException("يجب إضافة أصناف للتحويل");
        }

        for (StockTransferListItems item : stockTransfer.getTransferListItems()) {
            if (item.getItem() == null) {
                throw new DaoException("يوجد صنف غير صحيح داخل التحويل");
            }

            if (item.getQuantity() <= 0) {
                throw new DaoException("كمية الصنف يجب أن تكون أكبر من صفر: " + item.getItem().getNameItem());
            }
        }
    }

    private int getUserId(StockTransfer stockTransfer) {
        try {
            if (stockTransfer.getUsers() != null && stockTransfer.getUsers().getId() > 0) {
                return stockTransfer.getUsers().getId();
            }
        } catch (Exception ignored) {
            // default user id
        }

        return 1;
    }

    private int getLastInsertId() throws DaoException {
        return queryForInt("SELECT LAST_INSERT_ID()");
    }

    private int decreaseSourceStock(int itemId, int stockId, double quantity) throws DaoException, SQLException {
        String sql = """
                UPDATE items_stock
                SET current_quantity = current_quantity - ?
                WHERE item_id = ?
                  AND stock_id = ?
                  AND current_quantity >= ?
                """;

        return executeUpdateWithException(
                sql,
                quantity,
                itemId,
                stockId,
                quantity
        );
    }

    private int increaseTargetStock(int itemId, int stockId, double quantity) throws DaoException, SQLException {
        String sql = """
                INSERT INTO items_stock
                (
                    item_id,
                    stock_id,
                    first_balance,
                    current_quantity
                )
                VALUES
                (
                    ?,
                    ?,
                    0,
                    ?
                )
                ON DUPLICATE KEY UPDATE
                    current_quantity = current_quantity + VALUES(current_quantity)
                """;

        return executeUpdateWithException(
                sql,
                itemId,
                stockId,
                quantity
        );
    }

    private int insertStockMovement(
            int itemId,
            int stockId,
            String movementType,
            double quantityIn,
            double quantityOut,
            int referenceId,
            int referenceLineId,
            int userId,
            String notes
    ) throws DaoException, SQLException {
        String sql = """
                INSERT INTO stock_movements
                (
                    item_id,
                    stock_id,
                    movement_type,
                    quantity_in,
                    quantity_out,
                    reference_type,
                    reference_id,
                    reference_line_id,
                    notes,
                    user_id
                )
                VALUES
                (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    'STOCK_TRANSFER',
                    ?,
                    ?,
                    ?,
                    ?
                )
                """;

        return executeUpdateWithException(
                sql,
                itemId,
                stockId,
                movementType,
                quantityIn,
                quantityOut,
                referenceId,
                referenceLineId,
                notes,
                userId
        );
    }

    private int getNotAvailableItemsCountForCancel(int transferId) throws DaoException {
        String sql = """
                SELECT
                    COUNT(*) AS not_available_items
                FROM stock_transfer st
                         JOIN stock_transfer_list stl
                              ON stl.stock_transfer_id = st.id
                         LEFT JOIN items_stock ist
                                   ON ist.item_id = stl.item_id
                                       AND ist.stock_id = st.stock_to
                WHERE st.id = ?
                  AND st.status = 'POSTED'
                  AND COALESCE(ist.current_quantity, 0) < stl.quantity
                """;

        return queryForInt(sql, transferId);
    }
}