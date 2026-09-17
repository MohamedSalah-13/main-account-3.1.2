package com.hamza.account.features.expense.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.JdbcExpenseHeadingRepository;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.List;
import java.util.Objects;

/**
 * The one place the expense reports come from.
 * <p>
 * Every report asks {@code expenses.reports} before it reads anything, and the scope each one takes is the
 * list's own {@link ExpenseFilter} - so the invariant the plan names holds by construction and is proven
 * against MySQL rather than trusted: <b>the report by heading's total for a period, the expenses column
 * of the profit and loss for that period, and the list filtered by that period alone are one figure</b>
 * (docs/expenses-plan.md §4, {@code ExpenseDatabaseAcceptanceTest}).
 * <p>
 * Nothing here writes. Printing and exporting a report ask {@code expenses.export} on top, through
 * {@link #requireExport()} - a report on a screen is looked at, a file leaves the building.
 */
public final class ExpenseReportService {

    private final ExpenseReportRepository repository;
    private final ExpenseHeadingRepository headings;

    public ExpenseReportService() {
        this(new JdbcExpenseReportRepository(), new JdbcExpenseHeadingRepository());
    }

    public ExpenseReportService(ExpenseReportRepository repository, ExpenseHeadingRepository headings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.headings = Objects.requireNonNull(headings, "headings");
    }

    /**
     * By heading, with the period of equal length before it when the scope has a whole period. The
     * previous period is read only then, so an open-ended report costs one query.
     */
    public ExpenseByHeadingReport byHeading(ExpenseFilter scope) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        List<ExpenseReportRows.HeadingTotal> current = repository.headingTotals(scope);
        ExpenseFilter previousScope = scope.previousPeriod();
        List<ExpenseReportRows.HeadingTotal> previous = previousScope == null
                ? null : repository.headingTotals(previousScope);
        return ExpenseByHeadingReport.build(scope, headings.all(), current, previous);
    }

    /** One year by month, with the scope's other conditions. */
    public ExpenseYearMatrix yearMatrix(ExpenseFilter scope, int year) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        if (year < 1900 || year > 9999) {
            throw new UserValidationException("expense.report.error.year");
        }
        return ExpenseYearMatrix.build(scope, year, headings.all(),
                repository.headingDays(ExpenseYearMatrix.yearScope(scope, year)));
    }

    /** The trend; the year before is read only when a comparison is asked for. */
    public ExpenseTrend trend(ExpenseTrend.Filter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        List<ExpenseReportRows.Day> current = repository.days(filter.scope());
        List<ExpenseReportRows.Day> previous = filter.compareWithPreviousYear()
                ? repository.days(filter.previousScope()) : List.of();
        return ExpenseTrend.build(filter, current, previous);
    }

    public ExpenseDimensionReport byDimension(ExpenseFilter scope, ExpenseDimension dimension) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        Objects.requireNonNull(dimension, "dimension");
        return ExpenseDimensionReport.build(scope, dimension, repository.dimension(scope, dimension));
    }

    /**
     * Expenses against net sales, month by month. The scope must carry both dates and at most
     * {@link ExpenseSalesRatio#MAX_MONTHS} months; either refusal is said in words rather than reaching the
     * screen as a reference code.
     */
    public ExpenseSalesRatio salesRatio(ExpenseFilter scope) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        switch (ExpenseSalesRatio.problem(scope.from(), scope.to())) {
            case NO_PERIOD -> throw new UserValidationException("expense.report.error.period");
            case REVERSED -> throw new UserValidationException("expense.error.filter.range");
            case TOO_MANY_PERIODS -> throw new UserValidationException("expense.report.error.too.many");
            case NONE -> {
            }
        }
        return ExpenseSalesRatio.build(scope, repository.days(scope),
                repository.netSalesDays(scope.from(), scope.to()));
    }

    /** Asked before a report is printed or exported - the file, not the screen, is what leaves the shop. */
    public void requireExport() throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        AuthorizationGuard.require(AppPermissions.EXPENSES_EXPORT);
    }
}
