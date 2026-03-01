package com.hamza.account.model.dao;

import com.hamza.account.model.base.BaseEntity;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Items_Package;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ItemsPackageDao extends AbstractDao<Items_Package> {

    private static final String TABLE_NAME = "items_package";
    private static final String ID = "id";
    private static final String ITEMS_ID = "item_id";
    private static final String PACKAGE_ID = "package_id";
    private static final String QUANTITY = "quantity";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";

    ItemsPackageDao(Connection connection) {
        super(connection);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String query = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(query, id);
    }

    @Override
    public Items_Package getDataById(int id) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_NAME)
                .concat(" join items on items.id = items_package.item_id where items_package.id = ?");
        return queryForObject(query, this::map, id);
    }

    @Override
    public Items_Package map(ResultSet rs) throws DaoException {
        try {
            Items_Package itemsPackage = new Items_Package();
            itemsPackage.setId(rs.getInt(ID));
            itemsPackage.setItems_id(rs.getInt(ITEMS_ID));
            itemsPackage.setPackage_id(rs.getInt(PACKAGE_ID));
            itemsPackage.setQuantity(rs.getDouble(QUANTITY));
            itemsPackage.setItemsModel(new ItemsModel(rs.getInt(ITEMS_ID), rs.getNString("nameItem")));
            return itemsPackage;
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int insertList(List<Items_Package> list) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, ITEMS_ID, PACKAGE_ID, QUANTITY);
        try {
            return executeUpdateListWithException(list, query, (statement, itemsPackage) -> {
                statement.setInt(1, itemsPackage.getItems_id());
                statement.setInt(2, itemsPackage.getPackage_id());
                statement.setDouble(3, itemsPackage.getQuantity());
            });
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int updateList(List<Items_Package> list) throws DaoException {
        int count = 0;


        // check list
        var itemsPackageByPackageId = getItemsPackageByPackageId(list.getFirst().getPackage_id());
        if (itemsPackageByPackageId.size() != list.size()) {
            // delete items not used on new list
            var listId = itemsPackageByPackageId.stream().map(BaseEntity::getId).toList();
            var listItemPackagedId = list.stream().map(BaseEntity::getId).toList();
            listId.stream().filter(item -> !listItemPackagedId.contains(item)).forEach(item -> {
                try {
                    deleteById(item);
                } catch (DaoException e) {
                    log.error(e.getMessage(), e.getCause());
                }
            });
        }

        // insert or update
        List<Items_Package> itemsPackageListExist = new ArrayList<>();
        List<Items_Package> itemsPackageListNotExist = new ArrayList<>();
        for (Items_Package item : list) {
            var dataById = getDataById(item.getId());
            if (dataById != null) {
                itemsPackageListExist.add(item);
            } else {
                itemsPackageListNotExist.add(item);
            }
        }

        if (!itemsPackageListNotExist.isEmpty()) {
            count = insertList(itemsPackageListNotExist);
        }
        if (!itemsPackageListExist.isEmpty()) {
            try {
                String query = SqlStatements.updateStatement(TABLE_NAME, ID, ITEMS_ID, PACKAGE_ID, QUANTITY);
                count = executeUpdateListWithException(list, query, (statement, itemsPackage) -> {
                    statement.setInt(1, itemsPackage.getItems_id());
                    statement.setInt(2, itemsPackage.getPackage_id());
                    statement.setDouble(3, itemsPackage.getQuantity());
                    statement.setInt(4, itemsPackage.getId());
                });
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
        return count;
    }

    public List<Items_Package> getItemsPackageByPackageId(int packageId) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_NAME)
                .concat(" join items on items.id = items_package.item_id where package_id = ?");
        return queryForObjects(query, this::map, packageId);
    }
}
