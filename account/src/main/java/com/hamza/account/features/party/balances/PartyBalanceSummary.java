package com.hamza.account.features.party.balances;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;

/**
 * The footer of the balances list: the figures for everything the filter matched, not for the page.
 * <p>
 * Read in one statement built from the same select the page is, so the footer and the table cannot
 * come to describe different sets. The screen this replaces summed the rows it had loaded, which was
 * the whole table - right by accident, and wrong the moment a filter or a page appeared.
 *
 * @param parties       how many parties matched
 * @param totalBalance  what they come to together: the debtors less the creditors
 * @param totalOwed     what the debtors owe, unsigned
 * @param totalInCredit what the shop owes the creditors, unsigned
 * @param overLimit     how many have passed their credit limit
 */
public record PartyBalanceSummary(int parties, BigDecimal totalBalance, BigDecimal totalOwed,
                                  BigDecimal totalInCredit, int overLimit) {

    public static final PartyBalanceSummary EMPTY =
            new PartyBalanceSummary(0, null, null, null, 0);

    public PartyBalanceSummary {
        totalBalance = MoneyMath.money(totalBalance == null ? BigDecimal.ZERO : totalBalance);
        totalOwed = MoneyMath.money(totalOwed == null ? BigDecimal.ZERO : totalOwed);
        totalInCredit = MoneyMath.money(totalInCredit == null ? BigDecimal.ZERO : totalInCredit);
    }
}
