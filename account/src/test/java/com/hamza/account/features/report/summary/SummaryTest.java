package com.hamza.account.features.report.summary;

import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.report.monthly.DayFigures;
import com.hamza.account.features.report.monthly.MonthFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);

    static DayFigures day(LocalDate day, int invoices, String gross, String discount, String returnsGross,
                          String returnsDiscount) {
        return new DayFigures(day, new MonthFigures(invoices, new BigDecimal(gross), new BigDecimal(discount),
                returnsGross.equals("0") ? 0 : 1, new BigDecimal(returnsGross), new BigDecimal(returnsDiscount)));
    }

    @Test
    @DisplayName("a period's figures are its days added up; the net takes the discounts and the returns off")
    void withinAPeriod() {
        List<DayFigures> days = List.of(day(DAY.minusDays(5), 1, "1000", "100", "0", "0"),
                day(DAY.minusDays(1), 2, "500", "0", "200", "20"),
                day(DAY, 1, "300", "0", "0", "0"));

        MonthFigures twoDays = Summary.within(days, new ProfitLossPeriod(DAY.minusDays(1), DAY));

        assertEquals(3, twoDays.invoices());
        assertEquals(new BigDecimal("620"), twoDays.net(), "800 less 180 given back");
        assertEquals(new BigDecimal("0"), twoDays.discount(), "the sale of five days ago is outside");
    }

    @Test
    @DisplayName("the trend has every day, a quiet one as zero, so the line does not jump across it")
    void theTrendFillsQuietDays() {
        List<DayFigures> days = List.of(day(DAY.minusDays(2), 1, "100", "10", "0", "0"), day(DAY, 1, "50", "0", "0", "0"));

        List<TrendPoint> trend = Summary.trendOf(days, new ProfitLossPeriod(DAY.minusDays(3), DAY));

        assertEquals(4, trend.size());
        assertEquals(List.of(BigDecimal.ZERO, new BigDecimal("90"), BigDecimal.ZERO, new BigDecimal("50")),
                trend.stream().map(TrendPoint::net).toList());
        assertEquals(DAY.minusDays(3), trend.getFirst().day());
    }

    @Test
    @DisplayName("the change in net sales, and none where the period before sold nothing or it was not read")
    void theSalesChange() {
        ProfitLossPeriod today = new ProfitLossPeriod(DAY, DAY);
        MonthFigures now = new MonthFigures(1, new BigDecimal("150"), BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        MonthFigures before = new MonthFigures(1, new BigDecimal("100"), BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(Optional.of(new BigDecimal("50.00")), summary(today, now, before).salesChange());
        assertTrue(summary(today, now, MonthFigures.ZERO).salesChange().isEmpty());
        assertTrue(summary(today, null, null).salesChange().isEmpty());
    }

    private static Summary summary(ProfitLossPeriod period, MonthFigures sales, MonthFigures previous) {
        return new Summary(period, period, DAY, Set.of(SummaryCard.SALES), sales, previous, List.of(), null, null,
                null, null, null, null, List.of(), List.of());
    }
}
