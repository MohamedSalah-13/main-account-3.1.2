package com.hamza.account.features.party.profile;

import java.math.BigDecimal;

/**
 * One item a party bought (or, for a supplier, sold us) over a period.
 *
 * @param quantity       base units, net of returns - {@code unitName} names them
 * @param amount         the documents' lines after their own discounts
 * @param returnedAmount the returns' lines, positive
 * @param documents      how many of the party's documents named the item
 */
public record PartyItemRow(int itemId, String itemName, String unitName, int groupId, String groupName,
                           BigDecimal quantity, BigDecimal amount, BigDecimal returnedAmount, int documents) {

    public BigDecimal net() {
        return amount.subtract(returnedAmount);
    }
}
