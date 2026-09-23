package com.hamza.account.features.party.balances;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One party on the balances list.
 *
 * @param partyId      the party
 * @param name         their name
 * @param phone        their telephone, or empty
 * @param areaId       their area, or 0
 * @param areaName     that area's name, or empty - a party whose area row was deleted keeps the id
 * @param creditLimit  their limit; zero means none set, which is the reading the whole application
 *                     takes, and a supplier never has one
 * @param priceTierId  a customer's price tier, or 0
 * @param balance      what they owe as at the list's date. Positive means they owe the shop
 * @param periodDebit  what was charged to them inside the period window
 * @param periodCredit what they paid or were credited inside it
 * @param lastMovement the date of their most recent movement, or null for a party with none
 * @param balanceOwn      the balance in the party's own currency (V82, docs/currency-plan.md §14) - for
 *                        a party in the base, the balance itself. Not rounded to two places
 * @param periodDebitOwn  the period's debit in it
 * @param periodCreditOwn the period's credit in it
 * @param currencyId      the party's currency, or {@code null} for the base. The credit limit is written
 *                        in it
 */
public record PartyBalanceRow(
        int partyId,
        String name,
        String phone,
        int areaId,
        String areaName,
        BigDecimal creditLimit,
        int priceTierId,
        BigDecimal balance,
        BigDecimal periodDebit,
        BigDecimal periodCredit,
        LocalDate lastMovement,
        BigDecimal balanceOwn,
        BigDecimal periodDebitOwn,
        BigDecimal periodCreditOwn,
        Integer currencyId) {

    public PartyBalanceRow {
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        areaName = areaName == null ? "" : areaName;
        creditLimit = MoneyMath.money(creditLimit == null ? BigDecimal.ZERO : creditLimit);
        balance = MoneyMath.money(balance == null ? BigDecimal.ZERO : balance);
        periodDebit = MoneyMath.money(periodDebit == null ? BigDecimal.ZERO : periodDebit);
        periodCredit = MoneyMath.money(periodCredit == null ? BigDecimal.ZERO : periodCredit);
        balanceOwn = balanceOwn == null ? balance : balanceOwn;
        periodDebitOwn = periodDebitOwn == null ? periodDebit : periodDebitOwn;
        periodCreditOwn = periodCreditOwn == null ? periodCredit : periodCreditOwn;
    }

    /** A party in the base, whose figures in its own currency are its figures. */
    public PartyBalanceRow(int partyId, String name, String phone, int areaId, String areaName,
                           BigDecimal creditLimit, int priceTierId, BigDecimal balance, BigDecimal periodDebit,
                           BigDecimal periodCredit, LocalDate lastMovement) {
        this(partyId, name, phone, areaId, areaName, creditLimit, priceTierId, balance, periodDebit,
                periodCredit, lastMovement, null, null, null, null);
    }

    /** Whether the party deals in a currency other than the base. */
    public boolean isForeign() {
        return currencyId != null;
    }

    /** Whether a limit is set at all. Zero means none, not "a limit of nothing". */
    public boolean hasCreditLimit() {
        return creditLimit.signum() > 0;
    }

    /**
     * Whether the balance has passed the limit. False when there is no limit to pass. The limit is
     * written in the party's own currency, so it is held against the balance in it.
     */
    public boolean isOverCreditLimit() {
        return hasCreditLimit() && balanceOwn.compareTo(creditLimit) > 0;
    }

    /**
     * How much of the limit the balance uses, as a fraction. Zero when no limit is set.
     * <p>
     * Capped at nothing: a caller showing a bar caps it, and a caller showing a percentage wants to
     * see 140% rather than 100%.
     */
    public double limitUsage() {
        if (!hasCreditLimit() || balanceOwn.signum() <= 0) {
            return 0;
        }
        return balanceOwn.divide(creditLimit, 4, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /** Days since the last movement, as at {@code today}, or -1 for a party that never moved. */
    public long idleDays(LocalDate today) {
        return lastMovement == null ? -1 : java.time.temporal.ChronoUnit.DAYS.between(lastMovement, today);
    }
}
