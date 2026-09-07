package com.hamza.account.features.notification;

import com.hamza.controlsfx.language.LanguageManager;

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
    public static final String AUDIT = "audit";
    public static final String SYSTEM = "system";

    private NotificationCategories() {
    }

    /** Category to the localized label the settings screen shows, in display order. */
    public static Map<String, String> displayNames() {
        LanguageManager language = LanguageManager.getInstance();
        Map<String, String> names = new LinkedHashMap<>();
        names.put(ITEMS, language.getString("notification.category.items"));
        names.put(CUSTOMERS, language.getString("notification.category.customers"));
        names.put(TREASURY, language.getString("notification.category.treasury"));
        names.put(BACKUP, language.getString("notification.category.backup"));
        names.put(AUDIT, language.getString("notification.category.audit"));
        names.put(SYSTEM, language.getString("notification.category.system"));
        return names;
    }

    public static String[] all() {
        return displayNames().keySet().toArray(new String[0]);
    }
}
