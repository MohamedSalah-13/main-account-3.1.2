package com.hamza.account.features.invoice;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.returns.ReturnReason;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        List<? extends BasePurchasesAndSales> lines,
        int stockId,
        /** Required audit explanation when an existing shift-owned invoice is changed. */
        String correctionReason,
        /** Database version read when an existing invoice was opened for editing. */
        LocalDateTime expectedUpdatedAt,
        /**
         * The currency the lines, the discount and the cash were typed in (V83), or {@code null} for the
         * base. The save refuses one that is not the document's own rather than converting it -
         * {@code InvoicePartyCurrency.rateFor}.
         */
        Integer documentCurrencyId,
        /**
         * The price tier the screen priced the document at (V84), or {@code null} for a caller that
         * does not know tiers - which keeps whatever the document already had. Read by
         * {@code InvoicePriceTier.decide}, which asks {@code sales.price.tier.change} for any tier but
         * the customer's own and the one the document was saved at.
         */
        Integer priceTierId) {

    /** Every caller before V84: no tier chosen on the screen. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate, InvoiceType invoiceType,
                              BigDecimal invoiceDiscount, DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName, String treasuryName,
                              String delegateName, boolean allowInsufficientStock, int sourceInvoiceNumber,
                              ReturnReason returnReason, List<? extends BasePurchasesAndSales> lines,
                              int stockId, String correctionReason, LocalDateTime expectedUpdatedAt,
                              Integer documentCurrencyId) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount, discountType, enteredPaid,
                notes, partyId, partyName, treasuryName, delegateName, allowInsufficientStock,
                sourceInvoiceNumber, returnReason, lines, stockId, correctionReason, expectedUpdatedAt,
                documentCurrencyId, null);
    }

    public InvoiceSaveCommand {
        invoiceDiscount = MoneyMath.money(invoiceDiscount);
        enteredPaid = MoneyMath.money(enteredPaid);
        notes = notes == null ? "" : notes.trim();
        partyName = partyName == null ? "" : partyName.trim();
        treasuryName = treasuryName == null ? "" : treasuryName.trim();
        delegateName = delegateName == null ? "" : delegateName.trim();
        correctionReason = correctionReason == null ? "" : correctionReason.trim();
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (stockId <= 0) throw new IllegalArgumentException("stockId must be positive");
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
                treasuryName, delegateName, allowInsufficientStock, 0, null, lines,
                DefaultStock.ID, null, null);
    }

    /** Compatibility constructor for tests and callers using legacy double models. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, BigDecimal invoiceDiscount,
                              DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName, List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount,
                discountType, enteredPaid, notes, partyId, partyName,
                treasuryName, delegateName, false, 0, null, lines,
                DefaultStock.ID, null, null);
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
                treasuryName, delegateName, false, 0, null, lines,
                DefaultStock.ID, null, null);
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
                treasuryName, delegateName, allowInsufficientStock, 0, null, lines,
                DefaultStock.ID, null, null);
    }

    /** Full constructor for a return entered against a known source invoice. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate, InvoiceType invoiceType,
                              BigDecimal invoiceDiscount, DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName, String treasuryName, String delegateName,
                              boolean allowInsufficientStock, int sourceInvoiceNumber, ReturnReason returnReason,
                              List<? extends BasePurchasesAndSales> lines) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount, discountType, enteredPaid, notes, partyId,
                partyName, treasuryName, delegateName, allowInsufficientStock, sourceInvoiceNumber, returnReason,
                lines, DefaultStock.ID, null, null);
    }

    /** Full constructor with an explicit warehouse. */
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
                sourceInvoiceNumber, returnReason, lines, DefaultStock.ID, null, null);

    }

    /** Compatibility constructor retained for callers that do not edit an existing invoice. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, BigDecimal invoiceDiscount,
                              DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName,
                              boolean allowInsufficientStock, int sourceInvoiceNumber,
                              ReturnReason returnReason, List<? extends BasePurchasesAndSales> lines,
                              int stockId) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount, discountType,
                enteredPaid, notes, partyId, partyName, treasuryName, delegateName,
                allowInsufficientStock, sourceInvoiceNumber, returnReason, lines, stockId, null, null);
    }

    /** Compatibility constructor for callers that do not yet carry an edit version. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate,
                              InvoiceType invoiceType, BigDecimal invoiceDiscount,
                              DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName,
                              String treasuryName, String delegateName,
                              boolean allowInsufficientStock, int sourceInvoiceNumber,
                              ReturnReason returnReason, List<? extends BasePurchasesAndSales> lines,
                              int stockId, String correctionReason) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount, discountType,
                enteredPaid, notes, partyId, partyName, treasuryName, delegateName,
                allowInsufficientStock, sourceInvoiceNumber, returnReason, lines,
                stockId, correctionReason, null);
    }

    /** Every caller before V83: figures typed in the base. */
    public InvoiceSaveCommand(int existingInvoiceId, LocalDate invoiceDate, InvoiceType invoiceType,
                              BigDecimal invoiceDiscount, DiscountType discountType, BigDecimal enteredPaid,
                              String notes, int partyId, String partyName, String treasuryName,
                              String delegateName, boolean allowInsufficientStock, int sourceInvoiceNumber,
                              ReturnReason returnReason, List<? extends BasePurchasesAndSales> lines,
                              int stockId, String correctionReason, LocalDateTime expectedUpdatedAt) {
        this(existingInvoiceId, invoiceDate, invoiceType, invoiceDiscount, discountType, enteredPaid,
                notes, partyId, partyName, treasuryName, delegateName, allowInsufficientStock,
                sourceInvoiceNumber, returnReason, lines, stockId, correctionReason, expectedUpdatedAt, null);
    }

    public boolean updating() {
        return existingInvoiceId > 0;
    }

    /** Whether this command names the invoice it reverses. */
    public boolean hasSourceInvoice() {
        return sourceInvoiceNumber > 0;
    }
}
