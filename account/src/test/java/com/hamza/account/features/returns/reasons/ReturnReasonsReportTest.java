package com.hamza.account.features.returns.reasons;

import com.hamza.account.features.returns.ReturnReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnReasonsReportTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static ReturnReasonsReport report(String documentsNet, ReasonTotal... reasons) {
        return new ReturnReasonsReport(ReturnSide.SALES, DAY, DAY, List.of(reasons), List.of(),
                documentsNet == null ? null : money(documentsNet));
    }

    @Test
    @DisplayName("the reasons are listed largest first, and the report adds them up")
    void totals() {
        ReturnReasonsReport report = report("10000", new ReasonTotal("OTHER", 1, money("50")),
                new ReasonTotal("DAMAGED", 3, money("400")), new ReasonTotal(null, 2, money("150")));

        assertEquals(List.of("DAMAGED", "OTHER"), report.reasons().stream().map(ReasonTotal::storedValue)
                .filter(value -> value != null).toList());
        assertEquals("DAMAGED", report.reasons().get(0).storedValue());
        assertEquals(6, report.count());
        assertEquals(money("600"), report.value());
        assertEquals(Optional.of(money("6.00")), report.returnRate());
        assertEquals(Optional.of(money("66.67")), report.share(report.reasons().get(0)));
    }

    @Test
    @DisplayName("no reason is null or blank, counted together, and never the leading reason")
    void withoutAReason() {
        ReturnReasonsReport report = report("1000", new ReasonTotal(null, 5, money("900")),
                new ReasonTotal("", 1, money("10")), new ReasonTotal("WRONG_ITEM", 1, money("20")));

        assertEquals(6, report.withoutReason().count());
        assertEquals(money("910"), report.withoutReason().value());
        assertEquals("WRONG_ITEM", report.leadingReason().orElseThrow().storedValue(),
                "a larger pile of returns with no reason is not a reason");
    }

    @Test
    @DisplayName("a share of nothing is absent, not zero")
    void nothingToDivideBy() {
        ReturnReasonsReport empty = report(null);
        assertTrue(empty.isEmpty());
        assertTrue(empty.returnRate().isEmpty());
        assertTrue(empty.leadingReason().isEmpty());
        assertTrue(report("0", new ReasonTotal("OTHER", 1, money("5"))).returnRate().isEmpty(),
                "returns in a period that sold nothing have no rate");
    }

    @Test
    @DisplayName("a stored value names its reason, and one this build does not know is kept as written")
    void reasons() {
        assertEquals(Optional.of(ReturnReason.DAMAGED), new ReasonTotal("DAMAGED", 1, BigDecimal.ONE).reason());
        ReasonTotal unknown = new ReasonTotal("EXPIRED_ON_SHELF", 1, BigDecimal.ONE);
        assertTrue(unknown.reason().isEmpty());
        assertFalse(unknown.isWithoutReason());
        assertEquals(Optional.of(money("33.33")), new ReasonTotal("OTHER", 3, money("100")).average());
        assertTrue(new ReasonTotal("OTHER", 0, null).average().isEmpty());
    }

    @Test
    void rowsKeepNothingNull() {
        ReturnedItem item = new ReturnedItem(4, null, null, null, 2);
        assertEquals("", item.name());
        assertEquals(BigDecimal.ZERO, item.value());
        ReturnDocument document = new ReturnDocument(7, DAY, null, 0, null, null);
        assertEquals("", document.party());
        assertEquals(BigDecimal.ZERO, document.value());
    }
}
