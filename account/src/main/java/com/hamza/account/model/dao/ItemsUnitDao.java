package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ItemsUnitDao extends AbstractDao<ItemsUnitsModel> {

    private final DaoFactory daoFactory;
    private final String UNIT = "unit";
    private final String ITEMS_UNITS = "items_" + UNIT + "s";
    private final String ID = "id";
    private final String ITEMS_ID = "items_id";
    private final String ITEMS_BARCODE = "items_barcode";
    private final String QUANTITY = "quantity";
    private final String BUY_PRICE = "buy_price";
    private final String SEL_PRICE = "sel_price";
    private final String SEL_PRICE_2 = "sel_price2";
    private final String SEL_PRICE_3 = "sel_price3";
    private final String USER_ID = "user_id";
    private final String INSERT = SqlStatements.insertStatement(ITEMS_UNITS, ITEMS_ID, ITEMS_BARCODE, UNIT, QUANTITY
            , BUY_PRICE, SEL_PRICE, SEL_PRICE_2, SEL_PRICE_3, USER_ID);

    public ItemsUnitDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    @Override
    public List<ItemsUnitsModel> loadAll() throws DaoException {
        String query = SqlStatements.selectStatement(ITEMS_UNITS);
        return queryForObjects(query, this::map);
    }

    @Override
    public List<ItemsUnitsModel> loadAllById(int id) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(ITEMS_UNITS, ID);
        return queryForObjects(query, this::map, id);
    }

    @Override
    public int insert(ItemsUnitsModel itemsUnitsModel) throws DaoException {
        Object[] objects = getObjectsInsert(itemsUnitsModel);
        return executeUpdate(INSERT, objects);
    }

    @Override
    public int update(ItemsUnitsModel itemsUnitsModel) throws DaoException {
        // user_id records who added the row and is not rewritten on edit - it is
        // also what left the SET clause one placeholder longer than getData().
        String query = SqlStatements.updateStatement(ITEMS_UNITS, ID, ITEMS_ID, ITEMS_BARCODE, UNIT, QUANTITY
                , BUY_PRICE, SEL_PRICE, SEL_PRICE_2, SEL_PRICE_3);
        return executeUpdate(query, getData(itemsUnitsModel));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(ITEMS_UNITS, ID), id);
    }

    @Override
    public ItemsUnitsModel getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(ITEMS_UNITS, ID), this::map, id);
    }

    @Override
    public Object[] getData(ItemsUnitsModel itemsUnitsModel) throws DaoException {
        return new Object[]{itemsUnitsModel.getItemsId(), barcodeOrNull(itemsUnitsModel),
                itemsUnitsModel.getUnitsModel().getUnit_id(), itemsUnitsModel.getQuantityForUnit()
                , itemsUnitsModel.getBuyPrice(), itemsUnitsModel.getSelPrice()
                , itemsUnitsModel.getSelPrice2(), itemsUnitsModel.getSelPrice3()
                , itemsUnitsModel.getId()};
    }

    @Override
    public ItemsUnitsModel map(ResultSet rs) throws DaoException {
        ItemsUnitsModel itemsUnitsModel = new ItemsUnitsModel();
        try {
            itemsUnitsModel.setId(rs.getInt(ID));
            int unit = rs.getInt(UNIT);
            UnitsModel unitsModels = daoFactory.unitsDao().getDataById(unit);
            itemsUnitsModel.setItemsId(rs.getInt(ITEMS_ID));
            itemsUnitsModel.setItemsBarcode(rs.getString(ITEMS_BARCODE));
            itemsUnitsModel.setQuantityForUnit(rs.getDouble(QUANTITY));
            itemsUnitsModel.setUnitsModel(unitsModels);
            itemsUnitsModel.setBuyPrice(rs.getDouble(BUY_PRICE));
            itemsUnitsModel.setSelPrice(rs.getDouble(SEL_PRICE));
            itemsUnitsModel.setSelPrice2(rs.getDouble(SEL_PRICE_2));
            itemsUnitsModel.setSelPrice3(rs.getDouble(SEL_PRICE_3));
            itemsUnitsModel.setUsers(daoFactory.usersDao().getDataById(rs.getInt(USER_ID)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return itemsUnitsModel;
    }

    @Override
    public int insertList(List<ItemsUnitsModel> list) throws DaoException {
        try {
            return executeUpdateListWithException(list, INSERT
                    , (statement, itemsUnitsModel) -> setData(statement, getObjectsInsert(itemsUnitsModel)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private Object[] getObjectsInsert(ItemsUnitsModel itemsUnitsModel) {
        return new Object[]{itemsUnitsModel.getItemsId(), barcodeOrNull(itemsUnitsModel)
                , itemsUnitsModel.getUnitsModel().getUnit_id(), itemsUnitsModel.getQuantityForUnit()
                , itemsUnitsModel.getBuyPrice(), itemsUnitsModel.getSelPrice()
                , itemsUnitsModel.getSelPrice2(), itemsUnitsModel.getSelPrice3()
                , itemsUnitsModel.getUsers().getId()};
    }

    /**
     * A unit without its own barcode stores NULL, not ''. The column is unique,
     * and '' repeats - so the second unit an item was given without a barcode
     * used to collide with the first.
     */
    private String barcodeOrNull(ItemsUnitsModel itemsUnitsModel) {
        String barcode = itemsUnitsModel.getItemsBarcode();
        return barcode == null || barcode.isBlank() ? null : barcode;
    }

    public List<ItemsUnitsModel> getAllUnitsByItemId(int itemId) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(ITEMS_UNITS, ITEMS_ID);
        return queryForObjects(query, this::map, itemId);
    }

    public int deleteByItemId(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(ITEMS_UNITS, ITEMS_ID), id);
    }
}
