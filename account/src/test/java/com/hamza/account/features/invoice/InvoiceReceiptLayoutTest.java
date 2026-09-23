package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentType;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceReceiptLayoutTest {

    private static List<ModelPrintInvoice> lines() {
        return List.of(
                new ModelPrintInvoice("قلم", "1", "قطعة", 1262.5, 1, 1262.5, 5, 1257.5),
                new ModelPrintInvoice("كراسة", "2", "كرتونة", 25, 2, 50, 0, 50));
    }

    private static InvoicePrintDocument document(DocumentType type, InvoiceType payment, String discount,
                                                 String paid, InvoicePrintDocument.Balance balance) {
        return new InvoicePrintDocument(InvoicePrintDocument.Letterhead.EMPTY, type, 1227, "2026-09-14",
                "عميل", payment, "", "", 0, "", "", lines(),
                new BigDecimal("1307.50"), new BigDecimal(discount), new BigDecimal(paid), "now", balance);
    }

    private static Map<String, String> rows(InvoiceReceiptLayout layout) {
        Map<String, String> rows = new LinkedHashMap<>();
        layout.summary().forEach(row -> rows.put(row.getLabel(), row.getValue()));
        return rows;
    }

    /** The template used to print these as doubles: 1262.5, 2.0, 1257.5. */
    @Test
    void everyFigureOnALineIsWrittenTheWayTheScreensWriteIt() {
        InvoiceReceiptLayout layout = InvoiceReceiptLayout.of(
                document(DocumentType.SALES, InvoiceType.CASH, "0", "1307.50", null), key -> key);

        InvoiceReceiptLayout.Line pen = layout.lines().getFirst();
        assertEquals("قلم", pen.getName_item());
        assertEquals("1,262.50", pen.getPrice());
        assertEquals("1", pen.getQuantity());
        assertEquals("5.00", pen.getDiscount());
        assertEquals("1,257.50", pen.getTotal_amount());
        assertEquals("1,307.50", layout.linesTotal());
        assertTrue(layout.linesTotalLabel().endsWith(": 2"), layout.linesTotalLabel());
    }

    /** What a deferred sale's receipt stopped short of: what was paid, what is left, and the balance. */
    @Test
    void aDeferredReceiptCarriesWhatWasPaidWhatIsLeftAndTheBalance() {
        InvoiceReceiptLayout layout = InvoiceReceiptLayout.of(document(DocumentType.SALES, InvoiceType.DEFER,
                "7.50", "300", new InvoicePrintDocument.Balance(new BigDecimal("2905"), new BigDecimal("3905"))),
                key -> key);

        Map<String, String> rows = rows(layout);
        assertEquals(List.of("invoice.pdf.payment.type", "invoice.pdf.summary.total", "invoice.pdf.summary.discount",
                "invoice.pdf.summary.net", "invoice.pdf.summary.paid", "invoice.pdf.summary.rest",
                "invoice.pdf.balance.before", "invoice.pdf.balance.after"), new ArrayList<>(rows.keySet()));
        assertEquals("defer", rows.get("invoice.pdf.payment.type"));
        assertEquals("1,307.50", rows.get("invoice.pdf.summary.total"));
        assertEquals("7.50", rows.get("invoice.pdf.summary.discount"));
        assertEquals("1,300.00", rows.get("invoice.pdf.summary.net"));
        assertEquals("300.00", rows.get("invoice.pdf.summary.paid"));
        assertEquals("1,000.00", rows.get("invoice.pdf.summary.rest"));
        assertEquals("2,905.00", rows.get("invoice.pdf.balance.before"));
        assertEquals("3,905.00", rows.get("invoice.pdf.balance.after"));
    }

    /** A row that does not apply is absent, so the receipt has no gap where it would have been. */
    @Test
    void aCashSaleWithNoDiscountHasNeitherTheDiscountRowNorTheBalance() {
        Map<String, String> rows = rows(InvoiceReceiptLayout.of(
                document(DocumentType.SALES, InvoiceType.CASH, "0", "1307.50", null), key -> key));

        assertFalse(rows.containsKey("invoice.pdf.summary.discount"));
        assertFalse(rows.containsKey("invoice.pdf.balance.before"));
        assertEquals("0.00", rows.get("invoice.pdf.summary.rest"));
    }

    @Test
    void aReturnSaysRefunded() {
        Map<String, String> rows = rows(InvoiceReceiptLayout.of(
                document(DocumentType.SALES_RETURN, InvoiceType.CASH, "0", "1307.50", null), key -> key));

        assertTrue(rows.containsKey("invoice.pdf.summary.refunded"));
        assertFalse(rows.containsKey("invoice.pdf.summary.paid"));
    }

    @Test
    void theNetAndWhatIsStillOwedAreTheRowsInBold() {
        InvoiceReceiptLayout layout = InvoiceReceiptLayout.of(document(DocumentType.SALES, InvoiceType.DEFER,
                "0", "0", new InvoicePrintDocument.Balance(BigDecimal.ONE, BigDecimal.TEN)), key -> key);

        List<String> bold = layout.summary().stream().filter(InvoiceReceiptLayout.Row::getBold)
                .map(InvoiceReceiptLayout.Row::getLabel).toList();
        assertEquals(List.of("invoice.pdf.summary.net", "invoice.pdf.summary.rest", "invoice.pdf.balance.after"), bold);
    }

    /** Checked against the bundles: the keys reach LanguageManager through a method reference. */
    @Test
    void everyKeyTheReceiptAsksForExistsInEveryBundle() throws Exception {
        List<String> asked = new ArrayList<>();
        for (DocumentType type : DocumentType.values()) {
            for (InvoiceType payment : InvoiceType.values()) {
                InvoiceReceiptLayout.of(document(type, payment, "1", "1",
                        new InvoicePrintDocument.Balance(BigDecimal.ONE, BigDecimal.TEN)), key -> {
                    asked.add(key);
                    return key;
                });
            }
        }
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

    /** A dinar document written in the base says so, and gives its net to the dinar's three places. */
    @Test
    void aTranslatedReceiptSaysItsNetInThePartysCurrency() {
        InvoicePrintDocument base = document(DocumentType.SALES, InvoiceType.CASH, "0", "1307.50", null);
        InvoicePrintDocument dinars = new InvoicePrintDocument(base.letterhead(), base.type(), base.number(),
                base.date(), base.partyName(), base.invoiceType(), base.stockName(), base.delegateName(),
                base.sourceInvoiceNumber(), base.returnReason(), base.notes(), base.lines(), base.total(),
                base.discount(), base.paid(), base.printedAt(), null,
                new InvoicePrintDocument.DocumentCurrency(
                        com.hamza.account.features.party.currency.PartyCurrencyFixtures.KWD,
                        com.hamza.account.features.party.currency.PartyCurrencyFixtures.EGP,
                        new BigDecimal("158.2"), false, new BigDecimal("8.265")));

        Map<String, String> rows = rows(InvoiceReceiptLayout.of(dinars,
                key -> key.equals("invoice.pdf.currency.net.other") ? "net in %s" : key));

        assertEquals("EGP", rows.get("invoice.pdf.currency"), "its figures are in the base");
        assertEquals("1 KWD = 158.2 EGP", rows.get("invoice.pdf.currency.rate"));
        assertEquals("8.265", rows.get("net in KWD"));
        assertEquals("net in KWD", new ArrayList<>(rows.keySet()).getLast(), "the currency rows close the summary");
    }
}
