package com.hamza.account.features.audit;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.time.LocalDateTime;

public interface AuditLogRepository {
    AuditLogPage load(AuditLogQuery query) throws DaoException;
    AuditLogOptions options() throws DaoException;
    List<AuditLogEntry> exportRows(AuditLogQuery query, int limit) throws DaoException;
    void recordExport(AuditExportFormat format, AuditLogQuery query, int rows, String fileName) throws DaoException;
    int delete(List<Long> ids, String reason) throws DaoException;
    AuditRetentionPolicy retentionPolicy() throws DaoException;
    void saveRetentionPolicy(AuditRetentionPolicy policy, String reason) throws DaoException;
    long countBefore(LocalDateTime cutoff) throws DaoException;
    int purgeBefore(LocalDateTime cutoff, String reason, boolean automatic) throws DaoException;
}
