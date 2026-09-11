package com.hamza.account.table;

import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibleColumnsExcelWriterTest {

    private record Party(String name, String area, BigDecimal balance) {
    }

    private static final List<Party> PARTIES = List.of(
            new Party("first", "north", new BigDecimal("2905.00")),
            new Party("second", null, new BigDecimal("-150.50")));

    private static <V> TableColumn<Party, V> named(String id, TableColumn<Party, V> column) {
        column.setId(id);
        return column;
    }

    private static List<TableColumn<Party, ?>> visible() {
        return List.of(
                named("actions", new TableColumn<Party, Void>("actions")),
                named("name", Columns.text("name", Party::name)),
                named("area", Columns.text("party.column.area", Party::area)),
                named("balance", Columns.money("party.balances.column.balance", Party::balance)));
    }

    private static VisibleColumnsExcelWriter<Party> writer(List<TableColumn<Party, ?>> columns) {
        return new VisibleColumnsExcelWriter<>("sheet",
                VisibleColumns.dataColumns(columns, Set.of("actions")), PARTIES);
    }

    @Test
    void theHeaderIsTheVisibleDataColumnsInTheirOrder() {
        List<TableColumn<Party, ?>> columns = visible();

        assertArrayEquals(new Object[]{columns.get(1).getText(), columns.get(2).getText(),
                columns.get(3).getText()}, writer(columns).columnHeader());
    }

    @Test
    void aHiddenColumnIsNotInTheFile() {
        // What the table hands over is its visible columns, so a hidden one never arrives.
        List<TableColumn<Party, ?>> withoutArea = visible().stream()
                .filter(column -> !"area".equals(column.getId()))
                .toList();

        VisibleColumnsExcelWriter<Party> writer = writer(withoutArea);

        assertEquals(2, writer.columnHeader().length);
        assertArrayEquals(new Object[]{"first", new BigDecimal("2905.00")},
                writer.dataRow(PARTIES.getFirst()));
    }

    @Test
    void aCellHoldsTheColumnsOwnValueAndAnEmptyOneStaysEmpty() {
        assertArrayEquals(new Object[]{"second", "", new BigDecimal("-150.50")},
                writer(visible()).dataRow(PARTIES.get(1)));
    }
}
