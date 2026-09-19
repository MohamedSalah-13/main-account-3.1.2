package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class JdbcDiscountCeilingRepository extends AbstractDao<DiscountCeiling>
        implements DiscountCeilingRepository {

    /** Joins an open transaction on this thread, so the invoice save reads it on its own connection. */
    @Override
    public Optional<DiscountCeiling> ceilingOf(int employeeId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
                statement.setInt(1, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next()
                            ? DiscountCeiling.ofStored(rs.getBigDecimal(1))
                            : Optional.<DiscountCeiling>empty();
                }
            }
        });
    }

    @Override
    public int write(int employeeId, BigDecimal maxPercent) throws DaoException {
        return executeUpdate(UPDATE_SQL, maxPercent, employeeId);
    }

    @Override
    public DiscountCeiling map(ResultSet rs) throws DaoException {
        try {
            return new DiscountCeiling(rs.getBigDecimal("max_discount_percent"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
