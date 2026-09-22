package com.hamza.account.features.capital;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The read-only JDBC side of the equity statement. Three row types, so three small DAOs, as
 * {@code JdbcPartyProfileRepository} does.
 */
public final class JdbcCapitalRepository implements CapitalRepository {

    private final Days days = new Days();
    private final Before before = new Before();
    private final Forward forward = new Forward();

    @Override
    public List<CapitalDay> days(LocalDate from, LocalDate to) throws DaoException {
        return days.queryForObjects(CapitalStatements.BY_DAY_AND_TREASURY, days::map,
                Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public CapitalBefore before(LocalDate day) throws DaoException {
        List<CapitalBefore> rows = before.queryForObjects(CapitalStatements.BEFORE, before::map, Date.valueOf(day));
        return rows.getFirst();
    }

    @Override
    public BroughtForward broughtForward() throws DaoException {
        List<BroughtForward> rows = forward.queryForObjects(CapitalStatements.BROUGHT_FORWARD, forward::map);
        return rows.getFirst();
    }

    private static final class Days extends AbstractDao<CapitalDay> {
        @Override
        public CapitalDay map(ResultSet rs) throws DaoException {
            try {
                return new CapitalDay(rs.getDate("day").toLocalDate(), rs.getInt("treasury_id"),
                        rs.getString("treasury_name"), rs.getBigDecimal("paid_in"), rs.getBigDecimal("drawn"),
                        rs.getInt("movements"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }

    private static final class Before extends AbstractDao<CapitalBefore> {
        @Override
        public CapitalBefore map(ResultSet rs) throws DaoException {
            try {
                return new CapitalBefore(rs.getBigDecimal("paid_in"), rs.getBigDecimal("drawn"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }

    private static final class Forward extends AbstractDao<BroughtForward> {
        @Override
        public BroughtForward map(ResultSet rs) throws DaoException {
            try {
                return new BroughtForward(rs.getBigDecimal("treasuries"), rs.getBigDecimal("customers"),
                        rs.getBigDecimal("suppliers"), rs.getBigDecimal("stock"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }
}
