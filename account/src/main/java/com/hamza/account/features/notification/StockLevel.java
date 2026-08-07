package com.hamza.account.features.notification;

import com.hamza.controlsfx.notifications.NotificationSeverity;

/**
 * How an item's stock stands at the moment it is put on a sales invoice.
 * <p>
 * Split out from {@link StockLevelAlert} as a plain function of two numbers so the
 * boundaries - is exactly at the minimum "reached", is exactly zero "out" - can be
 * tested without a screen, a database or a notification centre. Those boundaries
 * are the part that is easy to get wrong by one.
 */
public enum StockLevel {

    /** Comfortably above the minimum, or no minimum set. Nothing to say. */
    OK(null),

    /** At or below the minimum the item was configured with, but still in stock. */
    AT_MINIMUM(NotificationSeverity.WARNING),

    /** Nothing left. Selling any more of it goes negative. */
    OUT_OF_STOCK(NotificationSeverity.WARNING),

    /**
     * Already sold past what the stock says exists. Not just low - the recorded
     * balance is now wrong, which is why it is louder than being out of stock.
     */
    NEGATIVE(NotificationSeverity.ERROR);

    private final NotificationSeverity severity;

    StockLevel(NotificationSeverity severity) {
        this.severity = severity;
    }

    /**
     * @param balance     what is left of the item once everything already on this
     *                    invoice is taken off, in the item's base unit
     * @param miniQuantity the minimum the item is configured with; zero or less
     *                     means no minimum was set, so {@link #AT_MINIMUM} cannot
     *                     apply and every item would otherwise report itself as low
     */
    public static StockLevel of(double balance, double miniQuantity) {
        if (balance < 0) {
            return NEGATIVE;
        }
        if (balance == 0) {
            return OUT_OF_STOCK;
        }
        if (miniQuantity > 0 && balance <= miniQuantity) {
            return AT_MINIMUM;
        }
        return OK;
    }

    public boolean isWorthReporting() {
        return severity != null;
    }

    public NotificationSeverity severity() {
        return severity;
    }
}
