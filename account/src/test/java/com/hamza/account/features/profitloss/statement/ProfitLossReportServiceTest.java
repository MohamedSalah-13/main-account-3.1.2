package com.hamza.account.features.profitloss.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.ProfitLossFigures;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitLossReportServiceTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /** User 2, never user 1: user 1 bypasses every permission and would prove nothing. */
    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "tester", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static ProfitLossRow day(LocalDate date, String sales, String cost, String expenses) {
        BigDecimal netSales = new BigDecimal(sales);
        BigDecimal costOfSales = new BigDecimal(cost);
        BigDecimal spent = new BigDecimal(expenses);
        BigDecimal gross = netSales.subtract(costOfSales);
        return new ProfitLossRow(date, netSales, costOfSales, gross, spent, gross.subtract(spent));
    }

    /** Remembers what it was asked; answers nothing unless told to. */
    private static final class Recording implements ProfitLossStatementRepository {
        final List<String> calls = new ArrayList<>();
        SalesBreakdown breakdown = SalesBreakdown.ZERO;

        @Override
        public SalesBreakdown breakdown(ProfitLossPeriod period) {
            calls.add("breakdown " + period.from() + ".." + period.to());
            return breakdown;
        }

        @Override
        public List<ExpenseHeadingTotal> expensesByHeading(ProfitLossPeriod period) {
            calls.add("expenses " + period.from() + ".." + period.to());
            return List.of();
        }

        @Override
        public OutsideProfitFigures outsideProfit(ProfitLossPeriod period) {
            calls.add("outside " + period.from() + ".." + period.to());
            return OutsideProfitFigures.ZERO;
        }

        @Override
        public List<ProfitLossMovement> movements(ProfitLossPeriod period) {
            calls.add("movements " + period.from() + ".." + period.to());
            return List.of();
        }
    }

    @Test
    @DisplayName("both periods' days are read in one call and split between them")
    void oneReadOfTheDays() throws Exception {
        List<String> loads = new ArrayList<>();
        List<ProfitLossRow> days = List.of(
                day(LocalDate.of(2026, 8, 5), "100", "60", "0"),
                day(LocalDate.of(2026, 8, 30), "999", "0", "0"),
                day(LocalDate.of(2026, 9, 2), "300", "200", "50"),
                day(LocalDate.of(2026, 9, 10), "200", "100", "0"));
        ProfitLossReportService service = new ProfitLossReportService((from, to) -> {
            loads.add(from + ".." + to);
            return days;
        }, new Recording());

        ProfitLossReport report = service.report(new ProfitLossPeriod(SEP_1, LocalDate.of(2026, 9, 23)),
                ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY);

        assertEquals(List.of("2026-08-01..2026-09-23"), loads);
        assertEquals(new ProfitLossPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 23)), report.previousPeriod());
        assertEquals(new BigDecimal("500"), report.current().netSales());
        assertEquals(new BigDecimal("150"), report.current().netProfit());
        assertEquals(new BigDecimal("100"), report.previous().netSales(), "the 30th of August is in neither period");
    }

    @Test
    @DisplayName("every day is a row, a quiet one as zeros, and the rows add up to the period")
    void everyDayIsARow() {
        List<ProfitLossRow> days = List.of(day(LocalDate.of(2026, 9, 2), "300", "200", "50"));
        List<ProfitLossPeriodRow> rows = ProfitLossReportService.rows(
                new ProfitLossPeriod(SEP_1, LocalDate.of(2026, 9, 7)), ProfitLossGrouping.DAY, days);

        assertEquals(7, rows.size());
        assertTrue(rows.get(0).isOneDay());
        assertEquals(ProfitLossFigures.ZERO, rows.get(0).figures());
        assertEquals(new BigDecimal("50"), rows.get(1).figures().netProfit());
    }

    @Test
    @DisplayName("a week or a month is cut at the period's edges")
    void rowsAreCutAtTheEdges() {
        ProfitLossPeriod period = new ProfitLossPeriod(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 11, 5));
        List<ProfitLossPeriodRow> months = ProfitLossReportService.rows(period, ProfitLossGrouping.MONTH, List.of());
        assertEquals(List.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
                months.stream().map(ProfitLossPeriodRow::start).toList());
        assertEquals(List.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 5)),
                months.stream().map(ProfitLossPeriodRow::end).toList());

        // Thursday the 10th to Friday the 25th: Thu-Fri, then two Saturday-to-Friday weeks.
        List<ProfitLossPeriodRow> weeks = ProfitLossReportService.rows(
                new ProfitLossPeriod(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 25)), ProfitLossGrouping.WEEK,
                List.of());
        assertEquals(List.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19)),
                weeks.stream().map(ProfitLossPeriodRow::start).toList());
        assertEquals(LocalDate.of(2026, 9, 11), weeks.get(0).end());
    }

    @Test
    @DisplayName("the statement's net profit is the period's, read from its days")
    void theStatementIsTheDays() throws Exception {
        Recording repository = new Recording();
        repository.breakdown = new SalesBreakdown(new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("300"), BigDecimal.ZERO);
        ProfitLossReportService service = new ProfitLossReportService((from, to) -> List.of(
                day(LocalDate.of(2026, 9, 2), "300", "200", "50"),
                day(LocalDate.of(2026, 9, 3), "200", "100", "0")), repository);

        ProfitLossReport report = service.report(new ProfitLossPeriod(SEP_1, LocalDate.of(2026, 9, 30)),
                ComparisonBasis.SAME_PERIOD_LAST_YEAR, ProfitLossGrouping.WEEK);

        StatementLine net = report.statement().stream()
                .filter(line -> "profitloss.net.profit".equals(line.messageKey())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("150"), net.current());
        assertEquals(List.of("outside 2026-09-01..2026-09-30", "breakdown 2026-09-01..2026-09-30",
                "expenses 2026-09-01..2026-09-30", "breakdown 2025-09-01..2025-09-30",
                "expenses 2025-09-01..2025-09-30", "outside 2025-09-01..2025-09-30"), repository.calls);
    }

    @Test
    @DisplayName("a reader the statement refuses has nothing else read for them")
    void theStatementIsAskedFirst() {
        Recording repository = new Recording();
        ProfitLossReportService service = new ProfitLossReportService((from, to) -> {
            throw new BusinessRuleException("refused");
        }, repository);

        assertThrows(BusinessRuleException.class, () -> service.report(
                new ProfitLossPeriod(SEP_1, SEP_1), ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    @DisplayName("a row's documents ask the statement's permission themselves")
    void movementsAskThePermission() throws Exception {
        Recording repository = new Recording();
        ProfitLossReportService service = new ProfitLossReportService((from, to) -> List.of(), repository);
        ProfitLossPeriodRow row = new ProfitLossPeriodRow(SEP_1, SEP_1, ProfitLossFigures.ZERO, List.of());

        signIn();
        assertThrows(BusinessRuleException.class, () -> service.movements(row));
        assertTrue(repository.calls.isEmpty());

        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        service.movements(row);
        assertEquals(List.of("movements 2026-09-01..2026-09-01"), repository.calls);
    }
}
