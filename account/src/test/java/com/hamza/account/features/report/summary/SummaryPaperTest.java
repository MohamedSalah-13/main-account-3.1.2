package com.hamza.account.features.report.summary;

import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.report.monthly.MonthFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryPaperTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);
    private static final ProfitLossPeriod TODAY = new ProfitLossPeriod(DAY, DAY);

    @Test
    @DisplayName("the paper carries the parts this reader may see: the sales' pieces, and the debts with no period before")
    void theLines() {
        MonthFigures sales = new MonthFigures(3, new BigDecimal("1000"), new BigDecimal("50"), 1,
                new BigDecimal("100"), new BigDecimal("10"));
        Summary summary = new Summary(TODAY, TODAY, DAY, Set.of(SummaryCard.SALES, SummaryCard.RECEIVABLES),
                sales, MonthFigures.ZERO, List.of(), null, null, null, null,
                new Receivables(4, new BigDecimal("2500"), List.of()), null, List.of(), List.of());

        List<SummaryPaper.Line> lines = SummaryPaper.lines(summary);

        assertEquals(List.of("report.dashboard.paper.sales.net", "report.dashboard.paper.sales.invoices",
                        "report.dashboard.paper.sales.discount", "report.dashboard.paper.sales.returns",
                        "report.dashboard.paper.receivables", "report.dashboard.paper.debtors"),
                lines.stream().map(SummaryPaper.Line::captionKey).toList());
        assertEquals(new BigDecimal("860"), lines.getFirst().current(), "1,000 less 50 less the 90 given back");
        assertTrue(lines.get(1).count());
        assertNull(lines.get(4).previous(), "a debt is as at today, not a period's");
    }

    @Test
    @DisplayName("nothing the reader may not see reaches the paper")
    void nothingHidden() {
        Summary summary = new Summary(TODAY, TODAY, DAY, Set.of(), null, null, List.of(), null, null, null, null,
                null, null, List.of(), List.of());

        assertTrue(SummaryPaper.lines(summary).isEmpty());
    }
}
