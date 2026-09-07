package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditAdminEventQueryTest {

    @Test
    void normalizesAndBoundsAdministrationFilters() {
        AuditAdminEventQuery query = new AuditAdminEventQuery("  export  ",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), 0,
                " retention_cleanup ", null, null, -1, 500);

        assertEquals("export", query.search());
        assertEquals("RETENTION_CLEANUP", query.eventType());
        assertEquals(AuditSourceFilter.ALL, query.source());
        assertEquals(AuditAdminSort.NEWEST, query.sort());
        assertEquals(0, query.page());
        assertEquals(200, query.pageSize());
    }

    @Test
    void buildsAnIndexFriendlyParameterizedFilter() {
        AuditAdminEventQuery query = new AuditAdminEventQuery("50%_!",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), 8,
                "export", AuditSourceFilter.APP, AuditAdminSort.EVENT, 0, 50);

        JdbcAuditAdminEventRepository.Filter filter = JdbcAuditAdminEventRepository.filter(query);

        assertTrue(filter.sql().contains("e.occurred_at >= ?"));
        assertTrue(filter.sql().contains("e.occurred_at < ?"));
        assertFalse(filter.sql().contains("DATE("));
        assertTrue(filter.sql().contains("ESCAPE '!'"));
        assertEquals(Timestamp.valueOf("2026-09-01 00:00:00"), filter.parameters().get(0));
        assertEquals(Timestamp.valueOf("2026-09-08 00:00:00"), filter.parameters().get(1));
        assertEquals("%50!%!_!!%", filter.parameters().get(2));
        assertEquals(8, filter.parameters().get(10));
        assertEquals("EXPORT", filter.parameters().get(11));
        assertEquals("APP", filter.parameters().get(12));
    }

    @Test
    void rejectsAnInvertedDateRange() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AuditAdminEventQuery("", LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 1), null, "", null, null, 0, 50));

        assertEquals("audit.log.validation.date.range", error.getMessage());
    }
}
