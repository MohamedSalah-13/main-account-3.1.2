package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;

/** Business boundary for assigning cashiers to the tills they may operate. */
public final class CashierTreasuryAssignmentService {
    private static final int HISTORY_LIMIT = 250;
    private final CashierTreasuryAssignmentRepository repository;
    private final ShiftPolicyService policies;
    private final UserSessionContext session;

    public CashierTreasuryAssignmentService(CashierTreasuryAssignmentRepository repository,
                                            ShiftPolicyService policies,
                                            UserSessionContext session) {
        this.repository = repository;
        this.policies = policies;
        this.session = session;
    }

    public List<CashierTreasuryAssignment> listAll() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        return repository.loadAll();
    }

    public List<CashierTreasuryAssignmentEvent> listHistory() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        return repository.loadHistory(HISTORY_LIMIT);
    }

    public List<CashierTreasuryChoice> availableTreasuries(int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_VIEW);
        requireCurrentUser(userId);
        ShiftPolicy policy = policies.current();
        if (policy.mode() == ShiftMode.DISABLED) return List.of();
        return repository.availableTreasuries(userId, policy.enforceTreasuryAssignments());
    }

    public void assign(int userId, int treasuryId, boolean defaultTreasury) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        int actor = signedInActor();
        if (userId <= 0 || treasuryId <= 0) {
            throw new UserValidationException(message("user.shift.assignment.error.invalid"));
        }
        TransactionTemplate.execute(() -> {
            repository.lockUser(userId);
            if (!repository.isAssignable(userId, treasuryId)) {
                throw new UserValidationException(message("user.shift.assignment.error.invalid"));
            }
            if (defaultTreasury) repository.clearDefault(userId, actor);
            repository.upsert(userId, treasuryId, defaultTreasury, actor);
            return null;
        });
    }

    public boolean hasActiveAssignments() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        return repository.hasActiveAssignments();
    }

    public void deactivate(int assignmentId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        int actor = signedInActor();
        TransactionTemplate.execute(() -> {
            CashierTreasuryAssignment assignment = repository.findById(assignmentId, true);
            if (assignment == null || !assignment.active()) {
                throw new BusinessRuleException(message("user.shift.assignment.error.not.found"));
            }
            if (repository.hasOpenShift(assignment.userId(), assignment.treasuryId())) {
                throw new BusinessRuleException(message("user.shift.assignment.error.open.shift"));
            }
            if (repository.deactivate(assignmentId, actor) != 1) {
                throw new BusinessRuleException(message("user.shift.assignment.error.not.found"));
            }
            return null;
        });
    }

    public boolean canOpenShift(int userId, int treasuryId) throws DaoException {
        ShiftPolicy policy = policies.current();
        return !policy.enforceTreasuryAssignments() || repository.canOpenShift(userId, treasuryId);
    }

    private int signedInActor() throws DaoException {
        if (session == null || !session.isSignedIn()) {
            throw new BusinessRuleException(message("user.shift.assignment.error.login.required"));
        }
        return session.currentUserId();
    }

    private void requireCurrentUser(int userId) throws DaoException {
        if (signedInActor() != userId) {
            throw new BusinessRuleException(message("user.shift.assignment.error.other.user"));
        }
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
