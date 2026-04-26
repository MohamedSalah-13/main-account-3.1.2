package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.Items_Stock_Model;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class ItemsDao extends AbstractDao<ItemsModel> {

    public static final String BARCODE = "barcode";
    public static final String NAME_ITEM = "nameItem";
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
    private final String STOCK_ID = "stock_id";

    private final String selPrice1 = "sel_price1";
    private final String selPrice2 = "sel_price2";
    private final String selPrice3 = "sel_price3";
    private final String itemActive = "item_active";
    private final String itemHasValidity = "item_has_validity";
    private final String numberValidityDays = "number_validity_days";
    private final String alertDaysBeforeExpire = "alert_days_before_expire";
    private final String has_package = "item_has_package";
    private final String USER_ID = "user_id";
    private final String QUERY_ITEMS = "SELECT * from items join quantity_items_table ip on items.id = ip.item_id ";
    private final DaoFactory daoFactory;
    private final Connection connection;

    ItemsDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.connection = connection;
        this.daoFactory = daoFactory;
    }

    @Override
    public List<ItemsModel> loadAll() throws DaoException {
        return queryForObjects(QUERY_ITEMS, this::map);
    }

    @Override
    public int insert(ItemsModel itemsModel) throws DaoException {
        if (!new TrialManager(connection).canAddItem()) return 0;
        Object[] objects = {itemsModel.getBarcode(), itemsModel.getNameItem()
                , itemsModel.getSubGroups().getId(), itemsModel.getBuyPrice()
                , itemsModel.getSelPrice1(), itemsModel.getSelPrice2(), itemsModel.getSelPrice3()
                , itemsModel.isActiveItem(), itemsModel.isHasValidate(), itemsModel.getNumberValidityDays()
                , itemsModel.getAlertDaysBeforeExpiry()
                , itemsModel.getUnitsType().getUnit_id()
                , itemsModel.getMini_quantity()
                , itemsModel.getFirstBalanceForStock()
                , itemsModel.getItem_image() != null ? itemsModel.getItem_image() : new byte[0]
                , itemsModel.isHasPackage()
                , itemsModel.getUsers().getId()};
        String INSERT_ITEM = SqlStatements.insertStatement(TABLE_NAME, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays, alertDaysBeforeExpire
                , UNIT_ID, MINI_QUANTITY, FIRST_BALANCE, ITEM_IMAGE, has_package, USER_ID);

        try {
            connection.setAutoCommit(false);
            executeUpdate(INSERT_ITEM, objects);

        }catch (Exception e){

        }
        return insertMultiData(() -> {
            executeUpdateWithException(INSERT_ITEM, objects);

        });

    }

    @Override
    public int update(ItemsModel itemsModel) throws DaoException {
        String string = SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays, alertDaysBeforeExpire
                , UNIT_ID, MINI_QUANTITY, FIRST_BALANCE, ITEM_IMAGE, has_package, USER_ID);

        return insertMultiData(() -> {
            executeUpdateWithException(string, getData(itemsModel));
            // إذا كان الصنف يمتلك مجموعة لا يتم إضافة وحدات له
            // update package
            if (!itemsModel.getItems_packageList().isEmpty() || itemsModel.isHasPackage()) {
                itemsModel.getItems_packageList().forEach(itemsPackage -> itemsPackage.setPackage_id(itemsModel.getId()));
                daoFactory.getItemsPackageDao().updateList(itemsModel.getItems_packageList());
            } else {
                //TODO 11/24/2025 6:13 AM Mohamed: not delete , update
                // update units
                if (!itemsModel.getItemsUnitsModelList().isEmpty()) {
                    // first delete all units
                    daoFactory.getItemsUnitDao().deleteByItemId(itemsModel.getId());
                    // update list if existing
                    itemsModel.getItemsUnitsModelList().forEach(itemsUnitsModel -> itemsUnitsModel.setItemsId(itemsModel.getId()));
                    // add new units
                    daoFactory.getItemsUnitDao().insertList(itemsModel.getItemsUnitsModelList());
                }
            }
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String query = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(query, id);
    }

    @Override
    public ItemsModel getDataById(int id) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where items.id = ? "), this::map, id);
    }

    @Override
    public ItemsModel getDataByString(String s) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where items.nameItem = ? "), this::map, s);
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
                , itemsModel.isHasPackage()
                , itemsModel.getUsers().getId()
                , itemsModel.getId()};

    }

    @Override
    public ItemsModel map(ResultSet rs) throws DaoException {
        try {
            var itemsModel = getItemsModel(rs);
            // others
            double purchase = rs.getDouble(QUANTITY_PURCHASE);
            double sales = rs.getDouble(QUANTITY_SALES);
            double purRe = rs.getDouble(QUANTITY_PURCHASE_RE);
            double saleRe = rs.getDouble(QUANTITY_SALES_RE);
            double fromStock = rs.getDouble(FROM_STOCK);
            double toStock = rs.getDouble(TO_STOCK);

            itemsModel.setItemStock(daoFactory.stockDao().getDataById(rs.getInt(STOCK_ID)));
            itemsModel.setSumPurchase(purchase);
            itemsModel.setSumSales(sales);
            itemsModel.setSumPurchaseRe(purRe);
            itemsModel.setSumSalesRe(saleRe);
            itemsModel.setFromStock(fromStock);
            itemsModel.setToStock(toStock);
            double sumAllBalance = (itemsModel.getFirstBalanceForStock() + purchase + saleRe + toStock) - (sales + purRe + fromStock);
            itemsModel.setSumAllBalance(sumAllBalance);
            itemsModel.setSumAllBalanceByBuyPrice(roundToTwoDecimalPlaces(itemsModel.getBuyPrice() * sumAllBalance));
            itemsModel.setSumAllBalanceBySelPrice(roundToTwoDecimalPlaces(itemsModel.getSelPrice1() * sumAllBalance));
            return itemsModel;
        } catch (SQLException e) {
            throw new DaoException(e);
        }

    }

    @Override
    public int updateList(List<ItemsModel> list) throws DaoException {
        try {
            String string = SqlStatements.updateStatement(TABLE_NAME, ID, BARCODE, NAME_ITEM, SUB_NUM, BUY_PRICE
                    , selPrice1, selPrice2, selPrice3, itemActive, itemHasValidity, numberValidityDays, alertDaysBeforeExpire
                    , UNIT_ID, MINI_QUANTITY, FIRST_BALANCE, ITEM_IMAGE, has_package, USER_ID);
            return executeUpdateListWithException(list, string
                    , (statement, itemsModel) -> this.setData(statement, getData(itemsModel)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
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
        itemsModel.setHasPackage(rs.getBoolean(has_package));

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
        return itemsModel;
    }


    public ItemsModel findItemByIdAndStockId(Integer itemId, Integer stockId) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where items.id = ? and ip.stock_id = ? "), this::map, itemId, stockId);
    }

    public ItemsModel findItemByStockIdAndName(String itemName, Integer stockId) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where nameItem = ? and ip.stock_id = ? "), this::map, itemName, stockId);
    }

    public ItemsModel findItemByStockIdAndBarcode(String barcode, Integer stockId) throws DaoException {
        return queryForObject(QUERY_ITEMS.concat(" where barcode = ? and ip.stock_id = ? "), this::map, barcode, stockId);
    }

    public int maxItemId() {
        try {
            CallableStatement cs = connection.prepareCall("{CALL max_item_id(?)}");
            cs.executeUpdate();
            return cs.getInt(1);
        } catch (SQLException e) {
            log.error(e.getMessage(), e.getCause());
        }
        return 0;
    }

    public List<ItemsModel> getMainItemsList() throws DaoException {
        return queryForObjects("SELECT * from items", this::getItemsModel);
    }

    public List<ItemsModel> getItemsByMainGroupId(int mainGroupId) throws DaoException {
        String query = QUERY_ITEMS + " where items.sub_num in (select id from sub_group where main_id = ?)";
        return queryForObjects(query, this::map, mainGroupId);
    }

    public int deleteRangeById(Integer... ids) throws DaoException {
        String query = SqlStatements.deleteInRangeId(TABLE_NAME, ID, ids);
        return executeUpdate(query);
    }
}
