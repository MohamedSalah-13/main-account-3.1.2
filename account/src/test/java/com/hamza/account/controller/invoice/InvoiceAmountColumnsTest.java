package com.hamza.account.controller.invoice;

import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.model.domain.Sales;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The invoice lines table's amount columns must show what an edit did to the line.
 * <p>
 * They were built from getters, which hand a cell a copy of the value taken when the row
 * was drawn: changing the quantity recalculated the line's total on the model, the footer
 * summed the new figure, and the row's own total and total-after-discount went on showing
 * the old one. Each test here takes the value a cell holds <b>before</b> the edit and asks
 * it again afterwards, which is what the cell does when it repaints.
 */
class InvoiceAmountColumnsTest {

    private final List<TableColumn<Sales, Number>> columns = InvoiceTableCoordinator.amountColumns();

    @Test
    void editingTheQuantityRecalculatesBothTotalsOnScreen() {
        Sales line = line(3, 2.5, 0);
        ObservableValue<Number> total = shown(InvoiceTableCoordinator.TOTAL_COLUMN, line);
        ObservableValue<Number> totalAfter = shown(InvoiceTableCoordinator.TOTAL_AFTER_COLUMN, line);

        line.setQuantity(5);
        InvoiceLineService.recalculate(line);

        assertEquals(12.5, total.getValue().doubleValue());
        assertEquals(12.5, totalAfter.getValue().doubleValue());
    }

    @Test
    void editingThePriceRecalculatesBothTotalsOnScreen() {
        Sales line = line(2, 10, 1);
        ObservableValue<Number> total = shown(InvoiceTableCoordinator.TOTAL_COLUMN, line);
        ObservableValue<Number> totalAfter = shown(InvoiceTableCoordinator.TOTAL_AFTER_COLUMN, line);

        line.setPrice(7.5);
        InvoiceLineService.recalculate(line);

        assertEquals(15.0, total.getValue().doubleValue());
        assertEquals(14.0, totalAfter.getValue().doubleValue());
    }

    @Test
    void editingTheDiscountRecalculatesTheTotalAfterItOnScreen() {
        Sales line = line(2, 10, 0);
        ObservableValue<Number> totalAfter = shown(InvoiceTableCoordinator.TOTAL_AFTER_COLUMN, line);

        line.setDiscount(3);
        InvoiceLineService.recalculate(line);

        assertEquals(17.0, totalAfter.getValue().doubleValue());
    }

    @Test
    void theEditedCellsThemselvesFollowTheLineToo() {
        Sales line = line(3, 2.5, 0);
        ObservableValue<Number> quantity = shown(InvoiceTableCoordinator.QUANTITY_COLUMN, line);
        ObservableValue<Number> price = shown(InvoiceTableCoordinator.PRICE_COLUMN, line);
        ObservableValue<Number> discount = shown(InvoiceTableCoordinator.DISCOUNT_COLUMN, line);

        // The +/- keys write the quantity straight onto the line, with no cell editor involved.
        line.setQuantity(4);
        line.setPrice(3);
        line.setDiscount(1);

        assertEquals(4.0, quantity.getValue().doubleValue());
        assertEquals(3.0, price.getValue().doubleValue());
        assertEquals(1.0, discount.getValue().doubleValue());
    }

    private static Sales line(double quantity, double price, double discount) {
        Sales line = new Sales();
        line.setQuantity(quantity);
        line.setPrice(price);
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
        return line;
    }

    /** The table's column index, mapped onto the amount columns that start at the quantity. */
    private ObservableValue<Number> shown(int tableColumn, Sales line) {
        TableColumn<Sales, Number> column = columns.get(tableColumn - InvoiceTableCoordinator.QUANTITY_COLUMN);
        return column.getCellValueFactory().call(new TableColumn.CellDataFeatures<>(null, column, line));
    }
}
