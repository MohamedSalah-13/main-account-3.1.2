package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Reading and deciding a delegate's discount ceiling. It wears the commission rule's two
 * permissions: it is set on the same screen, by the same person, as the rule beside it.
 */
public final class DiscountCeilingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DiscountCeilingRepository repository;

    public DiscountCeilingService() {
        this(new JdbcDiscountCeilingRepository());
    }

    public DiscountCeilingService(DiscountCeilingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<DiscountCeiling> ceilingOf(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        return repository.ceilingOf(employeeId);
    }

    /**
     * Sets the ceiling, or removes it.
     *
     * @param maxPercent what the box held: {@code null} for an empty box, which is "no ceiling" -
     *                   never a ceiling of zero, which is a delegate who may discount nothing
     */
    public int update(int employeeId, BigDecimal maxPercent) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_RULE_UPDATE);
        if (maxPercent != null && (maxPercent.signum() < 0 || maxPercent.compareTo(HUNDRED) > 0)) {
            throw new UserValidationException("delegate.ceiling.error.range");
        }
        // The column is DECIMAL(5,2); rounding here rather than in MySQL keeps what is stored
        // equal to what the screen then reads back.
        BigDecimal stored = maxPercent == null ? null : maxPercent.setScale(2, RoundingMode.HALF_UP);
        return TransactionTemplate.execute(() -> {
            if (repository.write(employeeId, stored) != 1) {
                throw new UserValidationException("delegate.ceiling.error.employee");
            }
            return 1;
        });
    }
}
