package com.hamza.account.controller.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.SceneAll;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/** A small keyboard-first item picker for the quick invoice table. */
final class QuickItemPicker {
    private final ItemsService itemsService;
    private final ToDoubleFunction<ItemsModel> price;

    QuickItemPicker(ItemsService itemsService, ToDoubleFunction<ItemsModel> price) {
        this.itemsService = itemsService;
        this.price = price;
    }

    void show(Consumer<ItemsModel> selected) {
        var language = LanguageManager.getInstance();
        TextField search = new TextField();
        search.setPromptText(language.getString("invoice.quick.item.search.prompt"));
        TableView<ItemsModel> items = new TableView<>();
        items.getColumns().addAll(column(language.getString("invoice.barcode"),
                        features -> new ReadOnlyStringWrapper(features.getValue().getBarcode())),
                column(language.getString("invoice.name"),
                        features -> new ReadOnlyStringWrapper(features.getValue().getNameItem())),
                numberColumn(language.getString("invoice.price"),
                        features -> new ReadOnlyDoubleWrapper(price.applyAsDouble(features.getValue()))));
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);

        Stage stage = new Stage();
        Button addItem = new Button(language.getString("invoice.quick.item.add"));
        addItem.setOnAction(event -> openNewItem(search.getText()));
        Runnable choose = () -> {
            ItemsModel item = items.getSelectionModel().getSelectedItem();
            if (item != null) {
                selected.accept(item);
                stage.close();
            }
        };
        search.textProperty().addListener((observable, oldText, text) -> load(items, text));
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                items.requestFocus();
                items.getSelectionModel().selectFirst();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                choose.run();
                event.consume();
            } else if (event.getCode() == KeyCode.F4) {
                openNewItem(search.getText());
                event.consume();
            }
        });
        items.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                choose.run();
                event.consume();
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
            if (event.getCode() == KeyCode.F4) {
                openNewItem(search.getText());
                event.consume();
            }
        });
        items.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) choose.run();
        });

        HBox searchBar = new HBox(8, search, addItem);
        VBox root = new VBox(8, searchBar, items);
        root.getStyleClass().addAll("app-root", "app-container");
        VBox.setVgrow(items, Priority.ALWAYS);
        Scene scene = new SceneAll(root);
        stage.setScene(scene);
        stage.setTitle(language.getString("invoice.quick.item.search.title"));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setMinWidth(620);
        stage.setMinHeight(420);
        load(items, "");
        stage.show();
        search.requestFocus();
    }

    private void openNewItem(String barcodeOrName) {
        try {
            new AddItemApplication(0, barcodeOrName).start(new Stage());
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.quick.item.search.title"), e);
        }
    }

    private void load(TableView<ItemsModel> table, String filter) {
        try {
            table.setItems(FXCollections.observableArrayList(itemsService.getFilterItems(
                    filter == null ? "" : filter.trim())));
            table.getSelectionModel().selectFirst();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.quick.item.search.title"), e);
        }
    }

    private static TableColumn<ItemsModel, String> column(String title,
            javafx.util.Callback<TableColumn.CellDataFeatures<ItemsModel, String>,
                    javafx.beans.value.ObservableValue<String>> value) {
        TableColumn<ItemsModel, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value);
        return column;
    }

    private static TableColumn<ItemsModel, Number> numberColumn(String title,
            javafx.util.Callback<TableColumn.CellDataFeatures<ItemsModel, Number>,
                    javafx.beans.value.ObservableValue<Number>> value) {
        TableColumn<ItemsModel, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(value);
        return column;
    }
}
