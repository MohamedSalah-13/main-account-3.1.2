package com.hamza.account.features.party.currency;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyConverter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a document's header says in its party's currency (V82, docs/currency-plan.md §14 ق-ج٣).
 * <p>
 * The document is written in the base - its prices, its lines, its total, its discount and its cash -
 * and these are the same three header figures translated at one rate, copied beside them. Nothing
 * reads them for a base figure; the party's ledger reads them for what the party owes in its own
 * currency.
 * <p>
 * <b>The rounding leaves nothing behind.</b> Translated one by one, a total, a discount and a cash
 * payment each round on their own, and a cash invoice - whose cash is its net, so it leaves nothing on
 * the account - could leave a cent in the party's currency for ever. So the net is translated once and
 * what the cash did not cover once, and the cash is the difference between the two; the total is
 * translated once and the discount is its difference from the net. A cash invoice therefore leaves
 * exactly zero in the party's currency, as it does in the base, and the net is the translated net
 * whatever the rounding did.
 *
 * @param rate     base units per one unit of the party's currency, copied onto the header
 * @param total    the header's {@code total} in the party's currency
 * @param discount the header's {@code discount} in it
 * @param paid     the header's cash column in it
 */
public record DocumentTranslation(BigDecimal rate, BigDecimal total, BigDecimal discount, BigDecimal paid) {

    public DocumentTranslation {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(paid, "paid");
    }

    /**
     * The header's three base figures in {@code currency} at {@code rate}.
     *
     * @param total    the document's total before its discount, in the base
     * @param discount its discount as an amount, in the base
     * @param paid     what its cash column moved, in the base
     */
    public static DocumentTranslation of(BigDecimal total, BigDecimal discount, BigDecimal paid,
                                         BigDecimal rate, Currency currency) {
        BigDecimal baseTotal = orZero(total);
        BigDecimal baseNet = baseTotal.subtract(orZero(discount));
        BigDecimal baseRemainder = baseNet.subtract(orZero(paid));

        BigDecimal net = CurrencyConverter.fromBase(baseNet, rate, currency);
        BigDecimal remainder = CurrencyConverter.fromBase(baseRemainder, rate, currency);
        BigDecimal translatedTotal = CurrencyConverter.fromBase(baseTotal, rate, currency);
        return new DocumentTranslation(rate, translatedTotal, translatedTotal.subtract(net),
                net.subtract(remainder));
    }

    /** What the document comes to after its discount, in the party's currency. */
    public BigDecimal net() {
        return total.subtract(discount);
    }

    /** What the cash did not cover - the part that went onto the party's account. */
    public BigDecimal remainder() {
        return net().subtract(paid);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
