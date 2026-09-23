package com.hamza.account.features.profitloss.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.profitloss.DailyProfitSource;
import com.hamza.account.features.profitloss.ProfitLossFigures;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The profit and loss screen's report.
 *
 * <p><b>Every figure comes from the statement's own days</b> - {@link DailyProfitSource}, which is
 * {@code ProfitLossService::load} and asks {@code reports.show.profit} before it reads - read once over
 * both periods and split between them. The rows, the cards and the statement's subtotals are sums of those
 * days, so they cannot disagree with each other or with the yearly report. The repository only explains
 * them: what the net sales are made of, which headings the expenses went to, and what is outside the
 * profit. Because the days are read first, nothing is read from it for a reader the statement refused.</p>
 */
public final class ProfitLossReportService {

    private final DailyProfitSource source;
    private final ProfitLossStatementRepository repository;

    public ProfitLossReportService(DailyProfitSource source, ProfitLossStatementRepository repository) {
        this.source = Objects.requireNonNull(source, "source");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public ProfitLossReport report(ProfitLossPeriod period, ComparisonBasis basis, ProfitLossGrouping grouping)
            throws DaoException {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(grouping, "grouping");
        ProfitLossPeriod previousPeriod = period.previous(basis);

        LocalDate first = previousPeriod.from().isBefore(period.from()) ? previousPeriod.from() : period.from();
        LocalDate last = previousPeriod.to().isAfter(period.to()) ? previousPeriod.to() : period.to();
        List<ProfitLossRow> days = source.load(first, last);

        List<ProfitLossRow> currentDays = new ArrayList<>();
        ProfitLossFigures current = ProfitLossFigures.ZERO;
        ProfitLossFigures previous = ProfitLossFigures.ZERO;
        for (ProfitLossRow day : days) {
            if (period.contains(day.date())) {
                currentDays.add(day);
                current = current.plus(day);
            } else if (previousPeriod.contains(day.date())) {
                previous = previous.plus(day);
            }
        }

        OutsideProfitFigures outside = repository.outsideProfit(period);
        ProfitLossStatement.Side now = new ProfitLossStatement.Side(current, repository.breakdown(period),
                repository.expensesByHeading(period), outside);
        ProfitLossStatement.Side before = new ProfitLossStatement.Side(previous, repository.breakdown(previousPeriod),
                repository.expensesByHeading(previousPeriod), repository.outsideProfit(previousPeriod));

        return new ProfitLossReport(period, basis, previousPeriod, grouping, rows(period, grouping, currentDays),
                current, previous, ProfitLossStatement.lines(now, before), outside);
    }

    /**
     * What one row is made of. It reads the documents and the expenses themselves, so it asks the
     * statement's permission itself rather than trusting that a report was shown first.
     */
    public List<ProfitLossMovement> movements(ProfitLossPeriodRow row) throws DaoException {
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_PROFIT);
        return repository.movements(new ProfitLossPeriod(row.start(), row.end()));
    }

    /** Every row from the first day to the last, a quiet one as zeros, each cut at the period's edges. */
    static List<ProfitLossPeriodRow> rows(ProfitLossPeriod period, ProfitLossGrouping grouping,
                                          List<ProfitLossRow> days) {
        List<ProfitLossPeriodRow> rows = new ArrayList<>();
        LocalDate start = period.from();
        while (!start.isAfter(period.to())) {
            LocalDate next = grouping.next(grouping.start(start));
            LocalDate end = next.minusDays(1).isAfter(period.to()) ? period.to() : next.minusDays(1);
            ProfitLossFigures figures = ProfitLossFigures.ZERO;
            List<ProfitLossRow> inRow = new ArrayList<>();
            for (ProfitLossRow day : days) {
                if (!day.date().isBefore(start) && !day.date().isAfter(end)) {
                    inRow.add(day);
                    figures = figures.plus(day);
                }
            }
            inRow.sort((a, b) -> a.date().compareTo(b.date()));
            rows.add(new ProfitLossPeriodRow(start, end, figures, inRow));
            start = end.plusDays(1);
        }
        return rows;
    }
}
