package com.hamza.account.features.audit;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Authorization and size boundary around the format-specific exporters. */
public final class AuditLogExportService {

    public static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository repository;
    private final Map<AuditExportFormat, AuditLogExporter> exporters;
    private final Clock clock;

    public AuditLogExportService(AuditLogRepository repository) {
        this(repository, List.of(new AuditExcelExporter(), new AuditPdfExporter()), Clock.systemDefaultZone());
    }

    AuditLogExportService(AuditLogRepository repository, List<AuditLogExporter> exporters, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        EnumMap<AuditExportFormat, AuditLogExporter> byFormat = new EnumMap<>(AuditExportFormat.class);
        exporters.forEach(exporter -> byFormat.put(exporter.format(), exporter));
        this.exporters = Map.copyOf(byFormat);
    }

    public AuditExportResult export(AuditLogQuery query, AuditExportFormat format, Path target)
            throws DaoException, IOException, UserValidationException {
        AuthorizationGuard.require(AppPermissions.AUDIT_EXPORT);
        AuditLogPage firstPage = repository.load(query.withPage(0));
        long total = firstPage.summary().total();
        if (total == 0) throw new UserValidationException("audit.log.export.empty");
        if (total > MAX_EXPORT_ROWS) throw new UserValidationException("audit.log.export.limit");
        AuditLogExporter exporter = exporters.get(format);
        if (exporter == null) throw new IllegalArgumentException("Unsupported audit export format: " + format);

        List<AuditLogEntry> rows = repository.exportRows(query, MAX_EXPORT_ROWS);
        exporter.export(target, new AuditExportDocument(query, rows, LocalDateTime.now(clock)));
        repository.recordExport(format, query, rows.size(), target.getFileName().toString());
        return new AuditExportResult(target, rows.size());
    }
}
