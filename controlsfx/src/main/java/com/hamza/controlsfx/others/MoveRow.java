package com.hamza.controlsfx.others;

import com.hamza.controlsfx.button.ImageDesign;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

public class MoveRow<T> extends VBox {

    public MoveRow(TableView<T> table) {
        Button upButton = new Button();
        Button downButton = new Button();

        this.getStylesheets().add(getClass().getResource("moveRow.css").toExternalForm());
        upButton.setGraphic(new ImageDesign(new ImageSetting().ARROW_UPWARD));
        downButton.setGraphic(new ImageDesign(new ImageSetting().ARROW_DOWNWARD));

        var selectionModel = table.getSelectionModel();
        selectionModel.setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        upButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        selectionModel.getSelectedIndices().isEmpty() ||
                                selectionModel.getSelectedIndices().stream().anyMatch(i -> i <= 0),
                selectionModel.getSelectedIndices()));

        downButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        selectionModel.getSelectedIndices().isEmpty() ||
                                selectionModel.getSelectedIndices().stream().anyMatch(i -> i >= table.getItems().size() - 1),
                selectionModel.getSelectedIndices(), table.getItems()));

        upButton.setOnAction(evt -> {
            var indices = table.getSelectionModel().getSelectedIndices();
            for (int i = 0; i < indices.size(); i++) {
                int index = indices.get(i);
                table.getItems().add(index - 1, table.getItems().remove(index));
                table.getSelectionModel().select(index - 1);
            }
        });

        downButton.setOnAction(evt -> {
            var indices = table.getSelectionModel().getSelectedIndices();
            for (int i = indices.size() - 1; i >= 0; i--) {
                int index = indices.get(i);
                table.getItems().add(index + 1, table.getItems().remove(index));
                table.getSelectionModel().select(index + 1);
            }
        });

        getChildren().addAll(upButton, downButton);
        setSpacing(10);
    }
}
