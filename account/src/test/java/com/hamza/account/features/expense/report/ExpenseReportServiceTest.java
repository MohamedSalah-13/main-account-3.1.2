package com.hamza.account.features.expense.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.hamza.account.features.expense.report.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseReportServiceTest {

    @AfterEach
    void cleanUp() {
        signOut();
    }

    private static ExpenseReportService service(Recording repository, Headings headings) {
        return new ExpenseReportService(repository, headings);
    }

    @Test
    @DisplayName("every report asks expenses.reports before it reads anything - expenses.show is not enough")
    void permissionFirst() {
        signInWith(AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_EXPORT);
        Recording repository = new Recording();
        Headings headings = new Headings();
        ExpenseReportService service = service(repository, headings);

        assertThrows(Exception.class, () -> service.byHeading(SEPTEMBER));
        assertThrows(Exception.class, () -> service.yearMatrix(SEPTEMBER, 2026));
        assertThrows(Exception.class, () -> service.trend(new ExpenseTrend.Filter(SEPTEMBER, TrendGranularity.MONTH, false)));
        assertThrows(Exception.class, () -> service.byDimension(SEPTEMBER, ExpenseDimension.USER));
        assertThrows(Exception.class, () -> service.salesRatio(SEPTEMBER));
        assertTrue(repository.calls.isEmpty());
        assertTrue(headings.calls.isEmpty());
    }

    @Test
    @DisplayName("the report by heading reads the period of equal length before, and only when there is one")
    void previousPeriodOnlyWithAWholePeriod() throws Exception {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        Recording repository = new Recording();

        service(repository, new Headings()).byHeading(SEPTEMBER);
        assertEquals(List.of("headings 2026-09-01..2026-09-30", "headings 2026-08-02..2026-08-31"), repository.calls);

        repository.calls.clear();
        service(repository, new Headings()).byHeading(ExpenseFilter.between(LocalDate.of(2026, 9, 1), null));
        assertEquals(List.of("headings 2026-09-01..null"), repository.calls);
    }

    @Test
    @DisplayName("the trend reads the year before only for a comparison")
    void trendComparison() throws Exception {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        Recording repository = new Recording();

        service(repository, new Headings()).trend(new ExpenseTrend.Filter(SEPTEMBER, TrendGranularity.WEEK, true));

        assertEquals(List.of("days 2026-09-01..2026-09-30", "days 2025-09-01..2025-09-30"), repository.calls);
    }

    @Test
    @DisplayName("the ratio refuses a scope with no end in words, before reading")
    void ratioNeedsAPeriod() throws Exception {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        Recording repository = new Recording();
        ExpenseReportService service = service(repository, new Headings());

        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> service.salesRatio(ExpenseFilter.between(null, LocalDate.of(2026, 9, 30))));
        assertEquals("expense.report.error.period", refused.getMessage());
        assertTrue(repository.calls.isEmpty());

        service.salesRatio(SEPTEMBER);
        assertEquals(List.of("days 2026-09-01..2026-09-30", "sales 2026-09-01..2026-09-30"), repository.calls);
    }

    @Test
    @DisplayName("the year matrix reads the year's dates, whatever the scope's own period")
    void yearMatrixReadsTheYear() throws Exception {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        Recording repository = new Recording();

        service(repository, new Headings()).yearMatrix(SEPTEMBER, 2025);

        assertEquals(List.of("heading days 2025-01-01..2025-12-31"), repository.calls);
    }

    @Test
    @DisplayName("printing or exporting a report asks expenses.export on top")
    void exportNeedsItsOwnPermission() {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        ExpenseReportService service = service(new Recording(), new Headings());
        assertThrows(Exception.class, service::requireExport);

        signInWith(AppPermissions.EXPENSES_REPORTS, AppPermissions.EXPENSES_EXPORT);
        assertDoesNotThrow(service::requireExport);
    }
}
