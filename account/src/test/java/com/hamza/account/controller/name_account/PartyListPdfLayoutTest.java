package com.hamza.account.controller.name_account;

import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PartyListPdfLayoutTest {

    private record Party(String name, BigDecimal balance, BigDecimal limit) {
    }

    private static final List<Party> PARTIES = List.of(
            new Party("first", new BigDecimal("2905.00"), new BigDecimal("5000.00")),
            new Party("second", new BigDecimal("-150.50"), new BigDecimal("1000.00")));

    private static <V> TableColumn<Party, V> named(String id, TableColumn<Party, V> column) {
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
        PartyListPdfLayout layout = PartyListPdfLayout.of(columns(), PARTIES,
                Set.of("actions"), Set.of(), null);

        assertEquals(3, layout.headers().length);
        assertEquals(3, layout.columnWidths().length);
        assertEquals(3, layout.rows().getFirst().length);
    }

    @Test
    void anAmountIsWrittenTheWayTheScreenWritesIt() {
        PartyListPdfLayout layout = PartyListPdfLayout.of(columns(), PARTIES,
                Set.of("actions"), Set.of(), null);

        assertEquals(Columns.money(new BigDecimal("2905.00")), layout.rows().getFirst()[1]);
        // Not BigDecimal.toString, which is what a PDF printed beside a screen reading 2,905.00.
        assertEquals("2,905.00", layout.rows().getFirst()[1]);
    }

    @Test
    void theTotalsLineSumsOnlyTheColumnsItNames() {
        PartyListPdfLayout layout = PartyListPdfLayout.of(columns(), PARTIES,
                Set.of("actions"), Set.of("balance"), "total");

        assertArrayEquals(new String[]{"total", Columns.money(new BigDecimal("2754.50")), ""},
                layout.totals());
    }

    @Test
    void withoutATotalledColumnOnScreenThereIsNoTotalsLine() {
        assertNull(PartyListPdfLayout.of(columns(), PARTIES, Set.of("actions"), Set.of(), "total").totals());
        // The one summed column is hidden, so the line would be a label over empty cells.
        List<TableColumn<Party, ?>> withoutBalance = columns().stream()
                .filter(column -> !"balance".equals(column.getId()))
                .toList();
        assertNull(PartyListPdfLayout.of(withoutBalance, PARTIES, Set.of("actions"),
                Set.of("balance"), "total").totals());
    }
}
