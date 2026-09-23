package com.hamza.account.features.report.summary;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.report.monthly.DayFigures;
import com.hamza.account.features.report.monthly.MonthlySide;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.account.features.report.summary.SummaryTest.day;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private static final ProfitLossPeriod MONTH = SummaryPeriod.MONTH.range(TODAY);

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

    private static SummaryService service(Recording repository) {
        return new SummaryService(repository, AuthorizationGuard::isGranted);
    }

    @Test
    @DisplayName("nothing is read for a reader without the summary's key, whatever else they hold")
    void theSummaryKeyFirst() {
        signIn(AppPermissions.REPORTS_SHOW_SALES, AppPermissions.TREASURY_SHOW);
        Recording repository = new Recording();

        assertThrows(BusinessRuleException.class, () -> service(repository).load(SummaryPeriod.MONTH, MONTH, TODAY));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    @DisplayName("a reader with the summary alone is read nothing: every card is behind its screen's key")
    void theSummaryKeyAloneReadsNothing() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY);
        Recording repository = new Recording();

        Summary summary = service(repository).load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertTrue(repository.calls.isEmpty(), repository.calls.toString());
        assertTrue(summary.cards().isEmpty());
        assertNull(summary.sales());
        assertNull(summary.cash());
        assertNull(summary.receivables());
    }

    @Test
    @DisplayName("the sales are read once, over the month before, this month and the trend's fourteen days")
    void theSalesAreReadOnce() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.REPORTS_SHOW_SALES);
        Recording repository = new Recording();

        Summary summary = service(repository).load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertEquals(List.of("days SALES 2026-08-01 2026-09-23"), repository.calls);
        assertEquals(new BigDecimal("270"), summary.sales().net(), "this month: 300 less 30");
        assertEquals(new BigDecimal("100"), summary.previousSales().net(), "the 1st to the 23rd of August");
        assertEquals(SummaryService.TREND_DAYS, summary.trend().size());
        assertEquals(TODAY, summary.trend().getLast().day());
    }

    @Test
    @DisplayName("a day's trend reaches back fourteen days though the period is one day")
    void theTrendReachesBackWhateverThePeriod() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.REPORTS_SHOW_SALES);
        Recording repository = new Recording();

        service(repository).load(SummaryPeriod.TODAY, SummaryPeriod.TODAY.range(TODAY), TODAY);

        assertEquals(List.of("days SALES 2026-09-10 2026-09-23"), repository.calls);
    }

    @Test
    @DisplayName("each card is read for its own key: the treasuries twice for the two periods, the debts once")
    void eachCardForItsKey() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.TREASURY_SHOW,
                AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.ITEMS_SHOW, AppPermissions.REPORTS_SHOW_ITEMS,
                AppPermissions.REPORTS_SHOW_PURCHASE);
        Recording repository = new Recording();

        Summary summary = service(repository).load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertEquals(List.of("days PURCHASES 2026-08-01 2026-09-23",
                "cash 2026-09-01 2026-09-23", "cash 2026-08-01 2026-08-23",
                "receivables 5", "low stock 5", "top items 2026-09-01 2026-09-23", "treasuries"), repository.calls);
        assertNull(summary.sales(), "no sales key, no sales");
        assertEquals(new BigDecimal("150"), summary.cash().net());
    }

    private static final class Recording implements SummaryRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<DayFigures> days(MonthlySide side, LocalDate from, LocalDate to) {
            calls.add("days " + side + " " + from + " " + to);
            return List.of(day(LocalDate.of(2026, 8, 10), 1, "100", "0", "0", "0"),
                    day(LocalDate.of(2026, 8, 30), 1, "999", "0", "0", "0"),
                    day(LocalDate.of(2026, 9, 5), 1, "300", "30", "0", "0"));
        }

        @Override
        public CashFlow cash(LocalDate from, LocalDate to) {
            calls.add("cash " + from + " " + to);
            return new CashFlow(new BigDecimal("200"), new BigDecimal("50"));
        }

        @Override
        public Receivables receivables(int top) {
            calls.add("receivables " + top);
            return Receivables.NONE;
        }

        @Override
        public LowStock lowStock(int limit) {
            calls.add("low stock " + limit);
            return LowStock.NONE;
        }

        @Override
        public List<TopSellingItem> topItems(LocalDate from, LocalDate to) {
            calls.add("top items " + from + " " + to);
            return List.of();
        }

        @Override
        public List<TreasuryBalanceSummary> treasuries() {
            calls.add("treasuries");
            return List.of();
        }
    }
}
