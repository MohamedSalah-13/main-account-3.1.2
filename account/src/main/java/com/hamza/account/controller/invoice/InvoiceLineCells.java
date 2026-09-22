package com.hamza.account.controller.invoice;

import com.hamza.account.features.invoice.InvoiceLineTotals;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.List;

/**
 * The editable cells of the invoice lines table, which open only on a line of the invoice.
 *
 * <p>The quick screen keeps a trailing entry row that names no item. Its barcode and name
 * cells are the entry surface; its price, discount, quantity and unit are not, and they used to
 * open anyway on a double-click: the edit was then refused by {@code InvoiceLineEditService} with
 * "the invoice line is not valid", and the quantity cell reopened itself on the refusal. A cell
 * that cannot be committed must not open. On the standard screen there is no entry row and these
 * behave exactly as {@link TextFieldTableCell} does.
 */
final class InvoiceLineCells {

    private InvoiceLineCells() {
    }

    /** A text cell that opens on a line of the invoice and never on the entry row. */
    static <T extends BasePurchasesAndSales, V> Callback<TableColumn<T, V>, TableCell<T, V>> text(
            StringConverter<V> converter) {
        return column -> new TextFieldTableCell<>(converter) {
            @Override
            public void startEdit() {
                if (isLine(getTableRow() == null ? null : getTableRow().getItem())) {
                    super.startEdit();
                }
            }
        };
    }

    /** The unit cell: a list of the line's own item's units, opened only when there is a choice. */
    static <T extends BasePurchasesAndSales> Callback<TableColumn<T, String>, TableCell<T, String>> unit() {
        return column -> new UnitCell<>();
    }

    static boolean isLine(BasePurchasesAndSales line) {
        return !InvoiceLineTotals.isPlaceholder(line);
    }

    /**
     * Whether a unit can be chosen on this row at all: a line of the invoice, of an item sold in
     * more than one unit, that was not picked from a source invoice (whose unit is the sale's).
     */
    static boolean offersUnits(BasePurchasesAndSales line) {
        return isLine(line) && line.getSourceLineId() <= 0 && ItemUnits.unitsFor(line.getItems()).size() > 1;
    }

    private static final class UnitCell<T extends BasePurchasesAndSales> extends TableCell<T, String> {
        private ComboBox<String> box;

        @Override
        public void startEdit() {
            BasePurchasesAndSales line = getTableRow() == null ? null : getTableRow().getItem();
            if (!offersUnits(line)) {
                return;
            }
            super.startEdit();
            if (!isEditing()) {
                return;
            }
            List<String> names = ItemUnits.unitsFor(line.getItems()).stream()
                    .map(UnitsModel::getUnit_name).toList();
            box = new ComboBox<>();
            box.getItems().setAll(names);
            box.setValue(getItem());
            box.setMaxWidth(Double.MAX_VALUE);
            // A choice is the commit: one gesture, as a combo reads everywhere else.
            box.setOnAction(event -> {
                String chosen = box.getValue();
                if (chosen != null && !chosen.equals(getItem())) {
                    commitEdit(chosen);
                } else {
                    cancelEdit();
                }
            });
            box.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
            setText(null);
            setGraphic(box);
            Platform.runLater(() -> {
                if (box != null) {
                    box.requestFocus();
                    box.show();
                }
            });
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            box = null;
            setGraphic(null);
            setText(getItem());
        }

        @Override
        public void commitEdit(String value) {
            super.commitEdit(value);
            box = null;
            setGraphic(null);
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing() && box != null) {
                setText(null);
                setGraphic(box);
            } else {
                setText(value);
                setGraphic(null);
            }
        }
    }
}
