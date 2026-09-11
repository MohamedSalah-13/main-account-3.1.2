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
 * @param balance       what they owe altogether, read from the ledger
 */
public record PartyAgeingRow(
        int partyId,
        String name,
        String phone,
        String areaName,
        int paymentTerms,
        Map<AgeingBucket, BigDecimal> buckets,
        BigDecimal unallocated,
        BigDecimal balance) {

    /**
     * Rounding is to two places, so the bands and the balance can differ by half a piastre per
     * band without either being wrong. Anything larger is a defect.
     */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    public PartyAgeingRow {
        Map<AgeingBucket, BigDecimal> complete = new EnumMap<>(AgeingBucket.class);
        for (AgeingBucket bucket : AgeingBucket.values()) {
            BigDecimal amount = buckets == null ? null : buckets.get(bucket);
            complete.put(bucket, MoneyMath.money(amount == null ? BigDecimal.ZERO : amount));
        }
        buckets = java.util.Collections.unmodifiableMap(complete);
        unallocated = MoneyMath.money(unallocated == null ? BigDecimal.ZERO : unallocated);
        balance = MoneyMath.money(balance == null ? BigDecimal.ZERO : balance);
        name = name == null ? "" : name;
        phone = phone == null ? "" : phone;
        areaName = areaName == null ? "" : areaName;

        BigDecimal reconciled = unallocated;
        for (BigDecimal amount : complete.values()) {
            reconciled = MoneyMath.add(reconciled, amount);
        }
        if (reconciled.subtract(balance).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalArgumentException(
                    "Ageing row for party " + partyId + " does not reconcile: bands and unallocated"
                            + " come to " + reconciled + " but the balance is " + balance);
        }
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
                total = MoneyMath.add(total, amount(bucket));
            }
        }
        return total;
    }

    /** What the open invoices account for, across all five bands. */
    public BigDecimal onOpenInvoices() {
        BigDecimal total = BigDecimal.ZERO;
        for (AgeingBucket bucket : AgeingBucket.values()) {
            total = MoneyMath.add(total, amount(bucket));
        }
        return total;
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
