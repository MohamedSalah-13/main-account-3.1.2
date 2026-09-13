package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import com.hamza.controlsfx.observer.EventBus;

/** Application boundary for reading and changing shift configuration. */
public final class ShiftPolicyService {
    private final ShiftPolicyRepository repository;
    private final EventBus events;
    private final CashierTreasuryAssignmentRepository assignments;

    public ShiftPolicyService(ShiftPolicyRepository repository) {
        this(repository, null, null);
    }

    public ShiftPolicyService(ShiftPolicyRepository repository, EventBus events) {
        this(repository, events, null);
    }

    public ShiftPolicyService(ShiftPolicyRepository repository, EventBus events,
                              CashierTreasuryAssignmentRepository assignments) {
        this.repository = repository;
        this.events = events;
        this.assignments = assignments;
    }

    public ShiftPolicy current() throws DaoException {
        return repository.load();
    }

    /** Must be called from a transaction before a policy-dependent shift mutation. */
    public void lockConfiguration() throws DaoException {
        repository.lockConfiguration();
    }

    public List<TreasuryShiftPolicy> treasuries() throws DaoException {
        return repository.loadTreasuries();
    }

    public void save(ShiftPolicy policy) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        validate(policy, null);
        TransactionTemplate.execute(() -> {
            repository.lockConfiguration();
            validate(policy, null);
            repository.save(policy);
            return null;
        });
        if (events != null) events.publish(new ShiftPolicyChanged(policy));
    }

    public void saveTreasury(TreasuryShiftPolicy policy) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        TransactionTemplate.execute(() -> {
            repository.lockConfiguration();
            repository.saveTreasury(policy);
            return null;
        });
    }

    public void saveConfiguration(ShiftPolicy policy, List<TreasuryShiftPolicy> treasuries) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        List<TreasuryShiftPolicy> safeTreasuries = treasuries == null ? List.of() : List.copyOf(treasuries);
        validate(policy, safeTreasuries);
        TransactionTemplate.execute(() -> {
            repository.lockConfiguration();
            validate(policy, safeTreasuries);
            repository.save(policy);
            for (TreasuryShiftPolicy treasury : safeTreasuries) repository.saveTreasury(treasury);
            return null;
        });
        if (events != null) events.publish(new ShiftPolicyChanged(policy));
    }

    private void validate(ShiftPolicy policy, List<TreasuryShiftPolicy> treasuries) throws DaoException {
        if (policy.mode() == ShiftMode.DISABLED && repository.hasOpenShifts()) {
            throw new BusinessRuleException(message("user.shift.policy.error.open.shifts"));
        }
        if (treasuries != null && policy.mode() != ShiftMode.DISABLED
                && treasuries.stream().noneMatch(item -> item.trackingMode() != ShiftTrackingMode.NONE)) {
            throw new BusinessRuleException(message("user.shift.policy.error.no.treasury"));
        }
        if (policy.enforceTreasuryAssignments() && assignments != null
                && !assignments.hasActiveAssignments()) {
            throw new BusinessRuleException(message("user.shift.assignment.error.enable.empty"));
        }
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
