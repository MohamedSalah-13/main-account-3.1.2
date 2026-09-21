package com.hamza.account.model.dao;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;
import com.hamza.account.features.items.ItemStockBalanceSql;
import com.hamza.account.features.items.WarehouseOpeningBalance;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.GenericMapper;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class ItemsDao extends AbstractDao<ItemsModel> {

    public static final String BARCODE = "barcode";
    public static final String NAME_ITEM = "nameItem";
    private static final int FILTER_ITEMS_LIMIT = 50;
    /**
     * An item answers to three kinds of code, and a search that knows only the
     * first two cannot find a carton by the code printed on it: {@code
     * items.barcode}, the extra codes in {@code item_barcodes}, and the code a
     * unit carries in {@code items_units}.
     */
    private static final String ITEM_UNIT_BARCODE_EXACT =
            "items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?)";
    private static final String ITEM_UNIT_BARCODE_LIKE =
            "items.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?)";
    /**
     * {@code quantity_items_table} is keyed by (item, stock): one row per warehouse an
     * item has ever moved through. Joining it to {@code items} directly - as every query
     * below used to - is only safe while {@link DefaultStock} is the one stock in the
     * database, because then item and (item, stock) are the same row. The moment a
     * second warehouse exists, that join returns one row per warehouse per item, and
     * each row's {@code quantityPurchase}/{@code quantitySales}/... is only that
     * warehouse's share, not the item's total - so a catalog query silently duplicates
     * every item and quietly halves (or worse) the balance {@link #map} computes from
     * it.
     * <p>
     * This mirrors what {@code InventoryDao}'s {@code MOVEMENTS} subquery already does:
     * pre-aggregate by {@code item_id} so there is exactly one row per item regardless
     * of how many stocks it has moved through. Since V18, {@code first_balance} in
     * {@code quantity_items_table} comes from the distinct (item, stock) row in
     * {@code items_stock}, so it must be summed alongside the movements. It is the only
     * opening there is since V78, which dropped the item row's copy of warehouse 1.
     * <p>
     * {@code ANY_VALUE(stock_id)} keeps {@link #STOCK_ID} resolvable for {@link #map}
     * without pinning the aggregate to one stock: with a single warehouse it is that
     * warehouse every time, and once a second one exists {@code itemsModel.getItemStock()}
     * from a catalog-wide query is informational only - the per-stock truth for a
     * specific warehouse is what {@link #findItemByIdAndStockId} and its siblings are
     * for, and they read one (item, stock) row through {@link ItemStockBalanceSql}.
     * <p>
     * The text is {@link ItemCatalogSql#MOVEMENTS}, kept beside the balance expression that
     * reads it, so the item reports join the very row this list does.
     */
    private static final String ITEM_MOVEMENTS_ALL_STOCKS = ItemCatalogSql.MOVEMENTS;
    /*
     * The three searches behind getFilterItems answer ids only, ranked and capped; the rows
     * and their balances are read for those ids afterwards (itemsInOrder). They used to join
     * ITEM_MOVEMENTS_ALL_STOCKS, which builds every item's balance from the whole line
     * history before the LIMIT can apply: about a second per suggestion on a copy with
     * 106,606 sales lines. "IN items_stock" keeps what that inner join kept - an item with
     * no warehouse row was never a match.
     */
    private static final String FILTER_ITEM_IDS_SQL_TEXT_STARTS = """
            SELECT items.id
            FROM items
            WHERE items.id IN (SELECT item_id FROM items_stock)
              AND (items.nameItem LIKE ?
               OR items.barcode LIKE ?
               OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
               OR %s)
            ORDER BY
                CASE
                    WHEN items.barcode = ? THEN 0
                    WHEN items.id = ? THEN 1
                    WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                    WHEN %s THEN 1
                    WHEN items.nameItem LIKE ? THEN 2
                    WHEN items.barcode LIKE ? THEN 3
                    WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?) THEN 3
                    WHEN %s THEN 3
                    ELSE 4
                END,
                items.id DESC
            LIMIT %d
            """.formatted(ITEM_UNIT_BARCODE_LIKE, ITEM_UNIT_BARCODE_EXACT, ITEM_UNIT_BARCODE_LIKE, FILTER_ITEMS_LIMIT);
    private static final String FILTER_ITEM_IDS_SQL_TEXT_CONTAINS = """
            SELECT items.id
            FROM items
            WHERE items.id IN (SELECT item_id FROM items_stock)
              AND (items.nameItem LIKE ?
               OR items.barcode LIKE ?
               OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
               OR %s)
            ORDER BY
                CASE
                    WHEN items.barcode = ? THEN 0
                    WHEN items.id = ? THEN 1
                    WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                    WHEN %s THEN 1
                    ELSE 2
                END,
                items.id DESC
            LIMIT %d
            """.formatted(ITEM_UNIT_BARCODE_LIKE, ITEM_UNIT_BARCODE_EXACT, FILTER_ITEMS_LIMIT);
    private static final String FILTER_ITEM_IDS_SQL_NUMERIC = """
            SELECT items.id
            FROM items
            WHERE items.id IN (SELECT item_id FROM items_stock)
              AND (items.id = ?
               OR items.barcode = ?
               OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
               OR %s)
            ORDER BY
                CASE
                    WHEN items.id = ? THEN 0
                    WHEN items.barcode = ? THEN 1
                    WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                    WHEN %s THEN 1
                    ELSE 2
                END,
                items.id DESC
            LIMIT %d
            """.formatted(ITEM_UNIT_BARCODE_EXACT, ITEM_UNIT_BARCODE_EXACT, FILTER_ITEMS_LIMIT);
    /**
     * What a catalog search matches, and in what order it ranks what it matched.
     * <p>
     * One predicate, not the two phases {@link #getFilterItems} runs: "contains" already
     * covers everything "starts with" covers, and the phases only ever existed to get
     * prefix matches to the top. Here that ranking is in the {@code ORDER BY} instead,
     * which is what lets the result be counted and paged - two phases deduplicated in
     * Java have no page boundary and no total.
     * <p>
     * The extra-barcode and unit-barcode tests are the reason a search cannot be a plain
     * {@code LIKE} on {@code items}: an item answers to codes in three tables.
     */
    private static final String SEARCH_TEXT_WHERE = """
            (items.nameItem LIKE ?
              OR items.barcode LIKE ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?))""";
    private static final String SEARCH_TEXT_ORDER = """
            CASE
                WHEN items.barcode = ? THEN 0
                WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                WHEN items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?) THEN 1
                WHEN items.nameItem LIKE ? THEN 2
                WHEN items.barcode LIKE ? THEN 3
                WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?) THEN 3
                WHEN items.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?) THEN 3
                ELSE 4
            END,
            items.id DESC""";
    /** Digits alone are an id or a barcode, and are matched exactly - never as a fragment of a name. */
    private static final String SEARCH_NUMERIC_WHERE = """
            (items.id = ?
              OR items.barcode = ?
              OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
              OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?))""";
    private static final String SEARCH_NUMERIC_ORDER = """
            CASE
                WHEN items.id = ? THEN 0
                WHEN items.barcode = ? THEN 1
                WHEN items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?) THEN 1
                WHEN items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?) THEN 1
                ELSE 2
            END,
            items.id DESC""";
    private final String ID = "id";
    private final String SUB_NUM = "sub_num";
    private final String BUY_PRICE = "buy_price";
    private final String UNIT_ID = "unit_id";
    private final String MINI_QUANTITY = "mini_quantity";
    private final String ITEM_IMAGE = "item_image";
    private final String TABLE_NAME = "items";
    private final String QUANTITY_PURCHASE = "quantityPurchase";
    private final String QUANTITY_SALES = "quantitySales";
    private final String QUANTITY_PURCHASE_RE = "quantityPurchaseRe";
    private final String QUANTITY_SALES_RE = "quantitySalesRe";
    private final String FROM_STOCK = "fromStock";
    private final String TO_STOCK = "toStock";
    private final String ADJUSTMENT = "adjustment";
    private static final String STOCK_FIRST_BALANCE = "stock_first_balance";
    private final String STOCK_ID = "stock_id";
    private final String selPrice1 = "sel_price1";
    private final String selPrice2 = "sel_price2";
    private final String selPrice3 = "sel_price3";
    private final String itemActive = "item_active";
    private final String itemHasValidity = "item_has_validity";
    private final String numberValidityDays = "number_validity_days";
    private final String alertDaysBeforeExpire = "alert_days_before_expire";

    private final String USER_ID = "user_id";
    private static final String UPDATED_AT = "updated_at";
    /**
     * One item in one warehouse, for the finders that name both. Binds the stock id, then
     * the item id.
     * <p>
     * The movement row is {@link ItemStockBalanceSql} - the view's own columns, computed for
     * this item alone - and not {@code quantity_items_table}. Joining the view built the
     * whole stock history of every item before it could return this one: 0.75 to 1.4 seconds
     * a barcode scan on a copy with 106,606 sales lines, against 3 ms this way, and the
     * invoice line mapper ({@code SalesDao.map}) paid it once per line it loaded.
     */
    private static final String QUERY_ITEM_IN_STOCK = "SELECT items.*, ip.*, ip.first_balance AS stock_first_balance "
            + "FROM items JOIN (" + ItemStockBalanceSql.forItems(1) + ") ip ON items.id = ip.item_id";
    /**
     * Every item a code belongs to: its own barcode, an extra barcode, or a unit's. Binds the
     * code three times. The three unique indexes cannot see each other, so more than one item
     * is possible, and the finder treats that the way it always has - as not found, logged.
     */
    private static final String ITEM_IDS_BY_CODE = "SELECT id FROM items WHERE barcode = ?"
            + " UNION SELECT item_id FROM item_barcodes WHERE barcode = ?"
            + " UNION SELECT items_id FROM items_units WHERE items_barcode = ?";
    private static final String ITEM_IDS_BY_NAME = "SELECT id FROM items WHERE nameItem = ?";
    private static final String LAST_ITEM_IDS = "SELECT id FROM items WHERE id IN (SELECT item_id FROM items_stock)"
            + " ORDER BY id DESC LIMIT 50";
    /** One row per item, aggregated across every warehouse. For every catalog query that names no stock. */
    private final String QUERY_ITEMS_ALL_STOCKS = "SELECT * from items join " + ITEM_MOVEMENTS_ALL_STOCKS + " ip on items.id = ip.item_id ";
    /**
     * The projection used only by the items-list screen.
     * <p>
     * It is intentionally explicit and intentionally omits {@code items.item_image}. A
     * picture is a {@code LONGBLOB}; selecting it for every row moved and allocated all
     * those bytes before the table could show the first page, even though the table only
     * needs the ordinary item fields and stock totals below. The separate picture window
     * reads one blob by id, on demand.
     */
    private static final String CATALOG_COLUMNS = """
            items.id,
            items.barcode,
            items.nameItem,
            items.sub_num,
            items.buy_price,
            items.sel_price1,
            items.sel_price2,
            items.sel_price3,
            items.unit_id,
            items.mini_quantity,
            ip.stock_first_balance,
            items.item_active,
            items.item_has_validity,
            items.number_validity_days,
            items.alert_days_before_expire,
            items.updated_at,
            ip.stock_id,
            ip.quantityPurchase,
            ip.quantitySales,
            ip.quantityPurchaseRe,
            ip.quantitySalesRe,
            ip.fromStock,
            ip.toStock,
            ip.adjustment,
            """ + ItemCatalogSql.UNIT_COUNT + " AS unit_count";
    private static final String QUERY_CATALOG_ITEMS =
            "SELECT " + CATALOG_COLUMNS + " FROM items JOIN " + ITEM_MOVEMENTS_ALL_STOCKS
                    + " ip ON items.id = ip.item_id ";
    private final DaoFactory daoFactory;

    ItemsDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    /**
     * The item, its opening stock row and its extra barcodes go in together or not
     * at all. This used to drive auto-commit by hand and relied on
     * {@code setAutoCommit(true)} to flush the work, with no explicit commit;
     * insertMultiData commits it properly and keeps the three statements on one
     * connection.
     */
    @Override
    public int insert(ItemsModel itemsModel) throws DaoException {
        if (!withConnection(c -> new TrialManager(c).canAddItem())) return 0;

        return insertMultiData(() -> {
            int itemId = insertItem(itemsModel);
            daoFactory.getItemsStockDao().insertForAllStocks(
                    itemId, DefaultStock.ID, itemsModel.getFirstBalanceForStock());

            // A new item's units were dropped on the floor here: only update()
            // ever wrote them, so an item saved with a carton had none until it
            // was opened and saved a second time.
            saveUnits(itemsModel);

            if (!itemsModel.getExtraBarcodes().isEmpty()) {
                daoFactory.getItemBarcodesDao().insertBarcodesForItem(itemId, itemsModel.getExtraBarcodes());
            }
        });
    }

    /**
     * Saves the item.
     * <p>
     * <b>The opening balance is the default warehouse's, and is written only while nothing has
     * moved the item there.</b> It is the one figure with no date on it: that warehouse's balance is
     * {@code first_balance + purchases + ... - sales}, so changing it changes what the shelf held at
     * every moment of its history, and a stock sheet printed and signed last month prints
     * differently today. Once anything has been bought, sold, returned, transferred or counted in
     * that warehouse the opening is a closed entry, and the way to correct the stock is a dated
     * count - {@link WarehouseOpeningBalance}.
     * <p>
     * It lives in {@code items_stock} alone since V78. The item row carried a copy that a trigger
     * pushed into warehouse 1 on <em>every</em> update of the item, so the two could only agree by
     * that trigger firing; the row no longer names it, and this writes the one place it is read.
     * <p>
     * A changed value is refused rather than quietly dropped: the user typed a number and is
     * entitled to know it was not saved.
     */
    @Override
    public int update(ItemsModel itemsModel) throws DaoException {
        boolean writesOpening = openingRule()
                .mayWrite(itemsModel.getId(), DefaultStock.ID, itemsModel.getFirstBalanceForStock());
        Object[] versionedValues = optimisticValues(getData(itemsModel), itemsModel.getUpdated_at());

        return insertMultiData(() -> {
            requireOptimisticUpdate(executeUpdateWithException(
                    optimisticUpdateSql(UPDATE_ITEM), versionedValues));
            if (writesOpening) {
                daoFactory.getItemsStockDao().updateOpeningBalance(
                        itemsModel.getId(), DefaultStock.ID, itemsModel.getFirstBalanceForStock());
            }

            saveUnits(itemsModel);

            // update extra barcodes: delete then re-insert
            daoFactory.getItemBarcodesDao().deleteByItemId(itemsModel.getId());
            if (!itemsModel.getExtraBarcodes().isEmpty()) {
                daoFactory.getItemBarcodesDao().insertBarcodesForItem(itemsModel.getId(), itemsModel.getExtraBarcodes());
            }
            itemsModel.setUpdated_at(readUpdatedAt(itemsModel.getId()));
        });
    }

    /** The item's own columns, in the order {@link #getData} binds them, the id last. */
    private final String UPDATE_ITEM = SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, SUB_NUM,
            BUY_PRICE, selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays,
            alertDaysBeforeExpire, UNIT_ID, MINI_QUANTITY, ITEM_IMAGE, USER_ID);

    /**
     * Whether the item's opening balance - the default warehouse's, the one the item screen shows -
     * is closed to editing. The item screen asks so it can grey the field; the rule itself is
     * applied in {@link #update}.
     */
    public boolean isOpeningBalanceLocked(int itemId) throws DaoException {
        return openingRule().isLocked(itemId, DefaultStock.ID);
    }

    private WarehouseOpeningBalance openingRule() {
        return new WarehouseOpeningBalance(daoFactory.warehouseStockDao());
    }

    /**
     * Replaces the item's rows in {@code items_units}.
     * <p>
     * The base unit is skipped: it is {@code items.unit_id}, and the row for it
     * in the loaded list is the one {@link #getItemsModel} synthesizes, so
     * writing it back would store the duplicate that {@code V5} removed.
     * <p>
     * Everything else is deleted first, including when the list is empty -
     * removing an item's last extra unit has to remove the row, not keep it.
     */
    private void saveUnits(ItemsModel itemsModel) throws DaoException {
        daoFactory.getItemsUnitDao().deleteByItemId(itemsModel.getId());

        int baseUnitId = itemsModel.getUnitsType() == null ? 0 : itemsModel.getUnitsType().getUnit_id();
        var extraUnits = itemsModel.getItemsUnitsModelList().stream()
                .filter(unit -> unit.getUnitsModel() != null && unit.getUnitsModel().getUnit_id() != baseUnitId)
                .peek(unit -> unit.setItemsId(itemsModel.getId()))
                .toList();

        if (!extraUnits.isEmpty()) {
            daoFactory.getItemsUnitDao().insertList(extraUnits);
        }
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String query = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(query, id);
    }

    @Override
    public ItemsModel getDataById(int id) throws DaoException {
        return findItemById(id);
    }

    @Override
    public ItemsModel getDataByString(String s) throws DaoException {
        List<Integer> ids = itemIds(ITEM_IDS_BY_NAME, s);
        return ids.size() == 1 ? findItemById(ids.getFirst()) : null;
    }

    @Override
    public Object[] getData(ItemsModel itemsModel) {
        return new Object[]{itemsModel.getBarcode(), itemsModel.getNameItem()
                , itemsModel.getSubGroups().getId(), itemsModel.getBuyPrice()
                , itemsModel.getSelPrice1(), itemsModel.getSelPrice2(), itemsModel.getSelPrice3()
                , itemsModel.isActiveItem(), itemsModel.isHasValidate(), itemsModel.getNumberValidityDays()
                , itemsModel.getAlertDaysBeforeExpiry()
                , itemsModel.getUnitsType().getUnit_id()
                , itemsModel.getMini_quantity()
                , itemsModel.getItem_image() != null ? itemsModel.getItem_image() : new byte[0]
                , itemsModel.getUsers().getId()
                , itemsModel.getId()};

    }

    @Override
    public ItemsModel map(ResultSet rs) throws DaoException {
        try {
            var itemsModel = getItemsModel(rs);
            itemsModel.setItemStock(daoFactory.stockDao().getDataById(rs.getInt(STOCK_ID)));
            applyBalances(itemsModel, rs);
            return itemsModel;
        } catch (SQLException e) {
            throw new DaoException(e);
        }

    }

    /**
     * The movement columns and the balance they add up to. Shared by {@link #map} and
     * {@link #mapCatalogRow} so a list and a finder can never disagree about what an
     * item's stock is - the two mappers differ in what they load, never in what they
     * compute.
     */
    private void applyBalances(ItemsModel itemsModel, ResultSet rs) throws SQLException {
        double purchase = rs.getDouble(QUANTITY_PURCHASE);
        double sales = rs.getDouble(QUANTITY_SALES);
        double purRe = rs.getDouble(QUANTITY_PURCHASE_RE);
        double saleRe = rs.getDouble(QUANTITY_SALES_RE);
        double fromStock = rs.getDouble(FROM_STOCK);
        double toStock = rs.getDouble(TO_STOCK);
        // What posted stock counts corrected the balance by, signed. Added by V8;
        // it belongs in the balance everywhere the balance is worked out, or a
        // counted item would read one way on the inventory sheet and another on
        // every invoice screen.
        double adjustment = rs.getDouble(ADJUSTMENT);

        itemsModel.setSumPurchase(purchase);
        itemsModel.setSumSales(sales);
        itemsModel.setSumPurchaseRe(purRe);
        itemsModel.setSumSalesRe(saleRe);
        itemsModel.setFromStock(fromStock);
        itemsModel.setToStock(toStock);
        double sumAllBalance = (itemsModel.getFirstBalanceForStock() + purchase + saleRe + toStock + adjustment) - (sales + purRe + fromStock);
        itemsModel.setSumAllBalance(sumAllBalance);
        itemsModel.setSumAllBalanceByBuyPrice(roundToTwoDecimalPlaces(itemsModel.getBuyPrice() * sumAllBalance));
        itemsModel.setSumAllBalanceBySelPrice(roundToTwoDecimalPlaces(itemsModel.getSelPrice1() * sumAllBalance));
    }

    /** The bulk update, writing neither the picture nor an opening balance. See {@link #updateBulk}. */
    @Override
    public int updateList(List<ItemsModel> list) throws DaoException {
        return updateBulk(list, false, java.util.Set.of());
    }

    /**
     * The save behind the "edit several items" screen.
     * <p>
     * <b>It names only the columns that screen can change</b> - group, buy price, first sell
     * price, active, minimum quantity - and the picture only when {@code writesImage} says the
     * screen was asked to change it. The rows it is handed are the items list's own, mapped for
     * display, and a list row carries no picture: this used to write every column from the row,
     * so raising the prices of a batch wrote an empty picture over every item in it. Naming the
     * columns is the same rule {@link #quickUpdate} and {@link #updateImage} already follow.
     * <p>
     * <b>The opening balance is written only for the ids in {@code writesOpeningFor}</b>, and this
     * method does not decide which those are: the service does, for the whole batch and before
     * anything is written, through {@link WarehouseOpeningBalance} ({@code BulkOpeningBalance}) - the
     * rule {@link #update} applies to one item. For those ids it goes where {@link #update} puts it,
     * the default warehouse's {@code items_stock} row. It used to be left out altogether while the
     * screen reported the save as done.
     */
    public int updateBulk(List<ItemsModel> list, boolean writesImage, java.util.Set<Integer> writesOpeningFor)
            throws DaoException {
        if (list.isEmpty()) return 0;

        // Execute one compare-and-swap at a time inside a single transaction. A JDBC
        // batch can legally return SUCCESS_NO_INFO, which cannot distinguish a real
        // update from a stale zero-row update. Here any stale item aborts and rolls back
        // the whole selection instead of partially applying a bulk edit.
        insertMultiData(() -> {
            for (ItemsModel model : list) {
                boolean writesOpening = writesOpeningFor.contains(model.getId());
                String sql = bulkUpdateSql(writesImage);
                Object[] values = optimisticValues(bulkUpdateValues(model, writesImage), model.getUpdated_at());
                requireOptimisticUpdate(executeUpdateWithException(sql, values));
                if (writesOpening) {
                    daoFactory.getItemsStockDao().updateOpeningBalance(
                            model.getId(), DefaultStock.ID, model.getFirstBalanceForStock());
                }
                model.setUpdated_at(readUpdatedAt(model.getId()));
            }
        });
        return list.size();
    }

    /** The statement {@link #updateBulk} runs, with its columns in the order {@link #bulkUpdateValues} binds them. */
    String bulkUpdateSql(boolean writesImage) {
        List<String> columns = new ArrayList<>(List.of(SUB_NUM, BUY_PRICE, selPrice1, itemActive, MINI_QUANTITY));
        if (writesImage) columns.add(ITEM_IMAGE);
        columns.add(USER_ID);
        return optimisticUpdateSql(SqlStatements.updateStatement(TABLE_NAME, ID, columns.toArray(String[]::new)));
    }

    Object[] bulkUpdateValues(ItemsModel model, boolean writesImage) {
        List<Object> values = new ArrayList<>(List.of(model.getSubGroups().getId(), model.getBuyPrice(),
                model.getSelPrice1(), model.isActiveItem(), model.getMini_quantity()));
        if (writesImage) {
            values.add(model.getItem_image() != null ? model.getItem_image() : new byte[0]);
        }
        values.add(model.getUsers().getId());
        values.add(model.getId());
        return values.toArray();
    }

    private int insertItem(ItemsModel itemsModel) throws DaoException {
        Object[] objects = {itemsModel.getBarcode(), itemsModel.getNameItem()
                , itemsModel.getSubGroups().getId(), itemsModel.getBuyPrice()
                , itemsModel.getSelPrice1(), itemsModel.getSelPrice2(), itemsModel.getSelPrice3()
                , itemsModel.isActiveItem(), itemsModel.isHasValidate(), itemsModel.getNumberValidityDays()
                , itemsModel.getAlertDaysBeforeExpiry()
                , itemsModel.getUnitsType().getUnit_id()
                , itemsModel.getMini_quantity()
                , itemsModel.getItem_image() != null ? itemsModel.getItem_image() : new byte[0]
                , itemsModel.getUsers().getId()};
        String INSERT_ITEM = SqlStatements.insertStatement(TABLE_NAME, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays, alertDaysBeforeExpire
                , UNIT_ID, MINI_QUANTITY, ITEM_IMAGE, USER_ID);

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 1; i < objects.length + 1; i++) {
                    statement.setObject(i, objects[i - 1]);
                }

                int affectedRows = statement.executeUpdate();
                if (affectedRows == 0) {
                    throw new DaoException("لم يتم إضافة الصنف");
                }

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int generatedId = generatedKeys.getInt(1);
                        itemsModel.setId(generatedId);
                        return generatedId;
                    }
                    throw new DaoException("لم يتم الحصول على رقم الصنف الجديد");
                }

            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    @NotNull
    private ItemsModel getItemsModel(ResultSet rs) throws SQLException, DaoException {
        ItemsModel itemsModel = new ItemsModel();
        double firstBalanceForStock = rs.getDouble(STOCK_FIRST_BALANCE);
        int unitId = rs.getInt(UNIT_ID);
        Blob blob = rs.getBlob(ITEM_IMAGE);
        int id = rs.getInt(ID);
        itemsModel.setId(id);
        itemsModel.setBarcode(rs.getString(BARCODE));
        itemsModel.setNameItem(rs.getString(NAME_ITEM));
        itemsModel.setMini_quantity(rs.getDouble(MINI_QUANTITY));
        itemsModel.setFirstBalanceForStock(firstBalanceForStock);
        itemsModel.setBuyPrice(rs.getDouble(BUY_PRICE));
        itemsModel.setSelPrice1(rs.getDouble(selPrice1));
        itemsModel.setSelPrice2(rs.getDouble(selPrice2));
        itemsModel.setSelPrice3(rs.getDouble(selPrice3));
        itemsModel.setActiveItem(rs.getBoolean(itemActive));
        itemsModel.setHasValidate(rs.getBoolean(itemHasValidity));
        itemsModel.setNumberValidityDays(rs.getInt(numberValidityDays));
        itemsModel.setAlertDaysBeforeExpiry(rs.getInt(alertDaysBeforeExpire));
        itemsModel.setUpdated_at(rs.getObject(UPDATED_AT, LocalDateTime.class));

        if (blob != null) {
            itemsModel.setItem_image(blob.getBytes(1, (int) blob.length()));
        }

        itemsModel.setSubGroups(daoFactory.getSupGroupsDao().getDataById(rs.getInt(SUB_NUM)));
        var dataById = daoFactory.unitsDao().getDataById(unitId);
        itemsModel.setUnitsType(dataById);

        // second add all another units
        var allUnitsByItemId = daoFactory.getItemsUnitDao().getAllUnitsByItemId(id);
        var e = new ItemsUnitsModel();
        e.setUnitsModel(dataById);
        e.setQuantityForUnit(1.0);
        e.setItemsId(itemsModel.getId());
        e.setItemsBarcode(itemsModel.getBarcode());
        allUnitsByItemId.addFirst(e);
        // add units to list
        itemsModel.setItemsUnitsModelList(allUnitsByItemId);
        // load additional barcodes for this item
        itemsModel.setExtraBarcodes(daoFactory.getItemBarcodesDao().getBarcodesByItemId(id));
        return itemsModel;
    }

    public ItemsModel findItemById(Integer itemId) throws DaoException {
        return queryForObject(queryItemsAcrossStocks(1), this::map, itemId);
    }

    /**
     * {@link #QUERY_ITEMS_ALL_STOCKS} for named items: the same columns, every warehouse
     * folded in by the same aggregate, computed for these ids alone. Binds each id.
     */
    private static String queryItemsAcrossStocks(int itemCount) {
        return "SELECT * FROM items JOIN " + ItemStockBalanceSql.acrossStocksForItems(itemCount)
                + " ip ON items.id = ip.item_id";
    }

    /** The items {@code ids} names, loaded whole, in the order {@code ids} gives them. */
    private List<ItemsModel> itemsInOrder(List<Integer> ids, GenericMapper<ItemsModel> mapper) throws DaoException {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, ItemsModel> byId = new LinkedHashMap<>();
        for (ItemsModel item : queryForObjects(queryItemsAcrossStocks(ids.size()), mapper, ids.toArray())) {
            byId.put(item.getId(), item);
        }
        List<ItemsModel> ordered = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            ItemsModel item = byId.get(id);
            if (item != null) {
                ordered.add(item);
            }
        }
        return ordered;
    }

    public ItemsModel findItemByIdAndStockId(Integer itemId, Integer stockId) throws DaoException {
        return queryForObject(QUERY_ITEM_IN_STOCK, this::map, stockId, itemId);
    }

    public ItemsModel findItemByStockIdAndName(String itemName, Integer stockId) throws DaoException {
        return findOneInStock(itemIds(ITEM_IDS_BY_NAME, itemName), stockId, ITEM_IDS_BY_NAME);
    }

    /**
     * All three places a code can live, so scanning the code on a carton finds
     * the item it belongs to. Which unit was scanned is answered from the item's
     * own list by {@code ItemUnits.unitByBarcode} - it is already loaded.
     * <p>
     * Two steps, both on an index: which item the code names, then that item's row.
     * Asking both at once put the code's {@code OR} beside the balance, and the balance
     * then had to be built for every item before the code could pick one.
     */
    public ItemsModel findItemByStockIdAndBarcode(String barcode, Integer stockId) throws DaoException {
        return findOneInStock(itemIds(ITEM_IDS_BY_CODE, barcode, barcode, barcode), stockId, ITEM_IDS_BY_CODE);
    }

    private ItemsModel findOneInStock(List<Integer> ids, Integer stockId, String lookup) throws DaoException {
        if (ids.isEmpty()) {
            return null;
        }
        if (ids.size() > 1) {
            // What queryForObject did when the joined query matched several items: a
            // code that belongs to two of them is a data problem, not a choice to make.
            log.warn("Expected at most one item but {} matched, returning none: {}", ids.size(), lookup);
            return null;
        }
        return findItemByIdAndStockId(ids.getFirst(), stockId);
    }

    private List<Integer> itemIds(String query, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                List<Integer> ids = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getInt(1));
                    }
                }
                return ids;
            }
        });
    }

    /**
     * The first of {@code codes} that already belongs to a different item,
     * ignoring {@code exceptItemId} (the item being edited), or {@code null} if
     * none of them do.
     * <p>
     * One query for the whole candidate list rather than one per code - an item
     * with a handful of extra barcodes and a few priced units used to mean a
     * full round trip to the database for every single one of them just to find
     * out none clashed. The three tables each have their own unique index and
     * none of them can see the others, so nothing stops the same code being an
     * item's barcode here and a carton's there - and then a scan is a coin toss.
     */
    public String firstBarcodeTakenByAnotherItem(List<String> codes, int exceptItemId) throws DaoException {
        if (codes.isEmpty()) return null;

        return withConnection(connection -> {
            try (PreparedStatement statement = takenBarcodesStatement(connection, codes, exceptItemId, true);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    /**
     * Which of {@code codes} already belong to a different item - the same question
     * {@link #firstBarcodeTakenByAnotherItem} asks, answered for all of them at once.
     * <p>
     * It exists for the code generator on the item screen, which offers a new item a
     * free number and has to find one: asking per candidate meant a query per number
     * tried, and the numbers it starts from are exactly the ones a shop that types its
     * own short codes has already used. Answering the whole batch turns a walk of a
     * thousand round trips into one.
     */
    public Set<String> takenBarcodesAmong(List<String> codes, int exceptItemId) throws DaoException {
        if (codes.isEmpty()) return Set.of();

        return withConnection(connection -> {
            try (PreparedStatement statement = takenBarcodesStatement(connection, codes, exceptItemId, false);
                 ResultSet rs = statement.executeQuery()) {
                Set<String> taken = new HashSet<>();
                while (rs.next()) {
                    taken.add(rs.getString(1));
                }
                return taken;
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    /**
     * The one statement behind both questions above. The three tables each have their
     * own unique index and none of them can see the others, so nothing stops the same
     * code being an item's barcode here and a carton's there - and then a scan is a
     * coin toss. Written once so the two callers cannot come to disagree about which
     * tables a barcode can hide in.
     * <p>
     * Every branch is wrapped in {@code CONVERT(... USING utf8mb4)} because MySQL
     * refuses a UNION of two columns whose collations differ, and these three have
     * been found to differ in the field: no migration names a charset, so a table
     * created by a migration takes the database default while a table restored from
     * a dump keeps the charset written in the dump - which resolves to the server's
     * default collation, a different one. A customer on 2026-09-16 could neither
     * save an item nor be offered a free code, with error 1271 in the log and
     * nothing wrong with the data. V63 converges the columns; this keeps the
     * statement answerable on a database that has not run it, or that diverges
     * again. It costs nothing: the conversion is in the select list, so the
     * {@code IN} still reads each table's own barcode index.
     */
    private PreparedStatement takenBarcodesStatement(Connection connection, List<String> codes,
                                                     int exceptItemId, boolean firstOnly) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(takenBarcodesSql(codes.size(), firstOnly));
        int index = 1;
        for (int i = 0; i < 3; i++) {
            statement.setInt(index++, exceptItemId);
            for (String code : codes) {
                statement.setString(index++, code);
            }
        }
        return statement;
    }

    /** The text of {@link #takenBarcodesStatement}, separated so a test can read it. */
    static String takenBarcodesSql(int codeCount, boolean firstOnly) {
        String placeholders = String.join(",", Collections.nCopies(codeCount, "?"));

        return """
                SELECT code FROM (
                    SELECT CONVERT(barcode USING utf8mb4) AS code FROM items WHERE id <> ? AND barcode IN (%1$s)
                    UNION ALL
                    SELECT CONVERT(barcode USING utf8mb4) AS code FROM item_barcodes WHERE item_id <> ? AND barcode IN (%1$s)
                    UNION ALL
                    SELECT CONVERT(items_barcode USING utf8mb4) AS code FROM items_units WHERE items_id <> ? AND items_barcode IN (%1$s)
                ) AS matches
                """.formatted(placeholders) + (firstOnly ? "LIMIT 1" : "");
    }

    /**
     * The name of the item that already answers to {@code code} - as its own
     * barcode, one of its extra codes, or the code on one of its units - or
     * {@code null} if the code is free. {@code exceptItemId} is the item being
     * edited, so its own codes do not count against it.
     * <p>
     * Same three tables as {@link #firstBarcodeTakenByAnotherItem}, asked for one
     * code instead of a list: this one answers a field the user is still typing
     * in, and a refusal there has to say which item is holding the code, not just
     * that something is.
     */
    public String itemNameHoldingBarcode(String code, int exceptItemId) throws DaoException {
        if (code == null || code.isBlank()) return null;

        String query = """
                SELECT items.nameItem FROM items
                WHERE items.id <> ?
                  AND (items.barcode = ?
                       OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
                       OR items.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?))
                LIMIT 1
                """;

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, exceptItemId);
                statement.setString(2, code);
                statement.setString(3, code);
                statement.setString(4, code);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    public int maxItemId() {
        try {
            return withConnection(connection -> {
                try (CallableStatement cs = connection.prepareCall("{CALL max_item_id(?)}")) {
                    cs.executeUpdate();
                    return cs.getInt(1);
                }
            });
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
        }
        return 0;
    }

    public List<ItemsModel> getItemsByMainGroupId(int mainGroupId) throws DaoException {
        String query = QUERY_ITEMS_ALL_STOCKS + " where items.sub_num in (select id from sub_group where main_id = ?)";
        return queryForObjects(query, this::map, mainGroupId);
    }

    public List<ItemsModel> getFilterItems(String searchText) throws DaoException {
        return getFilterItems(searchText, this::map);
    }

    private List<ItemsModel> getFilterItems(String searchText, GenericMapper<ItemsModel> mapper) throws DaoException {
        if (searchText == null) {
            return getLast50Items(mapper);
        }

        String q = searchText.trim();
        if (q.isEmpty()) {
            return getLast50Items(mapper);
        }

        boolean numericOnly = q.matches("\\d+");

        // 1) لو أرقام فقط: بحث سريع ودقيق (id/barcode =)
        if (numericOnly) {
            int id;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ex) {
                // باركود طويل جداً => اعتبره باركود فقط
                id = -1;
            }
            return itemsInOrder(itemIds(FILTER_ITEM_IDS_SQL_NUMERIC, id, q, q, q, id, q, q, q), mapper);
        }

        // 2) نص/مختلط: مرحلتين startsWith ثم contains
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        // LinkedHashSet يحافظ على الترتيب + يمنع التكرار
        Set<Integer> ids = new LinkedHashSet<>(FILTER_ITEMS_LIMIT);

        // Phase A: startsWith (سريع + يستفيد من index)
        addUpToLimit(ids, itemIds(
                FILTER_ITEM_IDS_SQL_TEXT_STARTS,
                likeStarts, likeStarts, likeStarts, likeStarts, // WHERE
                q, 0, q, q,                                      // ORDER BY (barcode exact, id exact disabled, extra barcode exact, unit barcode exact)
                likeStarts, likeStarts, likeStarts, likeStarts   // ORDER BY (name, barcode, extra barcode, unit barcode - all starts)
        ));

        // Phase B: contains (%text%) فقط إذا لسه محتاجين نتائج
        if (ids.size() < FILTER_ITEMS_LIMIT) {
            addUpToLimit(ids, itemIds(
                    FILTER_ITEM_IDS_SQL_TEXT_CONTAINS,
                    likeContains, likeContains, likeContains, likeContains, // WHERE (contains)
                    q, 0, q, q                                               // ORDER BY (barcode exact, id exact disabled, extra barcode exact, unit barcode exact)
            ));
        }

        return itemsInOrder(new ArrayList<>(ids), mapper);
    }

    private static void addUpToLimit(Set<Integer> target, List<Integer> source) {
        for (Integer id : source) {
            if (target.size() >= FILTER_ITEMS_LIMIT) return;
            target.add(id);
        }
    }

    public List<ItemsModel> getLast50Items() throws DaoException {
        return getLast50Items(this::map);
    }

    private List<ItemsModel> getLast50Items(GenericMapper<ItemsModel> mapper) throws DaoException {
        return itemsInOrder(itemIds(LAST_ITEM_IDS), mapper);
    }

    // ---------------------------------------------------------------------------
    // The catalog reads: the same rows as the four methods above, mapped for a list.
    //
    // What separates them is {@link #mapCatalogRow} - see its javadoc for why a list
    // must not be built out of {@link #map}. Every screen showing many items at once
    // belongs on this side; {@link #map} stays for the finders, which load one item and
    // need all of it.
    // ---------------------------------------------------------------------------

    /**
     * One page of the catalog - searched or not, filtered or not - mapped without a query
     * per row.
     * <p>
     * A search is paged exactly like a plain listing, which is the point: it used to be
     * capped at {@link #FILTER_ITEMS_LIMIT} rows with nothing on screen to say so, and
     * the group filter was applied in Java to whatever those rows happened to be - so a
     * group's items beyond the first fifty matches could not be reached at all, and the
     * table could look empty while matches existed.
     * <p>
     * What may narrow the page is {@link ItemCatalogFilter}, and what turns it into SQL is
     * {@link ItemCatalogSql} - neither of which this class knows the meaning of. That is
     * the seam: a new filter is a field on the record and a case in the builder, tested
     * without a database, and this method does not change.
     */
    public List<ItemsModel> getCatalogProducts(ItemCatalogFilter filter, int rowsPerPage, int offset)
            throws DaoException {
        ItemCatalogSql.Statement query = ItemCatalogSql.build(filter);
        List<Object> parameters = new ArrayList<>(query.whereParameters());
        parameters.addAll(query.orderParameters());
        parameters.add(rowsPerPage);
        parameters.add(offset);
        return queryForObjects(QUERY_CATALOG_ITEMS + query.where() + " ORDER BY " + query.order() + " LIMIT ? OFFSET ?",
                catalogMapper(), parameters.toArray());
    }

    /**
     * Every row {@link #getCatalogProducts} would page through for this filter, in the same
     * order, with no page boundary - what a print of the list or a bulk edit of "everything
     * this filter matches" has to cover. Same {@code WHERE}, so it is the set the count names.
     */
    public List<ItemsModel> getCatalogExtract(ItemCatalogFilter filter) throws DaoException {
        ItemCatalogSql.Statement query = ItemCatalogSql.build(filter);
        List<Object> parameters = new ArrayList<>(query.whereParameters());
        parameters.addAll(query.orderParameters());
        return queryForObjects(QUERY_CATALOG_ITEMS + query.where() + " ORDER BY " + query.order(),
                catalogMapper(), parameters.toArray());
    }

    /**
     * How many rows {@link #getCatalogProducts} would return in total.
     * <p>
     * Over {@code items} alone wherever it can be: the movement aggregate the page query
     * joins is a {@code GROUP BY} over every row of {@code quantity_items_table}, and
     * counting matches does not need a single balance out of it. A balance condition is
     * the one thing that changes that, and {@link ItemCatalogSql#requiresMovementJoin} is
     * what says so - counting over a narrower {@code FROM} than the page reads is a
     * pagination control that promises pages the query cannot fill.
     */
    public int getCatalogCount(ItemCatalogFilter filter) throws DaoException {
        ItemCatalogSql.Statement query = ItemCatalogSql.build(filter);
        String from = ItemCatalogSql.requiresMovementJoin(filter)
                ? "SELECT COUNT(*) FROM items JOIN " + ITEM_MOVEMENTS_ALL_STOCKS + " ip ON items.id = ip.item_id"
                : "SELECT COUNT(*) FROM items";
        return countCatalog(from + query.where(), query.whereParameters());
    }

    /**
     * The {@code WHERE} and {@code ORDER BY} shared by a catalog page and its count, so
     * the two can never disagree about which rows there are.
     * <p>
     * Retained as the shape {@code ItemsCatalogQueryTest} pins; the rules themselves moved
     * to {@link ItemCatalogSql}, where they can be read without a DAO around them.
     *
     * @param where            begins with {@code " WHERE "}, or is empty when nothing filters
     * @param whereParameters  bound before {@link #orderParameters()}, which the statement order requires
     */
    record CatalogQuery(String where, List<Object> whereParameters,
                        String order, List<Object> orderParameters) {
    }

    static CatalogQuery catalogQuery(String searchText, Integer mainGroupId, Integer subGroupId) {
        ItemCatalogSql.Statement statement = ItemCatalogSql.build(
                ItemCatalogFilter.EMPTY.withSearch(searchText).withGroup(mainGroupId, subGroupId));
        return new CatalogQuery(statement.where(), statement.whereParameters(),
                statement.order(), statement.orderParameters());
    }

    /** Visible to the SQL contract test without exposing it as part of the DAO API. */
    static String catalogSelectQuery() {
        return QUERY_CATALOG_ITEMS;
    }

    private int countCatalog(String sql, List<Object> parameters) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    /** The grouped read-only catalogue deliberately has no page boundary. */
    public List<ItemsModel> getAllCatalogProducts() throws DaoException {
        return queryForObjects(QUERY_CATALOG_ITEMS.concat(" ORDER BY items.id DESC"), catalogMapper());
    }

    /**
     * One item, mapped the way the rows around it in the list were mapped.
     * <p>
     * This is what a screen asks for after saving a single item: re-reading the one row
     * that changed costs a handful of queries, where re-reading its page costs a page.
     */
    public ItemsModel getCatalogItem(int id) throws DaoException {
        return queryForObject(QUERY_CATALOG_ITEMS.concat(" where items.id = ? "), catalogMapper(), id);
    }

    private GenericMapper<ItemsModel> catalogMapper() throws DaoException {
        ItemsCatalogLookups lookups = ItemsCatalogLookups.load(daoFactory);
        return resultSet -> mapCatalogRow(resultSet, lookups);
    }

    /**
     * An item row for a screen that shows many of them: the columns of {@code items},
     * the balance worked out exactly as {@link #map} works it out, and the three names a
     * list displays - sub group, base unit, warehouse - taken from {@code lookups}
     * rather than from a query of their own.
     * <p>
     * What it deliberately leaves empty is the item's picture, extra units and extra
     * barcodes. None is shown directly in the list. The blob is read by
     * {@link #getItemImage(int)} only after the operator presses the row's Show button;
     * omitting the other two is what keeps a page from costing several hundred queries.
     * <p>
     * <b>A model this produced is for display, and must not be handed to
     * {@link #update}.</b> That method replaces an item's units and extra barcodes with
     * whatever the model carries, so saving a catalog row through it would delete both.
     * A list screen edits through {@link #quickUpdate} or {@link #updateImage}, which
     * name their columns and touch nothing else; a screen that means to edit the whole
     * item loads it again through {@link #findItemById}.
     */
    private ItemsModel mapCatalogRow(ResultSet rs, ItemsCatalogLookups lookups) throws DaoException {
        try {
            ItemsModel itemsModel = new ItemsModel();
            itemsModel.setId(rs.getInt(ID));
            itemsModel.setBarcode(rs.getString(BARCODE));
            itemsModel.setNameItem(rs.getString(NAME_ITEM));
            itemsModel.setMini_quantity(rs.getDouble(MINI_QUANTITY));
            itemsModel.setFirstBalanceForStock(rs.getDouble(STOCK_FIRST_BALANCE));
            itemsModel.setBuyPrice(rs.getDouble(BUY_PRICE));
            itemsModel.setSelPrice1(rs.getDouble(selPrice1));
            itemsModel.setSelPrice2(rs.getDouble(selPrice2));
            itemsModel.setSelPrice3(rs.getDouble(selPrice3));
            itemsModel.setActiveItem(rs.getBoolean(itemActive));
            itemsModel.setHasValidate(rs.getBoolean(itemHasValidity));
            itemsModel.setNumberValidityDays(rs.getInt(numberValidityDays));
            itemsModel.setAlertDaysBeforeExpiry(rs.getInt(alertDaysBeforeExpire));
            itemsModel.setUpdated_at(rs.getObject(UPDATED_AT, LocalDateTime.class));
            itemsModel.setUnitCount(rs.getInt("unit_count"));

            itemsModel.setSubGroups(lookups.subGroup(rs.getInt(SUB_NUM)));
            itemsModel.setUnitsType(lookups.unit(rs.getInt(UNIT_ID)));
            itemsModel.setItemStock(lookups.stock(rs.getInt(STOCK_ID)));
            itemsModel.setItemsUnitsModelList(new ArrayList<>());
            itemsModel.setExtraBarcodes(new ArrayList<>());

            applyBalances(itemsModel, rs);
            return itemsModel;
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    /** Updates only the fields exposed by the items-table quick-edit mode. */
    /**
     * Writes one item's picture and nothing else.
     * <p>
     * The picture is editable from the items list, where the row was mapped for display
     * and carries neither the item's units nor its extra barcodes. Saving it through
     * {@link #update} - which is what the list used to do - replaces both from the model,
     * so setting a picture deleted every unit the item had. This is the same edit with
     * the columns named.
     */
    public int updateImage(int itemId, byte[] image) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, ITEM_IMAGE),
                image == null ? new byte[0] : image, itemId);
    }

    /** Reads one picture only when the separate picture window asks for it. */
    public byte[] getItemImage(int itemId) throws DaoException {
        return withConnection(connection -> {
            String sql = "SELECT item_image FROM items WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, itemId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) return new byte[0];
                    byte[] image = resultSet.getBytes(1);
                    return image == null ? new byte[0] : image;
                }
            }
        });
    }

    public int quickUpdate(ItemsModel item) throws DaoException {
        String sql = optimisticUpdateSql(SqlStatements.updateStatement(
                TABLE_NAME, ID, BARCODE, NAME_ITEM, BUY_PRICE,
                selPrice1, selPrice2, selPrice3, USER_ID));
        Object[] values = optimisticValues(new Object[]{
                item.getBarcode(), item.getNameItem(), item.getBuyPrice(),
                item.getSelPrice1(), item.getSelPrice2(), item.getSelPrice3(),
                item.getUsers().getId(), item.getId()
        }, item.getUpdated_at());
        int affected = executeUpdate(sql, values);
        requireOptimisticUpdate(affected);
        item.setUpdated_at(readUpdatedAt(item.getId()));
        return affected;
    }

    /** Adds the version bump and compare-and-swap predicate to a normal update statement. */
    static String optimisticUpdateSql(String updateSql) {
        return updateSql.replaceFirst(" SET ",
                " SET updated_at=CURRENT_TIMESTAMP(6), ") + " AND updated_at = ?";
    }

    /** Appends the version read with the model to the update's existing parameters. */
    static Object[] optimisticValues(Object[] values, LocalDateTime expectedVersion)
            throws DaoException {
        if (expectedVersion == null) {
            throw concurrentItemChange();
        }
        Object[] result = java.util.Arrays.copyOf(values, values.length + 1);
        result[values.length] = Timestamp.valueOf(expectedVersion);
        return result;
    }

    private static void requireOptimisticUpdate(int affected) throws DaoException {
        if (affected != 1) {
            throw concurrentItemChange();
        }
    }

    private LocalDateTime readUpdatedAt(int itemId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT updated_at FROM items WHERE id = ?")) {
                statement.setInt(1, itemId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw concurrentItemChange();
                    }
                    return result.getObject(1, LocalDateTime.class);
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    private static BusinessRuleException concurrentItemChange() {
        return new BusinessRuleException(LanguageManager.getInstance()
                .getString("item.error.concurrent.update"));
    }

}
