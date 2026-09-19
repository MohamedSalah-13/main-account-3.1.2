package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What one delegate is paid commission on, at what rates, <b>and from when</b>.
 *
 * <p>The date is the point. {@code targeted_sales}, which this replaces, held one row per
 * delegate with no period on it, and the view joined that row to every month in history - so
 * changing a target today changed last January's commission. It is the defect
 * {@code employees.salary} had, answered the way {@code employee_compensation} answered it:
 * the rule gets a day it starts on, a month is judged by the rule in force on its first day,
 * and a rule dated in the future is the ordinary case - April's target is agreed in March.
 *
 * @param effectiveFrom the day this rule starts. Unique per delegate, so an amendment is an
 *                      edit of that day's rule rather than a second one nobody can order
 * @param target        the month's target; zero for a flat rate with no target
 */
public record CommissionRule(int id, int employeeId, LocalDate effectiveFrom, CommissionBasis basis,
                             TierMode tierMode, BigDecimal target, CommissionTiers tiers, String notes) {

    public CommissionRule {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(tierMode, "tierMode");
        Objects.requireNonNull(tiers, "tiers");
        if (target == null || target.signum() < 0) {
            throw new IllegalArgumentException("commission.error.target.negative");
        }
        if (target.signum() == 0 && !tiers.validWithoutTarget()) {
            throw new IllegalArgumentException("commission.error.target.missing");
        }
    }

    public boolean hasTarget() {
        return target.signum() > 0;
    }

    /** What an amount earns under this rule. */
    public CommissionTiers.Result calculate(BigDecimal base) {
        return tiers.calculate(base, target, tierMode);
    }
}
