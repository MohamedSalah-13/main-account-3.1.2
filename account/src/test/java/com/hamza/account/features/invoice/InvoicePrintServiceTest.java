package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoicePrintServiceTest {

    @Test
    void preparesAStablePrintLineFromAnInvoiceLine() throws Exception {
        InvoicePrintService service = new InvoicePrintService(() -> mock(Print_Reports.class));
        Sales line = line();

        InvoicePrintRequest request = service.prepare(List.of(line), "2026/08/13 09:00", true,
                lines -> document());

        assertEquals(1, request.lines().size());
        assertEquals("صنف", request.lines().getFirst().getName_item());
        assertEquals(18, request.lines().getFirst().getTotal_amount());

        line.setTotal(999);
        assertEquals(18, request.lines().getFirst().getTotal_amount(),
                "print data must not follow later table-row mutations");
    }

    /**
     * The receipt reads the same document as the A4 page - that is what puts what was paid, what
     * is left and the balance on it - so the document is built for both formats, from the lines.
     */
    @Test
    void bothFormatsBuildTheDocumentFromTheCapturedLines() throws Exception {
        InvoicePrintService service = new InvoicePrintService(() -> mock(Print_Reports.class));
        for (boolean receipt : new boolean[]{true, false}) {
            AtomicInteger built = new AtomicInteger();
            InvoicePrintRequest request = service.prepare(List.of(line()), "now", receipt, lines -> {
                built.incrementAndGet();
                assertEquals(18, lines.getFirst().getTotal_amount());
                return document();
            });
            assertEquals(1, built.get());
            assertNotNull(request.document());
        }
    }

    @Test
    void routesReceiptAndStandardFormatsToTheirDedicatedPrinterMethods() throws Exception {
        Print_Reports reports = mock(Print_Reports.class);
        InvoicePrintService service = new InvoicePrintService(() -> reports);
        InvoicePrintRequest receipt = service.prepare(List.of(line()), "now", true, lines -> document());
        InvoicePrintRequest standard = service.prepare(List.of(line()), "now", false, lines -> document());

        service.print(receipt);
        service.print(standard);

        verify(reports).printReceiptInvoice(receipt.document(), "now");
        verify(reports).printInvoice(standard.document());
    }

    private static InvoicePrintDocument document() {
        return new InvoicePrintDocument(InvoicePrintDocument.Letterhead.EMPTY, DocumentType.SALES, 42,
                "2026-08-13", "عميل", InvoiceType.CASH, "", "", 0, "", "", List.of(),
                new BigDecimal("18"), BigDecimal.ZERO, new BigDecimal("18"), "now", null);
    }

    private Sales line() {
        ItemsModel item = new ItemsModel();
        item.setNameItem("صنف");
        item.setBarcode("123");
        Sales line = new Sales();
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setPrice(10);
        line.setQuantity(2);
        line.setTotal(20);
        line.setDiscount(2);
        return line;
    }
}
