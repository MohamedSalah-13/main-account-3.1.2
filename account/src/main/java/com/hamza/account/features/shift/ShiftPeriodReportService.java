package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

/** Authorized, JavaFX-free boundary for the supervisor's aggregated shift report. */
public final class ShiftPeriodReportService {
    private final ShiftPeriodReportRepository repository;

    public ShiftPeriodReportService(ShiftPeriodReportRepository repository) {
        this.repository = repository;
    }

    public ShiftPeriodReport search(ShiftPeriodQuery query) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        return new ShiftPeriodReport(query, repository.search(query));
    }
}
