package com.hamza.account.test.easyedit;

import com.hamza.account.Main;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TableTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        ObservableList<LineItem> items = FXCollections.observableArrayList();
        items.addAll(new LineItem("hello", 123.45, 6),
                new LineItem("world", 0.01, 11));
        TableView table = new EasyEditTable().makeTable(items);

        Button focusableNode = new Button("Nada");

        VBox root = new VBox();
        root.getChildren().addAll(table, focusableNode);
        Scene scene = new Scene(root, 300, 250);
        //css to remove empty lines in table
        scene.getStylesheets().add(Main.class.getResource("css/css.css").toExternalForm());

        primaryStage.setTitle("Easy edit table test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

}