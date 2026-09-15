package com.hamza.account.features.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceDetailsSearchTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void matchesByCodeNameOrUnitWithoutCaseSensitivity() {
        InvoiceDetailsLine line = line(2261, "Foam Browser A4", "Piece");

        assertTrue(InvoiceDetailsSearch.matches(line, "2261"));
        assertTrue(InvoiceDetailsSearch.matches(line, "browser"));
        assertTrue(InvoiceDetailsSearch.matches(line, "PIECE"));
        assertFalse(InvoiceDetailsSearch.matches(line, "printer"));
    }

    @Test
    void matchesMultipleWordsInAnyOrder() {
        InvoiceDetailsLine line = line(2261, "Foam Browser A4", "Piece");

        assertTrue(InvoiceDetailsSearch.matches(line, "a4 foam"));
        assertFalse(InvoiceDetailsSearch.matches(line, "a4 thermal"));
    }

    @Test
    void toleratesArabicDiacriticsAndArabicIndicDigits() {
        InvoiceDetailsLine line = line(1227, "أوراق مُلوّنة", "قطعة");

        assertTrue(InvoiceDetailsSearch.matches(line, "اوراق ملونة"));
        assertTrue(InvoiceDetailsSearch.matches(line, "١٢٢٧"));
    }

    @Test
    void blankSearchShowsEveryValidLine() {
        assertTrue(InvoiceDetailsSearch.matches(line(1, "Item", "Piece"), "  "));
        assertFalse(InvoiceDetailsSearch.matches(null, "Item"));
    }

    private static InvoiceDetailsLine line(int id, String name, String unit) {
        return new InvoiceDetailsLine(id, name, unit, ZERO, ZERO, ZERO, ZERO, ZERO);
    }
}
