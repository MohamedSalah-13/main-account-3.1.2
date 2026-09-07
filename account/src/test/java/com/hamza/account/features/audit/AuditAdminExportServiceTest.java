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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAdminExportServiceTest {

    @TempDir Path temp;
    private final UserSessionContext session = new UserSessionContext();

    @BeforeEach
    void signIn() {
        session.signIn(2, "security", Set.of(AppPermissions.AUDIT_ADMIN_EXPORT));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterEach
    void signOut() {
        session.signOut();
    }

    @Test
    void exportsThenJournalsAndPublishesTheCompletedOperation() throws Exception {
        AuditAdminEventRepository repository = mock(AuditAdminEventRepository.class);
        AuditAdminExporter exporter = mock(AuditAdminExporter.class);
        when(exporter.format()).thenReturn(AuditExportFormat.EXCEL);
        AuditAdminEventQuery query = query();
        AuditAdminEvent row = row();
        when(repository.load(any())).thenReturn(new AuditAdminEventPage(List.of(row),
                new AuditAdminSummary(1, 1, 0, 0, 0)));
        when(repository.exportRows(query, AuditLogExportService.MAX_EXPORT_ROWS)).thenReturn(List.of(row));
        List<AuditOperationEvent> events = new ArrayList<>();
        Path target = temp.resolve("administration.xlsx");
        AuditAdminExportService service = new AuditAdminExportService(repository, List.of(exporter),
                Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC), events::add);

        AuditExportResult result = service.export(query, AuditExportFormat.EXCEL, target);

        assertEquals(1, result.rowCount());
        verify(exporter).export(eq(target), any(AuditAdminExportDocument.class));
        verify(repository).recordExport(AuditExportFormat.EXCEL, query, 1, "administration.xlsx");
        assertEquals(List.of(new AuditOperationEvent.Exported(
                AuditExportFormat.EXCEL, target, 1, true)), events);
    }

    @Test
    void refusesAnUnboundedExportBeforeLoadingAllRows() throws Exception {
        AuditAdminEventRepository repository = mock(AuditAdminEventRepository.class);
        AuditAdminExporter exporter = mock(AuditAdminExporter.class);
        when(exporter.format()).thenReturn(AuditExportFormat.PDF);
        when(repository.load(any())).thenReturn(new AuditAdminEventPage(List.of(),
                new AuditAdminSummary(AuditLogExportService.MAX_EXPORT_ROWS + 1L, 0, 0, 0, 0)));
        AuditAdminExportService service = new AuditAdminExportService(repository, List.of(exporter),
                Clock.systemUTC(), AuditOperationListener.NONE);

        UserValidationException error = assertThrows(UserValidationException.class,
                () -> service.export(query(), AuditExportFormat.PDF, temp.resolve("too-large.pdf")));

        assertEquals("audit.admin.export.limit", error.getMessage());
        verify(repository, never()).exportRows(any(), anyInt());
    }

    private static AuditAdminEventQuery query() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        return new AuditAdminEventQuery("", day, day, null, "", AuditSourceFilter.ALL,
                AuditAdminSort.NEWEST, 0, AuditAdminEventQuery.DEFAULT_PAGE_SIZE);
    }

    private static AuditAdminEvent row() {
        return new AuditAdminEvent(7, "EXPORT", 2, "security", "APP", "machine-1", "Till 1",
                LocalDateTime.of(2026, 9, 7, 12, 30), "requested", 3,
                "{\"format\":\"EXCEL\"}");
    }
}
