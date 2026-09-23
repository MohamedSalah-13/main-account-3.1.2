package com.hamza.account.features.report.summary;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.report.monthly.DayFigures;
import com.hamza.account.features.report.monthly.MonthlySide;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The one place the summary comes from. It asks {@code reports.show.summary} before anything, and then
 * reads each part only for a reader holding that part's own key ({@link SummaryCard}).
 */
public final class SummaryService {

    /** How many days the trend covers, whatever the period: context for "how did we get here". */
    public static final int TREND_DAYS = 14;
    /** How many rows each ranked list shows. */
    public static final int LIST_ROWS = 5;

    private final SummaryRepository repository;
    private final Predicate<PermissionKey> granted;

    public SummaryService() {
        this(new JdbcSummaryRepository(), AuthorizationGuard::isGranted);
    }

    public SummaryService(SummaryRepository repository, Predicate<PermissionKey> granted) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.granted = Objects.requireNonNull(granted, "granted");
    }

    /**
     * @param preset the ready period {@code period} is, or null for the reader's own dates - it decides what
     *               the period is compared with ({@link SummaryPeriod#previousOf})
     */
    public Summary load(SummaryPeriod preset, ProfitLossPeriod period, LocalDate today) throws DaoException {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(today, "today");
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_SUMMARY);
        Set<SummaryCard> cards = SummaryCard.visible(granted);
        ProfitLossPeriod previous = SummaryPeriod.previousOf(preset, period);
        ProfitLossPeriod trendDays = new ProfitLossPeriod(today.minusDays(TREND_DAYS - 1L), today);

        var sales = cards.contains(SummaryCard.SALES) ? readDays(MonthlySide.SALES, previous, period, trendDays) : null;
        var purchases = cards.contains(SummaryCard.PURCHASES)
                ? readDays(MonthlySide.PURCHASES, previous, period, null) : null;
        boolean cash = cards.contains(SummaryCard.CASH);
        return new Summary(period, previous, today, cards,
                sales == null ? null : Summary.within(sales, period),
                sales == null ? null : Summary.within(sales, previous),
                sales == null ? List.of() : Summary.trendOf(sales, trendDays),
                purchases == null ? null : Summary.within(purchases, period),
                purchases == null ? null : Summary.within(purchases, previous),
                cash ? repository.cash(period.from(), period.to()) : null,
                cash ? repository.cash(previous.from(), previous.to()) : null,
                cards.contains(SummaryCard.RECEIVABLES) ? repository.receivables(LIST_ROWS) : null,
                cards.contains(SummaryCard.LOW_STOCK) ? repository.lowStock(LIST_ROWS) : null,
                cards.contains(SummaryCard.TOP_ITEMS) ? repository.topItems(period.from(), period.to()) : List.of(),
                cards.contains(SummaryCard.TREASURIES) ? repository.treasuries() : List.of());
    }

    /** One read of the side's days covering the period, the one before it and - for the sales - the trend. */
    private List<DayFigures> readDays(MonthlySide side, ProfitLossPeriod previous, ProfitLossPeriod period,
                                      ProfitLossPeriod trend) throws DaoException {
        LocalDate from = previous.from();
        LocalDate to = period.to();
        if (trend != null) {
            from = from.isBefore(trend.from()) ? from : trend.from();
            to = to.isAfter(trend.to()) ? to : trend.to();
        }
        return repository.days(side, from, to);
    }
}
