package com.hamza.account.config;

import java.util.Set;

/**
 * The settings that belong to the shop rather than to a computer.
 *
 * <p>Everything in {@link PropertiesName} is stored per Windows profile. This list is the
 * exception: a key named here is read from and written to {@code app_setting}, so every
 * till answers the same way, and a key not named here stays exactly where it was.
 *
 * <p><b>The test for membership is not "would it be convenient to share".</b> It is: would
 * two machines disagreeing about this be a <em>defect</em>? A printer name differs by
 * machine and should. A scale barcode's layout differs by machine only because somebody
 * typed it twice.
 *
 * <p>What is deliberately <b>not</b> here, and why, because the omissions are the part
 * that gets argued about later:
 *
 * <ul>
 *   <li><b>The price-check screen's settings.</b> {@code price.check.stock} names the
 *       warehouse that one wall-mounted display answers for. Sharing it would make every
 *       display report one branch's stock.</li>
 *   <li><b>Printers, paths, fonts, colours, window sizes, the open tab.</b> Facts about a
 *       desk.</li>
 *   <li><b>The backup folder, password and interval.</b> A folder path is per machine; who
 *       runs the scheduled backup is a different question, and it is answered by
 *       {@link #BACKUP_OWNER_MACHINE} rather than by sharing the schedule.</li>
 * </ul>
 */
public final class SharedSettingKeys {

    /**
     * Which computer runs the scheduled backup, by {@link MachineId}.
     * <p>
     * Not a user setting - no screen writes it as text - but it is shop-wide state of
     * exactly this shape, and giving it a table of its own to hold one row would be a
     * second store to keep in step.
     */
    public static final String BACKUP_OWNER_MACHINE = "backup.owner.machine";

    private static final Set<String> SHARED = Set.of(
            // The scale's barcode layout. One sticker, read at every till.
            "setting.barcode.scale.active",
            "setting.barcode.start",
            "setting.barcode.length",
            "setting.barcode.count.scale",
            "setting.barcode.count.item",
            "setting.barcode.has.check.digit",
            "setting.barcode.validate.check.digit",
            "setting.barcode.value.type",
            "setting.barcode.max.weight",
            "setting.barcode.min.weight",

            // Rules about what a sale or a return is allowed to be.
            "return.require.source.invoice",
            "return.free.limit",
            "item.sel.without.balance",
            "invoice.update.price",

            // What the customer's paper says, which is the company's and not the till's.
            "setting.currency",
            "setting.print.report.title",

            BACKUP_OWNER_MACHINE);

    private SharedSettingKeys() {
    }

    public static boolean isShared(String key) {
        return SHARED.contains(key);
    }

    /** The whole list, for the migration of local values and for the tests. */
    public static Set<String> all() {
        return SHARED;
    }
}
