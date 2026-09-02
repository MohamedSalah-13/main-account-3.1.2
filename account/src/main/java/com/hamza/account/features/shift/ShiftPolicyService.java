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

    public ShiftPolicyService(ShiftPolicyRepository repository) {
        this(repository, null);
    }

    public ShiftPolicyService(ShiftPolicyRepository repository, EventBus events) {
        this.repository = repository;
        this.events = events;
    }

    public ShiftPolicy current() throws DaoException {
        return repository.load();
    }

    public List<TreasuryShiftPolicy> treasuries() throws DaoException {
        return repository.loadTreasuries();
    }

    public void save(ShiftPolicy policy) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        validate(policy, null);
        repository.save(policy);
        if (events != null) events.publish(new ShiftPolicyChanged(policy));
    }

    public void saveTreasury(TreasuryShiftPolicy policy) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        repository.saveTreasury(policy);
    }

    public void saveConfiguration(ShiftPolicy policy, List<TreasuryShiftPolicy> treasuries) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        List<TreasuryShiftPolicy> safeTreasuries = treasuries == null ? List.of() : List.copyOf(treasuries);
        validate(policy, safeTreasuries);
        TransactionTemplate.execute(() -> {
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
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
