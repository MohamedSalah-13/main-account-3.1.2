package com.hamza.account.test;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class FrostedGlassDemo extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane();

        // Background image
        ImageView background = new ImageView(new Image("https://picsum.photos/800/600"));
        background.setFitWidth(800);
        background.setFitHeight(600);

        // Frosted glass effect container
        StackPane frostedContainer = new StackPane();
        frostedContainer.setMaxSize(400, 300);

        // Create the frosted glass effect
        Rectangle frost = new Rectangle(400, 300);
        frost.setArcWidth(20);
        frost.setArcHeight(20);
        frost.setFill(Color.rgb(255, 255, 255, 0.2));
        frost.setEffect(new GaussianBlur(15));

        // Content on top of frosted glass
        StackPane content = new StackPane();
        content.setMaxSize(380, 280);
        content.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); -fx-background-radius: 15;");

        frostedContainer.getChildren().addAll(frost, content);

        root.getChildren().addAll(background, frostedContainer);

        Scene scene = new Scene(root, 800, 600);
        scene.setFill(Color.TRANSPARENT);

        primaryStage.setTitle("Frosted Glass Effect");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}