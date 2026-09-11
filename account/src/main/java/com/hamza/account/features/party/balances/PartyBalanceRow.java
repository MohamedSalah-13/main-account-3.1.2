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
        LocalDate lastMovement) {

    public PartyBalanceRow {
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        areaName = areaName == null ? "" : areaName;
        creditLimit = MoneyMath.money(creditLimit == null ? BigDecimal.ZERO : creditLimit);
        balance = MoneyMath.money(balance == null ? BigDecimal.ZERO : balance);
        periodDebit = MoneyMath.money(periodDebit == null ? BigDecimal.ZERO : periodDebit);
        periodCredit = MoneyMath.money(periodCredit == null ? BigDecimal.ZERO : periodCredit);
    }

    /** Whether a limit is set at all. Zero means none, not "a limit of nothing". */
    public boolean hasCreditLimit() {
        return creditLimit.signum() > 0;
    }

    /** Whether the balance has passed the limit. False when there is no limit to pass. */
    public boolean isOverCreditLimit() {
        return hasCreditLimit() && balance.compareTo(creditLimit) > 0;
    }

    /**
     * How much of the limit the balance uses, as a fraction. Zero when no limit is set.
     * <p>
     * Capped at nothing: a caller showing a bar caps it, and a caller showing a percentage wants to
     * see 140% rather than 100%.
     */
    public double limitUsage() {
        if (!hasCreditLimit() || balance.signum() <= 0) {
            return 0;
        }
        return balance.divide(creditLimit, 4, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /** Days since the last movement, as at {@code today}, or -1 for a party that never moved. */
    public long idleDays(LocalDate today) {
        return lastMovement == null ? -1 : java.time.temporal.ChronoUnit.DAYS.between(lastMovement, today);
    }
}
