package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class JdbcDelegateActivityRepository extends AbstractDao<DelegateActivity>
        implements DelegateActivityRepository {

    @Override
    public List<DelegateActivity> activity(LocalDate from, LocalDate to) throws DaoException {
        Date first = Date.valueOf(from);
        Date last = Date.valueOf(to);
        // Three periods in the statement - sales, returns, collections - and all three are this one.
        return queryForObjects(DelegateActivityQuery.ACTIVITY_SQL, this::map,
                first, last, first, last, first, last);
    }

    @Override
    public BigDecimal unattributedCollections(LocalDate from, LocalDate to) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(DelegateActivityQuery.UNATTRIBUTED_COLLECTIONS_SQL)) {
                statement.setDate(1, Date.valueOf(from));
                statement.setDate(2, Date.valueOf(to));
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
                }
            }
        });
    }

    @Override
    public int attributeCollection(long accountNumber) throws DaoException {
        return executeUpdate(DelegateActivityQuery.ATTRIBUTE_COLLECTION_SQL, accountNumber);
    }

    @Override
    public DelegateActivity map(ResultSet rs) throws DaoException {
        try {
            return new DelegateActivity(rs.getInt("id"), rs.getString("column_name"),
                    rs.getBoolean("is_active"), rs.getBigDecimal("sales"),
                    rs.getBigDecimal("sales_returns"), rs.getBigDecimal("collected"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
