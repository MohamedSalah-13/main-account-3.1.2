package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** Authorized read boundary for the supervisor's shift journal. */
public final class ShiftCashAuditService {
    private final ShiftCashLedgerQueryDao repository;

    public ShiftCashAuditService() {
        this(new ShiftCashLedgerQueryDao());
    }

    ShiftCashAuditService(ShiftCashLedgerQueryDao repository) {
        this.repository = repository;
    }

    public List<ShiftCashLedgerEntry> search(ShiftCashLedgerFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_LEDGER_VIEW);
        if (filter == null) throw new DaoException("Shift cash journal filter is required");
        return repository.search(filter);
    }
}
