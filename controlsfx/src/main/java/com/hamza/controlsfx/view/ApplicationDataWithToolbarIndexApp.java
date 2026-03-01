package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.ToolbarAccountController;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

@Log4j2
public class ApplicationDataWithToolbarIndexApp<T> extends Dialog<T> {

    public ApplicationDataWithToolbarIndexApp(ToolbarAccountInt<T> toolbarAccountInt, TableViewShowDataInt<T> tableViewShowDataInt
            , Node node, String title) throws Exception {
        super();
        DialogPane dialogPane = this.getDialogPane();
        VBox vBox = new VBox();
        vBox.getChildren().addAll(getToolBar(toolbarAccountInt), node);
        dialogPane.setContent(vBox);
        var cancel = ButtonType.CANCEL;
        dialogPane.getButtonTypes().add(cancel);
        Button buttonCancel = (Button) getDialogPane().lookupButton(cancel);
        buttonCancel.setId("btnClose");
        buttonCancel.setText(Setting_Language.WORD_CANCEL);
        setTitle(title);

        TableView<T> tableView = new TableView<>();
        new TableColumnAnnotation().getTable(tableView, tableViewShowDataInt.classForColumn());
        tableView.setPrefHeight(200);
        tableView.setItems(FXCollections.observableArrayList(tableViewShowDataInt.dataList()));
        dialogPane.setExpandableContent(tableView);

        toolbarAccountInt.publisherTable().addObserver(message -> {
            try {
                tableView.getItems().clear();
                tableView.setItems(FXCollections.observableArrayList(tableViewShowDataInt.dataList()));
                tableView.refresh();
            } catch (DaoException e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private Pane getToolBar(ToolbarAccountInt<T> toolbarAccountInt) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("toolbar-account.fxml"));
        ToolbarAccountController<T> controller = new ToolbarAccountController<>(toolbarAccountInt);
        fxmlLoader.setController(controller);
        return fxmlLoader.load();
    }
}
