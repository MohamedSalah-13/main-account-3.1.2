package com.hamza.account.features.invoice;

import com.hamza.account.features.returns.ReturnReason;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Immutable input captured from the invoice screen before persistence starts. */
public record InvoiceSaveCommand(
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
        boolean allowInsufficientStock,
        /**
         * The invoice this return reverses, or {@code 0} for a document that is not a
         * return, or a return entered without one - {@code source_invoice_number} is
         * nullable for exactly that reason (see {@code V16__return_source.sql}). Read
         * by {@code ReturnGuard} in {@code InvoiceSaveService.persist}, which does
         * nothing at all when this is {@code 0}: a free return is still a return, just
         * one nothing here can check.
         */
        int sourceInvoiceNumber,
        /** Why the document was returned, or {@code null} - unset until item 10's dialog offers one. */
        ReturnReason returnReason,
        List<? extends BasePurchasesAndSales> lines) {

    public InvoiceSaveCommand {
        invoiceDiscount = MoneyMath.money(invoiceDiscount);
        enteredPaid = MoneyMath.money(enteredPaid);
        notes = notes == null ? "" : notes.trim();
        partyName = partyName == null ? "" : partyName.trim();
        treasuryName = treasuryName == null ? "" : treasuryName.trim();
        delegateName = delegateName == null ? "" : delegateName.trim();
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** Compatibility constructor: no source invoice or reason, as every caller before item 10. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, BigDecimal invoiceDiscount,
                              DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName,
                              boolean allowInsufficientStock, List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount,
                discountType, enteredPaid, notes, partyId, partyName,
                treasuryName, delegateName, allowInsufficientStock, 0, null, lines);
    }

    /** Compatibility constructor for tests and callers using legacy double models. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, BigDecimal invoiceDiscount,
                              DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName, List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount,
                discountType, enteredPaid, notes, partyId, partyName,
                treasuryName, delegateName, false, lines);
    }

    /** Compatibility constructor for tests and callers using legacy double models. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, double invoiceDiscount,
                              DiscountType discountType, double enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName, List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType,
                MoneyMath.decimal(invoiceDiscount), discountType,
                MoneyMath.decimal(enteredPaid), notes, partyId, partyName,
                treasuryName, delegateName, false, lines);
    }

    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, double invoiceDiscount,
                              DiscountType discountType, double enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName,
                              boolean allowInsufficientStock, List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType,
                MoneyMath.decimal(invoiceDiscount), discountType,
                MoneyMath.decimal(enteredPaid), notes, partyId, partyName,
                treasuryName, delegateName, allowInsufficientStock, 0, null, lines);
    }

    /** Full constructor for a return entered against a known source invoice. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, double invoiceDiscount,
                              DiscountType discountType, double enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName,
                              boolean allowInsufficientStock, int sourceInvoiceNumber,
                              ReturnReason returnReason, List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType,
                MoneyMath.decimal(invoiceDiscount), discountType,
                MoneyMath.decimal(enteredPaid), notes, partyId, partyName,
                treasuryName, delegateName, allowInsufficientStock,
                sourceInvoiceNumber, returnReason, lines);
    }

    public boolean updating() {
        return existingInvoiceId > 0;
    }

    /** Whether this command names the invoice it reverses. */
    public boolean hasSourceInvoice() {
        return sourceInvoiceNumber > 0;
    }
}
