package com.hamza.account.config;

import com.hamza.account.Main;
import com.hamza.controlsfx.alert.AlertSetting;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

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

    /**
     * app-theme.css gives each of these its own explicit, non-inherited font-family
     * and font-size rule, because every screen's own FXML root carries one of them.
     * That means a screen embedded as tab content inside another scene - the settings
     * tab inside the main screen, for instance - does not pick up an ancestor's inline
     * override just by inheritance: its own root re-declares both properties. {@link
     * #applyUiScale} and {@link #applyFontFamily} therefore restamp every node
     * carrying one of these classes within the given root's subtree, not just the
     * root itself.
     */
    private static final String ROOT_STYLE_SELECTOR = ".app-root, .reports-root, .items-root, "
            + ".settings-root, .treasury-root, .treasury-transfer-root, .table-screen-root, "
            + ".main-root, .backup-root, .settings-main-root";

    private ThemeManager() {
    }

    public static void initialize() {
        applyAlertTheme();
    }
    private static void applyAlertTheme() {
        AlertSetting.setStylesheets(getBaseStylesheet(), getStylesheet());
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

    /**
     * Reapplies theme, orientation, UI scale and the selected font to every
     * showing JavaFX window. This is the live-update path for appearance settings:
     * it avoids relying on a screen being reopened before its font can change.
     */
    public static void refreshOpenWindows() {
        Runnable refresh = () -> Window.getWindows().stream()
                .filter(Window::isShowing)
                .map(Window::getScene)
                .filter(Objects::nonNull)
                .forEach(ThemeManager::apply);
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
    }
    public static void apply(Scene scene) {
        if (scene == null) return;

        scene.getStylesheets().remove(getBaseStylesheet());
        for (Theme t : Theme.values()) {
            scene.getStylesheets().remove(t.getCssExternalForm());
        }

        scene.getStylesheets().add(getBaseStylesheet());
        scene.getStylesheets().add(getStylesheet());
        scene.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        if (scene.getRoot() != null) {
            applyUiScale(scene.getRoot());
            applyFontFamily(scene.getRoot());
            TableAppearance.apply(scene.getRoot());
        }
    }

    public static void apply(Parent root) {
        if (root == null) return;

        root.getStylesheets().remove(getBaseStylesheet());
        for (Theme t : Theme.values()) {
            root.getStylesheets().remove(t.getCssExternalForm());
        }

        root.getStylesheets().add(getBaseStylesheet());
        root.getStylesheets().add(getStylesheet());
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        applyUiScale(root);
        applyFontFamily(root);
        TableAppearance.apply(root);
    }

    /**
     * Stamps the current {@link UiScale} as an inline style on the root and on every
     * nested node matching {@link #ROOT_STYLE_SELECTOR}, replacing any font-size each
     * carries from a previous call rather than piling styles up.
     */
    private static void applyUiScale(Parent root) {
        String style = UiScale.fontSizeStyle();
        stampStyle(root, style, "-fx-font-size:[^;]*;");
        for (Node node : root.lookupAll(ROOT_STYLE_SELECTOR)) {
            stampStyle(node, style, "-fx-font-size:[^;]*;");
        }
    }

    /**
     * Stamps the current {@link FontManager} family as an inline style on the root
     * and on every nested node matching {@link #ROOT_STYLE_SELECTOR} - the same
     * strip-and-reapply technique {@link #applyUiScale} uses for font size.
     */
    private static void applyFontFamily(Parent root) {
        String style = FontManager.fontFamilyStyle();
        stampStyle(root, style, "-fx-font-family:[^;]*;");
        for (Node node : root.lookupAll(ROOT_STYLE_SELECTOR)) {
            stampStyle(node, style, "-fx-font-family:[^;]*;");
        }
    }

    private static void stampStyle(Node node, String styleToStamp, String stripRegex) {
        String existing = node.getStyle();
        String stripped = existing == null ? "" : existing.replaceAll(stripRegex, "").trim();
        node.setStyle(styleToStamp + (stripped.isEmpty() ? "" : " " + stripped));
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
