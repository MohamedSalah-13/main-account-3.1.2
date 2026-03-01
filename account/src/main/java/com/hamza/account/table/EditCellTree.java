package com.hamza.account.table;

import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

public class EditCellTree<S, T> extends TreeTableCell<S, T> {

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
    private final TextField textField = new TextField();
    private final StringConverter<T> converter;

    public EditCellTree(StringConverter<T> converter) {
        this.converter = converter;

        itemProperty().addListener((obx, oldItem, newItem) -> {
            if (newItem == null) {
                setText(null);
            } else {
                setText(converter.toString(newItem));
            }
        });
        setGraphic(textField);
        setContentDisplay(ContentDisplay.TEXT_ONLY);

        textField.setOnAction(evt -> {
            commitEdit(this.converter.fromString(textField.getText()));
        });
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                commitEdit(this.converter.fromString(textField.getText()));
            }
        });
        textField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                textField.setText(converter.toString(getItem()));
                cancelEdit();
                event.consume();
            } /*else if (event.getCode() == KeyCode.RIGHT) {
                getTableView().getSelectionModel().selectRightCell();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                getTableView().getSelectionModel().selectLeftCell();
                event.consume();
            }*/ else if (event.getCode() == KeyCode.UP) {
                getTableRow().getTreeTableView().getSelectionModel().selectAboveCell();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                getTableRow().getTreeTableView().getSelectionModel().selectBelowCell();
                event.consume();
            }
        });
    }

    /**
     * Convenience method for creating an EditCell for a String value.
     * @return
     */
    public static <S> EditCellTree<S, String> createStringEditCell() {
        return new EditCellTree<>(IDENTITY_CONVERTER);
    }

    // set the text of the text field and display the graphic
    @Override
    public void startEdit() {
        super.startEdit();
        textField.setText(converter.toString(getItem()));
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        textField.requestFocus();
    }

    // commits the edit. Update property if possible and revert to text display
    @Override
    public void commitEdit(T item) {

        // This block is necessary to support commit on losing focus, because the baked-in mechanism
        // sets our editing state to false before we can intercept the loss of focus.
        // The default commitEdit(...) method simply bails if we are not editing...
        if (item != null)
            if (!isEditing() && !item.equals(getItem())) {
                TreeTableView<S> table = getTableRow().getTreeTableView();
                if (table != null) {
                    TreeTableColumn<S, T> column = getTableColumn();
                    TreeTableColumn.CellEditEvent<S, T> event = new TreeTableColumn.CellEditEvent<>(table,
                            new TreeTablePosition<>(table, getIndex(), column),
                            TreeTableColumn.editCommitEvent(), item);
                    Event.fireEvent(column, event);
                }
            }

        super.commitEdit(item);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    // revert to text display
    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

}
