package com.hamza.controlsfx.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The statement builders are pure string work, so they can be checked without a
 * database. Only the range delete is covered here - it is the one that used to
 * paste ids into the SQL and had no answer for an empty list.
 */
class SqlStatementsTest {

    @Nested
    @DisplayName("deleteInRangeId")
    class DeleteInRange {

        @Test
        @DisplayName("binds one placeholder per id")
        void placeholdersMatchTheCount() {
            assertEquals("DELETE FROM total_sales WHERE invoice_number IN (?,?,?)",
                    SqlStatements.deleteInRangeId("total_sales", "invoice_number", 3));
        }

        @Test
        @DisplayName("a single id is still a valid IN list")
        void oneId() {
            assertEquals("DELETE FROM audit_log WHERE id IN (?)",
                    SqlStatements.deleteInRangeId("audit_log", "id", 1));
        }

        @Test
        @DisplayName("refuses an empty list rather than building IN ()")
        void emptyListIsRefused() {
            // IN () is a syntax error, not a delete of nothing: the statement reached
            // the server and failed there, so the screen reported a database error for
            // what was really a caller passing no ids.
            assertThrows(IllegalArgumentException.class,
                    () -> SqlStatements.deleteInRangeId("total_sales", "invoice_number", 0));
        }

        @Test
        @DisplayName("refuses a negative count")
        void negativeCountIsRefused() {
            assertThrows(IllegalArgumentException.class,
                    () -> SqlStatements.deleteInRangeId("total_sales", "invoice_number", -1));
        }
    }
}
