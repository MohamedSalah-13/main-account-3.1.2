package com.hamza.account.features.itemmerge;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One item on the merge screen, with what the user needs in order to decide.
 * <p>
 * {@code lineCount} and {@code lastMovement} are the two that settle it in practice:
 * of a group of near-identical rows, the one that is still being sold is the target and
 * the ones that stopped moving two years ago are the duplicates. Both come from
 * {@code card_item_view}, so they count exactly what the item card would show.
 *
 * @param groupKey     what put this row in the same group as its neighbours
 * @param lineCount    document lines over the four families
 * @param lastMovement the newest of those lines, or null if the item never moved
 */
public record ItemMergeCandidate(int id, String name, String barcode,
                                 int unitId, String unitName, boolean hasValidity,
                                 BigDecimal sellPrice, BigDecimal firstBalance,
                                 String groupKey, int lineCount, LocalDate lastMovement) {

    public ItemMergeCandidate {
        sellPrice = sellPrice == null ? BigDecimal.ZERO : sellPrice;
        firstBalance = firstBalance == null ? BigDecimal.ZERO : firstBalance;
    }

    /** Whether this row could be merged into {@code other} - the same rules, before a query. */
    public boolean canMergeInto(ItemMergeCandidate other) {
        return other != null
               && other.id != id
               && other.unitId == unitId
               && (!hasValidity || other.hasValidity);
    }
}
