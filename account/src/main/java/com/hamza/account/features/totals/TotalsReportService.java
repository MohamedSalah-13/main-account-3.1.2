package com.hamza.account.features.totals;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.account.authorization.PermissionKey;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The application boundary for the totals screen's printed summaries.
 *
 * <p>A report is a read of the same documents the list is showing, so it asks the same
 * permission the list does, and takes the same {@link TotalsSearchCriteria}. That is the
 * whole point of routing it through here rather than letting the screen build its own
 * query: a report that answered a slightly different question than the list it was
 * printed from would be impossible to argue with afterwards.</p>
 */
public final class TotalsReportService {

    private final TotalsReportRepository repository;

    public TotalsReportService() {
        this(new JdbcTotalsReportRepository());
    }

    public TotalsReportService(TotalsReportRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * @param showPermission the permission the list itself requires - a report is a second
     *                       way of reading the same rows, not a lesser one
     * @throws UserValidationException when this document family cannot answer that report
     */
    public TotalsReport run(DocumentTableSpec spec, DocumentTableSpec.Report report,
                            TotalsSearchCriteria criteria, PermissionKey showPermission,
                            String emptyMessage) throws DaoException {
        AuthorizationGuard.require(showPermission);
        if (!spec.supports(report)) {
            throw new UserValidationException(emptyMessage);
        }
        List<TotalsReportRow> rows = repository.run(spec, report, criteria);
        return new TotalsReport(report, rows, total(rows), spec.hasProfit(),
                report == DocumentTableSpec.Report.BY_ITEM,
                rows.size() >= DocumentTableSpec.REPORT_ROW_LIMIT);
    }

    /**
     * The last line of the report, summed from the rows above it rather than asked of the
     * database again - they are the report, so a second query could only disagree with it.
     */
    private static TotalsReportRow total(List<TotalsReportRow> rows) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        int count = 0;
        for (TotalsReportRow row : rows) {
            count += row.count();
            quantity = quantity.add(row.quantity());
            amount = amount.add(row.total());
            discount = discount.add(row.discount());
            paid = paid.add(row.paid());
            profit = profit.add(row.profit());
        }
        return new TotalsReportRow(null, count, quantity, amount, discount, paid, profit);
    }

    /**
     * @param truncated whether the row limit was reached, so the page can say so instead of
     *                  presenting a partial answer as a complete one
     */
    public record TotalsReport(DocumentTableSpec.Report kind, List<TotalsReportRow> rows,
                               TotalsReportRow total, boolean hasProfit, boolean perItem,
                               boolean truncated) {

        public TotalsReport {
            rows = List.copyOf(rows);
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }
}
