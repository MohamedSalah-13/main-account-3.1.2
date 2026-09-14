package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentLedgerEffect;
import com.hamza.account.document.DocumentType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Everything a printed invoice says, read once before any page is laid out.
 * <p>
 * <b>The figures are the saved header's, not the screen's.</b> {@code total} is the header's
 * total - the lines after their own discounts - {@code discount} the invoice's additional
 * discount, and {@code paid} its cash column. The net and what is left are worked out here from
 * those three by {@link DocumentLedgerEffect}, the one statement of what a document does to an
 * account, so the paper cannot owe a different amount from the statement. The printout this
 * replaced carried the total before the additional discount under the word "total", and nothing
 * of what was paid or left: a customer given a discount was handed a figure larger than their
 * debt.
 *
 * @param printedAt when the paper was printed, as it is to be read
 * @param balance   the party's balance either side of this document, or null when it is not
 *                  printed - a cash document, or a reader who may not see the party's account
 */
public record InvoicePrintDocument(
        Letterhead letterhead,
        DocumentType type,
        int number,
        String date,
        String partyName,
        InvoiceType invoiceType,
        String stockName,
        String delegateName,
        int sourceInvoiceNumber,
        String returnReason,
        String notes,
        List<ModelPrintInvoice> lines,
        BigDecimal total,
        BigDecimal discount,
        BigDecimal paid,
        String printedAt,
        Balance balance) {

    public InvoicePrintDocument {
        Objects.requireNonNull(letterhead, "letterhead");
        Objects.requireNonNull(type, "type");
        invoiceType = invoiceType == null ? InvoiceType.CASH : invoiceType;
        date = text(date);
        partyName = text(partyName);
        stockName = text(stockName);
        delegateName = text(delegateName);
        returnReason = text(returnReason);
        notes = text(notes);
        printedAt = text(printedAt);
        lines = List.copyOf(lines);
        total = MoneyMath.money(total);
        discount = MoneyMath.money(discount);
        paid = MoneyMath.money(paid);
    }

    /** What the document came to after its additional discount. */
    public BigDecimal net() {
        return effect().net();
    }

    /** What the cash did not cover, which went onto the party's account. */
    public BigDecimal rest() {
        return effect().onAccount();
    }

    public boolean deferred() {
        return invoiceType == InvoiceType.DEFER;
    }

    /** How much this document moved the party's balance, in the ledger's own sign. */
    public BigDecimal balanceChange() {
        return effect().balanceChange();
    }

    private DocumentLedgerEffect effect() {
        return new DocumentLedgerEffect(type, total, discount, paid);
    }

    /** The company as the letterhead prints it. Blank fields are left off the page. */
    public record Letterhead(String name, String address, String phone, String commercial,
                             String tax, byte[] logo) {

        public static final Letterhead EMPTY = new Letterhead("", "", "", "", "", null);

        public Letterhead {
            name = text(name);
            address = text(address);
            phone = text(phone);
            commercial = text(commercial);
            tax = text(tax);
        }
    }

    /**
     * The party's balance straight before and straight after this document, read off the
     * document's own row of the statement - so a reprint a month later still says what the
     * balance was then.
     */
    public record Balance(BigDecimal before, BigDecimal after) {

        public Balance {
            before = MoneyMath.money(before);
            after = MoneyMath.money(after);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
