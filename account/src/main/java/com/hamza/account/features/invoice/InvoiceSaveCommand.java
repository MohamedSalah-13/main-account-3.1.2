package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Immutable input captured from the invoice screen before persistence starts. */
public record InvoiceSaveCommand<T extends BasePurchasesAndSales>(
        int existingInvoiceId,
        LocalDate invoiceDate,
        InvoiceType invoiceType,
        BigDecimal invoiceDiscount,
        DiscountType discountType,
        BigDecimal enteredPaid,
        String notes,
        int partyId,
        String partyName,
        String treasuryName,
        String delegateName,
        List<T> lines) {

    public InvoiceSaveCommand {
        invoiceDiscount = MoneyMath.money(invoiceDiscount);
        enteredPaid = MoneyMath.money(enteredPaid);
        notes = notes == null ? "" : notes.trim();
        partyName = partyName == null ? "" : partyName.trim();
        treasuryName = treasuryName == null ? "" : treasuryName.trim();
        delegateName = delegateName == null ? "" : delegateName.trim();
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** Compatibility constructor for tests and callers using legacy double models. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, double invoiceDiscount,
                              DiscountType discountType, double enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName, List<T> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType,
                MoneyMath.decimal(invoiceDiscount), discountType,
                MoneyMath.decimal(enteredPaid), notes, partyId, partyName,
                treasuryName, delegateName, lines);
    }

    public boolean updating() {
        return existingInvoiceId > 0;
    }
}
