package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * One delegate's month on the performance report: what he did, and - for a reader allowed to
 * see rates - what the rule in force on the first of the month makes of it.
 *
 * <p>The commission here is a <b>preview</b>. It is computed when the report is read, from the
 * month as it stands and the rule as it stands, and nothing is stored: a month is only ever owed
 * what a commission run froze for it (phase C). Until there is a run, this figure is the honest
 * answer to "what would he get if the month closed now".
 *
 * @param rule    empty when the delegate had no rule by the first of the month, <b>and</b> when
 *                the reader may not see rates - in which case it was never fetched
 * @param outcome the rule applied to the month; present exactly when {@code rule} is
 */
public record DelegatePerformanceRow(DelegateActivity activity, Optional<CommissionRule> rule,
                                     Optional<CommissionTiers.Result> outcome) {

    public static DelegatePerformanceRow of(DelegateActivity activity, Optional<CommissionRule> rule) {
        return new DelegatePerformanceRow(activity, rule,
                rule.map(found -> found.calculate(activity.baseFor(found.basis()))));
    }

    public String name() {
        return activity.name();
    }

    /** The amount the rule is a percentage of; null without a rule. */
    public BigDecimal base() {
        return rule.map(found -> activity.baseFor(found.basis())).orElse(null);
    }

    /** Null without a rule, and null for a flat rate - which has no target to show. */
    public BigDecimal target() {
        return rule.filter(CommissionRule::hasTarget).map(CommissionRule::target).orElse(null);
    }

    public BigDecimal achievementPercent() {
        return outcome.map(CommissionTiers.Result::achievementPercent).orElse(null);
    }

    public BigDecimal ratePercent() {
        return outcome.filter(result -> result.tier() > 0).map(CommissionTiers.Result::ratePercent).orElse(null);
    }

    public BigDecimal commission() {
        return outcome.map(CommissionTiers.Result::amount).orElse(null);
    }

    /** A delegate with a target who has not reached the lowest tier - the row the report is opened to find. */
    public boolean belowLowestTier() {
        return rule.isPresent() && outcome.map(result -> result.tier() == 0).orElse(false);
    }
}
