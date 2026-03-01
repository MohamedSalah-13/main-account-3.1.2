package com.hamza.account.config;

import com.hamza.account.Main;
import com.hamza.account.controller.setting.FontColorController;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;

import java.util.Objects;

import static com.hamza.account.config.PropertiesName.getFontColorActive;
import static com.hamza.account.controller.setting.FontColorController.APPLICATION_CSS;

public class Style_Sheet {

    public static final String COLOR_SALES = getExternalForm("sales.css");
    public static final String CSS_SUB_RETURN = getExternalForm("pur-return.css");
    public static final String CSS_CUSTOM_RETURN = getExternalForm("sales-return.css");

    private Style_Sheet() {
    }

    private static String getExternalForm(String path) {
        return Objects.requireNonNull(Main.class.getResource("css/" + path)).toExternalForm();
    }

    public static void changeStyle(Scene scene) {
        scene.getStylesheets().removeAll();
        // Apply current theme stylesheet using ThemeManager while keeping legacy behavior
        scene.getStylesheets().add(Style_Sheet.getStyle());
        // Load font and color settings from fontcolor.properties
        var current = com.hamza.account.config.ThemeManager.getCurrentTheme();
        if (current == ThemeManager.Theme.LIGHT) {
            if (getFontColorActive())
                loadFontAndColorSettings(scene);
        }

        // Apply responsive base font-size so UI scales across screen sizes
        applyResponsiveBaseFontSize(scene);
    }

    private static void loadFontAndColorSettings(Scene scene) {
        String css = FontColorController.PREFS.get(APPLICATION_CSS, "");
        if (css != null && !css.isEmpty()) {
            // Apply the CSS to the scene
            scene.getStylesheets().add("data:text/css," + css.replace(" ", "%20"));
//            System.out.printf("Loaded font and color settings from preferences %s ",css);
        }
    }

    /**
     * Compute and apply a responsive base font size to the root node based on the primary screen size.
     * Using font-relative CSS (em) in styles will make controls scale proportionally.
     */
    private static void applyResponsiveBaseFontSize(Scene scene) {
        try {
            Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            double width = vb.getWidth();
            double height = vb.getHeight();

            // Start from a comfortable default size
            double base; // px

            // Scale based on the smaller dimension to handle different aspect ratios
            double minDim = Math.min(width, height);

            // Threshold-based scaling for simplicity and predictability
            if (minDim <= 900) {           // small screens
                base = 12;
            } else if (minDim <= 1200) {   // medium screens
                base = 13;
            } else if (minDim <= 1600) {   // standard desktops / laptops
                base = 14;
            } else if (minDim <= 2000) {   // large desktops / FHD with scaling
                base = 15;
            } else {                        // very large / 2K/4K
                base = 16;
            }

            // Allow override via system property if needed: -Dapp.fontSize=15
            String sys = System.getProperty("app.fontSize");
            if (sys != null && !sys.isBlank()) {
                try {
                    base = Double.parseDouble(sys);
                } catch (NumberFormatException ignored) {
                }
            }

            if (scene.getRoot() != null) {
                scene.getRoot().setStyle("-fx-font-size: " + base + "px;");
            }
        } catch (Exception ignored) {
            // Fail-safe: do nothing if screen info isn't available
        }
    }

    public static String getStyle() {
        // Return the current theme stylesheet; default to legacy CSS_MAIN when ThemeManager is LIGHT
        return ThemeManager.getStylesheet();
    }


}
