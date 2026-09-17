package com.hamza.account.features.expense.report;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.expense.ExpenseRow;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseVoucherLayoutTest {

    /** Echoes the key, so a test can see which label went where. */
    private static String label(String key) {
        return key;
    }

    private static ExpenseRow row(String payee, String reference, Integer employeeId, String employeeName) {
        return new ExpenseRow(247, LocalDate.of(2026, 9, 17), 11, "كهرباء", "إدارية", false, 1, "الخزينة",
                new BigDecimal("1250.5"), payee, reference, "فاتورة سبتمبر", employeeId, employeeName, 2, "ahmed",
                null, null);
    }

    @Test
    @DisplayName("the voucher carries the number, the date, the till, the heading path and the amount written as money")
    void carriesTheExpense() {
        DocumentPdfPage page = ExpenseVoucherLayout.of(row("شركة الكهرباء", "R-9", null, null),
                new InvoicePrintDocument.Letterhead("المحل", "القاهرة", "0100", "", "", null),
                ExpenseVoucherLayoutTest::label, "2026-09-17 10:00");

        assertEquals("expense.voucher.title", page.title());
        assertEquals("المحل", page.companyName());
        assertEquals("247", page.identity().get(0).value());
        assertEquals("2026-09-17", page.identity().get(1).value());
        assertEquals(List.of("إدارية › كهرباء", "1,250.50"), List.of(page.rows().get(0)));
        DocumentPdfPage.Field amount = page.summary().get(0);
        assertTrue(amount.emphasised());
        assertEquals("1,250.50", amount.value());
        assertEquals("فاتورة سبتمبر", page.notes());
        assertTrue(page.details().stream().anyMatch(field -> field.value().equals("شركة الكهرباء")));
        assertTrue(page.details().stream().anyMatch(field -> field.value().equals("R-9")));
        assertTrue(page.footer().endsWith("2026-09-17 10:00"));
    }

    @Test
    @DisplayName("a field the expense does not have is left off rather than printed empty")
    void optionalFieldsAreOmitted() {
        DocumentPdfPage page = ExpenseVoucherLayout.of(row("", null, null, null), null,
                ExpenseVoucherLayoutTest::label, "");

        assertFalse(page.details().stream().anyMatch(field -> field.label().equals("expense.voucher.payee")));
        assertFalse(page.details().stream().anyMatch(field -> field.label().equals("expense.voucher.reference")));
        assertFalse(page.details().stream().anyMatch(field -> field.label().equals("expense.voucher.employee")));
        assertEquals("", page.footer());
        assertEquals("", page.companyName());
    }
}
