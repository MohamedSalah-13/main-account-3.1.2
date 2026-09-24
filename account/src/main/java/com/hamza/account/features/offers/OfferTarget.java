package com.hamza.account.features.offers;

import java.util.Objects;

/**
 * One thing an offer reaches, or one it leaves out (docs/pricing-and-offers-plan.md ق-ع٦): an item - in one
 * of its units when {@link #unitId} is set - a sub group, a main group, or everything. An excluded target
 * takes a line back out of what the others reached: "every detergent except the powder" is a main group
 * and an excluded item.
 * <p>
 * The ids a scope does not use are null, which is what {@code offer_target_scope_chk} holds the row to.
 */
public record OfferTarget(OfferScope scope, Integer itemId, Integer unitId, Integer subGroupId,
                          Integer mainGroupId, boolean excluded) {

    public OfferTarget {
        Objects.requireNonNull(scope, "scope");
        switch (scope) {
            case ITEM -> requireOnly(itemId != null, subGroupId, mainGroupId);
            case SUB_GROUP -> requireOnly(subGroupId != null, itemId, unitId, mainGroupId);
            case MAIN_GROUP -> requireOnly(mainGroupId != null, itemId, unitId, subGroupId);
            case ALL -> {
                requireOnly(true, itemId, unitId, subGroupId, mainGroupId);
                if (excluded) {
                    throw new IllegalArgumentException("excluding everything leaves nothing");
                }
            }
        }
    }

    public static OfferTarget item(int itemId) {
        return new OfferTarget(OfferScope.ITEM, itemId, null, null, null, false);
    }

    public static OfferTarget itemInUnit(int itemId, int unitId) {
        return new OfferTarget(OfferScope.ITEM, itemId, unitId, null, null, false);
    }

    public static OfferTarget subGroup(int subGroupId) {
        return new OfferTarget(OfferScope.SUB_GROUP, null, null, subGroupId, null, false);
    }

    public static OfferTarget mainGroup(int mainGroupId) {
        return new OfferTarget(OfferScope.MAIN_GROUP, null, null, null, mainGroupId, false);
    }

    public static OfferTarget everything() {
        return new OfferTarget(OfferScope.ALL, null, null, null, null, false);
    }

    /** The same target, leaving out what it names instead of reaching it. */
    public OfferTarget except() {
        return new OfferTarget(scope, itemId, unitId, subGroupId, mainGroupId, true);
    }

    /** Whether this target names the line - whichever way round, reached or left out. */
    public boolean names(OfferEngine.Line line) {
        return switch (scope) {
            case ITEM -> itemId == line.itemId() && (unitId == null || unitId == line.unitId());
            case SUB_GROUP -> subGroupId == line.subGroupId();
            case MAIN_GROUP -> mainGroupId == line.mainGroupId();
            case ALL -> true;
        };
    }

    private static void requireOnly(boolean named, Integer... unused) {
        if (!named) {
            throw new IllegalArgumentException("a target names what its scope reaches");
        }
        for (Integer id : unused) {
            if (id != null) {
                throw new IllegalArgumentException("a target names nothing its scope does not use");
            }
        }
    }
}
