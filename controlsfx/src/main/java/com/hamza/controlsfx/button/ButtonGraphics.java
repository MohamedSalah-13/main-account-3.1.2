package com.hamza.controlsfx.button;

import javafx.scene.control.*;

import java.io.InputStream;

public class ButtonGraphics {

    public static void buttonGraphic(Button button, InputStream stream) {
        button.setGraphic(new ImageDesign(stream));
    }

    public static void buttonGraphic(TitledPane titledPane, InputStream stream) {
        titledPane.setGraphic(new ImageDesign(stream));
    }

    public static void buttonGraphic(MenuButton menuButton, InputStream stream) {
        menuButton.setGraphic(new ImageDesign(stream));
    }

    public static void buttonGraphic(MenuItem menuItem, InputStream stream) {
        menuItem.setGraphic(new ImageDesign(stream));
    }

    public static void buttonGraphic(Tab tab, InputStream stream) {
        tab.setGraphic(new ImageDesign(stream));
    }

}
