package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Reads what a printed invoice needs besides its own header and lines: the letterhead, and - on a
 * deferred document - the party's balance either side of it.
 * <p>
 * Both arrive through the two interfaces below rather than from a service named here, so the
 * decisions are testable without a database: which documents carry a balance, what "before" means,
 * and what happens to a reader who may not see the party's account.
 * <p>
 * <b>A reader who may not see the account gets the invoice without the balance, not a refusal.</b>
 * The balance is the party's statement, guarded by {@code customer.account.show} /
 * {@code suppliers.account.show}; a cashier without it is still allowed to print the invoice they
 * have just written. A failure to <em>read</em> either is not swallowed - it is a technical fault,
 * and a paper printed silently without a figure it should carry is worse than an error.
 */
@Log4j2
public final class InvoicePrintDocumentBuilder {

    /** The company, as the letterhead prints it. */
    @FunctionalInterface
    public interface LetterheadSource {
        InvoicePrintDocument.Letterhead load() throws DaoException;
    }

    /**
     * The party's running balance on one document's row of its statement, or null when the
     * document is not in the ledger. May throw {@link BusinessRuleException} when the reader may
     * not see the account.
     */
    @FunctionalInterface
    public interface BalanceSource {
        BigDecimal balanceAfter(PartyKind kind, int partyId, boolean isReturn, int number) throws DaoException;
    }

    private final LetterheadSource letterhead;
    private final BalanceSource balance;

    public InvoicePrintDocumentBuilder(LetterheadSource letterhead, BalanceSource balance) {
        this.letterhead = Objects.requireNonNull(letterhead, "letterhead");
        this.balance = Objects.requireNonNull(balance, "balance");
    }

    /**
     * @param totals the saved header, read back after the save - never the screen's fields
     * @param lines  the document's lines, already captured for printing
     */
    public InvoicePrintDocument build(DocumentType type, BaseTotals totals, String partyName, int partyId,
                                      String delegateName, int sourceInvoiceNumber, String returnReason,
                                      List<ModelPrintInvoice> lines, String printedAt) throws DaoException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(totals, "totals");
        String stockName = totals.getStockData() == null ? "" : totals.getStockData().getName();
        InvoicePrintDocument withoutBalance = new InvoicePrintDocument(letterhead.load(), type,
                totals.getId(), totals.getDate(), partyName, totals.getInvoiceType(), stockName,
                type.hasDelegate() ? delegateName : "",
                type.isReturn() ? sourceInvoiceNumber : 0,
                type.isReturn() ? returnReason : "",
                totals.getNotes(), lines,
                MoneyMath.decimal(totals.getTotal()), MoneyMath.decimal(totals.getDiscount()),
                MoneyMath.decimal(totals.getPaid()), printedAt, null);
        InvoicePrintDocument.Balance partyBalance = balanceOf(withoutBalance, partyId);
        return partyBalance == null ? withoutBalance : withBalance(withoutBalance, partyBalance);
    }

    /**
     * Only a deferred document prints a balance: a cash one moves nothing on the account, and a
     * balance on every till receipt is a customer's debt read out to the queue behind them.
     */
    private InvoicePrintDocument.Balance balanceOf(InvoicePrintDocument document, int partyId) throws DaoException {
        if (!document.deferred() || partyId <= 0) {
            return null;
        }
        BigDecimal after;
        try {
            after = balance.balanceAfter(document.type().partyKind(), partyId,
                    document.type().isReturn(), document.number());
        } catch (BusinessRuleException refused) {
            log.info("Invoice {} printed without the party balance: the reader may not see the account",
                    document.number());
            return null;
        }
        if (after == null) {
            log.warn("Invoice {} is not in party {}'s ledger; printed without the balance",
                    document.number(), partyId);
            return null;
        }
        return new InvoicePrintDocument.Balance(MoneyMath.subtract(after, document.balanceChange()), after);
    }

    private static InvoicePrintDocument withBalance(InvoicePrintDocument d, InvoicePrintDocument.Balance b) {
        return new InvoicePrintDocument(d.letterhead(), d.type(), d.number(), d.date(), d.partyName(),
                d.invoiceType(), d.stockName(), d.delegateName(), d.sourceInvoiceNumber(), d.returnReason(),
                d.notes(), d.lines(), d.total(), d.discount(), d.paid(), d.printedAt(), b);
    }
}
