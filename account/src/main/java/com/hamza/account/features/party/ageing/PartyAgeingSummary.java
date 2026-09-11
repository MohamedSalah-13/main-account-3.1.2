package com.hamza.account.features.party.ageing;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * The foot of the ageing report: what the whole filtered set comes to, per band.
 *
 * <p>Read in one statement built from the same select the page is, so the footer and the table
 * cannot describe different sets - and, as on every row, the bands plus {@code unallocated}
 * equal {@code balance}.
 *
 * @param parties     how many parties matched
 * @param buckets     the total in each band
 * @param unallocated the total that sits on no open invoice
 * @param balance     what they all owe together
 */
public record PartyAgeingSummary(
        int parties,
        Map<AgeingBucket, BigDecimal> buckets,
        BigDecimal unallocated,
        BigDecimal balance) {

    public static final PartyAgeingSummary EMPTY =
            new PartyAgeingSummary(0, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO);

    public PartyAgeingSummary {
        Map<AgeingBucket, BigDecimal> complete = new EnumMap<>(AgeingBucket.class);
        for (AgeingBucket bucket : AgeingBucket.values()) {
            BigDecimal amount = buckets == null ? null : buckets.get(bucket);
            complete.put(bucket, MoneyMath.money(amount == null ? BigDecimal.ZERO : amount));
        }
        buckets = java.util.Collections.unmodifiableMap(complete);
        unallocated = MoneyMath.money(unallocated == null ? BigDecimal.ZERO : unallocated);
        balance = MoneyMath.money(balance == null ? BigDecimal.ZERO : balance);
    }

    public BigDecimal amount(AgeingBucket bucket) {
        return buckets.get(bucket);
    }

    /** The four overdue bands together - the number the report exists to produce. */
    public BigDecimal overdue() {
        BigDecimal total = BigDecimal.ZERO;
        for (AgeingBucket bucket : AgeingBucket.values()) {
            if (bucket.isOverdue()) {
                total = MoneyMath.add(total, amount(bucket));
            }
        }
        return total;
    }

    /**
     * What share of the debt is overdue, as a percentage, or zero when nothing is owed.
     * <p>
     * Measured against the open invoices rather than against the balance: a set whose balance
     * is near zero because of unallocated payments would otherwise produce a meaningless
     * percentage, or a division by zero.
     */
    public BigDecimal overduePercent() {
        BigDecimal onInvoices = BigDecimal.ZERO;
        for (AgeingBucket bucket : AgeingBucket.values()) {
            onInvoices = MoneyMath.add(onInvoices, amount(bucket));
        }
        if (onInvoices.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return overdue().multiply(BigDecimal.valueOf(100))
                .divide(onInvoices, 2, java.math.RoundingMode.HALF_UP);
    }
}
