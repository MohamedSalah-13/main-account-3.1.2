package com.hamza.account.features.party.payment;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the allocation statements, and the arithmetic of what an invoice still owes. */
class OpenInvoiceQueryTest {

    @Test
    @DisplayName("a customer's open sales invoices, character for character")
    void theCustomerOpenInvoicesStatement() {
        assertEquals("""
                SELECT open.invoice_number, open.invoice_date, open.net, open.settled, open.notes
                FROM (SELECT d.invoice_number                         AS invoice_number,
                             d.invoice_date                         AS invoice_date,
                             ROUND(d.total - d.discount, 2) AS net,
                             ROUND(d.paid_up + COALESCE((SELECT SUM(m.paid - m.purchase)
                                             FROM customers_accounts m
                                             WHERE m.account_code = ?
                                               AND m.numberInv = d.invoice_number
                                               AND m.account_num <> ?), 0), 2)        AS settled,
                             d.notes                        AS notes
                      FROM total_sales d
                      WHERE d.sup_code = ?) open
                WHERE open.net - open.settled > 0
                ORDER BY open.invoice_date, open.invoice_number
                LIMIT 200""", OpenInvoiceQuery.openInvoicesSql(PartyKind.CUSTOMER));
    }

    @Test
    @DisplayName("what one invoice still owes, character for character")
    void theRemainingStatement() {
        assertEquals("""
                SELECT ROUND(d.total - d.discount - d.paid_up - COALESCE((SELECT SUM(m.paid - m.purchase)
                                             FROM customers_accounts m
                                             WHERE m.account_code = ?
                                               AND m.numberInv = d.invoice_number
                                               AND m.account_num <> ?), 0), 2) AS remaining
                FROM total_sales d
                WHERE d.sup_code = ? AND d.invoice_number = ?""",
                OpenInvoiceQuery.remainingOnInvoiceSql(PartyKind.CUSTOMER));
    }

    /**
     * The supplier's side reads {@code total_buy} through {@code sup_code} and
     * {@code suppliers_accounts} — all four names come from the two specifications, which is what
     * keeps a renamed column from producing an empty picker instead of an error.
     */
    @Test
    void theSupplierStatementReadsTheSupplierTables() {
        String sql = OpenInvoiceQuery.openInvoicesSql(PartyKind.SUPPLIER);
        assertTrue(sql.contains("FROM total_buy d"), sql);
        assertTrue(sql.contains("FROM suppliers_accounts m"), sql);
        assertFalse(sql.contains("total_sales"), sql);
        assertFalse(sql.contains("customers_accounts"), sql);
    }

    /** A payment settles an invoice, never a return: those are documents of their own. */
    @Test
    void aPaymentSettlesAnInvoiceAndNotAReturn() {
        assertEquals(DocumentType.SALES, OpenInvoiceQuery.invoiceType(PartyKind.CUSTOMER));
        assertEquals(DocumentType.PURCHASE, OpenInvoiceQuery.invoiceType(PartyKind.SUPPLIER));
    }

    /**
     * Parameter counts, because a statement whose placeholders and values have drifted by one
     * shifts every value along and answers something rather than failing.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theParameterCountsAreWhatTheServiceBinds(PartyKind kind) {
        assertEquals(3, placeholders(OpenInvoiceQuery.openInvoicesSql(kind)),
                "the party for the allocation subquery, the movement to ignore, the party again");
        assertEquals(4, placeholders(OpenInvoiceQuery.remainingOnInvoiceSql(kind)),
                "the same three, plus the invoice number");
    }

    /** No user value reaches the text of either statement. */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void nothingIsSplicedIn(PartyKind kind) {
        String sql = OpenInvoiceQuery.openInvoicesSql(kind)
                + OpenInvoiceQuery.remainingOnInvoiceSql(kind);
        assertFalse(sql.contains("= 7"), sql);
        assertTrue(sql.contains("LIMIT 200"), "the limit is a constant of this class, not a filter");
    }

    @Test
    @DisplayName("an invoice's remainder is its net less what has been put against it")
    void theRemainderArithmetic() {
        OpenInvoice invoice = new OpenInvoice(4312, LocalDate.of(2026, 9, 1),
                new BigDecimal("950"), new BigDecimal("400"), "");
        assertEquals(new BigDecimal("550.00"), invoice.remaining());
    }

    @Test
    void anInvoiceWithNothingAgainstItOwesItsWholeNet() {
        OpenInvoice invoice = new OpenInvoice(1, LocalDate.of(2026, 9, 1),
                new BigDecimal("100"), null, null);
        assertEquals(new BigDecimal("100.00"), invoice.remaining());
        assertEquals("", invoice.notes());
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }
}
