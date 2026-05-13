package com.hamza.controlsfx.button;

import javafx.scene.control.Button;

import java.io.InputStream;

public class ButtonGraphics {

    public static void buttonGraphic(Button button, InputStream stream) {
        button.setGraphic(new ImageDesign(stream));
    }

}
