package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YearlyReportServiceTest {

    /** 23 September 2026, noon. */
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, 9, 23).atTime(12, 0)
            .toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static ProfitLossRow day(LocalDate date, String sales, String cost, String expenses) {
        BigDecimal gross = money(sales).subtract(money(cost));
        return new ProfitLossRow(date, money(sales), money(cost), gross, money(expenses),
                gross.subtract(money(expenses)));
    }

    /** What the statement answers, recording each period it was asked for. */
    private static final class Statement implements DailyProfitSource {
        final List<String> asked = new ArrayList<>();
        final List<ProfitLossRow> days = new ArrayList<>();

        @Override
        public List<ProfitLossRow> load(LocalDate from, LocalDate to) {
            asked.add(from + ".." + to);
            // The real statement answers newest first, and only inside the period.
            return days.stream()
                    .filter(day -> !day.date().isBefore(from) && !day.date().isAfter(to))
                    .sorted((a, b) -> b.date().compareTo(a.date()))
                    .toList();
        }
    }

    private static final class Breakdowns implements YearlyBreakdownRepository {
        final List<MonthBreakdown> months = new ArrayList<>();
        final List<Integer> years = new ArrayList<>();
        int reads;

        @Override
        public List<MonthBreakdown> breakdown(int year) {
            reads++;
            return months;
        }

        @Override
        public List<Integer> years() {
            return years;
        }
    }

    @Test
    @DisplayName("both years are read in one call to the statement, and each day lands in its month")
    void oneCallFoldedIntoMonths() throws Exception {
        Statement statement = new Statement();
        statement.days.add(day(LocalDate.of(2026, 1, 5), "1000.00", "600.00", "100.00"));
        statement.days.add(day(LocalDate.of(2026, 1, 20), "500.00", "300.00", "0.00"));
        statement.days.add(day(LocalDate.of(2026, 3, 2), "200.00", "150.00", "20.00"));
        statement.days.add(day(LocalDate.of(2025, 1, 9), "800.00", "500.00", "50.00"));

        YearlyReport report = new YearlyReportService(statement, new Breakdowns(), CLOCK).report(2026);

        assertEquals(List.of("2025-01-01..2026-09-30"), statement.asked, "one query for both years");
        assertEquals(9, report.rows().size(), "January to this month, the quiet ones included");

        YearlyReportRow january = report.rows().get(0);
        assertEquals(YearMonth.of(2026, 1), january.month());
        assertEquals(money("1500.00"), january.netSales());
        assertEquals(money("600.00"), january.grossProfit());
        assertEquals(money("500.00"), january.netProfit());
        assertEquals(money("800.00"), january.previousNetSales(), "the same month a year back");
        assertEquals(2, january.days().size());
        assertEquals(LocalDate.of(2026, 1, 5), january.days().get(0).date(), "a month's days are oldest first");

        YearlyReportRow february = report.rows().get(1);
        assertFalse(february.hasActivity());
        assertEquals(BigDecimal.ZERO, february.netSales(), "a month with no trade is on the report as zeros");
    }

    @Test
    @DisplayName("the year before is cut at today's date, so September is set against September to the 23rd")
    void thePreviousYearStopsAtTheSameDay() throws Exception {
        Statement statement = new Statement();
        statement.days.add(day(LocalDate.of(2026, 9, 10), "100.00", "60.00", "0.00"));
        statement.days.add(day(LocalDate.of(2025, 9, 10), "90.00", "50.00", "0.00"));
        statement.days.add(day(LocalDate.of(2025, 9, 28), "999.00", "1.00", "0.00"));
        statement.days.add(day(LocalDate.of(2025, 11, 2), "777.00", "1.00", "0.00"));

        YearlyReport report = new YearlyReportService(statement, new Breakdowns(), CLOCK).report(2026);

        YearlyReportRow september = report.rows().get(8);
        assertEquals(money("90.00"), september.previousNetSales(), "the 28th of last September is after the 23rd");
        assertEquals(money("90.00"), report.summary().previous().netSales(),
                "and November of last year is not set against a November that has not happened");
        assertTrue(report.period().toDate());
        assertEquals(Optional.of(money("11.11")), report.summary().netSalesChange());
    }

    @Test
    @DisplayName("a past year is set against the whole year before it")
    void aPastYear() throws Exception {
        Statement statement = new Statement();
        statement.days.add(day(LocalDate.of(2025, 12, 31), "300.00", "200.00", "0.00"));
        statement.days.add(day(LocalDate.of(2024, 12, 31), "150.00", "100.00", "0.00"));
        statement.days.add(day(LocalDate.of(2026, 1, 1), "999.00", "1.00", "0.00"));

        YearlyReport report = new YearlyReportService(statement, new Breakdowns(), CLOCK).report(2025);

        assertEquals(List.of("2024-01-01..2025-12-31"), statement.asked);
        assertEquals(12, report.rows().size());
        assertEquals(money("300.00"), report.summary().current().netSales());
        assertEquals(money("150.00"), report.summary().previous().netSales());
        assertEquals(Optional.of(money("100.00")), report.summary().netSalesChange());
    }

    @Test
    @DisplayName("the breakdown explains each month's net sales, and a difference is reported rather than hidden")
    void theBreakdownExplainsTheNet() throws Exception {
        Statement statement = new Statement();
        statement.days.add(day(LocalDate.of(2026, 2, 3), "720.00", "400.00", "0.00"));
        statement.days.add(day(LocalDate.of(2026, 4, 3), "100.00", "50.00", "0.00"));
        Breakdowns breakdowns = new Breakdowns();
        breakdowns.months.add(MonthBreakdown.fromView(2, money("1000.00"), money("100.00"), money("200.00"),
                money("20.00"), money("0"), money("0"), money("0"), money("0")));
        breakdowns.months.add(MonthBreakdown.fromView(4, money("90.00"), money("0"), money("0"),
                money("0"), money("0"), money("0"), money("0"), money("0")));

        YearlyReport report = new YearlyReportService(statement, breakdowns, CLOCK).report(2026);

        assertEquals(0, report.rows().get(1).unexplainedSales().signum(), "1000 - 100 - 180 is the statement's 720");
        assertEquals(money("10.00"), report.rows().get(3).unexplainedSales());
        assertEquals(money("10.00"), report.summary().unexplainedSales());
    }

    @Test
    @DisplayName("the best and the worst month are judged by net profit among the months that traded")
    void bestAndWorst() throws Exception {
        Statement statement = new Statement();
        statement.days.add(day(LocalDate.of(2026, 1, 5), "1000.00", "600.00", "0.00"));
        statement.days.add(day(LocalDate.of(2026, 5, 5), "100.00", "60.00", "300.00"));
        statement.days.add(day(LocalDate.of(2026, 7, 5), "500.00", "300.00", "0.00"));

        YearlySummary summary = new YearlyReportService(statement, new Breakdowns(), CLOCK)
                .report(2026).summary();

        assertEquals(YearMonth.of(2026, 1), summary.best().orElseThrow().month());
        assertEquals(YearMonth.of(2026, 5), summary.worst().orElseThrow().month(),
                "a quiet February, at zero, is not the worst month - May lost 260");
        assertEquals(3, summary.activeMonths());
    }

    @Test
    @DisplayName("with one month traded there is a best month and no worst one")
    void oneMonthHasNoWorst() throws Exception {
        Statement statement = new Statement();
        statement.days.add(day(LocalDate.of(2026, 3, 5), "100.00", "60.00", "0.00"));

        YearlySummary summary = new YearlyReportService(statement, new Breakdowns(), CLOCK)
                .report(2026).summary();

        assertTrue(summary.best().isPresent());
        assertEquals(Optional.empty(), summary.worst());
        assertFalse(summary.hasPrevious());
        assertEquals(Optional.empty(), summary.netSalesChange(), "against a year with nothing, no change");
    }

    @Test
    @DisplayName("a refusal from the statement reads nothing else")
    void theStatementsRefusalStopsEverything() {
        Breakdowns breakdowns = new Breakdowns();
        DailyProfitSource refusing = (from, to) -> {
            throw new BusinessRuleException("refused");
        };

        assertThrows(BusinessRuleException.class,
                () -> new YearlyReportService(refusing, breakdowns, CLOCK).report(2026));
        assertEquals(0, breakdowns.reads, "the breakdown is read only after the statement's permission");
    }

    @Test
    @DisplayName("the years offered are the documents', newest first, and always this one")
    void theYears() throws Exception {
        Breakdowns breakdowns = new Breakdowns();
        breakdowns.years.addAll(List.of(2024, 2025, 2023));

        YearlyReportService service = new YearlyReportService((from, to) -> List.of(), breakdowns, CLOCK);

        assertEquals(List.of(2026, 2025, 2024, 2023), service.years());
        assertEquals(2026, service.defaultYear());
    }

    @Test
    @DisplayName("an empty year is a report of zeros that says it is empty")
    void anEmptyYear() throws Exception {
        YearlyReport report = new YearlyReportService((from, to) -> List.of(), new Breakdowns(), CLOCK)
                .report(2026);

        assertTrue(report.isEmpty());
        assertEquals(9, report.rows().size());
        assertEquals(Optional.empty(), report.summary().best());
    }
}
