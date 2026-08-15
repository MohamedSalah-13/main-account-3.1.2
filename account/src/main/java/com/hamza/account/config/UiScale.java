package com.hamza.account.config;

import javafx.stage.Screen;

import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Base font size the app renders at, persisted the same way as {@link ThemeManager}.
 * app-theme.css, dashboard.css and loginStyle.css size everything - fonts, padding,
 * radii, control minimums - in {@code em}, so stamping this as an inline
 * {@code -fx-font-size} on a scene's root scales the whole tree from one number.
 * {@link ThemeManager#apply} is what actually stamps it, right alongside the
 * stylesheets it already attaches to every scene/root - login included, since it
 * goes through the same {@code ThemeManager.apply(Scene)} call as every other window.
 */
public final class UiScale {

    private static final String PREF_NODE = "com.hamza.account.uiscale";
    private static final String KEY_FACTOR = "fontScaleFactor";
    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);

    /** The size app-theme.css's own {@code .root} rule carries; 1em everywhere resolves to this. */
    private static final double BASE_FONT_PX = 11.0;

    /** Percentage presets a user can pick between, 100% being the shipped default. */
    public static final double[] LEVELS = {0.85, 1.00, 1.15, 1.30, 1.50, 1.75};

    public static final double DEFAULT_FACTOR = 1.00;

    // A first-ever run below this starts at the smallest level instead of the default.
    private static final double SMALL_SCREEN_WIDTH = 1366.0;
    private static final double SMALL_SCREEN_HEIGHT = 768.0;

    private UiScale() {
    }

    /**
     * Picks a default on the very first run - a small screen starts at the smallest
     * preset rather than 100% - and leaves an existing choice alone. Called once at
     * startup, next to {@link ThemeManager#initialize()}.
     */
    public static void initialize() {
        if (PREFS.get(KEY_FACTOR, null) != null) return;
        setFactor(isSmallScreen() ? LEVELS[0] : DEFAULT_FACTOR);
    }

    public static double factor() {
        double stored = PREFS.getDouble(KEY_FACTOR, DEFAULT_FACTOR);
        return clamp(stored);
    }

    public static void setFactor(double factor) {
        PREFS.putDouble(KEY_FACTOR, clamp(factor));
    }

    private static double clamp(double factor) {
        double min = LEVELS[0];
        double max = LEVELS[LEVELS.length - 1];
        return Math.max(min, Math.min(max, factor));
    }

    private static boolean isSmallScreen() {
        var bounds = Screen.getPrimary().getVisualBounds();
        return bounds.getWidth() < SMALL_SCREEN_WIDTH || bounds.getHeight() < SMALL_SCREEN_HEIGHT;
    }

    /** "185%" style label for a level, for the settings combo box. */
    public static String label(double factor) {
        return Math.round(factor * 100) + "%";
    }

    /**
     * The inline style that stamps the current scale's font size onto a scene or
     * node root, so every {@code em} rule in the stylesheets scales from it.
     */
    public static String fontSizeStyle() {
        double px = Math.round(BASE_FONT_PX * factor() * 100.0) / 100.0;
        return String.format(Locale.ROOT, "-fx-font-size: %.2fpx;", px);
    }
}
