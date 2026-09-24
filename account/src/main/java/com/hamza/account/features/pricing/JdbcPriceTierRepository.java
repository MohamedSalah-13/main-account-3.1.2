package com.hamza.account.features.pricing;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC for the tiers. Every statement is {@link PriceTierQuery}'s and every value is bound; it joins a
 * transaction open on the calling thread, which is what lets the service lock and then write on one
 * connection.
 */
public final class JdbcPriceTierRepository extends AbstractDao<Object> implements PriceTierRepository {

    @Override
    public List<PriceTier> all() throws DaoException {
        return withConnection(connection -> {
            List<PriceTier> tiers = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(PriceTierQuery.ALL_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    if (PriceTiers.exists(id)) {
                        tiers.add(new PriceTier(id, rows.getString("name"), rows.getInt("is_active") == 1,
                                rule(rows)));
                    }
                }
            }
            return tiers;
        });
    }

    /** The rule on a row, or null - a row the CHECK let through that this build cannot read is none. */
    private static TierFillRule rule(ResultSet rows) throws SQLException {
        String source = rows.getString("rule_source");
        if (source == null) {
            return null;
        }
        int tier = rows.getInt("rule_tier_id");
        Integer tierId = rows.wasNull() ? null : tier;
        BigDecimal percent = rows.getBigDecimal("rule_percent");
        BigDecimal rounding = rows.getBigDecimal("rule_rounding");
        try {
            return new TierFillRule(TierFillRule.Source.valueOf(source), tierId, percent, rounding);
        } catch (IllegalArgumentException | NullPointerException unreadable) {
            return null;
        }
    }

    @Override
    public void lockAll() throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(PriceTierQuery.LOCK_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    // reading every row is what takes the locks
                }
            }
            return null;
        });
    }

    @Override
    public int update(PriceTier tier) throws DaoException {
        TierFillRule rule = tier.rule();
        return executeUpdate(PriceTierQuery.UPDATE_SQL,
                tier.name(),
                tier.active() ? 1 : 0,
                rule == null ? null : rule.source().name(),
                rule == null ? null : rule.sourceTierId(),
                rule == null ? null : rule.percent(),
                rule == null ? null : rule.rounding(),
                tier.id());
    }

    @Override
    public void clearNames(List<Integer> tierIds) throws DaoException {
        for (int id : tierIds) {
            executeUpdate(PriceTierQuery.CLEAR_NAME_SQL, id);
        }
    }

    @Override
    public Map<Integer, Integer> activeCustomersByTier() throws DaoException {
        return withConnection(connection -> {
            Map<Integer, Integer> counts = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(PriceTierQuery.CUSTOMERS_BY_TIER_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    counts.put(rows.getInt(1), rows.getInt(2));
                }
            }
            return counts;
        });
    }
}
