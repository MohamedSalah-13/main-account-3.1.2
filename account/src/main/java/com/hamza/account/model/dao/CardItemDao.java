package com.hamza.account.model.dao;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.model.domain.CardItems;
import com.hamza.account.type.ProcessType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.dateTime.DateUtils;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final String TYPE_VALUE = "type_value";
    private final String QUANTITY = "quantity";
    private final String BASE_QUANTITY = "base_quantity";
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

    /**
     * The lines of one item's card, already narrowed to the period and the warehouse.
     * <p>
     * The screen used to read every line the item had ever been on and then filter the
     * dates in a {@code FilteredList}, so opening the card of a fast moving item pulled
     * its whole history across the wire to show one month of it.
     * <p>
     * The order is the one the running balance is computed in - by document date, then
     * by the moment the document was entered, then by the line's own id - so two
     * documents dated the same day still stack up in the order they were written.
     */
    static String cardRowsSql(boolean byProcessType) {
        return "SELECT * FROM card_item_view_details"
                + " WHERE item_num = ? AND stock_id = ? AND invoice_date BETWEEN ? AND ?"
                + (byProcessType ? " AND table_name = ?" : "")
                + " ORDER BY invoice_date, date_insert, id";
    }

    /**
     * What the item's balance was on {@code date}, in base units.
     * <p>
     * The same three terms {@code quantity_items_table} adds up, and deliberately so:
     * the opening balance, every invoice line in base units, and what posted stock
     * counts corrected the balance by. A count is a movement like any other - leaving
     * it out would give the card a closing balance that reconciles with nothing after
     * the first inventory.
     * <p>
     * It reads the four line tables directly rather than {@code card_item_view}: the
     * view unions them and joins three more tables per row for names this query never
     * looks at, and the item predicate would only be applied after all of it.
     *
     * @param inclusive whether {@code date} itself counts - the closing balance of a
     *                  period includes its last day, the opening balance does not
     */
    static String balanceSql(boolean inclusive) {
        String comparison = inclusive ? "<=" : "<";
        return """
                SELECT i.first_balance
                     + COALESCE((SELECT SUM(base_quantity) FROM (
                           SELECT p.quantity * p.type_value AS base_quantity
                           FROM purchase p
                           JOIN total_buy h ON h.invoice_number = p.invoice_number
                           WHERE h.stock_id = ? AND p.num = ? AND h.invoice_date %s ?
                           UNION ALL
                           SELECT r.quantity * r.type_value
                           FROM sales_re r
                           JOIN total_sales_re h ON h.id = r.invoice_number
                           WHERE h.stock_id = ? AND r.item_id = ? AND h.invoice_date %s ?
                           UNION ALL
                           SELECT -(s.quantity * s.type_value)
                           FROM sales s
                           JOIN total_sales h ON h.invoice_number = s.invoice_number
                           WHERE h.stock_id = ? AND s.num = ? AND h.invoice_date %s ?
                           UNION ALL
                           SELECT -(r.quantity * r.type_value)
                           FROM purchase_re r
                           JOIN total_buy_re h ON h.id = r.invoice_number
                           WHERE h.stock_id = ? AND r.item_id = ? AND h.invoice_date %s ?
                       ) movements), 0)
                     + COALESCE((SELECT SUM(l.counted_qty * l.type_value - l.system_qty)
                                 FROM stock_count_lines l
                                 JOIN stock_count c ON c.id = l.count_id
                                 WHERE c.status = 'POSTED' AND c.stock_id = ? AND l.item_id = ?
                                   AND c.count_date %s ?), 0) AS balance
                FROM items i
                WHERE i.id = ?
                """.formatted(comparison, comparison, comparison, comparison, comparison);
    }

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
     * One item's card for a period, optionally narrowed to a single kind of document.
     *
     * @param processType the only kind of document to return, or null for all four
     */
    public List<CardItems> cardRows(int itemId, LocalDate from, LocalDate to, ProcessType processType) throws DaoException {
        String tableName = tableNameOf(processType);
        if (tableName == null) {
            return queryForObjects(cardRowsSql(false), this::map,
                    itemId, DefaultStock.ID, Date.valueOf(from), Date.valueOf(to));
        }
        return queryForObjects(cardRowsSql(true), this::map,
                itemId, DefaultStock.ID, Date.valueOf(from), Date.valueOf(to), tableName);
    }

    /** @see #balanceSql(boolean) */
    public double balanceOn(int itemId, LocalDate date, boolean inclusive) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(balanceSql(inclusive))) {
                Date on = Date.valueOf(date);
                int parameter = 1;
                for (int branch = 0; branch < 5; branch++) {
                    statement.setInt(parameter++, DefaultStock.ID);
                    statement.setInt(parameter++, itemId);
                    statement.setDate(parameter++, on);
                }
                statement.setInt(parameter, itemId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getDouble("balance") : 0.0;
                }
            }
        });
    }

    /**
     * The date of the item's first movement, or null if it has never moved.
     * <p>
     * The card opens on the item's whole history and needs the date the history starts
     * on. It used to find it by loading every line of the item and taking the smallest
     * date in Java, which is the one query the screen could least afford.
     */
    public LocalDate firstMovementDate(int itemId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(firstMovementSql())) {
                int parameter = 1;
                for (int branch = 0; branch < 4; branch++) {
                    statement.setInt(parameter++, DefaultStock.ID);
                    statement.setInt(parameter++, itemId);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) return null;
                    Date first = resultSet.getDate("first_date");
                    return first == null ? null : first.toLocalDate();
                }
            }
        });
    }

    static String firstMovementSql() {
        return """
                SELECT MIN(invoice_date) AS first_date FROM (
                    SELECT MIN(h.invoice_date) AS invoice_date
                    FROM purchase p
                    JOIN total_buy h ON h.invoice_number = p.invoice_number
                    WHERE h.stock_id = ? AND p.num = ?
                    UNION ALL
                    SELECT MIN(h.invoice_date)
                    FROM sales_re r
                    JOIN total_sales_re h ON h.id = r.invoice_number
                    WHERE h.stock_id = ? AND r.item_id = ?
                    UNION ALL
                    SELECT MIN(h.invoice_date)
                    FROM sales s
                    JOIN total_sales h ON h.invoice_number = s.invoice_number
                    WHERE h.stock_id = ? AND s.num = ?
                    UNION ALL
                    SELECT MIN(h.invoice_date)
                    FROM purchase_re r
                    JOIN total_buy_re h ON h.id = r.invoice_number
                    WHERE h.stock_id = ? AND r.item_id = ?
                ) firsts
                """;
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

    /** The view's {@code table_name} for a kind of document, or null for "all four". */
    public static String tableNameOf(ProcessType processType) {
        if (processType == null) return null;
        return switch (processType) {
            case PURCHASE -> "purchase";
            case SALES -> "sales";
            case PURCHASE_RETURN -> "purchase_re";
            case SALES_RETURN -> "sales_re";
        };
    }

    private static ProcessType processTypeOf(String tableName) {
        if (tableName == null) return null;
        return switch (tableName) {
            case "purchase" -> ProcessType.PURCHASE;
            case "sales" -> ProcessType.SALES;
            case "purchase_re" -> ProcessType.PURCHASE_RETURN;
            case "sales_re" -> ProcessType.SALES_RETURN;
            default -> null;
        };
    }

    @Override
    public CardItems map(ResultSet rs) throws DaoException {
        CardItems cardItems;
        try {
            double quantity = rs.getDouble(QUANTITY);
            double price = rs.getDouble(PRICE);
            double discount = rs.getDouble(DISCOUNT);
            String tableName = rs.getString(TABLE_NAME);

            cardItems = new CardItems();
            cardItems.setId(rs.getInt(ID));
            cardItems.setInvoice_num(rs.getInt(INVOICE_NUMBER));
            cardItems.setNumItem(rs.getInt(NUM));
            cardItems.setQuantity(quantity);
            cardItems.setPrice(price);
            cardItems.setBuyPrice(rs.getDouble(BUY_PRICE));
            cardItems.setProfit(rs.getDouble(PROFIT));
            cardItems.setDiscount(discount);
            cardItems.setTotals(quantity * price - discount);
            cardItems.setCreated_at(insertedAt(rs));
            cardItems.setNameItem(rs.getString(NAME));
            cardItems.setType_name(rs.getString(TYPE));
            cardItems.setTypeCode(rs.getInt(TYPE_ID));
            cardItems.setTypeValue(rs.getDouble(TYPE_VALUE));
            cardItems.setBaseQuantity(rs.getDouble(BASE_QUANTITY));
            cardItems.setBarcode(rs.getString(BARCODE));

            cardItems.setInvoice_date(rs.getDate(INVOICE_DATE).toLocalDate());
            cardItems.setName_account(rs.getString(NAME_CUSTOM));
            cardItems.setTable_name(tableName);
            cardItems.setDelegate_id(rs.getInt(DELEGATE_ID));
            cardItems.setDelegate_name(rs.getString(DELEGATE_NAME));

            var expirationDate = rs.getString("expiration_date");
            if (expirationDate != null)
                cardItems.setEndDate(LocalDate.parse(expirationDate, DateUtils.DATE_FORMATTER));

            ProcessType processType = processTypeOf(tableName);
            cardItems.setProcessType(processType);
            // The screen's own column, in the user's language: table_name is 'sales_re'
            // and the like, which is what the queries compare against and not something
            // to put in front of an Arabic-speaking user.
            cardItems.setProcessTypeName(processType == null ? tableName : processType.getType());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return cardItems;
    }

    /**
     * A document with no {@code date_insert} sorts as the oldest rather than failing
     * the whole card - the column is nullable on documents imported before it existed,
     * and parsing its string form used to throw a NullPointerException on them.
     */
    private LocalDateTime insertedAt(ResultSet rs) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(DATE);
        return timestamp == null ? LocalDateTime.MIN : timestamp.toLocalDateTime();
    }
}
