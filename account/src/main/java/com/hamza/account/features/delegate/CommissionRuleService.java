package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The rules about commission rules.
 *
 * <p>Two permissions, and they are the salary's pair for the salary's reasons: a rate is a
 * figure about a person, so reading one needs {@code commission.show} - it is not fetched for a
 * reader who may not see it, never fetched and hidden - and deciding one needs
 * {@code commission.rule.update}.
 */
public final class CommissionRuleService {

    private final CommissionRuleRepository repository;

    public CommissionRuleService() {
        this(new JdbcCommissionRuleRepository());
    }

    public CommissionRuleService(CommissionRuleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** Every rule this delegate has had, newest first. */
    public List<CommissionRule> history(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        return repository.history(employeeId);
    }

    /**
     * The rule a month is judged by: the one in force on its <b>first</b> day. A rule that
     * starts on the 10th governs from the next month, so no month is ever split between two
     * targets - and a delegate cannot be moved to a harder target after the month's sales are in.
     */
    public Optional<CommissionRule> ruleForMonth(int employeeId, int year, int month) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        return repository.inForceOn(employeeId, LocalDate.of(year, month, 1));
    }

    /**
     * Records the rule that starts on {@code effectiveFrom}, replacing that day's rule if there
     * is one. The arguments are what a form holds; the refusals are message keys.
     *
     * @param tiers lowest threshold first; a tier the form left empty is simply not in the list
     */
    public int save(int employeeId, LocalDate effectiveFrom, CommissionBasis basis, TierMode tierMode,
                    BigDecimal target, List<CommissionTiers.Tier> tiers, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_RULE_UPDATE);
        if (effectiveFrom == null) {
            throw new UserValidationException("commission.error.rule.date");
        }
        CommissionRule rule;
        try {
            rule = new CommissionRule(0, employeeId, effectiveFrom,
                    basis == null ? CommissionBasis.SALES : basis,
                    tierMode == null ? TierMode.WHOLE : tierMode,
                    target == null ? BigDecimal.ZERO : target,
                    new CommissionTiers(tiers), notes);
        } catch (IllegalArgumentException refused) {
            // The records refuse with the key of the sentence to show.
            throw new UserValidationException(refused.getMessage());
        }
        return TransactionTemplate.execute(() -> {
            if (!repository.isDelegate(employeeId)) {
                throw new UserValidationException("commission.error.not.delegate");
            }
            return repository.save(rule, currentUserId());
        });
    }

    public int remove(int employeeId, int ruleId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_RULE_UPDATE);
        return TransactionTemplate.execute(() -> repository.delete(employeeId, ruleId));
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
