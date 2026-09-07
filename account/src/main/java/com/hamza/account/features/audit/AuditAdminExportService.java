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
import java.util.Objects;

/** Permission and size boundary around exports of the immutable administration journal. */
public final class AuditAdminExportService {

    private final AuditAdminEventRepository repository;
    private final Map<AuditExportFormat, AuditAdminExporter> exporters;
    private final Clock clock;
    private final AuditOperationListener listener;

    public AuditAdminExportService(AuditAdminEventRepository repository,
                                   AuditOperationListener listener) {
        this(repository, List.of(new AuditAdminExcelExporter(), new AuditAdminPdfExporter()),
                Clock.systemDefaultZone(), listener);
    }

    AuditAdminExportService(AuditAdminEventRepository repository,
                            List<AuditAdminExporter> exporters,
                            Clock clock,
                            AuditOperationListener listener) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.listener = listener == null ? AuditOperationListener.NONE : listener;
        EnumMap<AuditExportFormat, AuditAdminExporter> byFormat = new EnumMap<>(AuditExportFormat.class);
        exporters.forEach(exporter -> byFormat.put(exporter.format(), exporter));
        this.exporters = Map.copyOf(byFormat);
    }

    public AuditExportResult export(AuditAdminEventQuery query, AuditExportFormat format, Path target)
            throws DaoException, IOException, UserValidationException {
        AuthorizationGuard.require(AppPermissions.AUDIT_ADMIN_EXPORT);
        AuditAdminEventPage firstPage = repository.load(query.withPage(0));
        long total = firstPage.summary().total();
        if (total == 0) throw new UserValidationException("audit.admin.export.empty");
        if (total > AuditLogExportService.MAX_EXPORT_ROWS) {
            throw new UserValidationException("audit.admin.export.limit");
        }
        AuditAdminExporter exporter = exporters.get(format);
        if (exporter == null) throw new IllegalArgumentException("Unsupported audit admin export format: " + format);

        List<AuditAdminEvent> rows = repository.exportRows(query, AuditLogExportService.MAX_EXPORT_ROWS);
        exporter.export(target, new AuditAdminExportDocument(query, rows, LocalDateTime.now(clock)));
        repository.recordExport(format, query, rows.size(), target.getFileName().toString());
        listener.notifySafely(new AuditOperationEvent.Exported(format, target, rows.size(), true));
        return new AuditExportResult(target, rows.size());
    }
}
