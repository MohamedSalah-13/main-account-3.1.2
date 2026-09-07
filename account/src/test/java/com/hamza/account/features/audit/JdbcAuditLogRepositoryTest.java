package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAuditLogRepositoryTest {

    @Test
    void dateRangeUsesIndexFriendlyHalfOpenTimestamps() {
        AuditLogQuery query = new AuditLogQuery("", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 7), null, AuditActionFilter.ALL, "",
                AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, 50);

        JdbcAuditLogRepository.Filter filter = JdbcAuditLogRepository.filter(query);

        assertTrue(filter.sql().contains("a.action_time >= ?"));
        assertTrue(filter.sql().contains("a.action_time < ?"));
        assertFalse(filter.sql().contains("DATE("));
        assertEquals(Timestamp.valueOf("2026-09-01 00:00:00"), filter.parameters().get(0));
        assertEquals(Timestamp.valueOf("2026-09-08 00:00:00"), filter.parameters().get(1));
    }

    @Test
    void everyOptionalFilterIsBoundAndLikeWildcardsAreEscaped() {
        AuditLogQuery query = new AuditLogQuery("50%_!", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 7), 8, AuditActionFilter.UPDATE, "items",
                AuditSourceFilter.APP, AuditLogSort.TABLE, 2, 50);

        JdbcAuditLogRepository.Filter filter = JdbcAuditLogRepository.filter(query);

        assertTrue(filter.sql().contains("ESCAPE '!'"));
        assertTrue(filter.sql().contains("a.user_id = ?"));
        assertTrue(filter.sql().contains("a.action_type = ?"));
        assertTrue(filter.sql().contains("a.table_name = ?"));
        assertTrue(filter.sql().contains("a.source = ?"));
        assertEquals("%50!%!_!!%", filter.parameters().get(2));
        assertEquals(8, filter.parameters().get(9));
        assertEquals("UPDATE", filter.parameters().get(10));
        assertEquals("ITEMS", filter.parameters().get(11));
        assertEquals("APP", filter.parameters().get(12));
    }
}
