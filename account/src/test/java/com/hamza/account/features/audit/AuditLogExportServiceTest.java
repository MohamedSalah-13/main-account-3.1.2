package com.hamza.account.features.audit;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogExportServiceTest {

    @TempDir Path temp;
    private final UserSessionContext session = new UserSessionContext();

    @BeforeEach
    void signIn() {
        session.signIn(2, "exporter", Set.of(AppPermissions.AUDIT_EXPORT));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterEach
    void signOut() {
        session.signOut();
    }

    @Test
    void exportsThroughTheSelectedStrategyThenJournalsTheCompletedFile() throws Exception {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogExporter exporter = mock(AuditLogExporter.class);
        when(exporter.format()).thenReturn(AuditExportFormat.EXCEL);
        AuditLogQuery query = query();
        AuditLogEntry row = new AuditLogEntry(1, "ITEMS", "2", AuditAction.INSERT, 2,
                "exporter", java.time.LocalDateTime.now(), "", "{}", "APP", "", "", "");
        when(repository.load(any())).thenReturn(new AuditLogPage(List.of(row),
                new AuditLogSummary(1, 1, 0, 0)));
        when(repository.exportRows(query, AuditLogExportService.MAX_EXPORT_ROWS)).thenReturn(List.of(row));
        Path target = temp.resolve("audit.xlsx");
        AuditLogExportService service = new AuditLogExportService(repository, List.of(exporter),
                Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC));

        AuditExportResult result = service.export(query, AuditExportFormat.EXCEL, target);

        assertEquals(1, result.rowCount());
        verify(exporter).export(eq(target), any(AuditExportDocument.class));
        verify(repository).recordExport(AuditExportFormat.EXCEL, query, 1, "audit.xlsx");
    }

    @Test
    void refusesAnUnboundedExportBeforeLoadingAllRows() throws Exception {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogExporter exporter = mock(AuditLogExporter.class);
        when(exporter.format()).thenReturn(AuditExportFormat.EXCEL);
        when(repository.load(any())).thenReturn(new AuditLogPage(List.of(),
                new AuditLogSummary(AuditLogExportService.MAX_EXPORT_ROWS + 1L, 0, 0, 0)));
        AuditLogQuery query = query();
        AuditLogExportService service = new AuditLogExportService(repository, List.of(exporter),
                Clock.systemUTC());

        UserValidationException error = assertThrows(UserValidationException.class,
                () -> service.export(query, AuditExportFormat.EXCEL, temp.resolve("too-large.xlsx")));

        assertEquals("audit.log.export.limit", error.getMessage());
        verify(repository, never()).exportRows(any(), anyInt());
    }

    private static AuditLogQuery query() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        return new AuditLogQuery("", day, day, null, AuditActionFilter.ALL, "",
                AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, 50);
    }
}
