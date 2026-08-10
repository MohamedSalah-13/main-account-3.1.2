package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.ToolbarAccountController;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.Objects;

@Log4j2
public class ApplicationDataWithToolbarIndexApp<T> extends Dialog<T> {

    private final Subscriptions subscriptions = new Subscriptions();

    private static final String TOOLBAR_STYLESHEET = Objects.requireNonNull(
            ApplicationDataWithToolbarIndexApp.class.getResource("/com/hamza/controlsfx/css/toolbar-account.css")).toExternalForm();

    public ApplicationDataWithToolbarIndexApp(ToolbarAccountInt<T> toolbarAccountInt, TableViewShowDataInt<T> tableViewShowDataInt
            , Node node, String title) throws Exception {
        super();
        DialogPane dialogPane = this.getDialogPane();
        // On the dialog pane rather than on the toolbar itself: the sheet reads the
        // -app-* theme colours, and those only resolve for a sheet attached to the
        // same node as the theme sheet the caller adds.
        dialogPane.getStylesheets().add(TOOLBAR_STYLESHEET);
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

        var eventBus = toolbarAccountInt.eventBus();
        var changeEvent = toolbarAccountInt.changeEvent();
        if (eventBus != null && changeEvent != null) {
            // The event the toolbar publishes is the one to listen for; only its type
            // matters here, since the reload reads the data again anyway.
            subscriptions.add(eventBus.subscribe(changeEvent.getClass(), event -> {
                try {
                    tableView.getItems().clear();
                    tableView.setItems(FXCollections.observableArrayList(tableViewShowDataInt.dataList()));
                    tableView.refresh();
                } catch (DaoException e) {
                    log.error(e.getMessage(), e);
                }
            }));
            // The dialog is built fresh every time it is opened, so its listener has
            // to go with it; the bus behind it lives for the whole process.
            subscriptions.disposeWith(vBox);
        }
    }

    private Pane getToolBar(ToolbarAccountInt<T> toolbarAccountInt) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("toolbar-account.fxml"));
        ToolbarAccountController<T> controller = new ToolbarAccountController<>(toolbarAccountInt);
        fxmlLoader.setController(controller);
        return fxmlLoader.load();
    }
}
