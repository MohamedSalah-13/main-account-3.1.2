package com.hamza.account.features.invoice;

import java.util.Objects;

/**
 * One item chosen in the catalog dialog - or one of a bundle's components, scanned by the bundle's barcode -
 * before it is resolved for a warehouse. {@code unitId} is the unit the line is in; null is the item's base.
 */
public record ItemPickRequest(int itemId, String itemName, double quantity, Integer unitId) {

    public ItemPickRequest {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be positive");
        }
        itemName = Objects.requireNonNullElse(itemName, "");
        if (!Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive and finite");
        }
    }

    /** A choice in the item's base unit - what the catalog dialog picks. */
    public ItemPickRequest(int itemId, String itemName, double quantity) {
        this(itemId, itemName, quantity, null);
    }
}
