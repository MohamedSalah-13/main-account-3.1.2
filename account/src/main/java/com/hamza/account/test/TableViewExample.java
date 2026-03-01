/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.hamza.account.test;

import com.hamza.account.openFxml.OpenFxmlApplication;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 *
 * @author Cool IT Help
 */
public class TableViewExample extends Application {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
//        Parent root = FXMLLoader.load(getClass().getResource("FXMLDocument.fxml"));

//        scene.getStylesheets().add("/CSS/mycss.css");
        var pane = new OpenFxmlApplication(new FXMLDocumentController()).getPane();
        Scene scene = new Scene(pane);
        stage.setScene(scene);
        stage.show();
    }

}
