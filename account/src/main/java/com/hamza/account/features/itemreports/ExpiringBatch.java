package com.hamza.account.features.itemreports;

import java.time.LocalDate;

/**
 * One expiry batch of one item, with what is left of it.
 * <p>
 * A batch is an (item, expiry date) pair, not an item: the same product sits on the shelf
 * in several batches at once, bought on different days, and only one of them is about to
 * go off. A report that worked per item could only ever state the item's whole balance
 * against one date, which is the wrong number against every batch but one.
 * <p>
 * {@code alertDays} is the item's own {@code alert_days_before_expire}, so "about to
 * expire" means what the business said it means for that product - milk and tinned food do
 * not get the same warning.
 *
 * @param quantity what remains of this batch in base units, always greater than zero -
 *                 a batch that has been sold out is not on a shelf and is not reported
 */
public record ExpiringBatch(int itemId,
                            String barcode,
                            String name,
                            String groupName,
                            String unitName,
                            double buyPrice,
                            LocalDate expiry,
                            double quantity,
                            int alertDays) {

    /** What this batch cost to buy. The money actually at risk of being thrown away. */
    public double valueAtCost() {
        return buyPrice * quantity;
    }

    /** Negative once the date has passed, which is what makes an expired batch sort first. */
    public long daysUntil(LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
    }

    public boolean isExpired(LocalDate today) {
        return expiry.isBefore(today);
    }

    /**
     * How many days ahead this item wants to be warned, falling back to a month where the
     * item names no window of its own - which most items do not.
     */
    public int effectiveAlertDays() {
        return alertDays > 0 ? alertDays : DEFAULT_ALERT_DAYS;
    }

    /** The warning window for an item that never had one set. */
    public static final int DEFAULT_ALERT_DAYS = 30;
}
