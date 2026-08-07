package com.hamza.account.features.notification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The categories this application notifies about.
 * <p>
 * Constants rather than string literals at each call site: the category is what
 * the mute setting and the policy key off, so a typo in one place would silently
 * create a second, unmutable category. {@link #displayNames()} is what the
 * settings screen lists, in the order declared here.
 * <p>
 * Adding a category means adding a constant and an entry in
 * {@link #displayNames()}; nothing else needs to change.
 */
public final class NotificationCategories {

    public static final String ITEMS = "items";
    public static final String CUSTOMERS = "customers";
    public static final String TREASURY = "treasury";
    public static final String BACKUP = "backup";
    public static final String SYSTEM = "system";

    private NotificationCategories() {
    }

    /** Category to the Arabic label the settings screen shows, in display order. */
    public static Map<String, String> displayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(ITEMS, "الأصناف والمخزون");
        names.put(CUSTOMERS, "العملاء والمديونيات");
        names.put(TREASURY, "الخزينة");
        names.put(BACKUP, "النسخ الاحتياطي");
        names.put(SYSTEM, "النظام");
        return names;
    }

    public static String[] all() {
        return displayNames().keySet().toArray(new String[0]);
    }
}
