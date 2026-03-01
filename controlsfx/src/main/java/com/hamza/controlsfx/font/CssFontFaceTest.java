package com.hamza.controlsfx.font;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class CssFontFaceTest extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        TextFlow flow = new TextFlow();
        flow.setPrefWidth(750);

        final Text t1 = new Text("Hello @FontFace - Grand Hotel.");
        t1.setStyle("-fx-font-family: 'Grand Hotel'; -fx-font-size: 80;");
        flow.getChildren().add(t1);

        final Text t2 = new Text("Hello @FontFace - New Rocker");
        t2.setStyle("-fx-font-family: 'New Rocker'; -fx-font-size: 80; -fx-fill: orange;");
        flow.getChildren().add(t2);

        Scene scene = new Scene(flow);
        scene.getStylesheets().add("http://fonts.googleapis.com/css?family=New+Rocker");
        scene.getStylesheets().add("http://fonts.googleapis.com/css?family=Grand+Hotel");

        primaryStage.setTitle("Hello @FontFace");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}