package com.hamza.account.reportData;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.account.type.InvoiceType;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 80mm receipt template, filled with what {@link Print_Reports#receiptParameters} hands it.
 * <p>
 * {@code ReportTemplatesCompileTest} only compiles: a field whose class no longer matches its bean, or
 * a parameter nobody passes, compiles perfectly and fails the first time a cashier prints. This fills
 * the real file with the real parameters and reads the text back.
 */
class ReceiptTemplateFillTest {

    private static final File TEMPLATE = resolve();

    private static File resolve() {
        File fromModule = new File("../reports/ar/invoice-80mm.jrxml");
        return fromModule.isFile() ? fromModule : new File("reports/ar/invoice-80mm.jrxml");
    }

    private static JasperPrint fill(int lineCount, InvoiceType payment, InvoicePrintDocument.Balance balance)
            throws Exception {
        List<ModelPrintInvoice> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(new ModelPrintInvoice("item " + i, "1", "unit", 1262.5, 2, 2525, 5, 2520));
        }
        BigDecimal total = BigDecimal.valueOf(2520L * lineCount);
        InvoicePrintDocument document = new InvoicePrintDocument(InvoicePrintDocument.Letterhead.EMPTY,
                DocumentType.SALES, 1227, "2026-09-14", "customer", payment, "", "", 0, "", "", lines,
                total, new BigDecimal("20"), new BigDecimal("300"), "now", balance);

        HashMap<String, Object> parameters = Print_Reports.receiptParameters(document, "2026-09-14 11:42",
                "admin", 7, key -> key);
        parameters.put("compName", "company");
        parameters.put("compTel", "0100");
        parameters.put("compAddress", "address");
        parameters.put("DesignCompanyName", " ");
        parameters.put("AddressAndTel", " ");
        try (var reader = new InputStreamReader(Files.newInputStream(
                new File(TEMPLATE.getParentFile().getParentFile().getParentFile(),
                        "controlsfx/src/main/resources/i18n/messages_ar.properties").toPath()), StandardCharsets.UTF_8)) {
            parameters.put("REPORT_RESOURCE_BUNDLE", new PropertyResourceBundle(reader));
        }
        parameters.put("REPORT_LOCALE", Locale.forLanguageTag("ar"));
        return JasperFillManager.fillReport(CompiledReports.file(TEMPLATE.getPath()), parameters,
                new JREmptyDataSource());
    }

    private static List<String> texts(JasperPrint print) {
        List<String> texts = new ArrayList<>();
        print.getPages().forEach(page -> collect(page.getElements(), texts));
        return texts;
    }

    private static void collect(List<JRPrintElement> elements, List<String> texts) {
        for (JRPrintElement element : elements) {
            if (element instanceof JRPrintText text) {
                texts.add(text.getFullText());
            } else if (element instanceof JRPrintFrame frame) {
                collect(frame.getElements(), texts);
            }
        }
    }

    @Test
    void aDeferredReceiptPrintsItsLinesItsSummaryAndTheBalance() throws Exception {
        JasperPrint print = fill(3, InvoiceType.DEFER,
                new InvoicePrintDocument.Balance(new BigDecimal("2905"), new BigDecimal("10125")));
        List<String> texts = texts(print);

        assertTrue(texts.contains("1,262.50"), "a line's price, formatted: " + texts);
        assertTrue(texts.contains("2,520.00"), "a line's total, formatted: " + texts);
        assertTrue(texts.contains("7,560.00"), "the lines added up: " + texts);
        assertTrue(texts.contains("invoice.pdf.summary.paid"), texts.toString());
        assertTrue(texts.contains("300.00"), texts.toString());
        assertTrue(texts.contains("7,240.00"), "what is left - 7,560 less 20 less 300: " + texts);
        assertTrue(texts.contains("10,125.00"), "the balance after: " + texts);
        assertFalse(texts.stream().anyMatch(t -> t != null && t.matches(".*\\d\\.\\d$")),
                "no amount printed as a bare double: " + texts);
    }

    /**
     * The template had a fixed 850-point page: a short receipt was a page of paper with the text
     * at its top, and a long one broke onto a second page. With pagination off, one page, as long
     * as what is on it.
     */
    @Test
    void theReceiptIsOnePageAsLongAsItsContent() throws Exception {
        JasperPrint shortReceipt = fill(2, InvoiceType.CASH, null);
        JasperPrint longReceipt = fill(60, InvoiceType.CASH, null);

        assertEquals(1, shortReceipt.getPages().size());
        assertEquals(1, longReceipt.getPages().size(), "sixty lines stay on one page");
        assertTrue(shortReceipt.getPageHeight() < 850, "short: " + shortReceipt.getPageHeight());
        assertTrue(longReceipt.getPageHeight() > shortReceipt.getPageHeight() + 1000,
                "long: " + longReceipt.getPageHeight());
    }

    @Test
    void aCashReceiptCarriesNoBalance() throws Exception {
        List<String> texts = texts(fill(1, InvoiceType.CASH, null));

        assertFalse(texts.contains("invoice.pdf.balance.after"), texts.toString());
        assertTrue(texts.contains("cash"), texts.toString());
    }
}
