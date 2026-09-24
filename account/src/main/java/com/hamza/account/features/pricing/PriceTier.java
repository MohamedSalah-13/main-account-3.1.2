package com.hamza.account.features.pricing;

import java.util.Objects;

/**
 * One of the three price tiers - its name, whether the shop sells at it, and how its prices are
 * filled (V84). A plain value: the screens wrap it, nothing binds to it.
 *
 * @param rule how the tier's prices are filled, or null for a tier typed by hand
 */
public record PriceTier(int id, String name, boolean active, TierFillRule rule) {

    public PriceTier {
        if (!PriceTiers.exists(id)) {
            throw new IllegalArgumentException("Unknown price tier " + id);
        }
        name = Objects.requireNonNullElse(name, "").trim();
    }

    public boolean isFirst() {
        return id == PriceTiers.FIRST;
    }

    public PriceTier withName(String newName) {
        return new PriceTier(id, newName, active, rule);
    }

    public PriceTier withActive(boolean nowActive) {
        return new PriceTier(id, name, nowActive, rule);
    }

    public PriceTier withRule(TierFillRule newRule) {
        return new PriceTier(id, name, active, newRule);
    }

    /** What a combo shows: the name, or the number when the tier has none. */
    @Override
    public String toString() {
        return name.isBlank() ? String.valueOf(id) : name;
    }
}
