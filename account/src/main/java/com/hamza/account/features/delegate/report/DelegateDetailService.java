package com.hamza.account.features.delegate.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * One delegate's period in detail. It opens nothing the performance report does not: sales and
 * cash, no target, no rate, no commission - so it asks {@code commission.reports} and no more.
 */
public final class DelegateDetailService {

    private final DelegateDetailRepository repository;

    public DelegateDetailService() {
        this(new JdbcDelegateDetailRepository());
    }

    public DelegateDetailService(DelegateDetailRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** The rows of one breakdown with what they come to. */
    public Breakdown breakdown(DelegateBreakdown breakdown, DelegateDetailFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_REPORTS);
        Objects.requireNonNull(breakdown, "breakdown");
        List<DelegateDetailRow> rows = repository.breakdown(breakdown, filter);
        // Asked only where it stands between the rows and the net: rows read off the documents
        // already carry it.
        BigDecimal[] headerDiscounts = breakdown.readOffLines()
                ? repository.headerDiscounts(filter)
                : new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        return new Breakdown(rows, DelegateDetailSummary.of(breakdown, rows, headerDiscounts[0], headerDiscounts[1]));
    }

    public Collections collections(DelegateDetailFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_REPORTS);
        List<DelegateCollectionRow> rows = repository.collections(filter);
        BigDecimal total = rows.stream().map(DelegateCollectionRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal onAccount = rows.stream().filter(DelegateCollectionRow::onAccount)
                .map(DelegateCollectionRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Collections(rows, total, onAccount);
    }

    public record Breakdown(List<DelegateDetailRow> rows, DelegateDetailSummary summary) {
    }

    /** @param onAccount the part of {@code total} allocated to no invoice */
    public record Collections(List<DelegateCollectionRow> rows, BigDecimal total, BigDecimal onAccount) {
    }
}
