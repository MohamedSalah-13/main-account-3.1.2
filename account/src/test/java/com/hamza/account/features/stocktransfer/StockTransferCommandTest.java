package com.hamza.account.features.stocktransfer;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StockTransferCommandTest {
    @Test void defaultsMissingDateAndRejectsSameWarehouse() {
        StockTransferCommand command = new StockTransferCommand(1, 2, null, List.of(new StockTransferLine(7, 2)), null);
        assertEquals(LocalDate.now(), command.transferDate());
        assertThrows(IllegalArgumentException.class, () -> new StockTransferCommand(1, 1, LocalDate.now(), List.of(new StockTransferLine(7, 2)), null));
    }
    @Test void rejectsEmptyDuplicateAndInvalidLines() {
        assertThrows(IllegalArgumentException.class, () -> new StockTransferCommand(1, 2, LocalDate.now(), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new StockTransferCommand(1, 2, LocalDate.now(), List.of(new StockTransferLine(7, 1), new StockTransferLine(7, 2)), null));
        assertThrows(IllegalArgumentException.class, () -> new StockTransferLine(7, 0));
    }
}