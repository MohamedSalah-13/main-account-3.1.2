package com.hamza.account.features.delegate;

/**
 * How a reached tier's rate is applied.
 *
 * <ul>
 *   <li>{@link #WHOLE} - the rate of the highest tier reached is paid on the whole amount.
 *       Reaching 100% at 3% pays 3% of everything. It is what the old {@code target_delegate}
 *       view meant, and the default.</li>
 *   <li>{@link #MARGINAL} - each tier's rate is paid only on the part of the amount that lies
 *       inside that tier, like a tax band. Crossing a threshold never makes the pound before
 *       it worth more.</li>
 * </ul>
 */
public enum TierMode {

    WHOLE("commission.tier.mode.whole"),
    MARGINAL("commission.tier.mode.marginal");

    private final String messageKey;

    TierMode(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public static TierMode of(String stored) {
        for (TierMode mode : values()) {
            if (mode.name().equals(stored)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown tier mode: " + stored);
    }
}
