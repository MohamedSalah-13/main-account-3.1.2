package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;
import java.util.List;

public record StockTransferCommand(int fromStockId, int toStockId, LocalDate transferDate,
                                   List<StockTransferLine> lines, Integer userId) {
    public StockTransferCommand {
        if (fromStockId <= 0 || toStockId <= 0 || fromStockId == toStockId)
            throw new IllegalArgumentException("Source and destination stocks must differ");
        transferDate = transferDate == null ? LocalDate.now() : transferDate;
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("Transfer needs at least one line");
        if (lines.stream().map(StockTransferLine::itemId).distinct().count() != lines.size())
            throw new IllegalArgumentException("An item may appear only once in a transfer");
    }
}