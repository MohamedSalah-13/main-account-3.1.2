package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.DateTimePaneController;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import lombok.Getter;

import java.io.IOException;

@Getter
public class DateTimeApplication {

    private final Pane pane;
    private final DateTimePaneController controller;

    public DateTimeApplication() throws IOException {
        controller = new DateTimePaneController();
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("timePane-view.fxml"));
        fxmlLoader.setController(controller);
        pane = fxmlLoader.load();
    }
}
