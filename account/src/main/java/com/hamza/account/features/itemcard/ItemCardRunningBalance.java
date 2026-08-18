package com.hamza.account.features.itemcard;

import com.hamza.account.model.domain.CardItems;

import java.util.List;

/**
 * Writes the running balance onto the rows of an item card.
 * <p>
 * The rows arrive in the order the movements happened (the card query orders by
 * document date, then by when the document was entered, then by the line's id), and
 * each row's balance is what was left of the item once that movement had been applied.
 * The sign is already on {@link CardItems#getBaseQuantity()}, so nothing here has to
 * know which documents put stock in.
 * <p>
 * The last row's balance equals the closing balance the database reports for the same
 * date only when no stock count was posted inside the period - a count moves the
 * balance without producing a card line, which is exactly why the screen shows the
 * closing balance from the database rather than from the last row.
 */
public final class ItemCardRunningBalance {

    private ItemCardRunningBalance() {
    }

    /**
     * @param opening what the item's balance was before the first row, in base units
     * @return the balance after the last row
     */
    public static double apply(List<CardItems> rows, double opening) {
        double balance = opening;
        for (CardItems row : rows) {
            balance += row.getBaseQuantity();
            row.setBalance(balance);
        }
        return balance;
    }
}
