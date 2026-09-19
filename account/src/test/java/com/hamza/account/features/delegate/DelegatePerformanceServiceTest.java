package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The session is never user 1, who bypasses every permission. */
class DelegatePerformanceServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final FakeActivity activity = new FakeActivity();
    private final FakeRules rules = new FakeRules();
    private final DelegatePerformanceService service = new DelegatePerformanceService(activity, rules);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static CommissionRule rule(int employeeId, CommissionBasis basis, String target, String... fromAndRate) {
        List<CommissionTiers.Tier> tiers = new ArrayList<>();
        for (int i = 0; i < fromAndRate.length; i += 2) {
            tiers.add(new CommissionTiers.Tier(n(fromAndRate[i]), n(fromAndRate[i + 1])));
        }
        return new CommissionRule(1, employeeId, LocalDate.of(2026, 1, 1), basis, TierMode.WHOLE,
                n(target), new CommissionTiers(tiers), null);
    }

    @Test
    void theReportNeedsItsOwnPermission() {
        signInWith(AppPermissions.COMMISSION_SHOW, AppPermissions.EMPLOYEE_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.month(OCTOBER));
        assertEquals(0, activity.reads, "refused before any query");
    }

    /**
     * Sales and collections are what the totals screen shows anyway. A target, a rate and a
     * commission are figures about a person: without the permission they are <b>not fetched</b>.
     */
    @Test
    void withoutTheRatePermissionNoRuleIsEverRead() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("90000"), n("0"), n("40000")));
        rules.byEmployee(5, rule(5, CommissionBasis.SALES, "100000", "80", "2"));

        DelegatePerformanceMonth month = service.month(OCTOBER);

        assertEquals(0, rules.reads, "hiding a column afterwards is what this replaces");
        assertFalse(month.ratesVisible());
        DelegatePerformanceRow row = month.rows().get(0);
        assertTrue(row.rule().isEmpty());
        assertNull(row.commission());
        assertNull(row.target());
        assertEquals(0, month.totalCommission().signum());
        assertEquals(0, n("90000").compareTo(month.totalNetSales()), "the activity is still all there");
    }

    /** The whole month, both ends, and the rule of its first day - for the collections too. */
    @Test
    void theMonthIsAskedWholeAndJudgedByItsFirstDay() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS, AppPermissions.COMMISSION_SHOW);
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("1"), n("0"), n("0")));

        service.month(YearMonth.of(2028, 2));

        assertEquals(LocalDate.of(2028, 2, 1), activity.from);
        assertEquals(LocalDate.of(2028, 2, 29), activity.to, "a leap February ends on the 29th");
        assertEquals(LocalDate.of(2028, 2, 1), activity.unattributedFrom);
        assertEquals(LocalDate.of(2028, 2, 29), activity.unattributedTo);
        assertEquals(LocalDate.of(2028, 2, 1), rules.askedDay);
    }

    /** Each delegate is measured on his own rule's basis: one on what he sold, one on what he collected. */
    @Test
    void eachDelegateIsJudgedOnTheBasisOfHisOwnRule() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS, AppPermissions.COMMISSION_SHOW);
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("100000"), n("10000"), n("30000")));
        activity.rows.add(new DelegateActivity(6, "Omar", true, n("100000"), n("10000"), n("30000")));
        activity.rows.add(new DelegateActivity(7, "Direct sale", true, n("500"), n("0"), n("500")));
        rules.byEmployee(5, rule(5, CommissionBasis.SALES, "100000", "80", "2"));
        rules.byEmployee(6, rule(6, CommissionBasis.COLLECTED, "100000", "80", "2"));

        DelegatePerformanceMonth month = service.month(OCTOBER);
        DelegatePerformanceRow onSales = month.rows().get(0);
        DelegatePerformanceRow onCollected = month.rows().get(1);
        DelegatePerformanceRow noRule = month.rows().get(2);

        assertEquals(0, n("90000").compareTo(onSales.base()), "sales less the month's returns");
        assertEquals(n("1800.00"), onSales.commission());
        assertEquals(n("90.00"), onSales.achievementPercent());
        assertFalse(onSales.belowLowestTier());

        assertEquals(0, n("30000").compareTo(onCollected.base()));
        assertEquals(n("0.00"), onCollected.commission(), "30% of target is below the 80% tier");
        assertNull(onCollected.ratePercent(), "no tier reached, so no rate to show");
        assertTrue(onCollected.belowLowestTier());

        assertTrue(noRule.rule().isEmpty());
        assertNull(noRule.commission(), "no rule is an empty cell, not a zero");
        assertFalse(noRule.belowLowestTier());

        assertEquals(1, month.belowLowestTier());
        assertEquals(n("1800.00"), month.totalCommission());
        assertEquals(0, n("180500").compareTo(month.totalNetSales()), "the totals are the rows', nothing else");
        assertEquals(0, n("60500").compareTo(month.totalCollected()));
    }

    /** A flat rate has no target, so it shows none - an empty cell, not a zero. */
    @Test
    void aFlatRateShowsNoTargetAndNoAchievement() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS, AppPermissions.COMMISSION_SHOW);
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("40000"), n("0"), n("0")));
        rules.byEmployee(5, rule(5, CommissionBasis.SALES, "0", "0", "2.5"));

        DelegatePerformanceRow row = service.month(OCTOBER).rows().get(0);
        assertNull(row.target());
        assertNull(row.achievementPercent());
        assertEquals(n("1000.00"), row.commission());
    }

    /** Returns outweighing sales is a bad month, not a debt the delegate owes. */
    @Test
    void aNegativeMonthEarnsNothing() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS, AppPermissions.COMMISSION_SHOW);
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("1000"), n("5000"), n("-2000")));
        rules.byEmployee(5, rule(5, CommissionBasis.SALES, "0", "0", "2"));

        DelegatePerformanceMonth month = service.month(OCTOBER);
        assertEquals(n("0.00"), month.rows().get(0).commission());
        assertEquals(0, n("-4000").compareTo(month.totalNetSales()), "the report still says what happened");
    }

    @Test
    void collectionsThatNameNobodyAreReportedNotDropped() throws Exception {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        activity.unattributed = n("7500");
        assertEquals(0, n("7500").compareTo(service.month(OCTOBER).unattributedCollections()));
    }

    private static final class FakeActivity implements DelegateActivityRepository {
        final List<DelegateActivity> rows = new ArrayList<>();
        BigDecimal unattributed = BigDecimal.ZERO;
        int reads;
        LocalDate from;
        LocalDate to;
        LocalDate unattributedFrom;
        LocalDate unattributedTo;

        @Override
        public List<DelegateActivity> activity(LocalDate from, LocalDate to) {
            reads++;
            this.from = from;
            this.to = to;
            return rows;
        }

        @Override
        public BigDecimal unattributedCollections(LocalDate from, LocalDate to) {
            reads++;
            unattributedFrom = from;
            unattributedTo = to;
            return unattributed;
        }

        @Override
        public int attributeCollection(long accountNumber) {
            return 0;
        }
    }

    private static final class FakeRules implements CommissionRuleRepository {
        private final java.util.Map<Integer, CommissionRule> byEmployee = new java.util.HashMap<>();
        int reads;
        LocalDate askedDay;

        void byEmployee(int employeeId, CommissionRule rule) {
            byEmployee.put(employeeId, rule);
        }

        @Override
        public List<CommissionRule> history(int employeeId) {
            return List.of();
        }

        @Override
        public Optional<CommissionRule> inForceOn(int employeeId, LocalDate day) {
            reads++;
            askedDay = day;
            return Optional.ofNullable(byEmployee.get(employeeId));
        }

        @Override
        public int save(CommissionRule rule, int userId) {
            return 0;
        }

        @Override
        public int delete(int employeeId, int ruleId) {
            return 0;
        }

        @Override
        public boolean isDelegate(int employeeId) {
            return true;
        }
    }
}
