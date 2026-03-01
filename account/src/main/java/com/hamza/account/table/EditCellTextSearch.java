package com.hamza.account.table;

import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.controlsfx.control.SearchableComboBox;

public class EditCellTextSearch<S, T> extends TableCell<S, T> {

    /**
     * Convenience converter that does nothing (converts Strings to themselves and vice-versa...).
     */
    public static final StringConverter<String> IDENTITY_CONVERTER = new StringConverter<>() {

        @Override
        public String toString(String object) {
            return object;
        }

        @Override
        public String fromString(String string) {
            return string;
        }

    };
    private final SearchableComboBox<String> searchableComboBox = new SearchableComboBox<>();
    private final StringConverter<T> converter;

    public EditCellTextSearch(StringConverter<T> converter, ObservableList<String> items) {
        this.converter = converter;
        searchableComboBox.getItems().addAll(items);
        itemProperty().addListener((obx, oldItem, newItem) -> {
            if (newItem == null) {
                setText(null);
            } else {
                setText(converter.toString(newItem));
            }
        });
        setGraphic(searchableComboBox);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
        this.getStyleClass().add("combo-box-table-cell");

//        searchableComboBox.setOnAction(evt -> {
//            if (searchableComboBox.getSelectionModel().getSelectedItem() != null) {
//                commitEdit(this.converter.fromString(searchableComboBox.getSelectionModel().getSelectedItem()));
//            }
//        });

        searchableComboBox.setOnMouseClicked(keyEvent -> {
            if (searchableComboBox.getSelectionModel().getSelectedItem() != null) {
                commitEdit(this.converter.fromString(searchableComboBox.getSelectionModel().getSelectedItem()));
            }

        });
        searchableComboBox.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (searchableComboBox.getSelectionModel().getSelectedItem() != null) {
                    commitEdit(this.converter.fromString(searchableComboBox.getSelectionModel().getSelectedItem()));
                }
            }
        });

    }

    public static <S> EditCellTextSearch<S, String> createStringEditCell(ObservableList<String> items) {
        return new EditCellTextSearch<>(IDENTITY_CONVERTER, items);
    }

    @Override
    public void startEdit() {
        super.startEdit();
        searchableComboBox.getSelectionModel().select(converter.toString(getItem()));
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        searchableComboBox.requestFocus();
    }

    @Override
    public void commitEdit(T item) {
        if (item != null)
            if (!isEditing() && !item.equals(getItem())) {
                TableView<S> table = getTableView();
                if (table != null) {
                    TableColumn<S, T> column = getTableColumn();
                    TableColumn.CellEditEvent<S, T> event = new TableColumn.CellEditEvent<>(table,
                            new TablePosition<>(table, getIndex(), column),
                            TableColumn.editCommitEvent(), item);
                    Event.fireEvent(column, event);
                }
            }

        super.commitEdit(item);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
//        this.setGraphic(this.searchableComboBox);
    }


    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setContentDisplay(ContentDisplay.TEXT_ONLY);
//        searchableComboBox.getSelectionModel().select(converter.toString(getItem()));
//        searchableComboBox.requestFocus();
    }

    @Override
    protected void updateItem(T t, boolean b) {
        super.updateItem(t, b);
        if (b) {
            setText(null);
            setGraphic(null);
        } else {
            if (t != null) {
                setText(converter.toString(t));
                setGraphic(searchableComboBox);
            }
        }
    }
}
