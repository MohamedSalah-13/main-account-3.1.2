package com.hamza.account.controller.convert_stock;

import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

public class ShowDataTransfer<T> {

    public ShowDataTransfer(ShowDataTransferList<T> dataTransferList) throws Exception {
        TableView<T> tableView = new TableView<>();
        tableView.setEditable(false);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        new TableColumnAnnotation().getTable(tableView, dataTransferList.classOfColumns());

        tableView.setItems(FXCollections.observableArrayList(dataTransferList.listTable()));
        dataTransferList.tableData(tableView);

        Label title = new Label(dataTransferList.titlePane());
        title.setStyle("""
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                -fx-text-fill: #1f2937;
                """);

        Label countLabel = new Label("عدد السطور: " + tableView.getItems().size());
        countLabel.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: #6b7280;
                """);

        HBox header = new HBox(10, title, countLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 5, 10, 5));

        VBox root = new VBox(10);
        root.setPrefWidth(850);
        root.setPrefHeight(520);
        root.setPadding(new Insets(12));
        root.setStyle("""
                -fx-background-color: #f8fafc;
                """);

        tableView.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #e5e7eb;
                -fx-border-radius: 8px;
                -fx-background-radius: 8px;
                """);

        root.getChildren().addAll(header, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        AppSettingInterface appSettingInterface = new AppSettingInterface() {
            @Override
            public @NotNull Pane pane() {
                return root;
            }

            @Override
            public String title() {
                return dataTransferList.titlePane();
            }

            @Override
            public boolean resize() {
                return true;
            }
        };

        new OpenApplication<>(appSettingInterface);
    }
}