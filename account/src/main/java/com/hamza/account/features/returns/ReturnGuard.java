package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The last check before a return is persisted, alongside {@code InvoiceStockGuard} in
 * {@code InvoiceSaveService.persist} - and, like it, a no-op the moment there is
 * nothing to check. That happens for every document that is not a return, and for a
 * return entered without a source invoice: {@code source_invoice_number} is nullable
 * exactly so a free return keeps working, and this guard is what "nothing to check"
 * means for one.
 */
public final class ReturnGuard {

    private final ReturnableRepository repository;
    private final ReturnPolicy policy;

    public ReturnGuard(ReturnableRepository repository) {
        this(repository, ReturnPolicy.DEFAULT);
    }

    public ReturnGuard(ReturnableRepository repository, ReturnPolicy policy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * @param returnType        the return being saved - {@code SALES_RETURN} or {@code PURCHASE_RETURN}
     * @param sourceInvoiceNumber the invoice it names, or {@code 0} for none
     * @param excludingReturnId  the return's own id when updating one already saved, so
     *                           it is not checked against its own previous quantities;
     *                           {@code 0} when creating a new one
     * @param invoiceType        how the return itself is being settled
     * @param lines              the return's proposed lines
     */
    public void validate(DocumentType returnType, int sourceInvoiceNumber,
                         int excludingReturnId, InvoiceType invoiceType,
                         List<? extends BasePurchasesAndSales> lines) throws DaoException {
        Objects.requireNonNull(returnType, "returnType");
        if (sourceInvoiceNumber <= 0) {
            if (returnType.isReturn() && policy.requireSourceInvoice()) {
                throw new BusinessRuleException(message("return.error.source.required"));
            }
            return;
        }

        DocumentType sourceType = returnType.reverses();
        if (!repository.sourceExists(sourceType, sourceInvoiceNumber)) {
            throw new BusinessRuleException(message("return.error.source.not.found"));
        }

        requireSettlementMatchesSource(sourceType, sourceInvoiceNumber, invoiceType);

        ReturnableDocument source = ReturnableDocument.of(sourceType, sourceInvoiceNumber,
                repository.sourceLines(sourceType, sourceInvoiceNumber));
        Map<Integer, Double> alreadyReturned = repository.alreadyReturnedBaseQuantities(
                returnType, sourceInvoiceNumber, excludingReturnId);

        ReturnEligibility.Decision decision = ReturnEligibility.check(
                source, alreadyReturned, proposedLines(lines), policy);
        if (!decision.isAllowed()) {
            throw new BusinessRuleException(
                    ((ReturnEligibility.Decision.Refused) decision).message());
        }
    }

    /**
     * A return of a cash invoice must itself be cash.
     * <p>
     * A cash invoice was settled in full at the counter: nothing was ever put on the
     * party's account, so a return of it has an account balance of exactly zero to
     * reverse. Settling that return on account instead credits a balance that never
     * existed - and on the walk-in "بيع نقدى" customer, which is where cash sales go,
     * there is no account at all for the credit to sit in. It simply accumulates
     * against a party nobody will ever collect from or pay.
     * <p>
     * The reverse is deliberately allowed: a return of a <em>deferred</em> invoice may
     * be settled in cash (refunding someone who still owes you is a real thing) or on
     * account (the ordinary credit note).
     */
    private void requireSettlementMatchesSource(DocumentType sourceType, int sourceInvoiceNumber,
                                                InvoiceType invoiceType) throws DaoException {
        if (invoiceType != InvoiceType.DEFER) {
            return;
        }
        InvoiceType sourceInvoiceType = repository
                .sourceInvoiceType(sourceType, sourceInvoiceNumber)
                .orElse(null);
        if (sourceInvoiceType == InvoiceType.CASH) {
            throw new BusinessRuleException(
                    message("return.error.deferred.against.cash", sourceInvoiceNumber));
        }
    }

    private static List<ReturnEligibility.LineQuantity> proposedLines(
            List<? extends BasePurchasesAndSales> lines) {
        List<ReturnEligibility.LineQuantity> result = new ArrayList<>();
        for (BasePurchasesAndSales line : lines) {
            if (line == null || line.getItems() == null) {
                continue;
            }
            result.add(new ReturnEligibility.LineQuantity(line.getItems().getId(),
                    ItemUnits.toBase(line.getQuantity(), line.getUnitsType())));
        }
        return result;
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
