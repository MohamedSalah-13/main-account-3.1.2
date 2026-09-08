package com.hamza.account.features.treasury.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryStatementServiceTest {

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    @Test
    void permissionIsCheckedBeforeRepositoryAccess() {
        signIn();
        TreasuryStatementRepository repository = new FailingRepository();
        TreasuryStatementService service = new TreasuryStatementService(repository);

        assertThrows(Exception.class, () -> service.search(filter(0, 2)));
        assertThrows(Exception.class, service::options);
        assertThrows(Exception.class, () -> service.forPrint(filter(0, 2)));
    }

    @Test
    void anExtraRowSignalsNextPageAndIsNotDisplayed() throws Exception {
        signIn(AppPermissions.TREASURY_SHOW);
        StubRepository repository = new StubRepository(List.of(row(1), row(2), row(3)));
        TreasuryStatementPage page = new TreasuryStatementService(repository).search(filter(0, 2));

        assertEquals(List.of(row(1), row(2)), page.rows());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        assertEquals(repository.summary, page.summary());
    }

    @Test
    void aLaterPageAdvertisesItsPreviousPage() throws Exception {
        signIn(AppPermissions.TREASURY_SHOW);
        TreasuryStatementPage page = new TreasuryStatementService(new StubRepository(List.of(row(1))))
                .search(filter(3, 2));

        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals(3, page.page());
    }

    @Test
    void printingIsBoundedAndReportsTruncation() throws Exception {
        signIn(AppPermissions.TREASURY_SHOW);
        List<TreasuryStatementRow> rows = new ArrayList<>();
        for (int i = 0; i <= TreasuryStatementService.PRINT_LIMIT; i++) rows.add(row(i));

        TreasuryStatementPrintData data = new TreasuryStatementService(new StubRepository(rows))
                .forPrint(filter(4, 20));

        assertTrue(data.truncated());
        assertEquals(TreasuryStatementService.PRINT_LIMIT, data.rows().size());
    }

    private void signIn(com.hamza.account.authorization.PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "tester", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private TreasuryStatementFilter filter(int page, int size) {
        return new TreasuryStatementFilter(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                null, null, null, page, size);
    }

    private TreasuryStatementRow row(int id) {
        return new TreasuryStatementRow(id, LocalDate.of(2026, 9, 8), LocalDateTime.of(2026, 9, 8, 12, 0),
                TreasuryMovementKind.SALES, "المبيعات", 1, "Main",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 2, "tester");
    }

    private final class StubRepository implements TreasuryStatementRepository {
        private final List<TreasuryStatementRow> rows;
        private final TreasuryStatementSummary summary = new TreasuryStatementSummary(
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("11"));

        private StubRepository(List<TreasuryStatementRow> rows) { this.rows = rows; }
        @Override public List<TreasuryStatementRow> search(TreasuryStatementFilter filter) { return rows; }
        @Override public TreasuryStatementSummary summarize(TreasuryStatementFilter filter) { return summary; }
        @Override public TreasuryStatementOptions options() { return new TreasuryStatementOptions(List.of(), List.of()); }
    }

    private static final class FailingRepository implements TreasuryStatementRepository {
        @Override public List<TreasuryStatementRow> search(TreasuryStatementFilter filter) { throw new AssertionError(); }
        @Override public TreasuryStatementSummary summarize(TreasuryStatementFilter filter) { throw new AssertionError(); }
        @Override public TreasuryStatementOptions options() { throw new AssertionError(); }
    }
}
