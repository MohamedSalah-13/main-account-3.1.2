package com.hamza.account.features.delegate.trend;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** The read-only JDBC side of a delegate's trend. The parameter order is {@link DelegateTrendQuery}'s. */
public final class JdbcDelegateTrendRepository extends AbstractDao<DelegateTrendDay>
        implements DelegateTrendRepository {

    @Override
    public List<DelegateTrendDay> daily(int delegateId, LocalDate from, LocalDate to) throws DaoException {
        Date first = Date.valueOf(from);
        Date last = Date.valueOf(to);
        return queryForObjects(DelegateTrendQuery.DAILY_SQL, this::map,
                delegateId, first, last,
                delegateId, first, last,
                delegateId, first, last);
    }

    @Override
    public DelegateTrendDay map(ResultSet rs) throws DaoException {
        try {
            return new DelegateTrendDay(rs.getDate("day").toLocalDate(), rs.getBigDecimal("sales"),
                    rs.getBigDecimal("sales_returns"), rs.getBigDecimal("collected"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
