package com.hamza.account.features.report.itemsales;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hamza.account.features.report.itemsales.ItemSalesRowTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSalesServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);
    private static final ItemSalesFilter TODAY = ItemSalesFilter.of(DAY, DAY);

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /** User 2, never user 1: user 1 bypasses every permission and would prove nothing. */
    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "tester", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static ItemSalesService service(Recording repository) {
        return new ItemSalesService(repository, AuthorizationGuard::isGranted);
    }

    @Test
    @DisplayName("nothing is read for a reader without the item reports' key - neither the rows nor a row's lines")
    void thePermissionIsAskedBeforeAnythingIsRead() {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        Recording repository = new Recording();

        assertThrows(BusinessRuleException.class, () -> service(repository).report(TODAY));
        assertThrows(BusinessRuleException.class, () -> service(repository).lines(TODAY, 1));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    @DisplayName("the cost is not read for a reader without the profit key, and there is no margin")
    void theCostIsReadOnlyWithTheProfitKey() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        Recording repository = new Recording();

        ItemSalesReport report = service(repository).report(TODAY);

        assertEquals(List.of("rows without cost", "discounts"), repository.calls);
        assertFalse(report.marginVisible());
        assertTrue(report.margin().isEmpty());
    }

    @Test
    @DisplayName("with the profit key the cost is read and the margin shown")
    void withTheProfitKey() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS, AppPermissions.REPORTS_SHOW_PROFIT);
        Recording repository = new Recording();

        ItemSalesReport report = service(repository).report(TODAY);

        assertEquals(List.of("rows with cost", "discounts"), repository.calls);
        assertEquals(Optional.of(new BigDecimal("135")), report.margin());
        assertEquals(Optional.of(new BigDecimal("516")), report.invoicesNet(), "540 less the 24 of the invoices");
    }

    @Test
    @DisplayName("a search leaves the invoices' own discounts unread: they belong to no item")
    void aSearchLeavesTheDiscountsOut() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        Recording repository = new Recording();

        ItemSalesReport report = service(repository).report(new ItemSalesFilter(DAY, DAY, "rice"));

        assertEquals(List.of("rows without cost"), repository.calls);
        assertTrue(report.headerDiscounts().isEmpty());
    }

    @Test
    @DisplayName("a row's lines are read over the report's own period")
    void theLinesAreTheReportsPeriod() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        Recording repository = new Recording();

        service(repository).lines(TODAY, 7);

        assertEquals(List.of("lines 7 " + DAY + " " + DAY), repository.calls);
    }

    private static final class Recording implements ItemSalesRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<ItemSalesRow> rows(ItemSalesFilter filter, boolean withCost) {
            calls.add(withCost ? "rows with cost" : "rows without cost");
            return List.of(row(1, "rice", "10", "1", "600", "60", withCost ? "405" : null));
        }

        @Override
        public BigDecimal headerDiscounts(LocalDate from, LocalDate to) {
            calls.add("discounts");
            return new BigDecimal("24");
        }

        @Override
        public List<ItemSalesLine> lines(int itemId, LocalDate from, LocalDate to) {
            calls.add("lines " + itemId + " " + from + " " + to);
            return List.of();
        }
    }
}
