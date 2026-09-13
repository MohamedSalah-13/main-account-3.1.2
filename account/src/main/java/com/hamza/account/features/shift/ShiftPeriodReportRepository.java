package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface ShiftPeriodReportRepository {
    List<ShiftPeriodRow> search(ShiftPeriodQuery query) throws DaoException;
}
