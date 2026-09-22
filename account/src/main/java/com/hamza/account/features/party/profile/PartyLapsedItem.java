package com.hamza.account.features.party.profile;

import java.math.BigDecimal;

/**
 * An item the party took in the period before and not in this one.
 *
 * @param previousQuantity base units in the period before, net of returns
 * @param previousNet      what it came to then
 */
public record PartyLapsedItem(int itemId, String itemName, String unitName, String groupName,
                              BigDecimal previousQuantity, BigDecimal previousNet, int previousDocuments) {
}
