package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoicePrintServiceTest {

    @Test
    void preparesAStablePrintLineFromAnInvoiceLine() throws Exception {
        InvoicePrintService service = new InvoicePrintService(() -> mock(Print_Reports.class));
        Sales line = line();

        InvoicePrintRequest request = service.prepare(List.of(line), "عميل", 42,
                2, "2026/08/13 09:00", LocalDate.of(2026, 8, 13), true, lines -> fail("a receipt builds no page"));

        assertEquals(1, request.lines().size());
        assertEquals("صنف", request.lines().getFirst().getName_item());
        assertEquals(18, request.lines().getFirst().getTotal_amount());

        line.setTotal(999);
        assertEquals(18, request.lines().getFirst().getTotal_amount(),
                "print data must not follow later table-row mutations");
    }

    @Test
    void theUprightPageIsBuiltFromTheCapturedLinesAndOnlyForThatFormat() throws Exception {
        InvoicePrintService service = new InvoicePrintService(() -> mock(Print_Reports.class));
        AtomicInteger built = new AtomicInteger();

        InvoicePrintRequest standard = service.prepare(List.of(line()), "عميل", 42, 2, "now",
                LocalDate.of(2026, 8, 13), false, lines -> {
                    built.incrementAndGet();
                    assertEquals(18, lines.getFirst().getTotal_amount());
                    return document(lines.size());
                });

        assertEquals(1, built.get());
        assertNotNull(standard.document());
    }

    @Test
    void routesReceiptAndStandardFormatsToTheirDedicatedPrinterMethods() throws Exception {
        Print_Reports reports = mock(Print_Reports.class);
        InvoicePrintService service = new InvoicePrintService(() -> reports);
        InvoicePrintRequest receipt = service.prepare(List.of(line()), "عميل", 42,
                2, "now", LocalDate.of(2026, 8, 13), true, lines -> null);
        InvoicePrintRequest standard = service.prepare(List.of(line()), "عميل", 42,
                2, "now", LocalDate.of(2026, 8, 13), false, lines -> document(lines.size()));

        service.print(receipt);
        service.print(standard);

        verify(reports).printReceiptInvoice(receipt.lines(), "عميل", 42,
                2, "now", "2026-08-13", 0);
        verify(reports).printInvoice(standard.document());
    }

    private static InvoicePrintDocument document(int lines) {
        assertEquals(1, lines);
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
