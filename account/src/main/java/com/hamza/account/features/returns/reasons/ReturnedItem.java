package com.hamza.account.features.returns.reasons;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An item that came back over a period: how much of it, in its base unit, what its lines came to after
 * their own discounts, and on how many returns. A discount on a whole return belongs to no item and is not
 * shared out - {@code ItemNetLines}'s rule - so the items add up to the returns' lines, not their net.
 */
public record ReturnedItem(int itemId, String name, BigDecimal baseQuantity, BigDecimal value, int returns) {

    public ReturnedItem {
        name = Objects.requireNonNullElse(name, "");
        baseQuantity = baseQuantity == null ? BigDecimal.ZERO : baseQuantity;
        value = value == null ? BigDecimal.ZERO : value;
    }
}
