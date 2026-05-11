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

    private static final String BASE_THEME_FILE = "app-theme.css";

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

    public static String getBaseStylesheet() {
        return Objects.requireNonNull(Main.class.getResource("css/" + BASE_THEME_FILE)).toExternalForm();
    }

    public static String getStylesheet() {
        return getCurrentTheme().getCssExternalForm();
    }

    public static void apply(Scene scene) {
        if (scene == null) return;

        scene.getStylesheets().remove(getBaseStylesheet());
        for (Theme t : Theme.values()) {
            scene.getStylesheets().remove(t.getCssExternalForm());
        }

        scene.getStylesheets().add(getBaseStylesheet());
        scene.getStylesheets().add(getStylesheet());
    }

    public static void apply(Parent root) {
        if (root == null) return;

        root.getStylesheets().remove(getBaseStylesheet());
        for (Theme t : Theme.values()) {
            root.getStylesheets().remove(t.getCssExternalForm());
        }

        root.getStylesheets().add(getBaseStylesheet());
        root.getStylesheets().add(getStylesheet());
    }

    public enum Theme {
        LIGHT("theme-light.css"),
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
