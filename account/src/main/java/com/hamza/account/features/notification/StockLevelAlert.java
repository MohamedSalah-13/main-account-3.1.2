package com.hamza.account.features.notification;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationCenter;
import com.hamza.controlsfx.notifications.NotificationPreferences;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * Warns the moment an item is put on a sales invoice while its stock is at or
 * below its minimum, exhausted, or already negative.
 * <p>
 * An event, not a polled rule: the cashier needs to know while the item is in
 * front of them, not up to half an hour later when {@link LowStockSource} next
 * runs. The two overlap on purpose - this one catches it at the till, that one
 * catches the items nobody has tried to sell.
 * <p>
 * The balance judged is what is left <em>after</em> the quantity already on this
 * invoice, not the stored stock figure. Adding the last three units should say
 * "this leaves none", and the stored figure would still read three until the
 * invoice is saved. Each call site works that number out itself, because only it
 * knows how its rows convert to the item's base unit.
 * <p>
 * Keyed per item, so scanning the same low item five times leaves one entry with
 * a counter rather than five rows, and the cooldown stops it re-toasting for the
 * rest of the shift.
 */
@Log4j2
public final class StockLevelAlert {

    /** The switch in the settings screen. Kept here so both call sites read the same one. */
    public static final String EVENT_ID = "items.sale-stock-level";

    private static final DecimalFormat QUANTITY = new DecimalFormat("#.##");

    private StockLevelAlert() {
    }

    public static boolean isEnabled() {
        return NotificationPreferences.isEventEnabled(EVENT_ID, true);
    }

    public static void setEnabled(boolean enabled) {
        NotificationPreferences.setEventEnabled(EVENT_ID, enabled);
    }

    /**
     * Called after a row has been added to a sales invoice.
     *
     * @param item              the item that was added
     * @param remainingBalance  the item's balance less everything already on this
     *                          invoice, in the item's base unit
     */
    public static void check(ItemsModel item, double remainingBalance) {
        if (item == null || !isEnabled()) {
            return;
        }

        StockLevel level = StockLevel.of(remainingBalance, item.getMini_quantity());
        if (!level.isWorthReporting()) {
            return;
        }

        try {
            NotificationCenter.getInstance().publish(AppNotification.builder(EVENT_ID + "." + item.getId())
                    .category(NotificationCategories.ITEMS)
                    .severity(level.severity())
                    .title(titleOf(level))
                    .message(messageOf(item, remainingBalance, level))
                    .payload(item)
                    .build());
        } catch (Exception e) {
            // Never let a notification break the sale it was reporting on.
            log.error("Could not raise the stock level alert for item {}", item.getId(), e);
        }
    }

    private static String titleOf(StockLevel level) {
        return switch (level) {
            case NEGATIVE -> "رصيد الصنف بالسالب";
            case OUT_OF_STOCK -> "الصنف نفد من المخزن";
            case AT_MINIMUM -> "الصنف وصل للحد الأدنى";
            case OK -> "";
        };
    }

    private static String messageOf(ItemsModel item, double remainingBalance, StockLevel level) {
        String balance = "الرصيد بعد الفاتورة: " + QUANTITY.format(remainingBalance);
        // The minimum only explains the AT_MINIMUM case; on the other two it is
        // noise next to a balance of zero or less.
        if (level == StockLevel.AT_MINIMUM) {
            return item.getNameItem() + " - " + balance
                    + " - الحد الأدنى: " + QUANTITY.format(item.getMini_quantity());
        }
        return item.getNameItem() + " - " + balance;
    }

    /**
     * The item's balance less the quantity of it already sitting on the invoice.
     *
     * @param alreadyOnInvoice quantity of this item already on the invoice, in the
     *                         item's base unit - the caller converts, since rows may
     *                         use different units
     */
    public static double remainingAfter(@NotNull ItemsModel item, double alreadyOnInvoice) {
        return item.getSumAllBalance() - alreadyOnInvoice;
    }
}
