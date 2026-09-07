package com.hamza.account.features.audit;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public interface AuditAdminEventRepository {
    AuditAdminEventPage load(AuditAdminEventQuery query) throws DaoException;
    AuditAdminOptions options() throws DaoException;
    AuditActivitySnapshot activity(LocalDate today) throws DaoException;
    List<AuditAdminEvent> exportRows(AuditAdminEventQuery query, int limit) throws DaoException;
    void recordExport(AuditExportFormat format, AuditAdminEventQuery query, int rows, String fileName)
            throws DaoException;
}
