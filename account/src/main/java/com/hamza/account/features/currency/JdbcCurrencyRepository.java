package com.hamza.account.features.currency;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The JDBC side of the currencies. The statements are {@link CurrencyQuery}'s. */
public final class JdbcCurrencyRepository extends AbstractDao<Currency> implements CurrencyRepository {

    @Override
    public List<Currency> all() throws DaoException {
        return queryForObjects(CurrencyQuery.ALL_SQL, this::map);
    }

    @Override
    public Currency find(int id) throws DaoException {
        return queryForObject(CurrencyQuery.BY_ID_SQL, this::map, id);
    }

    @Override
    public Currency base() throws DaoException {
        return queryForObject(CurrencyQuery.BASE_SQL, this::map);
    }

    @Override
    public int insert(CurrencyDraft draft, int userId) throws DaoException {
        return insertReturningId(CurrencyQuery.INSERT_SQL, draft.code(), draft.name(), draft.symbol(),
                draft.latinSymbol(), draft.decimalPlaces(), draft.active(), draft.sortOrder(), userId);
    }

    @Override
    public int update(CurrencyDraft draft) throws DaoException {
        return executeUpdate(CurrencyQuery.UPDATE_SQL, draft.code(), draft.name(), draft.symbol(),
                draft.latinSymbol(), draft.decimalPlaces(), draft.active(), draft.sortOrder(), draft.id());
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate(CurrencyQuery.DELETE_SQL, id);
    }

    @Override
    public void lockAll() throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.LOCK_ALL_SQL);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    // Reading every row is what takes every lock.
                    rs.getInt(1);
                }
            }
            return null;
        });
    }

    @Override
    public void clearBase() throws DaoException {
        executeUpdate(CurrencyQuery.CLEAR_BASE_SQL);
    }

    @Override
    public int markBase(int id) throws DaoException {
        return executeUpdate(CurrencyQuery.MARK_BASE_SQL, id);
    }

    @Override
    public int rateCount() throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.RATE_COUNT_SQL);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    @Override
    public int foreignTreasuryCount() throws DaoException {
        return count(CurrencyQuery.FOREIGN_TREASURY_COUNT_SQL);
    }

    @Override
    public int activeTreasuryCount(int currencyId) throws DaoException {
        return count(CurrencyQuery.ACTIVE_TREASURY_COUNT_SQL, currencyId);
    }

    @Override
    public int foreignPartyCount() throws DaoException {
        return count(CurrencyQuery.FOREIGN_PARTY_COUNT_SQL);
    }

    @Override
    public int activePartyCount(int currencyId) throws DaoException {
        return count(CurrencyQuery.ACTIVE_PARTY_COUNT_SQL, currencyId, currencyId);
    }

    private int count(String sql, Object... values) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public Currency lockForRate(int id) throws DaoException {
        return queryForObject(CurrencyQuery.LOCK_FOR_RATE_SQL, this::map, id);
    }

    @Override
    public List<ExchangeRate> rates(int currencyId) throws DaoException {
        return withConnection(connection -> {
            List<ExchangeRate> rates = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.RATES_OF_SQL)) {
                statement.setInt(1, currencyId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rates.add(mapRate(rs));
                    }
                }
            }
            return rates;
        });
    }

    @Override
    public ExchangeRate findRate(int rateId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.RATE_BY_ID_SQL)) {
                statement.setInt(1, rateId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? mapRate(rs) : null;
                }
            }
        });
    }

    @Override
    public Optional<RateInForce> rateOn(int currencyId, LocalDate day) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.RATE_ON_SQL)) {
                statement.setInt(1, currencyId);
                statement.setDate(2, Date.valueOf(day));
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    LocalDate effective = rs.getDate("effective_date").toLocalDate();
                    var rate = rs.getBigDecimal("rate");
                    var previous = rs.next() ? rs.getBigDecimal("rate") : null;
                    return Optional.of(new RateInForce(currencyId, effective, rate, previous));
                }
            }
        });
    }

    @Override
    public Map<Integer, RateInForce> ratesInForce(LocalDate day) throws DaoException {
        return withConnection(connection -> {
            Map<Integer, RateInForce> rates = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.RATES_IN_FORCE_SQL)) {
                statement.setDate(1, Date.valueOf(day));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        int currencyId = rs.getInt("currency_id");
                        rates.put(currencyId, new RateInForce(currencyId,
                                rs.getDate("effective_date").toLocalDate(), rs.getBigDecimal("rate"),
                                rs.getBigDecimal("previous_rate")));
                    }
                }
            }
            return rates;
        });
    }

    @Override
    public boolean rateDayTaken(int currencyId, LocalDate day, int exceptRateId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(CurrencyQuery.RATE_DAY_TAKEN_SQL)) {
                statement.setInt(1, currencyId);
                statement.setDate(2, Date.valueOf(day));
                statement.setInt(3, exceptRateId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        });
    }

    @Override
    public int insertRate(ExchangeRateDraft draft, int userId) throws DaoException {
        return insertReturningId(CurrencyQuery.INSERT_RATE_SQL, draft.currencyId(),
                Date.valueOf(draft.effectiveDate()), draft.rate(), draft.notes(), userId);
    }

    @Override
    public int updateRate(ExchangeRateDraft draft) throws DaoException {
        return executeUpdate(CurrencyQuery.UPDATE_RATE_SQL, Date.valueOf(draft.effectiveDate()), draft.rate(),
                draft.notes(), draft.id());
    }

    @Override
    public int deleteRate(int rateId) throws DaoException {
        return executeUpdate(CurrencyQuery.DELETE_RATE_SQL, rateId);
    }

    @Override
    public Currency map(ResultSet rs) throws DaoException {
        try {
            return new Currency(rs.getInt("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("symbol"), rs.getString("symbol_latin"), rs.getInt("decimal_places"), rs.getBoolean("is_base"),
                    rs.getBoolean("is_active"), rs.getInt("sort_order"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private static ExchangeRate mapRate(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new ExchangeRate(rs.getInt("id"), rs.getInt("currency_id"),
                rs.getDate("effective_date").toLocalDate(), rs.getBigDecimal("rate"),
                rs.getString("notes"), rs.getString("entered_by"),
                created == null ? null : created.toLocalDateTime());
    }
}
