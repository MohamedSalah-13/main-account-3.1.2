package com.hamza.account.config;

import java.util.prefs.Preferences;

/**
 * Where a setting is stored.
 *
 * <p>Two stores, and one line decides which: a key named in {@link SharedSettingKeys} is
 * the shop's and lives in {@code app_setting}, everything else is this computer's and lives
 * in {@code Preferences} exactly as it always has. The choice is made here rather than at
 * the four hundred call sites in {@link PropertiesName}, which is the whole reason the
 * shared half could be introduced without touching a single screen.
 *
 * <p>A shared value is also written to {@code Preferences} on the way past. That mirror is
 * not a cache for speed - {@link SharedSettings} has one of those - it is what the machine
 * reads when the database cannot be reached at all, so a till whose network is down keeps
 * reading scale barcodes the way the shop last said to.
 */
public class PreferencesSetting {

    private static final Preferences preferences = Preferences.userNodeForPackage(PropertiesName.class);

    protected static String getString(String key, String defaultValue) {
        return SharedSettings.read(key).orElseGet(() -> preferences.get(key, defaultValue));
    }

    protected static void putString(String key, String value) {
        SharedSettings.write(key, value);
        preferences.put(key, value);
    }

    protected static int getInt(String key, int defaultValue) {
        return parseInt(getString(key, null), defaultValue);
    }

    protected static void putInt(String key, int value) {
        putString(key, Integer.toString(value));
    }

    protected static boolean getBoolean(String key, boolean defaultValue) {
        String stored = getString(key, null);
        return stored == null ? defaultValue : Boolean.parseBoolean(stored);
    }

    protected static void putBoolean(String key, boolean value) {
        putString(key, Boolean.toString(value));
    }

    protected static double getDouble(String key, double defaultValue) {
        String stored = getString(key, null);
        if (stored == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(stored);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected static void putDouble(String key, double value) {
        putString(key, Double.toString(value));
    }

    protected static void remove(String key) {
        preferences.remove(key);
    }

    /**
     * What this machine has stored for a key, or {@code null} when it has never been set.
     * <p>
     * {@code Preferences.get} cannot say the difference between "not set" and "set to the
     * default", and {@link SharedSettings} needs it: publishing a default as though the
     * shop had chosen it is how a machine that has never been configured would overwrite
     * the one that has.
     */
    static String storedValue(String key) {
        return preferences.get(key, null);
    }

    private static int parseInt(String stored, int defaultValue) {
        if (stored == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(stored.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
