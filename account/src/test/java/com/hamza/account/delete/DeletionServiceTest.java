package com.hamza.account.delete;

import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delete rules, checked without a database and without a signed-in user:
 * the scanner and the permission check are both handed to the service, so the
 * order of the checks and the outcome each produces can be pinned down here.
 */
class DeletionServiceTest {

    private static final DeleteRule RULE = DeleteRule.forEntity("الوحدة")
            .requirePermission(UserPermissionType.UNITS_DELETE)
            .protectId(1, "لا يمكن حذف الوحدة الافتراضية")
            .referencedBy("sales", "type", "سطر فاتورة بيع")
            .build();

    /** A scanner that answers with whatever the test wants, and counts its calls. */
    private static class StubScanner extends ReferenceScanner {
        private final List<Reference> answer;
        private final AtomicInteger calls = new AtomicInteger();

        StubScanner(List<Reference> answer) {
            this.answer = answer;
        }

        @Override
        public List<Reference> scan(List<ReferenceCheck> checks, int id) {
            calls.incrementAndGet();
            return answer;
        }
    }

    private static DeletionService service(List<Reference> references, boolean permitted) {
        return new DeletionService(new StubScanner(references), permissionType -> permitted);
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a user without the permission is denied, and nothing is queried")
        void deniedWithoutPermission() throws DaoException {
            var scanner = new StubScanner(List.of());
            var deletionService = new DeletionService(scanner, permissionType -> false);

            var outcome = deletionService.delete(RULE, 5, id -> {
                throw new AssertionError("must not delete");
            });

            assertInstanceOf(DeleteOutcome.Denied.class, outcome);
            // The permission is checked before anything touches the database: a user
            // who may not delete should not cost a query to find that out.
            assertEquals(0, scanner.calls.get());
        }

        @Test
        @DisplayName("a protected id is refused with its own reason")
        void protectedId() throws DaoException {
            var outcome = service(List.of(), true).delete(RULE, 1, id -> {
                throw new AssertionError("must not delete");
            });

            assertInstanceOf(DeleteOutcome.Protected.class, outcome);
            assertEquals("لا يمكن حذف الوحدة الافتراضية", outcome.message());
        }

        @Test
        @DisplayName("references block the delete and are named with their counts")
        void blockedByReferences() throws DaoException {
            var references = List.of(new Reference("سطر فاتورة بيع", 12), new Reference("صنف", 3));

            var outcome = service(references, true).delete(RULE, 5, id -> {
                throw new AssertionError("must not delete");
            });

            assertInstanceOf(DeleteOutcome.Blocked.class, outcome);
            assertEquals("لا يمكن حذف الوحدة: مستخدم في 12 سطر فاتورة بيع، 3 صنف", outcome.message());
        }

        @Test
        @DisplayName("an id that is not a real id is not found rather than deleted")
        void nonPositiveId() throws DaoException {
            var outcome = service(List.of(), true).delete(RULE, 0, id -> {
                throw new AssertionError("must not delete");
            });

            assertInstanceOf(DeleteOutcome.NotFound.class, outcome);
        }
    }

    @Nested
    @DisplayName("deleting")
    class Deleting {

        @Test
        @DisplayName("nothing in the way means the row is deleted")
        void deletes() throws DaoException {
            var outcome = service(List.of(), true).delete(RULE, 5, id -> 1);

            assertInstanceOf(DeleteOutcome.Deleted.class, outcome);
            assertTrue(outcome.succeeded());
            assertEquals(1, outcome.rowsOrThrow());
        }

        @Test
        @DisplayName("zero rows after every check has passed means the id was not there")
        void zeroRowsIsNotFound() throws DaoException {
            // Not a refusal: every reason to refuse was checked and cleared above, so
            // the only thing left that explains no rows is a row that is not there.
            var outcome = service(List.of(), true).delete(RULE, 5, id -> 0);

            assertInstanceOf(DeleteOutcome.NotFound.class, outcome);
            assertFalse(outcome.succeeded());
        }

        @Test
        @DisplayName("the id reaches the deleter")
        void passesTheId() throws DaoException {
            var seen = new AtomicInteger();
            service(List.of(), true).delete(RULE, 42, id -> {
                seen.set(id);
                return 1;
            });

            assertEquals(42, seen.get());
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        @DisplayName("answers null when the rule would allow the delete")
        void nullWhenAllowed() throws DaoException {
            assertNull(service(List.of(), true).check(RULE, 5));
        }

        @Test
        @DisplayName("answers the refusal without deleting anything")
        void refusalWithoutDeleting() throws DaoException {
            var outcome = service(List.of(new Reference("صنف", 2)), true).check(RULE, 5);

            assertInstanceOf(DeleteOutcome.Blocked.class, outcome);
        }
    }

    @Nested
    @DisplayName("rowsOrThrow")
    class RowsOrThrow {

        @Test
        @DisplayName("carries the refusal message to the screens that expect an exception")
        void throwsWithMessage() {
            var blocked = new DeleteOutcome.Blocked("الوحدة", List.of(new Reference("صنف", 3)));

            var thrown = assertThrows(DaoException.class, blocked::rowsOrThrow);
            assertEquals(blocked.message(), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("ReferenceCheck")
    class Identifiers {

        @Test
        @DisplayName("refuses anything that is not a plain identifier")
        void rejectsNonIdentifiers() {
            // The table and column are concatenated into the scan query, so this is
            // the point that keeps them to names the schema could actually have.
            assertThrows(IllegalArgumentException.class,
                    () -> new ReferenceCheck("sales; DROP TABLE items", "type", "سطر"));
            assertThrows(IllegalArgumentException.class,
                    () -> new ReferenceCheck("sales", "type = 1 OR 1", "سطر"));
        }

        @Test
        @DisplayName("accepts the names the registry uses")
        void acceptsRealNames() {
            var check = new ReferenceCheck("total_sales_re", "sup_id", "مرتجع بيع");
            assertEquals("total_sales_re", check.table());
        }
    }
}
