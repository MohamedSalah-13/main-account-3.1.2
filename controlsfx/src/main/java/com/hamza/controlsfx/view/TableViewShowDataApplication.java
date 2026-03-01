package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.TableViewShowDataController;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import lombok.Getter;


public class TableViewShowDataApplication<T> {

    @Getter
    private final Pane pane;

    public TableViewShowDataApplication(TableViewShowDataInt<T> tableViewShowDataInt) throws Exception {
        TableViewShowDataController<T> tableViewShowDataController = new TableViewShowDataController<>(tableViewShowDataInt);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("tableDetails-view.fxml"));
        fxmlLoader.setController(tableViewShowDataController);
        pane = fxmlLoader.load();
    }

}