package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;

import java.util.List;

/** Values produced by a successful atomic invoice header/line save. */
public record InvoiceSaveResult<T1 extends BasePurchasesAndSales, T2 extends BaseTotals>(
        int invoiceNumber,
        boolean updated,
        T2 invoice,
        InvoicePaymentTerms payment,
        List<T1> persistedLines) {

    public InvoiceSaveResult {
        persistedLines = List.copyOf(persistedLines);
    }
}
