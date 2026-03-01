package com.hamza.account.config;

import com.hamza.account.Main;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Centralized theme manager for the application. Supports switching between themes
 * and persists the selected theme using Java Preferences.
 */
public final class ThemeManager {

    private static final String PREF_NODE = "com.hamza.account.theme";
    private static final String KEY_THEME = "currentTheme";
    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);
    private ThemeManager() {
    }

    public static Theme getCurrentTheme() {
        String name = PREFS.get(KEY_THEME, Theme.LIGHT.name());
        try {
            return Theme.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return Theme.LIGHT;
        }
    }

    public static void setCurrentTheme(Theme theme) {
        if (theme == null) return;
        PREFS.put(KEY_THEME, theme.name());
    }

    /**
     * Returns the stylesheet URL to use for the current theme. For LIGHT it returns mainStyle.css,
     * for DARK it returns theme-dark.css which overrides variables defined in mainStyle.css.
     */
    public static String getStylesheet() {
        return getCurrentTheme().getCssExternalForm();
    }

    /**
     * Apply current theme to a Scene: it removes previously added theme sheets from this manager
     * and adds the current theme stylesheet.
     */
    public static void apply(Scene scene) {
        if (scene == null) return;
        // Remove previously known theme styles to avoid duplicates
        for (Theme t : Theme.values()) {
            scene.getStylesheets().remove(t.getCssExternalForm());
        }
        scene.getStylesheets().add(getStylesheet());
    }

    /**
     * Apply current theme to a Parent root (useful when you don't have direct Scene reference yet).
     */
    public static void apply(Parent root) {
        if (root == null) return;
        for (Theme t : Theme.values()) {
            root.getStylesheets().remove(t.getCssExternalForm());
        }
        root.getStylesheets().add(getStylesheet());
    }

    public enum Theme {
        LIGHT("mainStyle.css"),
        DARK("theme-dark.css"),
        GLASS("glass-theme.css");

        private final String cssFileName;

        Theme(String cssFileName) {
            this.cssFileName = cssFileName;
        }

        public String getCssExternalForm() {
            return Objects.requireNonNull(Main.class.getResource("css/" + cssFileName)).toExternalForm();
        }
    }
}
