package com.hamza.account.table;

import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNull;

import java.util.prefs.Preferences;

public class StageDimensions {
    public static final double WIDTH = 800;
    public static final double HEIGHT = 600;
    public static final double SCALE = 0.8;
    public static final double SCALE_SMALL = 0.5;
    public static final double SCALE_LARGE = 1.5;

    public static void stageDimensions(@NotNull Class<?> clazz, @NotNull Stage stage) {
        Preferences prefs = Preferences.userNodeForPackage(clazz);

        final String baseKey = clazz.getName() + ".stage.";
        final String widthKey = baseKey + "width";
        final String heightKey = baseKey + "height";
        final String xKey = baseKey + "x";
        final String yKey = baseKey + "y";

        // Enforce constraints first
//        stage.setMinWidth(WIDTH);
//        stage.setMinHeight(HEIGHT);
        stage.setResizable(true);
        // Apply stored values, falling back to defaults
        applyInitialBounds(stage, prefs, widthKey, heightKey, xKey, yKey);

        // Persist changes when the user moves/resizes
        bindToPreferences(stage, prefs, widthKey, heightKey, xKey, yKey);

        // Save on close without overriding existing handlers
        stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST,
                event -> saveStageBounds(stage, prefs, widthKey, heightKey, xKey, yKey));
    }

    private static void applyInitialBounds(Stage stage, Preferences prefs,
                                           String widthKey, String heightKey, String xKey, String yKey) {
        stage.setWidth(prefs.getDouble(widthKey, WIDTH));
        stage.setHeight(prefs.getDouble(heightKey, HEIGHT));
        stage.setX(prefs.getDouble(xKey, 0));
        stage.setY(prefs.getDouble(yKey, 0));
    }

    private static void bindToPreferences(Stage stage, Preferences prefs,
                                          String widthKey, String heightKey, String xKey, String yKey) {
        stage.widthProperty().addListener((obs, oldVal, newVal) -> prefs.putDouble(widthKey, newVal.doubleValue()));
        stage.heightProperty().addListener((obs, oldVal, newVal) -> prefs.putDouble(heightKey, newVal.doubleValue()));
        stage.xProperty().addListener((obs, oldVal, newVal) -> prefs.putDouble(xKey, newVal.doubleValue()));
        stage.yProperty().addListener((obs, oldVal, newVal) -> prefs.putDouble(yKey, newVal.doubleValue()));
    }

    private static void saveStageBounds(Stage stage, Preferences prefs,
                                        String widthKey, String heightKey, String xKey, String yKey) {
        prefs.putDouble(widthKey, stage.getWidth());
        prefs.putDouble(heightKey, stage.getHeight());
        prefs.putDouble(xKey, stage.getX());
        prefs.putDouble(yKey, stage.getY());
    }
}
