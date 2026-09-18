package com.hamza.account.features.treasury;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.account.features.treasury.TreasuryVoucherLayout.CashVoucher;
import com.hamza.account.features.treasury.TreasuryVoucherLayout.TransferVoucher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a treasury voucher says, with the labels left as their keys - so a wrong label on a figure
 * shows as a wrong key, without a bundle or a toolkit.
 */
class TreasuryVoucherLayoutTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Test
    @DisplayName("a deposit is a receipt voucher, signed by whoever received the money")
    void aDepositIsAReceipt() {
        DocumentPdfPage page = TreasuryVoucherLayout.of(new CashVoucher(movement(CashDirection.DEPOSIT,
                CashCategory.NORMAL, "1500.5"), "soha"), null, key -> key, "");

        assertEquals("treasury.voucher.receipt.title", page.title());
        assertEquals("treasury.voucher.signature.receiver", page.signatureLabel());
        assertEquals(List.of("12", "2026-09-18"), values(page.identity()));
        assertEquals("1,500.50", page.rows().get(0)[1]);
        assertEquals("1,500.50", page.summary().get(0).value());
        assertTrue(page.summary().get(0).emphasised(), "the amount is the figure the reader is looking for");
        assertTrue(values(page.details()).contains("soha"));
        assertEquals("", page.footer(), "no printing time asked for, no footer");
    }

    @Test
    @DisplayName("a withdrawal is a payment voucher, signed by whoever was paid")
    void aWithdrawalIsAPayment() {
        DocumentPdfPage page = TreasuryVoucherLayout.of(new CashVoucher(movement(CashDirection.WITHDRAWAL,
                CashCategory.NORMAL, "200"), null), null, key -> key, "2026-09-18 10:00:00");

        assertEquals("treasury.voucher.payment.title", page.title());
        assertEquals("treasury.voucher.signature.payee", page.signatureLabel());
        assertFalse(labels(page.details()).contains("treasury.voucher.entered.by"), "nobody to name, no empty line");
        assertEquals("invoice.pdf.printed.at: 2026-09-18 10:00:00", page.footer());
    }

    @Test
    @DisplayName("capital paid in says so on the paper - it is not ordinary cash")
    void theCategoryIsOnThePaper() {
        DocumentPdfPage page = TreasuryVoucherLayout.of(new CashVoucher(movement(CashDirection.DEPOSIT,
                CashCategory.CAPITAL_IN, "5000"), "admin"), null, key -> key, "");

        assertTrue(values(page.details()).contains(CashCategory.CAPITAL_IN.labelKey()));
    }

    @Test
    @DisplayName("a transfer slip carries the fee and what left the source, which is amount plus fee")
    void aTransferShowsWhatLeftTheSource() {
        DocumentPdfPage page = TreasuryVoucherLayout.of(new TransferVoucher(transfer("200", "5"), "soha"),
                null, key -> key, "");

        assertEquals("treasury.voucher.transfer.title", page.title());
        assertEquals(2, page.rows().size());
        assertEquals("5.00", page.rows().get(1)[1]);
        assertEquals(List.of("200.00", "205.00"), values(page.summary()));
        assertEquals("treasury.voucher.left.source", page.summary().get(1).label());
        assertTrue(page.summary().get(1).emphasised());
        assertEquals(List.of("wallet", "bank", "soha"), values(page.details()));
    }

    @Test
    @DisplayName("a transfer that cost nothing has no fee line, and what left is what arrived")
    void aFreeTransferHasNoFeeLine() {
        DocumentPdfPage page = TreasuryVoucherLayout.of(new TransferVoucher(transfer("200", null), null),
                null, key -> key, "");

        assertEquals(1, page.rows().size());
        assertEquals(List.of("200.00", "200.00"), values(page.summary()));
    }

    @Test
    @DisplayName("the letterhead prints the company, and an absent one prints nothing rather than failing")
    void theLetterhead() {
        var letterhead = new InvoicePrintDocument.Letterhead("حمزة", "القاهرة", "0100", "", "", null);

        DocumentPdfPage with = TreasuryVoucherLayout.of(new TransferVoucher(transfer("1", null), null),
                letterhead, key -> key, "");
        DocumentPdfPage without = TreasuryVoucherLayout.of(new TransferVoucher(transfer("1", null), null),
                null, key -> key, "");

        assertEquals("حمزة", with.companyName());
        assertEquals(List.of("القاهرة", "invoice.pdf.phone: 0100"), with.companyLines());
        assertEquals("", without.companyName());
    }

    private static CashMovement movement(CashDirection direction, CashCategory category, String amount) {
        return new CashMovement(12, 1, "الخزينة الرئيسية", direction, category, new BigDecimal(amount), DAY,
                "إيداع مبيعات اليوم", "ملاحظة");
    }

    private static TreasuryTransfer transfer(String amount, String fee) {
        return new TreasuryTransfer(7, 2, "wallet", 3, "bank", new BigDecimal(amount), DAY, "",
                fee == null ? null : new BigDecimal(fee));
    }

    private static List<String> values(List<DocumentPdfPage.Field> fields) {
        return fields.stream().map(DocumentPdfPage.Field::value).toList();
    }

    private static List<String> labels(List<DocumentPdfPage.Field> fields) {
        return fields.stream().map(DocumentPdfPage.Field::label).toList();
    }
}
