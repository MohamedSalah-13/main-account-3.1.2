package com.hamza.account.features.documentdelete;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentDeletionServiceTest {

    private final List<String> steps = new ArrayList<>();
    private final RecordingRepository repository = new RecordingRepository();
    private final DocumentDeletionService service = new DocumentDeletionService(
            repository, new RecordingTransaction(), () -> steps.add("backup"));

    @BeforeEach
    void signIn() {
        // Not user 1: that id bypasses every permission, and would make the refusal case pass empty.
        signInWith(List.of(AppPermissions.SALES_DELETE, AppPermissions.PURCHASE_RE_DELETE));
    }

    @Test
    void checksBeforeTheBackupAndAgainInsideTheTransactionBeforeDeleting() throws Exception {
        DocumentDeletionResult result = service.delete(DocumentType.SALES, List.of(5, 6), "reason");

        assertEquals(List.of(
                "period SALES [5, 6]", "returns SALES [5, 6]",
                "backup",
                "begin",
                "period SALES [5, 6]", "returns SALES [5, 6]",
                "delete SALES [5, 6] reason",
                "commit"), steps);
        assertEquals(new DocumentDeletionResult(2, 2), result);
    }

    @Test
    void aDocumentWithoutThePermissionIsRefusedBeforeAnythingIsReadOrCopied() {
        assertThrows(BusinessRuleException.class,
                () -> service.delete(DocumentType.PURCHASE, List.of(5), "reason"));

        assertEquals(List.of(), steps);
    }

    @Test
    void eachFamilyAsksForItsOwnPermission() throws Exception {
        signInWith(List.of(AppPermissions.PURCHASE_RE_DELETE));

        service.delete(DocumentType.PURCHASE_RETURN, List.of(3), null);

        assertThrows(BusinessRuleException.class,
                () -> service.delete(DocumentType.SALES, List.of(3), null));
    }

    @Test
    void aBatchTheRulesRefuseCostsNoBackup() {
        repository.refuseReturns = true;

        assertThrows(BusinessRuleException.class,
                () -> service.delete(DocumentType.SALES, List.of(5), "reason"));

        assertEquals(List.of("period SALES [5]", "returns SALES [5]"), steps);
    }

    @Test
    void aFailedBackupDeletesNothing() {
        DocumentDeletionService failing = new DocumentDeletionService(repository, new RecordingTransaction(),
                () -> { throw new IOException("disk full"); });

        DaoException failure = assertThrows(DaoException.class,
                () -> failing.delete(DocumentType.SALES, List.of(5), "reason"));

        assertTrue(failure.getCause() instanceof IOException);
        assertTrue(steps.stream().noneMatch(step -> step.startsWith("delete")), steps.toString());
    }

    @Test
    void aReturnSavedWhileTheBackupRanRefusesTheDeleteInsideTheTransaction() {
        DocumentDeletionService racing = new DocumentDeletionService(repository, new RecordingTransaction(),
                () -> repository.refuseReturns = true);

        assertThrows(BusinessRuleException.class,
                () -> racing.delete(DocumentType.SALES, List.of(5), "reason"));

        assertTrue(steps.stream().noneMatch(step -> step.startsWith("delete")), steps.toString());
        assertEquals("rollback", steps.getLast());
    }

    @Test
    void answersHowManyWentWhenAnotherMachineDeletedSomeFirst() throws Exception {
        repository.rowsDeleted = 1;

        DocumentDeletionResult result = service.delete(DocumentType.SALES, List.of(5, 6, 7), "reason");

        assertEquals(new DocumentDeletionResult(3, 1), result);
    }

    @Test
    void theSameDocumentTwiceIsOneDocument() throws Exception {
        DocumentDeletionResult result = service.delete(DocumentType.SALES, List.of(5, 5, 6), "reason");

        assertTrue(steps.contains("delete SALES [5, 6] reason"), steps.toString());
        assertEquals(2, result.requested());
    }

    @Test
    void nothingToDeleteTakesNoBackupAndTouchesNothing() throws Exception {
        assertEquals(new DocumentDeletionResult(0, 0), service.delete(DocumentType.SALES, List.of(), "reason"));
        assertEquals(new DocumentDeletionResult(0, 0), service.delete(DocumentType.SALES, null, "reason"));

        assertEquals(List.of(), steps);
    }

    private static void signInWith(List<com.hamza.account.authorization.PermissionKey> permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", permissions);
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private final class RecordingRepository implements DocumentDeletionRepository {
        private boolean refuseReturns;
        private Integer rowsDeleted;

        @Override
        public void requirePeriodOpen(DocumentType type, List<Integer> ids) {
            steps.add("period " + type + " " + ids);
        }

        @Override
        public void requireNoReturns(DocumentType type, List<Integer> ids) throws DaoException {
            steps.add("returns " + type + " " + ids);
            if (refuseReturns) {
                throw new BusinessRuleException("return.error.source.has.returns");
            }
        }

        /** What the next {@code stockShortfalls} should find; empty unless a case sets it. */
        private List<DocumentDeleteStockCheck.StockLine> stockLines = List.of();

        @Override
        public List<DocumentDeleteStockCheck.StockLine> stockLinesOf(
                DocumentType type, List<Integer> ids) {
            steps.add("stock " + type + " " + ids);
            return stockLines;
        }

        @Override
        public int deleteDocuments(DocumentType type, List<Integer> ids, String correctionReason) {
            steps.add("delete " + type + " " + ids + " " + correctionReason);
            return rowsDeleted == null ? ids.size() : rowsDeleted;
        }
    }

    private final class RecordingTransaction implements DocumentDeletionTransaction {
        @Override
        public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
            steps.add("begin");
            try {
                T result = work.get();
                steps.add("commit");
                return result;
            } catch (DaoException e) {
                steps.add("rollback");
                throw e;
            } catch (Exception e) {
                steps.add("rollback");
                throw new DaoException(e);
            }
        }
    }
}
