package com.hamza.account.model.dao;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.Items_Stock_Model;
import com.hamza.account.opening.OpeningBalanceGuard;
import com.hamza.account.opening.OpeningBalanceRegistry;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.GenericMapper;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * of how many stocks it has moved through. {@code first_balance} is deliberately
     * left out of the aggregate - {@code items.first_balance} is already read
     * unambiguously from the outer join, and summing the view's copy of it here would
     * reintroduce the same one-row-per-stock double count on the opening balance that
     * {@code mini_quantity_view} had.
     * <p>
     * {@code ANY_VALUE(stock_id)} keeps {@link #STOCK_ID} resolvable for {@link #map}
     * without pinning the aggregate to one stock: with a single warehouse it is that
     * warehouse every time, and once a second one exists {@code itemsModel.getItemStock()}
     * from a catalog-wide query is informational only - the per-stock truth for a
     * specific warehouse is what {@link #findItemByIdAndStockId} and its siblings are
     * for, and they still join the raw (unaggregated) view scoped to one {@code stock_id}.
     */
    private static final String ITEM_MOVEMENTS_ALL_STOCKS = """
            (SELECT item_id,
                    ANY_VALUE(stock_id)     AS stock_id,
                    SUM(quantityPurchase)   AS quantityPurchase,
                    SUM(quantitySales)      AS quantitySales,
                    SUM(quantityPurchaseRe) AS quantityPurchaseRe,
                    SUM(quantitySalesRe)    AS quantitySalesRe,
                    SUM(fromStock)          AS fromStock,
                    SUM(toStock)            AS toStock,
                    SUM(adjustment)         AS adjustment
             FROM quantity_items_table
             GROUP BY item_id)
            """;
    private static final String FILTER_ITEMS_SQL_TEXT_STARTS = """
            SELECT *
            FROM items
            JOIN %s ip ON items.id = ip.item_id
            WHERE items.nameItem LIKE ?
               OR items.barcode LIKE ?
               OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
               OR %s
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
            """.formatted(ITEM_MOVEMENTS_ALL_STOCKS,
            ITEM_UNIT_BARCODE_LIKE, ITEM_UNIT_BARCODE_EXACT, ITEM_UNIT_BARCODE_LIKE, FILTER_ITEMS_LIMIT);
    private static final String FILTER_ITEMS_SQL_TEXT_CONTAINS = """
            SELECT *
            FROM items
            JOIN %s ip ON items.id = ip.item_id
            WHERE items.nameItem LIKE ?
               OR items.barcode LIKE ?
               OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
               OR %s
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
            """.formatted(ITEM_MOVEMENTS_ALL_STOCKS,
            ITEM_UNIT_BARCODE_LIKE, ITEM_UNIT_BARCODE_EXACT, FILTER_ITEMS_LIMIT);
    private static final String FILTER_ITEMS_SQL_NUMERIC = """
            SELECT *
            FROM items
            JOIN %s ip ON items.id = ip.item_id
            WHERE items.id = ?
               OR items.barcode = ?
               OR items.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
               OR %s
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
            """.formatted(ITEM_MOVEMENTS_ALL_STOCKS,
            ITEM_UNIT_BARCODE_EXACT, ITEM_UNIT_BARCODE_EXACT, FILTER_ITEMS_LIMIT);
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
    private final String FIRST_BALANCE = "first_balance";
    private final String TABLE_NAME = "items";
    private final String QUANTITY_PURCHASE = "quantityPurchase";
    private final String QUANTITY_SALES = "quantitySales";
    private final String QUANTITY_PURCHASE_RE = "quantityPurchaseRe";
    private final String QUANTITY_SALES_RE = "quantitySalesRe";
    private final String FROM_STOCK = "fromStock";
    private final String TO_STOCK = "toStock";
    private final String ADJUSTMENT = "adjustment";

    /** Where the opening balance sits in the array {@link #getData} builds. */
    private static final int OPENING_BALANCE_INDEX = 13;
    private final String STOCK_ID = "stock_id";
    private final String selPrice1 = "sel_price1";
    private final String selPrice2 = "sel_price2";
    private final String selPrice3 = "sel_price3";
    private final String itemActive = "item_active";
    private final String itemHasValidity = "item_has_validity";
    private final String numberValidityDays = "number_validity_days";
    private final String alertDaysBeforeExpire = "alert_days_before_expire";

    private final String USER_ID = "user_id";
    /** One row per (item, stock). For the finder methods that already scope to one warehouse via {@code ip.stock_id = ?}; see {@link #ITEM_MOVEMENTS_ALL_STOCKS}. */
    private final String QUERY_ITEMS = "SELECT * from items join quantity_items_table ip on items.id = ip.item_id ";
    /** One row per item, aggregated across every warehouse. For every catalog query that names no stock. */
    private final String QUERY_ITEMS_ALL_STOCKS = "SELECT * from items join " + ITEM_MOVEMENTS_ALL_STOCKS + " ip on items.id = ip.item_id ";
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
     * The columns an update writes, and the values to write, built together.
     * <p>
     * They have to be built together because the opening balance drops out of both once
     * the item has moved - see {@link #update}. Two lists kept in step by hand is how a
     * value ends up written into the wrong column.
     */
    private record UpdateStatement(String sql, Object[] values, boolean writesOpening) {
    }

    /**
     * Saves the item.
     * <p>
     * <b>The opening balance is written only while the item has never moved.</b> It is
     * the one figure in the row that has no date on it: the balance is
     * {@code first_balance + purchases + ... - sales}, so changing it changes what the
     * item's stock was at every moment of its history, and a stock sheet printed and
     * signed last month prints differently today. Once anything has been bought, sold,
     * returned, transferred or counted, the opening balance is a closed entry and the
     * way to correct the stock is a dated movement - which is what the stock-count
     * screen is for.
     * <p>
     * A changed value is refused rather than quietly dropped: the user typed a number
     * and is entitled to know it was not saved.
     */
    @Override
    public int update(ItemsModel itemsModel) throws DaoException {
        UpdateStatement statement = updateStatementFor(itemsModel);

        return insertMultiData(() -> {
            executeUpdateWithException(statement.sql(), statement.values());
            if (statement.writesOpening()) {
                daoFactory.getItemsStockDao().updateOpeningBalance(
                        itemsModel.getId(), DefaultStock.ID, itemsModel.getFirstBalanceForStock());
            }

            saveUnits(itemsModel);

            // update extra barcodes: delete then re-insert
            daoFactory.getItemBarcodesDao().deleteByItemId(itemsModel.getId());
            if (!itemsModel.getExtraBarcodes().isEmpty()) {
                daoFactory.getItemBarcodesDao().insertBarcodesForItem(itemsModel.getId(), itemsModel.getExtraBarcodes());
            }
        });
    }

    private UpdateStatement updateStatementFor(ItemsModel itemsModel) throws DaoException {
        boolean mayWriteOpening = OpeningBalanceGuard.shared()
                .mayWrite(OpeningBalanceRegistry.ITEMS, itemsModel.getId(), itemsModel.getFirstBalanceForStock());

        if (mayWriteOpening) {
            return new UpdateStatement(
                    SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                            , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays
                            , alertDaysBeforeExpire, UNIT_ID, MINI_QUANTITY, FIRST_BALANCE, ITEM_IMAGE, USER_ID),
                    getData(itemsModel), true);
        }

        return new UpdateStatement(
                SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                        , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays
                        , alertDaysBeforeExpire, UNIT_ID, MINI_QUANTITY, ITEM_IMAGE, USER_ID),
                dataWithoutOpeningBalance(itemsModel), false);
    }

    /**
     * {@link #getData} without the opening balance, for the locked case. The column is
     * left out of the statement rather than written with its current value, so a value
     * that reached here some other way cannot overwrite it either.
     */
    private Object[] dataWithoutOpeningBalance(ItemsModel itemsModel) {
        return OpeningBalanceGuard.without(getData(itemsModel), OPENING_BALANCE_INDEX);
    }

    /**
     * Whether the item's opening balance is closed to editing. The item screen asks so
     * it can grey the field; the rule itself is applied in {@link #update}.
     */
    public boolean isOpeningBalanceLocked(int itemId) throws DaoException {
        return OpeningBalanceGuard.shared().isLocked(OpeningBalanceRegistry.ITEMS, itemId);
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
        return queryForObject(QUERY_ITEMS_ALL_STOCKS.concat(" where items.id = ? "), this::map, id);
    }

    @Override
    public ItemsModel getDataByString(String s) throws DaoException {
        return queryForObject(QUERY_ITEMS_ALL_STOCKS.concat(" where items.nameItem = ? "), this::map, s);
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
                , itemsModel.getFirstBalanceForStock()
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

    @Override
    public int updateList(List<ItemsModel> list) throws DaoException {
        try {
            // No FIRST_BALANCE, ever. This is the bulk update behind the "edit several
            // items" screen, which changes prices and groups; it has no field for an
            // opening balance and no business rewriting one. Leaving the column in meant
            // every item in the batch had its opening balance written back from whatever
            // the loaded model happened to hold - and it was the one path around the
            // rule in update(), unlogged and a hundred rows at a time.
            String string = SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                    , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays, alertDaysBeforeExpire
                    , UNIT_ID, MINI_QUANTITY, ITEM_IMAGE, USER_ID);
            return executeUpdateListWithException(list, string
                    , (statement, model) -> this.setData(statement, dataWithoutOpeningBalance(model)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private int insertItem(ItemsModel itemsModel) throws DaoException {
        Object[] objects = {itemsModel.getBarcode(), itemsModel.getNameItem()
                , itemsModel.getSubGroups().getId(), itemsModel.getBuyPrice()
                , itemsModel.getSelPrice1(), itemsModel.getSelPrice2(), itemsModel.getSelPrice3()
                , itemsModel.isActiveItem(), itemsModel.isHasValidate(), itemsModel.getNumberValidityDays()
                , itemsModel.getAlertDaysBeforeExpiry()
                , itemsModel.getUnitsType().getUnit_id()
                , itemsModel.getMini_quantity()
                , itemsModel.getFirstBalanceForStock()
                , itemsModel.getItem_image() != null ? itemsModel.getItem_image() : new byte[0]
                , itemsModel.getUsers().getId()};
        String INSERT_ITEM = SqlStatements.insertStatement(TABLE_NAME, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays, alertDaysBeforeExpire
                , UNIT_ID, MINI_QUANTITY, FIRST_BALANCE, ITEM_IMAGE, USER_ID);

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
        double firstBalanceForStock = rs.getDouble(FIRST_BALANCE);
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
        return queryForObject(QUERY_ITEMS_ALL_STOCKS.concat(" where items.id = ? "), this::map, itemId);
    }

    public ItemsModel findItemByIdAndStockId(Integer itemId, Integer stockId) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where items.id = ? and ip.stock_id = ? "), this::map, itemId, stockId);
    }

    public ItemsModel findItemByStockIdAndName(String itemName, Integer stockId) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where nameItem = ? and ip.stock_id = ? "), this::map, itemName, stockId);
    }

    /**
     * All three places a code can live, so scanning the code on a carton finds
     * the item it belongs to. Which unit was scanned is answered from the item's
     * own list by {@code ItemUnits.unitByBarcode} - it is already loaded.
     */
    public ItemsModel findItemByStockIdAndBarcode(String barcode, Integer stockId) throws DaoException {
        String query = QUERY_ITEMS.concat(
                " where (items.barcode = ?"
                        + " or items.id in (select item_id from item_barcodes where barcode = ?)"
                        + " or items.id in (select items_id from items_units where items_barcode = ?))"
                        + " and ip.stock_id = ? ");
        return queryForObject(query, this::map, barcode, barcode, barcode, stockId);
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

        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        String query = """
                SELECT code FROM (
                    SELECT barcode AS code FROM items WHERE id <> ? AND barcode IN (%1$s)
                    UNION ALL
                    SELECT barcode AS code FROM item_barcodes WHERE item_id <> ? AND barcode IN (%1$s)
                    UNION ALL
                    SELECT items_barcode AS code FROM items_units WHERE items_id <> ? AND items_barcode IN (%1$s)
                ) AS matches
                LIMIT 1
                """.formatted(placeholders);

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                int index = 1;
                for (int i = 0; i < 3; i++) {
                    statement.setInt(index++, exceptItemId);
                    for (String code : codes) {
                        statement.setString(index++, code);
                    }
                }
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
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
            return queryForObjects(FILTER_ITEMS_SQL_NUMERIC, mapper, id, q, q, q, id, q, q, q);
        }

        // 2) نص/مختلط: مرحلتين startsWith ثم contains
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        // LinkedHashMap يحافظ على الترتيب + يمنع التكرار حسب id
        Map<Integer, ItemsModel> result = new LinkedHashMap<>(FILTER_ITEMS_LIMIT);

        // Phase A: startsWith (سريع + يستفيد من index)
        List<ItemsModel> starts = queryForObjects(
                FILTER_ITEMS_SQL_TEXT_STARTS,
                mapper,
                likeStarts, likeStarts, likeStarts, likeStarts, // WHERE
                q, 0, q, q,                                      // ORDER BY (barcode exact, id exact disabled, extra barcode exact, unit barcode exact)
                likeStarts, likeStarts, likeStarts, likeStarts   // ORDER BY (name, barcode, extra barcode, unit barcode - all starts)
        );
        putUniqueById(result, starts, FILTER_ITEMS_LIMIT);

        // Phase B: contains (%text%) فقط إذا لسه محتاجين نتائج
        if (result.size() < FILTER_ITEMS_LIMIT) {
            List<ItemsModel> contains = queryForObjects(
                    FILTER_ITEMS_SQL_TEXT_CONTAINS,
                    mapper,
                    likeContains, likeContains, likeContains, likeContains, // WHERE (contains)
                    q, 0, q, q                                               // ORDER BY (barcode exact, id exact disabled, extra barcode exact, unit barcode exact)
            );
            putUniqueById(result, contains, FILTER_ITEMS_LIMIT);
        }

        return new ArrayList<>(result.values());
    }

    private void putUniqueById(Map<Integer, ItemsModel> target, List<ItemsModel> source, int limit) {
        for (ItemsModel item : source) {
            if (item == null) continue;
            target.putIfAbsent(item.getId(), item);
            if (target.size() >= limit) return;
        }
    }

    public List<ItemsModel> getLast50Items() throws DaoException {
        return getLast50Items(this::map);
    }

    private List<ItemsModel> getLast50Items(GenericMapper<ItemsModel> mapper) throws DaoException {
        return queryForObjects(QUERY_ITEMS_ALL_STOCKS.concat(" ORDER BY id DESC LIMIT 50"), mapper);
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
        return queryForObjects(QUERY_ITEMS_ALL_STOCKS + query.where() + " ORDER BY " + query.order() + " LIMIT ? OFFSET ?",
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
        return queryForObjects(QUERY_ITEMS_ALL_STOCKS.concat(" ORDER BY items.id DESC"), catalogMapper());
    }

    /**
     * One item, mapped the way the rows around it in the list were mapped.
     * <p>
     * This is what a screen asks for after saving a single item: re-reading the one row
     * that changed costs a handful of queries, where re-reading its page costs a page.
     */
    public ItemsModel getCatalogItem(int id) throws DaoException {
        return queryForObject(QUERY_ITEMS_ALL_STOCKS.concat(" where items.id = ? "), catalogMapper(), id);
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
     * What it deliberately leaves empty is the item's extra units and its extra
     * barcodes. Neither is shown in a list, and loading them is what made a page of
     * fifty items cost several hundred queries: {@link #getItemsModel} asks for the sub
     * group (which asks for its main group), the base unit, the unit list (whose own
     * mapper asks for a unit and a user per row) and the extra barcodes, once per item.
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
            itemsModel.setFirstBalanceForStock(rs.getDouble(FIRST_BALANCE));
            itemsModel.setBuyPrice(rs.getDouble(BUY_PRICE));
            itemsModel.setSelPrice1(rs.getDouble(selPrice1));
            itemsModel.setSelPrice2(rs.getDouble(selPrice2));
            itemsModel.setSelPrice3(rs.getDouble(selPrice3));
            itemsModel.setActiveItem(rs.getBoolean(itemActive));
            itemsModel.setHasValidate(rs.getBoolean(itemHasValidity));
            itemsModel.setNumberValidityDays(rs.getInt(numberValidityDays));
            itemsModel.setAlertDaysBeforeExpiry(rs.getInt(alertDaysBeforeExpire));

            Blob blob = rs.getBlob(ITEM_IMAGE);
            if (blob != null) {
                itemsModel.setItem_image(blob.getBytes(1, (int) blob.length()));
            }

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

    public int quickUpdate(ItemsModel item) throws DaoException {
        String sql = SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, BUY_PRICE,
                selPrice1, selPrice2, selPrice3, USER_ID);
        return executeUpdate(sql, item.getBarcode(), item.getNameItem(), item.getBuyPrice(),
                item.getSelPrice1(), item.getSelPrice2(), item.getSelPrice3(), item.getUsers().getId(), item.getId());
    }

}
