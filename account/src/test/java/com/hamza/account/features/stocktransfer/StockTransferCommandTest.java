package com.hamza.account.features.stocktransfer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StockTransferCommandTest {

    @Test void defaultsMissingDateAndRejectsSameWarehouse() {
        StockTransferCommand command = new StockTransferCommand(1, 2, null, List.of(new StockTransferLine(7, 2)), null);
        assertEquals(LocalDate.now(), command.transferDate());
        assertThrows(IllegalArgumentException.class, () -> new StockTransferCommand(1, 1, LocalDate.now(), List.of(new StockTransferLine(7, 2)), null));
    }

    @Test void rejectsEmptyAndInvalidLines() {
        assertThrows(IllegalArgumentException.class, () -> new StockTransferCommand(1, 2, LocalDate.now(), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new StockTransferLine(7, 0));
    }

    /**
     * Two cartons and three pieces of one item is an ordinary thing to type, and the screen
     * builds it. The record used to refuse it with an {@code IllegalArgumentException}, which
     * reached the user as a reference code after the line had been accepted at entry.
     */
    @Test void acceptsOneItemInTwoUnits() {
        StockTransferCommand command = new StockTransferCommand(1, 2, LocalDate.now(),
                List.of(new StockTransferLine(7, 2, 3, 12), new StockTransferLine(7, 3, 1, 1)), null);
        assertEquals(2, command.lines().size());
        assertEquals(List.of(7), command.itemIds());
    }

    /**
     * The figure a balance is compared with is the item's, not the line's. Checked per line -
     * as it was - ten and ten both pass against a balance of fifteen.
     */
    @Test void sumsTheDemandOfOneItemAcrossItsLines() {
        StockTransferCommand command = new StockTransferCommand(1, 2, LocalDate.now(),
                List.of(new StockTransferLine(7, 2, 3, 12), new StockTransferLine(7, 3, 1, 1)), null);
        assertEquals(Map.of(7, 27.0), command.baseQuantityByItem());
    }

    @Test void namesEachItemOnceLowestFirst() {
        StockTransferCommand command = new StockTransferCommand(1, 2, LocalDate.now(),
                List.of(new StockTransferLine(9, 1), new StockTransferLine(4, 1), new StockTransferLine(9, 2)), null);
        assertEquals(List.of(4, 9), command.itemIds());
        assertEquals(Map.of(4, 1.0, 9, 3.0), command.baseQuantityByItem());
    }

    /** The demand is read in the order the items are locked in, so a refusal names the same item twice running. */
    @Test void demandIteratesInLockOrder() {
        StockTransferCommand command = new StockTransferCommand(1, 2, LocalDate.now(),
                List.of(new StockTransferLine(9, 1), new StockTransferLine(4, 1)), null);
        assertEquals(List.of(4, 9), List.copyOf(command.baseQuantityByItem().keySet()));
    }
}
