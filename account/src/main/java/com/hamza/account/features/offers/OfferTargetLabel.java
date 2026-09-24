package com.hamza.account.features.offers;

/** A target as the screen shows it: the target, and the name of what it names - an item, a unit, a group. */
public record OfferTargetLabel(OfferTarget target, String itemName, String unitName, String subGroupName,
                               String mainGroupName) {
}
