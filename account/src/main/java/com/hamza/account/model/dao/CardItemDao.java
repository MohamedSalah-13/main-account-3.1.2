package com.hamza.account.model.dao;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.model.domain.CardItems;
import com.hamza.account.type.ProcessType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.dateTime.DateUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CardItemDao extends AbstractDao<CardItems> {

    // this name with name report card_item
    private final String TABLE_VIEW = "card_item_view_details";
    private final String TABLE_NAME = "table_name";
    //    private final String ARABIC_NAME = "arabic_name";
    private final String INVOICE_NUMBER = "invoice_number";
    private final String ID = "id";
    private final String NUM = "item_num";
    private final String TYPE = "unit_name";
    private final String BARCODE = "barcode";
    private final String TYPE_ID = "unit_type";
    private final String QUANTITY = "quantity";
    private final String PRICE = "price";
    private final String BUY_PRICE = "buy_price";
    private final String PROFIT = "profit";
    private final String DISCOUNT = "discount";
    private final String NAME = "nameItem";
    private final String DATE = "date_insert";
    private final String INVOICE_DATE = "invoice_date";
    private final String NAME_CUSTOM = "name_custom";
    private final String DELEGATE_ID = "delegate_id";
    private final String DELEGATE_NAME = "delegate_name";

    public CardItemDao() {
        super();
    }

    @Override
    public List<CardItems> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<CardItems> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, NUM), this::map, id);
    }

    /**
     * Remaining stock per expiry date, expressed in the item's base unit.
     * The factor stored on each historical line is used deliberately: changing
     * an item's unit factor later must not rewrite what an old carton meant.
     */
    public Map<LocalDate, Double> expiryBalancesByItem(int itemId) throws DaoException {
        return withConnection(connection -> {
            Map<LocalDate, Double> balances = new LinkedHashMap<>();
            try (var statement = connection.prepareStatement(expiryBalanceSql())) {
                int parameter = 1;
                for (int branch = 0; branch < 4; branch++) {
                    statement.setInt(parameter++, DefaultStock.ID);
                    statement.setInt(parameter++, itemId);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        balances.put(
                                resultSet.getDate("expiration_date").toLocalDate(),
                                resultSet.getDouble("available_quantity"));
                    }
                }
            }
            return balances;
        });
    }

    String expiryBalanceSql() {
        return """
                SELECT expiration_date, SUM(base_quantity) AS available_quantity
                FROM (
                    SELECT p.expiration_date, p.quantity * p.type_value AS base_quantity
                    FROM purchase p
                    JOIN total_buy h ON h.invoice_number = p.invoice_number
                    WHERE h.stock_id = ? AND p.num = ? AND p.expiration_date IS NOT NULL
                    UNION ALL
                    SELECT r.expiration_date, r.quantity * r.type_value AS base_quantity
                    FROM sales_re r
                    JOIN total_sales_re h ON h.id = r.invoice_number
                    WHERE h.stock_id = ? AND r.item_id = ? AND r.expiration_date IS NOT NULL
                    UNION ALL
                    SELECT s.expiration_date, -(s.quantity * s.type_value) AS base_quantity
                    FROM sales s
                    JOIN total_sales h ON h.invoice_number = s.invoice_number
                    WHERE h.stock_id = ? AND s.num = ? AND s.expiration_date IS NOT NULL
                    UNION ALL
                    SELECT r.expiration_date, -(r.quantity * r.type_value) AS base_quantity
                    FROM purchase_re r
                    JOIN total_buy_re h ON h.id = r.invoice_number
                    WHERE h.stock_id = ? AND r.item_id = ? AND r.expiration_date IS NOT NULL
                ) expiry_movements
                GROUP BY expiration_date
                HAVING SUM(base_quantity) > 0
                ORDER BY expiration_date
                """;
    }

    @Override
    public CardItems map(ResultSet rs) throws DaoException {
        CardItems cardItems;
        try {
            int inv_num = rs.getInt(INVOICE_NUMBER);
            int numItem = rs.getInt(NUM);
            String type = rs.getString(TYPE);
            double aDoubleQuantity = rs.getDouble(QUANTITY);
            double aDoublePrice = rs.getDouble(PRICE);

            cardItems = new CardItems();
            cardItems.setId(rs.getInt(ID));
            cardItems.setInvoice_num(inv_num);
            cardItems.setNumItem(numItem);
            cardItems.setQuantity(aDoubleQuantity);
            cardItems.setPrice(aDoublePrice);
            cardItems.setBuyPrice(rs.getDouble(BUY_PRICE));
            cardItems.setProfit(rs.getDouble(PROFIT));
            cardItems.setDiscount(rs.getDouble(DISCOUNT));
            cardItems.setTotals(rs.getDouble(QUANTITY) * rs.getDouble(PRICE) - rs.getDouble(DISCOUNT));
            cardItems.setCreated_at(LocalDateTime.parse(rs.getString(DATE), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            cardItems.setNameItem(rs.getString(NAME));
            cardItems.setType_name(type);
            cardItems.setTypeCode(rs.getInt(TYPE_ID));
            cardItems.setBarcode(rs.getString(BARCODE));

            cardItems.setInvoice_date(LocalDate.parse(rs.getString(INVOICE_DATE), DateUtils.DATE_FORMATTER));
            cardItems.setName_account(rs.getString(NAME_CUSTOM));
            cardItems.setTable_name(rs.getString(TABLE_NAME));
            cardItems.setDelegate_id(rs.getInt(DELEGATE_ID));
            cardItems.setDelegate_name(rs.getString(DELEGATE_NAME));

            var expirationDate = rs.getString("expiration_date");
            if (expirationDate != null)
                cardItems.setEndDate(LocalDate.parse(expirationDate, DateUtils.DATE_FORMATTER));

            String string = rs.getString(TABLE_NAME);
            ProcessType processType = switch (string) {
                case "purchase" -> ProcessType.PURCHASE;
                case "sales" -> ProcessType.SALES;
                case "purchase_re" -> ProcessType.PURCHASE_RETURN;
                case "sales_re" -> ProcessType.SALES_RETURN;
                default -> null;
            };

            cardItems.setProcessType(processType);


        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return cardItems;
    }
}
