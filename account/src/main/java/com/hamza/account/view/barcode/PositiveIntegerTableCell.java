package com.hamza.account.view.barcode;

import com.hamza.account.features.barcodeprint.BarcodePrintValidation;
import com.hamza.controlsfx.others.TextFormat;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

/** Editable quantity cell that rejects non-digits before they reach the table value. */
final class PositiveIntegerTableCell extends TableCell<PrintBarcodeModel, Integer> {
    private TextField editor;

    @Override
    public void startEdit() {
        if (isEmpty()) {
            return;
        }
        super.startEdit();
        if (editor == null) {
            editor = createEditor();
        }
        editor.setText(String.valueOf(getItem()));
        setText(null);
        setGraphic(editor);
        editor.selectAll();
        editor.requestFocus();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setGraphic(null);
        setText(getItem() == null ? "" : String.valueOf(getItem()));
    }

    @Override
    protected void updateItem(Integer value, boolean empty) {
        super.updateItem(value, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        } else if (isEditing()) {
            editor.setText(String.valueOf(value));
            setText(null);
            setGraphic(editor);
        } else {
            setText(String.valueOf(value));
            setGraphic(null);
        }
    }

    private TextField createEditor() {
        var field = new TextField();
        field.setTextFormatter(TextFormat.createNumericTextFormatter());
        field.setOnAction(event -> commitEditor());
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });
        field.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused && isEditing()) {
                commitEditor();
            }
        });
        return field;
    }

    private void commitEditor() {
        try {
            int value = Integer.parseInt(editor.getText());
            if (value < 1 || value > BarcodePrintValidation.MAX_COPIES_PER_LINE) {
                cancelEdit();
                return;
            }
            commitEdit(value);
        } catch (NumberFormatException ignored) {
            cancelEdit();
        }
    }
}
