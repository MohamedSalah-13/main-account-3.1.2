package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * One delegate's commission for one month, <b>with everything that produced the figure</b>.
 *
 * <p>The amount alone would be unreadable in a year: the rule may have been replaced, the
 * delegate's invoices corrected, the customer moved. So the line carries the basis, the target,
 * the tiers as they stood, the three figures of the month, the base the rule was applied to, and
 * the tier that was reached - enough to work the amount out again with a pen, from the line alone.
 *
 * @param id             zero for a line that has not been written yet - a preview
 * @param tiersSnapshot  the rule's tiers on the day of approval, {@code from:rate|from:rate}
 * @param baseAmount     what the rate was applied to; may be negative, and earns nothing then
 * @param achievementPercent null for a flat rate, which has no target to measure against
 * @param tier           the tier reached, from 1; zero when the lowest was not reached
 * @param posting        where this line went, once it has gone anywhere
 */
public record CommissionLine(int id, int runId, int employeeId, String employeeName, int ruleId,
                             CommissionBasis basis, TierMode tierMode, BigDecimal target, String tiersSnapshot,
                             BigDecimal sales, BigDecimal salesReturns, BigDecimal collected,
                             BigDecimal baseAmount, BigDecimal achievementPercent, int tier,
                             BigDecimal ratePercent, BigDecimal amount, Posting posting) {

    /** The two roads a commission reaches a delegate's account by; a line takes exactly one, once. */
    public enum Posting {
        NONE("commission.posting.none"),
        PAYROLL("commission.posting.payroll"),
        ACCOUNT("commission.posting.account");

        private final String messageKey;

        Posting(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public CommissionLine {
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(tierMode, "tierMode");
        Objects.requireNonNull(posting, "posting");
    }

    /**
     * The line a month would get if it were approved now. Empty for a delegate with no rule: no
     * rule is not a commission of zero, it is no commission line at all.
     */
    public static java.util.Optional<CommissionLine> preview(DelegatePerformanceRow row) {
        return row.rule().map(rule -> {
            CommissionTiers.Result outcome = row.outcome().orElseThrow();
            DelegateActivity activity = row.activity();
            return new CommissionLine(0, 0, activity.employeeId(), activity.name(), rule.id(),
                    rule.basis(), rule.tierMode(), rule.target(), snapshot(rule.tiers()),
                    activity.sales(), activity.salesReturns(), activity.collected(),
                    activity.baseFor(rule.basis()), outcome.achievementPercent(), outcome.tier(),
                    outcome.ratePercent(), outcome.amount(), Posting.NONE);
        });
    }

    public boolean posted() {
        return posting != Posting.NONE;
    }

    /** {@code 50:1|80:2.5|100:3} - numbers only, so it reads the same in any language and any year. */
    static String snapshot(CommissionTiers tiers) {
        return tiers.tiers().stream()
                .map(tier -> plain(tier.fromPercent()) + ":" + plain(tier.ratePercent()))
                .collect(Collectors.joining("|"));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
