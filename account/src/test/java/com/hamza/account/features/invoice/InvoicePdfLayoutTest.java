package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePdfLayoutTest {

    private static final InvoicePrintDocument.Letterhead COMPANY = new InvoicePrintDocument.Letterhead(
            "شركة", "القاهرة", "0100", "5555", "", null);

    /** quantity 2 at 10 less a line discount of 1, and quantity 1 at 5 - a net of 24. */
    private static List<ModelPrintInvoice> lines() {
        return List.of(
                new ModelPrintInvoice("قلم", "622100", "قطعة", 10, 2, 20, 1, 19),
                new ModelPrintInvoice("كراسة", null, "كرتونة", 5, 1, 5, 0, 5));
    }

    private static InvoicePrintDocument document(DocumentType type, InvoiceType payment, String discount,
                                                 String paid, InvoicePrintDocument.Balance balance) {
        return new InvoicePrintDocument(COMPANY, type, 1024, "2026-09-14", "عميل أ", payment,
                "الرئيسي", "مندوب", 900, "تالف", "تسليم مساءً", lines(),
                new BigDecimal("24"), new BigDecimal(discount), new BigDecimal(paid), "2026-09-14 10:00",
                balance);
    }

    private static Map<String, String> fields(List<DocumentPdfPage.Field> fields) {
        return fields.stream().collect(Collectors.toMap(DocumentPdfPage.Field::label,
                DocumentPdfPage.Field::value, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    @Test
    void theLinesAreNumberedAndKeepTheirBarcode() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.SALES, InvoiceType.CASH, "0", "24", null),
                key -> key);

        assertEquals(8, page.headers().length);
        assertEquals("invoice.pdf.column.barcode", page.headers()[2]);
        String[] pen = page.rows().get(0);
        assertArrayEquals(new String[]{"1", "قلم", "622100", "قطعة", "2", "10.00", "1.00", "19.00"}, pen);
        assertEquals("2", page.rows().get(1)[0]);
        assertEquals("-", page.rows().get(1)[2], "an item with no barcode prints a dash, not 'null'");
    }

    /** A carton and a piece are not two of anything, so the totals line counts lines instead. */
    @Test
    void theTotalsLineAddsTheMoneyAndCountsTheLinesButNotTheQuantities() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.SALES, InvoiceType.CASH, "0", "24", null),
                key -> key);

        assertEquals("", page.totals()[4]);
        assertEquals("1.00", page.totals()[6]);
        assertEquals("24.00", page.totals()[7]);
        assertTrue(page.totals()[1].endsWith(": 2"), page.totals()[1]);
    }

    /**
     * The defect this replaced: the page printed the total before the additional discount and
     * nothing after it. A deferred sale of 24 less 4, with 5 paid, owes 15.
     */
    @Test
    void theSummaryCarriesTheDiscountTheNetWhatWasPaidAndWhatIsLeft() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.SALES, InvoiceType.DEFER, "4", "5", null),
                key -> key);

        Map<String, String> summary = fields(page.summary());
        assertEquals("24.00", summary.get("invoice.pdf.summary.total"));
        assertEquals("4.00", summary.get("invoice.pdf.summary.discount"));
        assertEquals("20.00", summary.get("invoice.pdf.summary.net"));
        assertEquals("5.00", summary.get("invoice.pdf.summary.paid"));
        assertEquals("15.00", summary.get("invoice.pdf.summary.rest"));
        assertFalse(summary.containsKey("invoice.pdf.balance.before"));
    }

    @Test
    void noAdditionalDiscountLeavesThatLineOff() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.SALES, InvoiceType.CASH, "0", "24", null),
                key -> key);

        assertFalse(fields(page.summary()).containsKey("invoice.pdf.summary.discount"));
        assertEquals("0.00", fields(page.summary()).get("invoice.pdf.summary.rest"));
    }

    @Test
    void aBalancePrintsBeforeAndAfter() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.SALES, InvoiceType.DEFER, "4", "5",
                new InvoicePrintDocument.Balance(new BigDecimal("100"), new BigDecimal("115"))), key -> key);

        Map<String, String> summary = fields(page.summary());
        assertEquals("100.00", summary.get("invoice.pdf.balance.before"));
        assertEquals("115.00", summary.get("invoice.pdf.balance.after"));
    }

    @Test
    void aSaleNamesItsCustomerAndDelegateAndNothingAboutAReturn() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.SALES, InvoiceType.DEFER, "0", "0", null),
                key -> key);

        Map<String, String> details = fields(page.details());
        assertEquals("عميل أ", details.get("invoice.pdf.party.customer"));
        assertEquals("defer", details.get("invoice.pdf.payment.type"));
        assertEquals("الرئيسي", details.get("invoice.pdf.stock"));
        assertEquals("مندوب", details.get("invoice.pdf.delegate"));
        assertEquals(Map.of("invoice.pdf.number", "1024", "invoice.pdf.date", "2026-09-14"),
                Map.copyOf(fields(page.identity())));
        assertEquals("شركة", page.companyName());
        assertEquals(List.of("القاهرة", "invoice.pdf.phone: 0100", "invoice.pdf.commercial: 5555"),
                page.companyLines(), "a blank tax number is left off the letterhead");
        assertEquals("تسليم مساءً", page.notes());
    }

    /**
     * The builder is what blanks the delegate on a purchase and the source on a sale; the layout
     * prints what it is given. Here: a purchase return names its supplier and says "refunded".
     */
    @Test
    void aReturnSaysRefundedAndNamesTheSupplierOnThePurchaseSide() {
        DocumentPdfPage page = InvoicePdfLayout.of(document(DocumentType.PURCHASE_RETURN, InvoiceType.CASH, "0", "24",
                null), key -> key);

        Map<String, String> details = fields(page.details());
        assertTrue(details.containsKey("invoice.pdf.party.supplier"));
        assertEquals("900", details.get("invoice.pdf.source.invoice"));
        assertEquals("تالف", details.get("invoice.pdf.return.reason"));
        assertTrue(fields(page.summary()).containsKey("invoice.pdf.summary.refunded"));
        assertFalse(fields(page.summary()).containsKey("invoice.pdf.summary.paid"));
    }

    /**
     * The keys reach LanguageManager through a method reference, which the message-key scan can
     * only see because the interface method is called {@code text}. Checked here as well, against
     * the bundles themselves - including the single-word keys that scan does not look at.
     */
    @Test
    void everyKeyTheLayoutAsksForExistsInEveryBundle() throws Exception {
        List<String> asked = new ArrayList<>();
        InvoicePdfLayout.Labels recording = key -> {
            asked.add(key);
            return key;
        };
        for (DocumentType type : DocumentType.values()) {
            for (InvoiceType payment : InvoiceType.values()) {
                InvoicePdfLayout.of(document(type, payment, "1", "1",
                        new InvoicePrintDocument.Balance(BigDecimal.ONE, BigDecimal.TEN)), recording);
            }
        }
        assertFalse(asked.isEmpty());
        Path dir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var reader = new InputStreamReader(Files.newInputStream(dir.resolve(bundle)), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            for (String key : asked) {
                assertNotNull(properties.getProperty(key), key + " is missing from " + bundle);
            }
        }
    }
}
