package com.hamza.account.features.stockcount;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * <b>One line per item on a count sheet</b>, and what a scan does to the sheet.
 * <p>
 * A line carries the item's whole book balance as it stood when the item was scanned
 * ({@code system_qty}, in base units), and what a posted count moves is each line's counted
 * quantity less that book. So two lines of one item subtract the book twice: two cartons of
 * twelve and three loose pieces against a book of 30 posted {@code 24 - 30} and {@code 3 - 30},
 * and the shelf holding 27 was booked at -3. The screen built exactly that sheet - it kept a line
 * per item <em>and</em> unit, which is right for an invoice, where each line is a thing sold, and
 * wrong here, where the book belongs to the item. Reproduced on MySQL before it was fixed.
 * <p>
 * So the item is counted on one line. Scanning a second unit of an item already on the sheet
 * restates its line in the item's base unit and adds the scan there - two cartons and a piece
 * read 25 pieces - which is also the unit the difference is posted in. The line keeps the book
 * snapshot it was given at its first scan: the adjustment is a difference against the moment the
 * item was first counted, for the reason {@code StockCountService.ensureEveryCountedItemHasARow}
 * gives.
 * <p>
 * The screen is not the only way a sheet can be built, so {@link #repeatedItem} is also what the
 * service refuses a sheet with, before it is saved or posted.
 */
public final class StockCountLines {

    private StockCountLines() {
    }

    /**
     * Puts one scan on the sheet and answers the line it landed on.
     * <ul>
     *   <li>the item is on the sheet in the unit scanned: one more of that unit;</li>
     *   <li>the item is on the sheet in another unit: its line is restated in the base unit and
     *       the scan is added in base units;</li>
     *   <li>the item is not on the sheet: a new line of one, at the top, where the eye is.</li>
     * </ul>
     *
     * @param lines   the sheet, changed in place - the screen passes the list its table shows
     * @param scanned what {@code StockCountService.lineFor} built for this scan; its snapshot is
     *                used only when the item is new to the sheet
     */
    public static StockCountLine scan(List<StockCountLine> lines, StockCountLine scanned,
                                      int baseUnitId, String baseUnitName) {
        for (int index = 0; index < lines.size(); index++) {
            StockCountLine existing = lines.get(index);
            if (existing.getItemId() != scanned.getItemId()) {
                continue;
            }
            if (existing.getUnitId() == scanned.getUnitId()) {
                existing.setCountedQuantity(existing.getCountedQuantity() + 1);
                return existing;
            }
            StockCountLine restated = existing.inBaseUnit(baseUnitId, baseUnitName);
            restated.setCountedQuantity(restated.getCountedQuantity() + scanned.getTypeValue());
            lines.set(index, restated);
            return restated;
        }
        scanned.setCountedQuantity(1);
        lines.addFirst(scanned);
        return scanned;
    }

    /** The first line of an item the sheet names more than once, or empty when every item is on one line. */
    public static Optional<StockCountLine> repeatedItem(List<StockCountLine> lines) {
        Set<Integer> seen = new HashSet<>();
        return lines.stream().filter(line -> !seen.add(line.getItemId())).findFirst();
    }
}
