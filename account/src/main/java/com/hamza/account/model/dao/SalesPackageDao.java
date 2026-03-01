package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Sales_Package;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class SalesPackageDao extends AbstractDao<Sales_Package> {
    private static final String TABLE_NAME = "sales_package";
    private static final String ID = "id";
    private static final String SALES_ID = "sales_id";
    private static final String ITEM_ID = "item_id";
    private static final String UNIT_ID = "unit_id";
    private static final String QUANTITY = "quantity";
    private static final String SEL_PRICE = "price";
    private static final String BUY_PRICE = "buy_price";
    private static final String TOTAL_SEL_PRICE = "total_sel_price";
    private static final String TOTAL_BUY_PRICE = "total_buy_price";
    private static final String TOTAL_PROFIT = "total_profit";
    private static final String DISCOUNT = "discount";
    private static final String UNIT_VALUE = "unit_value";
    private static final String EXPIRATION_DATE = "expiration_date";

    public SalesPackageDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Sales_Package> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, SALES_ID), this::map, id);
    }

    @Override
    public Object[] getData(Sales_Package salesPackage) throws DaoException {
        return new Object[]{salesPackage.getSales_id(), salesPackage.getItems_id(), salesPackage.getUnit_id()
                , salesPackage.getQuantity(), salesPackage.getSelling_price(), salesPackage.getBuying_price()
                , salesPackage.getDiscount(), salesPackage.getTotal_sales(), salesPackage.getTotal_buying()
                , salesPackage.getTotal_profit(), salesPackage.getUnit_value(), salesPackage.getExpiration_date()};
    }

    @Override
    public Sales_Package map(ResultSet rs) throws DaoException {
        Sales_Package salesPackage = new Sales_Package();
        try {
            salesPackage.setId(rs.getInt(ID));
            salesPackage.setSales_id(rs.getInt(SALES_ID));
            salesPackage.setItems_id(rs.getInt(ITEM_ID));
            salesPackage.setUnit_id(rs.getInt(UNIT_ID));
            salesPackage.setQuantity(rs.getDouble(QUANTITY));
            salesPackage.setSelling_price(rs.getDouble(SEL_PRICE));
            salesPackage.setBuying_price(rs.getDouble(BUY_PRICE));
            salesPackage.setTotal_sales(rs.getDouble(TOTAL_SEL_PRICE));
            salesPackage.setTotal_buying(rs.getDouble(TOTAL_BUY_PRICE));
            salesPackage.setTotal_profit(rs.getDouble(TOTAL_PROFIT));
            salesPackage.setDiscount(rs.getDouble(DISCOUNT));
            salesPackage.setUnit_value(rs.getDouble(UNIT_VALUE));
//            salesPackage.setExpiration_date(rs.getString(EXPIRATION_DATE));

        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return salesPackage;
    }

    @Override
    public int insertList(List<Sales_Package> list) throws DaoException {
        String sql = SqlStatements.insertStatement(TABLE_NAME, SALES_ID, ITEM_ID, UNIT_ID
                , QUANTITY, SEL_PRICE, BUY_PRICE
                , DISCOUNT, TOTAL_SEL_PRICE, TOTAL_BUY_PRICE
                , TOTAL_PROFIT, UNIT_VALUE, EXPIRATION_DATE);
        try {
            return executeUpdateListWithException(list, sql, (statement, salesPackage) -> {
                setData(statement, getData(salesPackage));
            });
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int updateList(List<Sales_Package> list) throws DaoException {
        return super.updateList(list);
    }

    public List<Sales_Package> getSalesPackageByItemId(int item_id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ITEM_ID), this::map, item_id);
    }
}
