package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.reportData.Print_Reports;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Prepares immutable invoice print data and executes the selected print format. */
public final class InvoicePrintService {

    private final Supplier<Print_Reports> reportsFactory;

    public InvoicePrintService() {
        this(Print_Reports::new);
    }

    InvoicePrintService(Supplier<Print_Reports> reportsFactory) {
        this.reportsFactory = reportsFactory;
    }

    public <T extends BasePurchasesAndSales> InvoicePrintRequest prepare(
            List<T> source,
            String partyName,
            int invoiceNumber,
            double discount,
            String printedAt,
            LocalDate invoiceDate,
            boolean receipt,
            Map<String, Object> invoiceDetails,
            String reportName) {
        List<ModelPrintInvoice> lines = source.stream()
                .map(line -> new ModelPrintInvoice(
                        line.getItems().getNameItem(), line.getItems().getBarcode(),
                        line.getUnitsType().getUnit_name(), line.getPrice(), line.getQuantity(),
                        line.getTotal(), line.getDiscount(),
                        MoneyMath.asDouble(MoneyMath.subtract(
                                MoneyMath.decimal(line.getTotal()),
                                MoneyMath.decimal(line.getDiscount())))))
                .toList();
        return new InvoicePrintRequest(lines, partyName, invoiceNumber, discount,
                printedAt, invoiceDate, receipt, invoiceDetails, reportName);
    }

    public void print(InvoicePrintRequest request) {
        Print_Reports reports = reportsFactory.get();
        if (request.receipt()) {
            reports.printReceiptInvoice(request.lines(), request.partyName(),
                    request.invoiceNumber(), request.discount(), request.printedAt(),
                    request.invoiceDate().toString(), 0);
            return;
        }
        reports.printInvoice(request.lines(), new HashMap<>(request.invoiceDetails()),
                request.reportName());
    }
}
