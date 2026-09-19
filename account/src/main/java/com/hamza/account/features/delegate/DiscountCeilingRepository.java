package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.Optional;

/** Where a delegate's discount ceiling is kept: one nullable column on the employee's own row. */
public interface DiscountCeilingRepository {

    String SELECT_SQL = "SELECT max_discount_percent FROM employees WHERE id = ?";

    /** {@code NULL} bound to the first parameter is how a ceiling is removed. */
    String UPDATE_SQL = "UPDATE employees SET max_discount_percent = ? WHERE id = ?";

    /** The stored ceiling; empty both for "none set" and for an employee that does not exist. */
    Optional<DiscountCeiling> ceilingOf(int employeeId) throws DaoException;

    /** Writes the ceiling, or removes it when {@code maxPercent} is null; the rows it matched. */
    int write(int employeeId, BigDecimal maxPercent) throws DaoException;

    /** For a save path with no database behind it: nobody has a ceiling. */
    static DiscountCeilingRepository none() {
        return new DiscountCeilingRepository() {
            @Override
            public Optional<DiscountCeiling> ceilingOf(int employeeId) {
                return Optional.empty();
            }

            @Override
            public int write(int employeeId, BigDecimal maxPercent) {
                return 0;
            }
        };
    }
}
