package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.service.ShiftReportService.ShiftReportData;
import com.hamza.account.service.ShiftReportService.ShiftReportType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShiftReportLayoutTest {

    private static final LocalDateTime OPENED = LocalDateTime.of(2026, 9, 22, 8, 0, 0);
    private static final LocalDateTime CLOSED = LocalDateTime.of(2026, 9, 22, 16, 30, 0);
    private static final LocalDateTime PRINTED = LocalDateTime.of(2026, 9, 22, 16, 31, 5);

    /** Opening 500; in 1,250 + 100 + 40; out 75 + 60 + 20 + 15; expected 1,720. */
    private static ShiftSummary summary() {
        return ShiftSummary.builder()
                .openBalance(new BigDecimal("500"))
                .totalSales(new BigDecimal("1250"))
                .totalDeposits(new BigDecimal("100"))
                .otherIn(new BigDecimal("40"))
                .totalIn(new BigDecimal("1390"))
                .totalSalesReturns(new BigDecimal("75"))
                .totalExpenses(new BigDecimal("60"))
                .totalWithdrawals(new BigDecimal("20"))
                .otherOut(new BigDecimal("15"))
                .totalOut(new BigDecimal("170"))
                .invoicesCount(12)
                .build();
    }

    private static UserShift shift(String counted, String notes) {
        UserShift shift = new UserShift();
        shift.setId(7);
        shift.setUsername("soha");
        shift.setTreasuryName("الخزينة الرئيسية");
        shift.setOpenTime(OPENED);
        shift.setCloseTime(counted == null ? null : CLOSED);
        shift.setOpenBalance(new BigDecimal("500"));
        shift.setCloseBalance(counted == null ? BigDecimal.ZERO : new BigDecimal(counted));
        shift.setNotes(notes);
        return shift;
    }

    private static ShiftReportLayout x(boolean showExpected) {
        return ShiftReportLayout.of(new ShiftReportData(shift(null, null), summary(), PRINTED,
                ShiftReportType.X, showExpected), PRINTED, "soha", key -> key);
    }

    private static ShiftReportLayout z(String counted, String notes) {
        return ShiftReportLayout.of(new ShiftReportData(shift(counted, notes), summary(), CLOSED,
                ShiftReportType.Z, true), PRINTED, "manager", key -> key);
    }

    private static List<String> labels(ShiftReportLayout layout) {
        return layout.rows().stream().map(ShiftReportLayout.Row::getLabel).toList();
    }

    private static Optional<ShiftReportLayout.Row> row(ShiftReportLayout layout, String label) {
        return layout.rows().stream().filter(row -> row.getLabel().equals(label)).findFirst();
    }

    private static String value(ShiftReportLayout layout, String label) {
        return row(layout, label).map(ShiftReportLayout.Row::getValue)
                .orElseThrow(() -> new AssertionError(label + " is not on " + labels(layout)));
    }

    @Test
    void theRowsFollowTheDrawersArithmeticInToOutToBalance() {
        List<String> labels = labels(z("1720", null));

        assertTrue(labels.indexOf("user.shift.report.section.in") < labels.indexOf("user.shift.report.section.out"));
        assertTrue(labels.indexOf("user.shift.report.section.out") < labels.indexOf("user.shift.report.section.cash"));
        assertTrue(labels.indexOf("user.shift.report.row.opening") < labels.indexOf("user.shift.report.row.expected"),
                "the expected balance comes after what makes it up: " + labels);
        assertTrue(labels.indexOf("user.shift.report.row.expected") < labels.indexOf("user.shift.report.row.counted"));
    }

    @Test
    void theFiguresOnThePaperAddUp() {
        ShiftReportLayout layout = z("1720", null);

        assertEquals("500.00", value(layout, "user.shift.report.row.opening"));
        assertEquals("1,390.00", value(layout, "user.shift.report.row.total.in"));
        assertEquals("170.00", value(layout, "user.shift.report.row.total.out"));
        assertEquals("1,720.00", value(layout, "user.shift.report.row.expected"),
                "500 + 1,390 - 170");
        assertEquals("1,250.00", value(layout, "user.shift.report.row.sales"));
    }

    @Test
    void everyFigureIsTextInLatinDigits() {
        for (ShiftReportLayout.Row row : z("1700", null).rows()) {
            assertFalse(row.getValue().chars().anyMatch(c -> c >= '٠' && c <= '٩'),
                    "Arabic-Indic digits in " + row.getLabel() + ": " + row.getValue());
        }
    }

    @Test
    void anXReportHasNoCountedCashNoDifferenceNoCloseTimeAndNoSignatures() {
        ShiftReportLayout layout = x(true);
        List<String> labels = labels(layout);

        assertEquals("user.shift.report.x.title", layout.title());
        assertEquals("user.shift.report.x.subtitle", layout.subtitle());
        assertFalse(labels.contains("user.shift.report.row.closed"), labels.toString());
        assertFalse(labels.contains("user.shift.report.row.counted"), labels.toString());
        assertTrue(labels.stream().noneMatch(label -> label.startsWith("user.shift.report.row.difference")));
        assertTrue(layout.signatures().isEmpty());
        assertEquals(ShiftReportLayout.Kind.TOTAL, row(layout, "user.shift.report.row.expected").orElseThrow().kind(),
                "on an X report the expected balance is the answer");
    }

    /**
     * It used to print every movement and hide the expected balance alone, which left that figure one
     * addition away on the paper handed to the person it is kept from.
     */
    @Test
    void aBlindXReportCarriesNoAmountButTheOpeningBalance() {
        ShiftReportLayout layout = x(false);
        List<String> labels = labels(layout);

        assertEquals("user.shift.report.x.subtitle.blind", layout.subtitle(), "the paper says why it is bare");
        assertFalse(labels.contains("user.shift.report.row.expected"), labels.toString());
        assertFalse(labels.contains("user.shift.report.section.in"), labels.toString());
        assertFalse(labels.contains("user.shift.report.section.out"), labels.toString());
        assertTrue(labels.stream().noneMatch(label -> label.startsWith("user.shift.report.row.total")));
        assertFalse(labels.contains("user.shift.report.row.sales"), labels.toString());
        assertFalse(labels.contains("user.shift.report.row.expenses"), labels.toString());
        assertFalse(labels.contains("user.shift.report.row.other.in"), labels.toString());
        assertFalse(labels.contains("user.shift.report.row.other.out"), labels.toString());

        assertEquals("500.00", value(layout, "user.shift.report.row.opening"), "the float the cashier was given");
        assertEquals("12", value(layout, "user.shift.report.row.invoices"), "what they rang up is not an amount");
        assertEquals("soha", value(layout, "user.shift.report.row.cashier"));
    }

    @Test
    void aZReportCarriesTheCloseTheCountTheDifferenceAndTwoSignatures() {
        ShiftReportLayout layout = z("1720", null);

        assertEquals("user.shift.report.z.title", layout.title());
        assertEquals("2026-09-22 16:30:00", value(layout, "user.shift.report.row.closed"));
        assertEquals("1,720.00", value(layout, "user.shift.report.row.counted"));
        assertEquals("0.00", value(layout, "user.shift.report.row.difference.none"));
        assertEquals(List.of("user.shift.report.signature.cashier", "user.shift.report.signature.supervisor"),
                layout.signatures());
    }

    /**
     * The sign is in the label. A minus sign printed in a right-to-left line ends up on the far side
     * of the number, and a short drawer reading "50.00-" is the one figure on the paper that must not
     * be misread.
     */
    @Test
    void aShortDrawerSaysShortAndPrintsTheAmountWithoutASign() {
        ShiftReportLayout shortOf = z("1670", null);
        ShiftReportLayout over = z("1730", null);

        assertEquals("50.00", value(shortOf, "user.shift.report.row.difference.short"));
        assertEquals("10.00", value(over, "user.shift.report.row.difference.over"));
        assertEquals(ShiftReportLayout.Kind.TOTAL,
                row(shortOf, "user.shift.report.row.difference.short").orElseThrow().kind());
    }

    @Test
    void notesArePrintedBehindTheirLabelOnlyWhenThereAreAny() {
        assertEquals("", z("1720", null).notes());
        assertEquals("", z("1720", "   ").notes());
        assertEquals("user.shift.report.notes counted twice", z("1720", " counted twice ").notes());
    }

    @Test
    void thePaperSaysWhenAndByWhomItWasPrinted() {
        ShiftReportLayout layout = z("1720", null);

        assertEquals("user.shift.report.printed", layout.printedLabel());
        assertEquals("2026-09-22 16:31:05 - manager", layout.printed(),
                "the date on its own, not behind an Arabic word the bidi pass would reverse it after");
    }

    @Test
    void theShiftDetailsComeFirst() {
        ShiftReportLayout layout = x(true);

        assertEquals("7", value(layout, "user.shift.report.row.shift"));
        assertEquals("soha", value(layout, "user.shift.report.row.cashier"));
        assertEquals("الخزينة الرئيسية", value(layout, "user.shift.report.row.treasury"));
        assertEquals("2026-09-22 08:00:00", value(layout, "user.shift.report.row.opened"));
        assertEquals("12", value(layout, "user.shift.report.row.invoices"));
        assertEquals(ShiftReportLayout.Kind.HEADING, layout.rows().get(labels(layout)
                .indexOf("user.shift.report.section.in")).kind());
    }
}
