package com.hamza.account.features.invoice;

import java.util.Objects;

/** One item chosen in the catalog dialog, before it is resolved for a warehouse. */
public record ItemPickRequest(int itemId, String itemName, double quantity) {

    public ItemPickRequest {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be positive");
        }
        itemName = Objects.requireNonNullElse(itemName, "");
        if (!Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive and finite");
        }
    }
}
