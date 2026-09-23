package com.hamza.account.features.party.ageing;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * One party's debt, split by how overdue it is.
 *
 * <p><b>The row reconciles, and the constructor is where that is enforced.</b> The five bands
 * plus {@link #unallocated()} equal {@link #balance()}, because {@code unallocated} is defined
 * as the difference - see {@link PartyAgeingQuery}. Checking it here as well is not
 * belt-and-braces: it is the only place a mistake in the SQL, or a column read into the wrong
 * field by a mapper, would ever be noticed. A wrong ageing report looks exactly like a right
 * one.
 *
 * @param partyId       the party
 * @param name          their name
 * @param phone         their telephone, or empty
 * @param areaName      their area, or empty when they have none or its row was deleted
 * @param paymentTerms  the agreed days, which is what the bands were measured from
 * @param buckets       what is owed in each band; every band is present, zero when nothing
 * @param unallocated   what this party owes that sits on no open invoice - opening balance,
 *                      returns, notes, and payments taken on account. Usually negative
 * @param balance       what they owe altogether, read from the ledger - in the party's own currency
 *                      (V82, docs/currency-plan.md §14 ق-ج٨), as every figure before it is. For a party
 *                      in the base, the base balance
 * @param currencyId    the party's currency, or {@code null} for the base
 * @param bookBalance   the same balance in the books, in the base. Equal to {@code balance} for a party
 *                      in the base; for a foreign one it differs by the rates its movements were valued at
 */
public record PartyAgeingRow(
        int partyId,
        String name,
        String phone,
        String areaName,
        int paymentTerms,
        Map<AgeingBucket, BigDecimal> buckets,
        BigDecimal unallocated,
        BigDecimal balance,
        Integer currencyId,
        BigDecimal bookBalance) {

    /**
     * Rounding is to two places (three in a currency that has them), so the bands and the balance can
     * differ by half a piastre per band without either being wrong. Anything larger is a defect.
     */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    public PartyAgeingRow {
        Map<AgeingBucket, BigDecimal> complete = new EnumMap<>(AgeingBucket.class);
        for (AgeingBucket bucket : AgeingBucket.values()) {
            BigDecimal amount = buckets == null ? null : buckets.get(bucket);
            complete.put(bucket, amountOf(amount));
        }
        buckets = java.util.Collections.unmodifiableMap(complete);
        unallocated = amountOf(unallocated);
        balance = amountOf(balance);
        bookBalance = bookBalance == null ? balance : MoneyMath.money(bookBalance);
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        areaName = areaName == null ? "" : areaName;

        BigDecimal reconciled = unallocated;
        for (BigDecimal amount : complete.values()) {
            reconciled = reconciled.add(amount);
        }
        if (reconciled.subtract(balance).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalArgumentException(
                    "Ageing row for party " + partyId + " does not reconcile: bands and unallocated"
                            + " come to " + reconciled + " but the balance is " + balance);
        }
    }

    /** A party in the base, whose figures in the books are its figures. */
    public PartyAgeingRow(int partyId, String name, String phone, String areaName, int paymentTerms,
                          Map<AgeingBucket, BigDecimal> buckets, BigDecimal unallocated, BigDecimal balance) {
        this(partyId, name, phone, areaName, paymentTerms, buckets, unallocated, balance, null, null);
    }

    /**
     * An amount as the row holds it: nothing is zero, and at least two places - never rounded to two,
     * since a row of a party in a foreign currency is in its places, and a Kuwaiti dinar has three.
     */
    private static BigDecimal amountOf(BigDecimal value) {
        if (value == null) {
            return MoneyMath.ZERO;
        }
        return value.scale() < MoneyMath.ZERO.scale() ? value.setScale(MoneyMath.ZERO.scale()) : value;
    }

    /** Whether the party deals in a currency other than the base. */
    public boolean isForeign() {
        return currencyId != null;
    }

    /** What is in one band. Never null - every band is present. */
    public BigDecimal amount(AgeingBucket bucket) {
        return buckets.get(bucket);
    }

    /** What has actually fallen due: the four overdue bands, without {@code CURRENT}. */
    public BigDecimal overdue() {
        BigDecimal total = BigDecimal.ZERO;
        for (AgeingBucket bucket : AgeingBucket.values()) {
            if (bucket.isOverdue()) {
                total = total.add(amount(bucket));
            }
        }
        return amountOf(total);
    }

    /** What the open invoices account for, across all five bands. */
    public BigDecimal onOpenInvoices() {
        BigDecimal total = BigDecimal.ZERO;
        for (AgeingBucket bucket : AgeingBucket.values()) {
            total = total.add(amount(bucket));
        }
        return amountOf(total);
    }

    /**
     * Whether this row is worth a manager's attention: something is past its due date.
     * <p>
     * Not "the balance is positive" - a party can owe nothing on balance and still have an
     * invoice ninety days overdue, offset by a payment nobody allocated.
     */
    public boolean isOverdue() {
        return overdue().signum() > 0;
    }
}
