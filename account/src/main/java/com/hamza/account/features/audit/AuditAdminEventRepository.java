package com.hamza.account.features.audit;

import com.hamza.controlsfx.database.DaoException;

public interface AuditAdminEventRepository {
    AuditAdminEventPage load(AuditAdminEventQuery query) throws DaoException;
    AuditAdminOptions options() throws DaoException;
}
