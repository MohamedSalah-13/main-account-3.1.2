package com.hamza.account.features.delegate.trend;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Objects;

/**
 * The one place a delegate's trend comes from.
 * <p>
 * It asks {@code commission.reports} - the performance report's own key, whose figures this draws
 * over time - before anything is read. No rate, target or commission is on the chart, so
 * {@code commission.show} is not needed; the year before is read only when a comparison is asked for.
 */
public final class DelegateTrendService {

    private final DelegateTrendRepository repository;

    public DelegateTrendService() {
        this(new JdbcDelegateTrendRepository());
    }

    public DelegateTrendService(DelegateTrendRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public DelegateTrend trend(DelegateTrendFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_REPORTS);
        List<DelegateTrendDay> current = repository.daily(filter.delegateId(), filter.from(), filter.to());
        List<DelegateTrendDay> previous = filter.compareWithPreviousYear()
                ? repository.daily(filter.delegateId(), filter.previousFrom(), filter.previousTo())
                : List.of();
        return DelegateTrend.build(filter, current, previous);
    }
}
