package com.hamza.account.features.party.rfm;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerRfmServiceTest {

    private static final CustomerRfmFilter FIRST_PAGE = new CustomerRfmFilter(LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31), "", 1, CustomerRfmOrder.SCORE, 0, 2);

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

    @Test
    void nothingIsReadForSomeoneWhoMayNotSeeTheBalances() {
        signIn();
        Recording repository = new Recording(3);

        assertThrows(BusinessRuleException.class, () -> new CustomerRfmService(repository).search(FIRST_PAGE));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    void theExtraRowSaysAnotherPageFollowsAndIsNotShown() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);

        CustomerRfmPage page = new CustomerRfmService(new Recording(3)).search(FIRST_PAGE);

        assertEquals(2, page.rows().size());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        assertFalse(new CustomerRfmService(new Recording(2)).search(FIRST_PAGE).hasNext());
    }

    @Test
    void aFileAsksTheReportKeyOnTopAndReadsTheWholeSet() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        Recording repository = new Recording(3);
        CustomerRfmService service = new CustomerRfmService(repository);

        assertThrows(BusinessRuleException.class, () -> service.forExport(FIRST_PAGE.withPage(4)));
        assertTrue(repository.calls.isEmpty());

        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.REPORTS_SHOW_CUSTOMERS);
        CustomerRfmPage whole = service.forExport(FIRST_PAGE.withPage(4));
        assertEquals(3, whole.rows().size());
        assertEquals(List.of("search page 0 of " + CustomerRfmService.PRINT_LIMIT,
                "summary page 0 of " + CustomerRfmService.PRINT_LIMIT), repository.calls);
    }

    private static final class Recording implements CustomerRfmRepository {
        private final int rows;
        private final List<String> calls = new ArrayList<>();

        Recording(int rows) {
            this.rows = rows;
        }

        @Override
        public List<CustomerRfmRow> search(CustomerRfmFilter filter) {
            calls.add("search page " + filter.page() + " of " + filter.pageSize());
            return IntStream.rangeClosed(1, rows).mapToObj(id -> new CustomerRfmRow(id, "c" + id,
                    LocalDate.of(2026, 3, id), 31 - id, id, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("9"),
                    1, 1, 1)).toList();
        }

        @Override
        public java.util.Optional<String> customerName(int id) {
            calls.add("name " + id);
            return java.util.Optional.of("cash");
        }

        @Override
        public CustomerRfmSummary summarize(CustomerRfmFilter filter) {
            calls.add("summary page " + filter.page() + " of " + filter.pageSize());
            return new CustomerRfmSummary(rows, rows, rows, BigDecimal.ONE);
        }
    }
}
