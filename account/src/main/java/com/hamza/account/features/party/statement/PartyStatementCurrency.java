package com.hamza.account.features.party.statement;

import com.hamza.account.features.currency.Currency;

import java.math.BigDecimal;
import java.util.List;

/**
 * Which of a statement's two sets of figures it shows (docs/currency-plan.md §14 ق-ج٨).
 * <p>
 * <b>A statement of a party in a foreign currency is written in that currency.</b> A customer who buys
 * in dollars owes dollars: a thousand-dollar invoice paid with a thousand dollars leaves nothing, whatever
 * the pound did between the two days - and that zero is the figure the customer is asked to agree with.
 * Its value in the books is a different question, answered beside it: the book value of each movement,
 * and of the balance at the end. The two part exactly by the difference the rates made, which is shown
 * and never posted (ق-ج٧).
 * <p>
 * A statement of a party in the base is the one it always was.
 *
 * @param foreign the party's currency, or {@code null} for the base
 */
public record PartyStatementCurrency(Currency foreign) {

    public static final PartyStatementCurrency BASE = new PartyStatementCurrency(null);

    /** True when the figures shown are the party's own, not the books'. */
    public boolean isForeign() {
        return foreign != null;
    }

    /** The row as it is shown: its own-currency figures in place of the base ones for a foreign party. */
    public PartyStatementRow shown(PartyStatementRow row) {
        return isForeign() ? row.inOwnCurrency() : row;
    }

    public List<PartyStatementRow> shown(List<PartyStatementRow> rows) {
        return isForeign() ? rows.stream().map(PartyStatementRow::inOwnCurrency).toList() : rows;
    }

    public PartyStatementSummary summary(PartyStatementTotals totals) {
        return isForeign() ? totals.own() : totals.base();
    }

    /** What a movement did to the party's account in the books - the statement's book-value column. */
    public BigDecimal bookValue(PartyStatementRow row) {
        return row.balanceChange();
    }

    /** The code written beside the figures - "USD" - or empty for the base, which writes none. */
    public String code() {
        return isForeign() ? foreign.code() : "";
    }
}
