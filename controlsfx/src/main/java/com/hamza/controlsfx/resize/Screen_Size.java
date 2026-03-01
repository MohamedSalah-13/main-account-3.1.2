package com.hamza.controlsfx.resize;

import javafx.geometry.Rectangle2D;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Screen_Size {

    /**
     * The primary screen of the user's system.
     * This variable holds a reference to the primary screen,
     * allowing access to its properties and methods.
     */
    private static final Screen SCREEN = Screen.getPrimary();
    /**
     * The visual bounds of the primary screen. This rectangle provides the coordinates and size
     * of the usable area of the primary screen, excluding any taskbars, menu bars, or other OS-level UI elements.
     */
    private static final Rectangle2D BOUNDS = SCREEN.getVisualBounds();
    /**
     * Represents the width of the primary screen's visual bounds.
     * This value is derived from the width of the visual bounds of the primary screen,
     * which is useful when adjusting the size and layout of application stages or panes
     * to fit within the screen dimensions.
     */
    private static final double BOUNDS_WIDTH = BOUNDS.getWidth();
    /**
     * Represents the height of the visual bounds of the primary screen.
     * This value is derived from the primary screen's visual bounds using the method `getHeight()`.
     * It is used for various layout calculations and adjustments based on the screen size.
     */
    private static final double BOUNDS_HEIGHT = BOUNDS.getHeight();

    /**
     * Adjusts the given stage to fill the entire screen based on the primary screen's visual bounds.
     *
     * @param stage the stage to be adjusted to full screen
     */
    public static void adjustStageToFullScreen(Stage stage) {
        setStageBounds(stage, BOUNDS.getMinX(), BOUNDS.getMinY(), BOUNDS_WIDTH, BOUNDS_HEIGHT);
    }

    /**
     * Sets the preferred size of the specified pane based on specified width and height divisors.
     *
     * @param pane the Pane whose preferred size is to be set
     * @param widthDivisor the divisor for the width calculation
     * @param heightDivisor the divisor for the height calculation
     */
    public static void setPanePreferredSize(Pane pane, double widthDivisor, double heightDivisor) {
        pane.setPrefSize(BOUNDS_WIDTH / widthDivisor, BOUNDS_HEIGHT / heightDivisor);
    }

    /**
     * Sets the position and size of the given Stage.
     *
     * @param stage the Stage to be positioned and resized
     * @param x the x-coordinate position of the Stage
     * @param y the y-coordinate position of the Stage
     * @param width the width of the Stage
     * @param height the height of the Stage
     */
    private static void setStageBounds(Stage stage, double x, double y, double width, double height) {
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);
    }
}
