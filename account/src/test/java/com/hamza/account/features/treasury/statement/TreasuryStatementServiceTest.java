package com.hamza.account.features.treasury.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
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
    void everyTreasuryAtOnceIsInTheBaseAndNoCurrencyIsAskedFor() throws Exception {
        signIn(AppPermissions.TREASURY_SHOW);
        StubRepository repository = new StubRepository(List.of(row(1)));
        repository.currency = DOLLAR;

        TreasuryStatementPage page = new TreasuryStatementService(repository).search(filter(0, 2));

        assertFalse(page.currency().isForeign(), "dollars and pounds summed together are no figure at all");
        assertEquals(repository.summary, page.summary());
        assertEquals(List.of(), repository.currencyAskedFor);
    }

    @Test
    void oneForeignTreasuryIsShownInItsOwnCurrencyOnScreenAndOnPaper() throws Exception {
        signIn(AppPermissions.TREASURY_SHOW);
        StubRepository repository = new StubRepository(List.of(row(1)));
        repository.currency = DOLLAR;
        TreasuryStatementService service = new TreasuryStatementService(repository);

        TreasuryStatementPage page = service.search(oneTreasury(7));
        TreasuryStatementPrintData paper = service.forPrint(oneTreasury(7));

        assertEquals("USD", page.currency().code());
        assertEquals(repository.ownSummary, page.summary());
        assertEquals(repository.summary, page.totals().base(), "the book value stays beside it");
        assertEquals("USD", paper.currency().code());
        assertEquals(repository.ownSummary, paper.summary());
        assertEquals(List.of(7, 7), repository.currencyAskedFor);
    }

    @Test
    void oneTreasuryInTheBaseIsShownInTheBase() throws Exception {
        signIn(AppPermissions.TREASURY_SHOW);
        StubRepository repository = new StubRepository(List.of(row(1)));

        TreasuryStatementPage page = new TreasuryStatementService(repository).search(oneTreasury(1));

        assertFalse(page.currency().isForeign());
        assertEquals("", page.currency().code());
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

    private TreasuryStatementFilter oneTreasury(int treasuryId) {
        return new TreasuryStatementFilter(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                treasuryId, null, null, 0, 20);
    }

    private static final Currency DOLLAR = new Currency(2, "USD", "دولار أمريكي", "$", "", 2, false, true, 2);

    private TreasuryStatementRow row(int id) {
        return new TreasuryStatementRow(id, LocalDate.of(2026, 9, 8), LocalDateTime.of(2026, 9, 8, 12, 0),
                TreasuryMovementKind.SALES, "المبيعات", 1, "Main",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 2, "tester");
    }

    private final class StubRepository implements TreasuryStatementRepository {
        private final List<TreasuryStatementRow> rows;
        private final TreasuryStatementSummary summary = new TreasuryStatementSummary(
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("11"));
        private final TreasuryStatementSummary ownSummary = new TreasuryStatementSummary(
                new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("50"), new BigDecimal("200"));
        private Currency currency;
        private final List<Integer> currencyAskedFor = new ArrayList<>();

        private StubRepository(List<TreasuryStatementRow> rows) { this.rows = rows; }
        @Override public List<TreasuryStatementRow> search(TreasuryStatementFilter filter) { return rows; }
        @Override public TreasuryStatementTotals summarize(TreasuryStatementFilter filter) {
            return new TreasuryStatementTotals(summary, ownSummary);
        }
        @Override public TreasuryStatementOptions options() { return new TreasuryStatementOptions(List.of(), List.of()); }
        @Override public Currency currencyOf(int treasuryId) {
            currencyAskedFor.add(treasuryId);
            return currency;
        }
    }

    private static final class FailingRepository implements TreasuryStatementRepository {
        @Override public List<TreasuryStatementRow> search(TreasuryStatementFilter filter) { throw new AssertionError(); }
        @Override public TreasuryStatementTotals summarize(TreasuryStatementFilter filter) { throw new AssertionError(); }
        @Override public TreasuryStatementOptions options() { throw new AssertionError(); }
        @Override public Currency currencyOf(int treasuryId) { throw new AssertionError(); }
    }
}
