package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

/** Authorized supervisor boundary for explicit shift accounting reconciliation. */
public final class ShiftReconciliationService {
    private final ShiftReconciliationDao repository;

    public ShiftReconciliationService() {
        this(new ShiftReconciliationDao());
    }

    ShiftReconciliationService(ShiftReconciliationDao repository) {
        this.repository = repository;
    }

    public ShiftReconciliationResult reconcile(int shiftId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_LEDGER_VIEW);
        if (shiftId <= 0) throw new DaoException("A valid shift is required for reconciliation");
        return repository.reconcile(shiftId);
    }
}
