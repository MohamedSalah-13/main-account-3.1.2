package com.hamza.account.controller.convert_stock;

import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

public class ShowDataTransfer<T> {

    public ShowDataTransfer(ShowDataTransferList<T> dataTransferList) throws Exception {
        TableView<T> tableView = new TableView<>();
        new TableColumnAnnotation().getTable(tableView, dataTransferList.classOfColumns());
        tableView.setItems(FXCollections.observableList(dataTransferList.listTable()));
        dataTransferList.tableData(tableView);

        VBox vBox = new VBox(5);
        vBox.setPrefWidth(400);
        vBox.getChildren().add(tableView);
        VBox.setVgrow(tableView, Priority.SOMETIMES);

        AppSettingInterface appSettingInterface = new AppSettingInterface() {

            @Override
            public @NotNull Pane pane() {
                return vBox;
            }

            @Override
            public String title() {
                return dataTransferList.titlePane();
            }

        };

        new OpenApplication<>(appSettingInterface);
    }
}
