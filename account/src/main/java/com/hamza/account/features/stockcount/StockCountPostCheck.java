package com.hamza.account.features.stockcount;

import com.hamza.account.features.documentdelete.DocumentDeleteStockCheck;

import java.util.List;
import java.util.Map;

/**
 * What posting a count would leave below zero - a warning, never a refusal.
 * <p>
 * A line's "resulting balance" on screen is what was counted, which is true only while nothing has
 * moved the item since it was scanned. Posting adds the line's difference to the balance as it is
 * <em>now</em> ({@code system_qty} is a snapshot, deliberately - see {@code StockCount}): counted 2
 * against a book of 5, then 4 sold before the post, and the shelf is booked at {@code 1 - 3 = -2}.
 * That is a real answer - the goods did leave - so it is not refused; the person posting is told
 * which items and decides, the way {@code DocumentDeleteStockCheck} tells somebody deleting a
 * purchase. The arithmetic is that check's own, with the difference as what the post adds.
 */
public final class StockCountPostCheck {

    private StockCountPostCheck() {
    }

    /**
     * @param balances  each item's balance in the counted warehouse now, in base units
     * @param stockName the counted warehouse, for the sentence
     * @return the lines that would end below zero, in sheet order; empty when none would
     */
    public static List<DocumentDeleteStockCheck.Shortfall> shortfalls(List<StockCountLine> lines,
                                                                     Map<Integer, Double> balances,
                                                                     String stockName) {
        return DocumentDeleteStockCheck.shortfalls(lines.stream()
                .map(line -> new DocumentDeleteStockCheck.StockLine(line.getItemName(), stockName,
                        balances.getOrDefault(line.getItemId(), 0.0), -line.difference()))
                .toList());
    }
}
