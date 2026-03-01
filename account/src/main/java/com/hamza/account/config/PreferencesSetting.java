package com.hamza.account.config;

import java.util.prefs.Preferences;

public class PreferencesSetting {

    private static final Preferences preferences = Preferences.userNodeForPackage(PropertiesName.class);

    protected static String getString(String key, String defaultValue) {
        return preferences.get(key, defaultValue);
    }

    protected static void putString(String key, String value) {
        preferences.put(key, value);
    }

    protected static int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    protected static void putInt(String key, int value) {
        preferences.putInt(key, value);
    }

    protected static boolean getBoolean(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    protected static void putBoolean(String key, boolean value) {
        preferences.putBoolean(key, value);
    }

    protected static double getDouble(String key, double defaultValue) {
        return preferences.getDouble(key, defaultValue);
    }

    protected static void putDouble(String key, double value) {
        preferences.putDouble(key, value);
    }

    protected static void remove(String key) {
        preferences.remove(key);
    }

}
