package com.hamza.account.features.delegate;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the three pairs of boxes on the rules screen into tiers. It is here, and not in the
 * controller, so that what an empty box means can be tested without a toolkit.
 *
 * <p><b>An empty box is not a zero.</b> A tier whose two boxes are both empty does not exist; a
 * tier "from 0% at 0%" is a real, if pointless, tier. The screen therefore gives the second and
 * third tiers optional formatters that leave an untouched box empty - a formatter that seeds
 * {@code 0.0} would turn every rule into three tiers starting at zero, which is refused for
 * having no order, on a form nobody typed into.
 */
public final class CommissionRuleForm {

    private CommissionRuleForm() {
    }

    /**
     * @param values from, rate, from, rate, from, rate - lowest tier first; null for an empty box
     * @throws UserValidationException with a message key: a half-filled tier, or a third tier
     *                                 with no second. Order and ranges are the tiers' own to refuse
     */
    public static List<CommissionTiers.Tier> tiers(BigDecimal... values) throws UserValidationException {
        if (values == null || values.length != CommissionTiers.MAX_TIERS * 2) {
            throw new IllegalArgumentException("three pairs of values are expected");
        }
        List<CommissionTiers.Tier> tiers = new ArrayList<>();
        boolean gap = false;
        for (int i = 0; i < values.length; i += 2) {
            BigDecimal from = values[i];
            BigDecimal rate = values[i + 1];
            if (from == null && rate == null) {
                gap = true;
                continue;
            }
            if (from == null || rate == null) {
                throw new UserValidationException("commission.error.tier.incomplete");
            }
            if (gap) {
                throw new UserValidationException("commission.error.tier.gap");
            }
            try {
                tiers.add(new CommissionTiers.Tier(from, rate));
            } catch (IllegalArgumentException refused) {
                throw new UserValidationException(refused.getMessage());
            }
        }
        if (tiers.isEmpty()) {
            throw new UserValidationException("commission.error.tier.count");
        }
        return tiers;
    }
}
