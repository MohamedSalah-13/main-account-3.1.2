package com.hamza.account.view.calculator;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CalculatorApplication extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/hamza/account/view/calculator/calculator-view.fxml"));
            Parent root = fxmlLoader.load();

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Calculator");
            stage.setResizable(false);
            stage.show();
        } catch (Exception e) {
            log.error("Error starting calculator application", e);
            throw e;
        }
    }

    /**
     * Opens the calculator in a new window
     */
    public void openCalculator() {
        try {
            start(new Stage());
        } catch (Exception e) {
            log.error("Error opening calculator", e);
        }
    }
}