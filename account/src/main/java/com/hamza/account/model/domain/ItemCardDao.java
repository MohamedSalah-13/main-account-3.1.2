package com.hamza.account.model.domain;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ItemCardDao extends AbstractDao<ItemCardModel> {

    public ItemCardDao(Connection connection) {
        super(connection);
    }

    /**
     * Load item card movements with filters.
     * @param itemId      item ID (required)
     * @param stockId     stock ID (optional, null = all stocks)
     * @param startDate   start date (optional)
     * @param endDate     end date (optional)
     * @return list of movements sorted by date and datetime
     */
    public List<ItemCardModel> getItemMovements(Integer itemId, Integer stockId,
                                                LocalDate startDate, LocalDate endDate) throws DaoException {
        StringBuilder sql = new StringBuilder(
                "SELECT item_id, barcode, nameItem, stock_id, stock_name, unit_name, " +
                        "movement_date, movement_type_ar, quantity_in, quantity_out, running_balance, " +
                        "invoice_number, party_name, price, notes, user_name " +
                        "FROM v_item_movements_details WHERE item_id = ?"
        );
        if (stockId != null && stockId > 0) {
            sql.append(" AND stock_id = ?");
        }
        if (startDate != null) {
            sql.append(" AND movement_date >= ?");
        }
        if (endDate != null) {
            sql.append(" AND movement_date <= ?");
        }
        sql.append(" ORDER BY movement_date ASC, movement_datetime ASC");

        // Build parameters
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(itemId);
        if (stockId != null && stockId > 0) params.add(stockId);
        if (startDate != null) params.add(Date.valueOf(startDate));
        if (endDate != null) params.add(Date.valueOf(endDate));

        return queryForObjects(sql.toString(), this::map, params.toArray());
    }

    @Override
    public ItemCardModel map(ResultSet rs) throws DaoException {
        try {
            ItemCardModel model = new ItemCardModel();
            model.setItemId(rs.getInt("item_id"));
            model.setBarcode(rs.getString("barcode"));
            model.setItemName(rs.getString("nameItem"));
            model.setStockName(rs.getString("stock_name"));
            model.setUnitName(rs.getString("unit_name"));
            Date date = rs.getDate("movement_date");
            model.setMovementDate(date != null ? date.toLocalDate() : null);
            model.setMovementTypeAr(rs.getString("movement_type_ar"));
            model.setQuantityIn(rs.getDouble("quantity_in"));
            model.setQuantityOut(rs.getDouble("quantity_out"));
            model.setRunningBalance(rs.getDouble("running_balance"));
            model.setInvoiceNumber(rs.getLong("invoice_number"));
            model.setPartyName(rs.getString("party_name"));
            model.setPrice(rs.getDouble("price"));
            model.setNotes(rs.getString("notes"));
            model.setUserName(rs.getString("user_name"));
            return model;
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
