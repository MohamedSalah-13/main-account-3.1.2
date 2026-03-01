package com.hamza.controlsfx.font;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class HelloFontFace extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("Hello @FontFace\n-- Gafata --");
        label.setStyle("-fx-font-family: Gafata; -fx-font-size: 80;");
        Scene scene = new Scene(label);
        scene.getStylesheets().add("http://fonts.googleapis.com/css?family=Gafata");
        primaryStage.setTitle("Hello @FontFace");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}