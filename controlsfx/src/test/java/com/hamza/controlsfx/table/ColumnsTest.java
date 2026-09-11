package com.hamza.controlsfx.table;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColumnsTest {

    private record Row(String name, int quantity, double price, LocalDate date) {
    }

    @Test
    void titleResolvesThroughTheSameBundleTheAnnotationUses() {
        TableColumn<Row, String> column = Columns.text("item.title", Row::name);
        assertEquals(LanguageManager.getInstance().getString("item.title"), column.getText());
    }

    @Test
    void textExtractsTheFieldByReferenceNotByName() {
        TableColumn<Row, String> column = Columns.text("item.title", Row::name);
        Row row = new Row("juice", 3, 9.5, LocalDate.of(2026, 1, 1));
        assertEquals("juice", cellValue(column, row));
    }

    @Test
    void numberPreservesTheDeclaredNumberTypeInsteadOfCoercingToDouble() {
        TableColumn<Row, Number> quantity = Columns.number("item.title", Row::quantity);
        TableColumn<Row, Number> price = Columns.number("item.title", Row::price);
        Row row = new Row("juice", 3, 9.5, LocalDate.of(2026, 1, 1));

        Number quantityValue = cellValue(quantity, row);
        Number priceValue = cellValue(price, row);

        assertEquals(Integer.class, quantityValue.getClass());
        assertEquals(Double.class, priceValue.getClass());
        assertEquals(3, quantityValue);
        assertEquals(9.5, priceValue);
    }

    @Test
    void dateFormatsAsIsoAndNullRendersEmptyRatherThanTheWordNull() {
        TableColumn<Row, String> column = Columns.date("item.title", Row::date);
        Row withDate = new Row("juice", 3, 9.5, LocalDate.of(2026, 1, 5));
        Row withoutDate = new Row("juice", 3, 9.5, null);

        assertEquals("2026-01-05", cellValue(column, withDate));
        assertEquals("", cellValue(column, withoutDate));
    }

    @Test
    void numberShowsTheValueTheRowHadWhenTheCellWasBuilt() {
        SimpleDoubleProperty total = new SimpleDoubleProperty(7.5);
        TableColumn<SimpleDoubleProperty, Number> column = Columns.number("item.title", SimpleDoubleProperty::get);
        ObservableValue<Number> shown = cellObservable(column, total);

        total.set(22.5);

        assertEquals(7.5, shown.getValue());
    }

    @Test
    void observableFollowsAChangeMadeAfterTheCellWasBuilt() {
        SimpleDoubleProperty total = new SimpleDoubleProperty(7.5);
        TableColumn<SimpleDoubleProperty, Number> column = Columns.observable("item.title", row -> row);
        ObservableValue<Number> shown = cellObservable(column, total);

        total.set(22.5);

        assertEquals(22.5, shown.getValue());
    }

    @Test
    void columnSetsNoIdSoACallerMustOptIntoOneExplicitly() {
        TableColumn<Row, String> column = Columns.text("item.title", Row::name);
        assertNull(column.getId());
    }

    /**
     * TableView itself needs the JavaFX toolkit running to touch, even just to
     * construct one - so pass null for it. Columns' cell value factories never
     * read the TableView argument, only the row.
     */
    private static <S, T> T cellValue(TableColumn<S, T> column, S row) {
        return cellObservable(column, row).getValue();
    }

    /** What a cell holds on to - the same object is asked again whenever the cell repaints. */
    private static <S, T> ObservableValue<T> cellObservable(TableColumn<S, T> column, S row) {
        var features = new TableColumn.CellDataFeatures<S, T>(null, column, row);
        return column.getCellValueFactory().call(features);
    }
}
