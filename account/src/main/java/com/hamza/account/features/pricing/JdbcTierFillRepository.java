package com.hamza.account.features.pricing;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC for a fill - {@link TierFillQuery}'s statements, every value bound, inside the caller's transaction. */
public final class JdbcTierFillRepository extends AbstractDao<Object> implements TierFillRepository {

    @Override
    public List<TierFill.ItemSource> readAll() throws DaoException {
        return withConnection(connection -> {
            Map<Integer, List<TierFill.UnitSource>> unitsByItem = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(TierFillQuery.UNITS_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    unitsByItem.computeIfAbsent(rows.getInt("items_id"), id -> new ArrayList<>())
                            .add(new TierFill.UnitSource(rows.getInt("unit_id"), rows.getString("unit_name"),
                                    rows.getBigDecimal("buy_price"),
                                    List.of(rows.getBigDecimal("sel_price"), rows.getBigDecimal("sel_price2"),
                                            rows.getBigDecimal("sel_price3"))));
                }
            }
            List<TierFill.ItemSource> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(TierFillQuery.ITEMS_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    items.add(new TierFill.ItemSource(id, rows.getString("nameItem"), rows.getString("unit_name"),
                            rows.getBigDecimal("buy_price"),
                            List.of(rows.getBigDecimal("sel_price1"), rows.getBigDecimal("sel_price2"),
                                    rows.getBigDecimal("sel_price3")),
                            unitsByItem.getOrDefault(id, List.of())));
                }
            }
            return items;
        });
    }

    @Override
    public void lockCatalogue() throws DaoException {
        withConnection(connection -> {
            for (String sql : List.of(TierFillQuery.LOCK_ITEMS_SQL, TierFillQuery.LOCK_UNITS_SQL)) {
                try (PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        // reading every row is what takes the locks
                    }
                }
            }
            return null;
        });
    }

    @Override
    public int writeItem(int itemId, int tierId, BigDecimal before, BigDecimal after) throws DaoException {
        return executeUpdate(TierFillQuery.writeItemSql(tierId), after, itemId, before);
    }

    @Override
    public int writeUnit(int itemId, int unitId, int tierId, BigDecimal before, BigDecimal after) throws DaoException {
        return executeUpdate(TierFillQuery.writeUnitSql(tierId), after, itemId, unitId, before);
    }
}
