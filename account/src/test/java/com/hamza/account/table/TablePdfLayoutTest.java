package com.hamza.account.table;

import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TablePdfLayoutTest {

    private record Party(String name, BigDecimal balance, BigDecimal limit) {
    }

    private record Item(int code, double price, double balance) {
    }

    private static final List<Party> PARTIES = List.of(
            new Party("first", new BigDecimal("2905.00"), new BigDecimal("5000.00")),
            new Party("second", new BigDecimal("-150.50"), new BigDecimal("1000.00")));

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static List<TableColumn<Party, ?>> columns() {
        return List.of(
                named("actions", new TableColumn<Party, Void>("actions")),
                named("name", Columns.text("name", Party::name)),
                named("balance", Columns.money("party.balances.column.balance", Party::balance)),
                named("limit", Columns.money("party.balances.column.limit", Party::limit)));
    }

    @Test
    void aScreenOnlyColumnIsNotPrinted() {
        TablePdfLayout layout = TablePdfLayout.of(columns(), PARTIES,
                Set.of("actions"), Set.of(), null);

        assertEquals(3, layout.headers().length);
        assertEquals(3, layout.columnWidths().length);
        assertEquals(3, layout.rows().getFirst().length);
    }

    @Test
    void anAmountIsWrittenTheWayTheScreenWritesIt() {
        TablePdfLayout layout = TablePdfLayout.of(columns(), PARTIES,
                Set.of("actions"), Set.of(), null);

        assertEquals(Columns.money(new BigDecimal("2905.00")), layout.rows().getFirst()[1]);
        // Not BigDecimal.toString, which is what a PDF printed beside a screen reading 2,905.00.
        assertEquals("2,905.00", layout.rows().getFirst()[1]);
    }

    @Test
    void theTotalsLineSumsOnlyTheColumnsItNames() {
        TablePdfLayout layout = TablePdfLayout.of(columns(), PARTIES,
                Set.of("actions"), Set.of("balance"), "total");

        assertArrayEquals(new String[]{"total", Columns.money(new BigDecimal("2754.50")), ""},
                layout.totals());
    }

    @Test
    void withoutATotalledColumnOnScreenThereIsNoTotalsLine() {
        assertNull(TablePdfLayout.of(columns(), PARTIES, Set.of("actions"), Set.of(), "total").totals());
        // The one summed column is hidden, so the line would be a label over empty cells.
        List<TableColumn<Party, ?>> withoutBalance = columns().stream()
                .filter(column -> !"balance".equals(column.getId()))
                .toList();
        assertNull(TablePdfLayout.of(withoutBalance, PARTIES, Set.of("actions"),
                Set.of("balance"), "total").totals());
    }

    /**
     * The items list holds its prices and balances as {@code double}, so nothing in the value says
     * which is money; the caller does. A code it names as neither stays as it is - written as a
     * quantity, a four-digit code would come out with a thousands separator in it.
     */
    @Test
    void aNamedNumberColumnIsMoneyOrAQuantityAndAnUnnamedOneIsLeftAlone() {
        List<TableColumn<Item, ?>> columns = List.of(
                named("code", Columns.number("code", Item::code)),
                named("price", Columns.number("total", Item::price)),
                named("balance", Columns.number("balance", Item::balance)));

        TablePdfLayout layout = TablePdfLayout.of(columns, List.of(new Item(1234, 1050.5, 3.0)),
                Set.of(), Set.of(), null,
                new TablePdfLayout.NumberFormats(Set.of("price"), Set.of("balance")));

        assertArrayEquals(new String[]{"1234", "1,050.50", "3"}, layout.rows().getFirst());
    }
}
