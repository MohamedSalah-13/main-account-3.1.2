package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.reportData.Print_Reports;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoicePrintServiceTest {

    @Test
    void preparesAStablePrintLineFromAnInvoiceLine() {
        InvoicePrintService service = new InvoicePrintService(() -> mock(Print_Reports.class));
        Sales line = line();

        InvoicePrintRequest request = service.prepare(List.of(line), "عميل", 42,
                2, "2026/08/13 09:00", LocalDate.of(2026, 8, 13), true,
                Map.of("id", 42), "فاتورة بيع");

        assertEquals(1, request.lines().size());
        assertEquals("صنف", request.lines().getFirst().getName_item());
        assertEquals(18, request.lines().getFirst().getTotal_amount());

        line.setTotal(999);
        assertEquals(18, request.lines().getFirst().getTotal_amount(),
                "print data must not follow later table-row mutations");
    }

    @Test
    void routesReceiptAndStandardFormatsToTheirDedicatedPrinterMethods() {
        Print_Reports reports = mock(Print_Reports.class);
        InvoicePrintService service = new InvoicePrintService(() -> reports);
        InvoicePrintRequest receipt = service.prepare(List.of(line()), "عميل", 42,
                2, "now", LocalDate.of(2026, 8, 13), true,
                Map.of("id", 42), "فاتورة بيع");
        InvoicePrintRequest standard = service.prepare(List.of(line()), "عميل", 42,
                2, "now", LocalDate.of(2026, 8, 13), false,
                Map.of("id", 42), "فاتورة بيع");

        service.print(receipt);
        service.print(standard);

        verify(reports).printReceiptInvoice(receipt.lines(), "عميل", 42,
                2, "now", "2026-08-13", 0);
        verify(reports).printInvoice(eq(standard.lines()), any(), eq("فاتورة بيع"));
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
