package com.hamza.account.features.totals;

import java.math.BigDecimal;
import java.util.List;

/**
 * How many rows are ticked on the page, and what they come to - the line beside the delete button.
 * <p>
 * A delete button that acts on "the ticked rows" gives no hint how many that is until the
 * confirmation; a tick left on a row scrolled out of view is exactly the one nobody counts.
 */
public record TotalsSelection(int count, BigDecimal total) {

    public static final TotalsSelection NONE = new TotalsSelection(0, BigDecimal.ZERO);

    public static TotalsSelection of(List<BigDecimal> tickedTotals) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : tickedTotals) {
            if (value != null) {
                total = total.add(value);
            }
        }
        return new TotalsSelection(tickedTotals.size(), total);
    }

    public boolean isEmpty() {
        return count == 0;
    }
}
