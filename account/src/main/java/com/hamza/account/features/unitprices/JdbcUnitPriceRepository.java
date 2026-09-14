package com.hamza.account.features.unitprices;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDBC for the unit prices screen. Every statement is {@link UnitPriceQuery}'s, and every value is
 * bound. It joins a transaction open on the calling thread, which is what lets the service lock and
 * then write on one connection.
 */
public final class JdbcUnitPriceRepository extends AbstractDao<Object> implements UnitPriceRepository {

    @Override
    public List<UnitPriceItem> findPage(UnitPriceFilter filter, int limit, int offset) throws DaoException {
        return withUnits(readItems(UnitPriceQuery.page(filter, limit, offset)), false);
    }

    @Override
    public int count(UnitPriceFilter filter) throws DaoException {
        UnitPriceQuery.Statement statement = UnitPriceQuery.count(filter);
        return withConnection(connection -> {
            try (PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
                bind(prepared, statement.parameters());
                try (ResultSet rows = prepared.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public List<UnitPriceItem> findAll(UnitPriceFilter filter) throws DaoException {
        return withUnits(readItems(UnitPriceQuery.all(filter)), false);
    }

    @Override
    public Map<Integer, UnitPriceItem> lockItems(Set<Integer> itemIds) throws DaoException {
        if (itemIds == null || itemIds.isEmpty()) return Map.of();
        List<Integer> ids = itemIds.stream().sorted().toList();
        Map<Integer, UnitPriceItem> locked = new LinkedHashMap<>();
        for (UnitPriceItem item : withUnits(readItems(UnitPriceQuery.lockItems(ids)), true)) {
            locked.put(item.id(), item);
        }
        return locked;
    }

    @Override
    public int updateItemPrices(int itemId, Prices prices, int userId) throws DaoException {
        UnitPriceQuery.Statement statement = UnitPriceQuery.updateItem(itemId, prices, userId);
        return executeUpdate(statement.sql(), statement.parameters().toArray());
    }

    @Override
    public int updateUnitPrices(int itemId, int unitId, Prices prices, int userId) throws DaoException {
        UnitPriceQuery.Statement statement = UnitPriceQuery.updateUnit(itemId, unitId, prices, userId);
        return executeUpdate(statement.sql(), statement.parameters().toArray());
    }

    private List<UnitPriceItem> readItems(UnitPriceQuery.Statement statement) throws DaoException {
        return withConnection(connection -> {
            List<UnitPriceItem> items = new ArrayList<>();
            try (PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
                bind(prepared, statement.parameters());
                try (ResultSet rows = prepared.executeQuery()) {
                    while (rows.next()) {
                        items.add(new UnitPriceItem(rows.getInt("id"), rows.getString("barcode"),
                                rows.getString("nameItem"), rows.getString("base_unit_name"),
                                new Prices(rows.getDouble("buy_price"), rows.getDouble("sel_price1"),
                                        rows.getDouble("sel_price2"), rows.getDouble("sel_price3")),
                                List.of()));
                    }
                }
            }
            return items;
        });
    }

    /**
     * Fills in each item's units with one query for the whole page - never one per item, which is
     * the several-hundred-round-trip page {@code ItemsDao.map} is warned against for.
     */
    private List<UnitPriceItem> withUnits(List<UnitPriceItem> items, boolean lock) throws DaoException {
        if (items.isEmpty()) return items;
        List<Integer> ids = items.stream().map(UnitPriceItem::id).toList();
        UnitPriceQuery.Statement statement = UnitPriceQuery.units(ids, lock);
        Map<Integer, List<UnitPriceLine>> units = withConnection(connection -> {
            Map<Integer, List<UnitPriceLine>> byItem = new LinkedHashMap<>();
            try (PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
                bind(prepared, statement.parameters());
                try (ResultSet rows = prepared.executeQuery()) {
                    while (rows.next()) {
                        byItem.computeIfAbsent(rows.getInt("items_id"), id -> new ArrayList<>())
                                .add(new UnitPriceLine(rows.getInt("unit"), rows.getString("unit_name"),
                                        rows.getDouble("quantity"),
                                        new Prices(rows.getDouble("buy_price"), rows.getDouble("sel_price"),
                                                rows.getDouble("sel_price2"), rows.getDouble("sel_price3"))));
                    }
                }
            }
            return byItem;
        });
        return items.stream()
                .map(item -> item.withUnits(units.getOrDefault(item.id(), List.of())))
                .toList();
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }
}
