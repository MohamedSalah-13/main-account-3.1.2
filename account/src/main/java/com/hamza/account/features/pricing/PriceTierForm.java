package com.hamza.account.features.pricing;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the price tiers screen decides before it asks the service anything - which source a tier may be
 * filled from, the rule its three boxes describe, and which tiers a save would switch off under
 * customers. No JavaFX: the screen reads its controls and hands the values here.
 */
public final class PriceTierForm {

    private PriceTierForm() {
    }

    /** What a tier's prices are filled from, as the screen's list offers it: nothing, the cost, or a tier. */
    public record RuleSource(TierFillRule.Source source, Integer tierId) {

        public static final RuleSource NONE = new RuleSource(null, null);
        public static final RuleSource COST = new RuleSource(TierFillRule.Source.COST, null);

        public static RuleSource tier(int tierId) {
            return new RuleSource(TierFillRule.Source.TIER, tierId);
        }

        public static RuleSource of(TierFillRule rule) {
            if (rule == null) {
                return NONE;
            }
            return rule.source() == TierFillRule.Source.COST ? COST : tier(rule.sourceTierId());
        }

        public boolean none() {
            return source == null;
        }

        /** Nothing, the cost, and every other tier - never the tier itself. */
        public static List<RuleSource> choicesFor(int tierId) {
            List<RuleSource> choices = new ArrayList<>(List.of(NONE, COST));
            for (int other : PriceTiers.IDS) {
                if (other != tierId) {
                    choices.add(tier(other));
                }
            }
            return choices;
        }
    }

    /**
     * The rule a row describes, or null for a tier typed by hand. A rule needs its percentage and its
     * rounding; each missing or impossible figure refuses with its own message key.
     */
    public static TierFillRule rule(RuleSource source, BigDecimal percent, BigDecimal rounding)
            throws UserValidationException {
        if (source == null || source.none()) {
            return null;
        }
        if (percent == null) {
            throw new UserValidationException("pricing.tier.error.rule.percent.required");
        }
        if (percent.compareTo(BigDecimal.valueOf(-100)) <= 0 || percent.compareTo(PriceTierService.PERCENT_MAX) > 0) {
            throw new UserValidationException("pricing.tier.error.rule.percent");
        }
        if (rounding == null || rounding.signum() <= 0) {
            throw new UserValidationException("pricing.tier.error.rule.rounding");
        }
        return new TierFillRule(source.source(), source.tierId(), percent, rounding);
    }

    /**
     * The tiers a save would switch off that customers are on, with how many - what the screen asks about
     * before it saves: those customers' invoices open at tier 1 while theirs is off.
     */
    public static Map<PriceTier, Integer> switchedOffUnderCustomers(PriceTierCatalog before, List<PriceTier> after,
                                                                    Map<Integer, Integer> customersByTier) {
        Objects.requireNonNull(before, "before");
        Map<PriceTier, Integer> affected = new java.util.LinkedHashMap<>();
        for (PriceTier tier : after) {
            boolean wasActive = before.isActive(tier.id());
            int customers = customersByTier.getOrDefault(tier.id(), 0);
            if (wasActive && !tier.active() && customers > 0) {
                affected.put(tier, customers);
            }
        }
        return affected;
    }
}
