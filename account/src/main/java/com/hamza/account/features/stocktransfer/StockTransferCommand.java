package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One transfer as the screen asks for it: where from, where to, when, and what.
 * <p>
 * <b>An item may appear more than once, in different units.</b> Two cartons and three
 * pieces of the same item is an ordinary thing to type, and the screen builds it - it
 * replaces a pending line only when the item <em>and</em> the unit match. This record
 * used to refuse it with an {@code IllegalArgumentException}, so the line was accepted
 * at entry and the save then reached the user as a reference code.
 * <p>
 * What that refusal was really protecting is {@link #baseQuantityByItem()}: the balance
 * used to be checked once per <em>line</em>, so two lines of ten each would both pass
 * against a balance of fifteen. The demand is summed per item here, which is the same
 * thing {@code InvoiceStockGuard} does for a document - it judges the whole document's
 * effect on an item, not a line at a time.
 */
public record StockTransferCommand(int fromStockId, int toStockId, LocalDate transferDate,
                                   List<StockTransferLine> lines, Integer userId) {
    public StockTransferCommand {
        if (fromStockId <= 0 || toStockId <= 0 || fromStockId == toStockId)
            throw new IllegalArgumentException("Source and destination stocks must differ");
        transferDate = transferDate == null ? LocalDate.now() : transferDate;
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("Transfer needs at least one line");
    }

    /**
     * The items this transfer touches, each once, lowest id first - what to lock and what to
     * read a balance for. The order is the lock order every writer of {@code items_stock}
     * takes, so a transfer and a sale queue behind each other instead of deadlocking.
     */
    public List<Integer> itemIds() {
        return lines.stream().map(StockTransferLine::itemId).distinct().sorted().toList();
    }

    /**
     * What the source warehouse must cover, in base units, per item - the figure to compare a
     * balance with. Iterates in {@link #itemIds()} order so a refusal names the same item
     * whichever line of it came first.
     */
    public Map<Integer, Double> baseQuantityByItem() {
        Map<Integer, Double> demand = new LinkedHashMap<>();
        for (int itemId : itemIds()) {
            demand.put(itemId, 0.0);
        }
        for (StockTransferLine line : lines) {
            demand.merge(line.itemId(), line.baseQuantity(), Double::sum);
        }
        return Collections.unmodifiableMap(demand);
    }
}
