package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.BoxController;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import lombok.Getter;

import java.io.InputStream;

@Getter
public class BoxApplication {

    private final Pane pane;
    private final BoxController boxController;

    public BoxApplication(String title, InputStream image, String color) throws Exception {
        boxController = new BoxController(title, image, color);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("box-data.fxml"));
        fxmlLoader.setController(boxController);
        pane = fxmlLoader.load();
    }

}
