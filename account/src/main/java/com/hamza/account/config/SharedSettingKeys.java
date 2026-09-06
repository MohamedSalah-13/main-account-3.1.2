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

    /*
     * Named, because the key has two readers that must not drift apart: PropertiesName,
     * which stores under it, and the settings screen, which marks the control as the
     * shop's. Written as a literal in both places, a rename in one of them would leave a
     * shared setting quietly presented as a local one.
     */

    /** The scale's barcode layout. One sticker, read at every till. */
    public static final String BARCODE_SCALE_ACTIVE = "setting.barcode.scale.active";
    public static final String BARCODE_START = "setting.barcode.start";
    public static final String BARCODE_LENGTH = "setting.barcode.length";
    public static final String BARCODE_COUNT_SCALE = "setting.barcode.count.scale";
    public static final String BARCODE_COUNT_ITEM = "setting.barcode.count.item";
    public static final String BARCODE_HAS_CHECK_DIGIT = "setting.barcode.has.check.digit";
    public static final String BARCODE_VALIDATE_CHECK_DIGIT = "setting.barcode.validate.check.digit";
    public static final String BARCODE_VALUE_TYPE = "setting.barcode.value.type";
    public static final String BARCODE_MAX_WEIGHT = "setting.barcode.max.weight";
    public static final String BARCODE_MIN_WEIGHT = "setting.barcode.min.weight";

    /** Rules about what a sale or a return is allowed to be. */
    public static final String RETURN_REQUIRE_SOURCE_INVOICE = "return.require.source.invoice";
    public static final String RETURN_FREE_LIMIT = "return.free.limit";
    public static final String SEL_WITHOUT_BALANCE = "item.sel.without.balance";
    public static final String INVOICE_UPDATE_PRICE = "invoice.update.price";

    /** What the customer's paper says, which is the company's and not the till's. */
    public static final String CURRENCY = "setting.currency";
    public static final String PRINT_REPORT_TITLE = "setting.print.report.title";

    /**
     * Whether the same item scanned twice becomes one line of two or two lines of one.
     * <p>
     * Not a display preference: it is the shape of the rows that get stored. Two tills
     * answering differently means the same sale is recorded two ways depending on which
     * one the customer walked up to, and every later reading of those lines - a report, a
     * return against the invoice - meets both shapes.
     */
    public static final String INVOICE_MERGE_REPEATED_ITEM = "invoice.increase.item.one.table";

    /**
     * The customer a new invoice opens on - the walk-in account a cash sale is filed
     * under.
     * <p>
     * Shared because the alternative is cash sales landing in different accounts
     * depending on which till served them, which is a hole in the books rather than a
     * difference of habit. Its neighbour {@link #DEFAULT_DELEGATE_NOT_SHARED} deliberately
     * is not.
     */
    public static final String DEFAULT_CUSTOMER = "setting.save.name.customer";

    /**
     * The default <em>delegate</em>, and the reason it stays on the machine.
     * <p>
     * A delegate is a salesperson, and salespeople are paid on what they sell. A shop
     * where each till is manned by a different one wants each till to default to its own;
     * sharing the key would credit every sale in the building to whoever was set last.
     * The two failure modes are not equal - setting it once per machine is a chore,
     * paying the wrong person is not - so this constant exists only to be named here and
     * is never put in the set below.
     */
    public static final String DEFAULT_DELEGATE_NOT_SHARED = "setting.save.name.delegate";

    private static final Set<String> SHARED = Set.of(
            BARCODE_SCALE_ACTIVE,
            BARCODE_START,
            BARCODE_LENGTH,
            BARCODE_COUNT_SCALE,
            BARCODE_COUNT_ITEM,
            BARCODE_HAS_CHECK_DIGIT,
            BARCODE_VALIDATE_CHECK_DIGIT,
            BARCODE_VALUE_TYPE,
            BARCODE_MAX_WEIGHT,
            BARCODE_MIN_WEIGHT,

            RETURN_REQUIRE_SOURCE_INVOICE,
            RETURN_FREE_LIMIT,
            SEL_WITHOUT_BALANCE,
            INVOICE_UPDATE_PRICE,

            CURRENCY,
            PRINT_REPORT_TITLE,

            INVOICE_MERGE_REPEATED_ITEM,
            DEFAULT_CUSTOMER,

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
