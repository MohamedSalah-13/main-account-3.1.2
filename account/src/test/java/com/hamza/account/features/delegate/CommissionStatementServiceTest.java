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
class CommissionStatementServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final List<CommissionRunRepository.PeriodLine> approvedLines = new ArrayList<>();
    private final List<DelegateActivity> activityNow = new ArrayList<>();
    private CommissionRule ruleNow;
    private int askedLimit;
    private LocalDate askedRuleDay;

    private final CommissionStatementService service = new CommissionStatementService(
            new Runs(), new Activity(), new Rules());

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static CommissionRule flatTwoPercent() {
        return new CommissionRule(41, 5, LocalDate.of(2026, 1, 1), CommissionBasis.SALES, TierMode.WHOLE,
                BigDecimal.ZERO, new CommissionTiers(List.of(new CommissionTiers.Tier(BigDecimal.ZERO, n("2")))), null);
    }

    private static CommissionLine approved(String amount) {
        return new CommissionLine(11, 3, 5, "Ali", 41, CommissionBasis.SALES, TierMode.WHOLE, BigDecimal.ZERO, "0:2",
                n("1000"), BigDecimal.ZERO, BigDecimal.ZERO, n("1000"), null, 1, n("2"), n(amount),
                CommissionLine.Posting.PAYROLL);
    }

    @Test
    void aStatementShowsRatesSoItNeedsThePermissionToSeeThem() {
        signInWith(AppPermissions.COMMISSION_REPORTS);
        assertThrows(BusinessRuleException.class, () -> service.forDelegate(5));
    }

    /** The ordinary month: nothing changed after approval, and the difference is a plain zero. */
    @Test
    void aMonthNobodyTouchedHasNoDifference() throws Exception {
        signInWith(AppPermissions.COMMISSION_SHOW);
        approvedLines.add(new CommissionRunRepository.PeriodLine(OCTOBER, approved("20.00")));
        activityNow.add(new DelegateActivity(5, "Ali", true, n("1000"), BigDecimal.ZERO, BigDecimal.ZERO));
        ruleNow = flatTwoPercent();

        CommissionStatementService.Row row = service.forDelegate(5).get(0);

        assertEquals(n("20.00"), row.live());
        assertEquals(0, row.difference().signum());
        assertFalse(row.drifted());
        assertEquals(LocalDate.of(2026, 10, 1), askedRuleDay, "computed again by the rule of the month's first day");
        assertEquals(CommissionStatementService.MONTHS, askedLimit);
    }

    /** An invoice entered after approval: the live figure moves, the approved one is where it was. */
    @Test
    void aLateInvoiceShowsAsADifferenceAndChangesNothing() throws Exception {
        signInWith(AppPermissions.COMMISSION_SHOW);
        approvedLines.add(new CommissionRunRepository.PeriodLine(OCTOBER, approved("20.00")));
        activityNow.add(new DelegateActivity(5, "Ali", true, n("1500"), BigDecimal.ZERO, BigDecimal.ZERO));
        ruleNow = flatTwoPercent();

        CommissionStatementService.Row row = service.forDelegate(5).get(0);

        assertEquals(n("20.00"), row.approved().amount());
        assertEquals(n("30.00"), row.live());
        assertEquals(n("10.00"), row.difference());
        assertTrue(row.drifted());
    }

    /** His invoices of the month were all moved to somebody else: nothing, under a rule, is zero - not "no figure". */
    @Test
    void aDelegateTheMonthNoLongerNamesHasALiveFigureOfZero() throws Exception {
        signInWith(AppPermissions.COMMISSION_SHOW);
        approvedLines.add(new CommissionRunRepository.PeriodLine(OCTOBER, approved("20.00")));
        ruleNow = flatTwoPercent();

        CommissionStatementService.Row row = service.forDelegate(5).get(0);

        assertEquals(n("0.00"), row.live());
        assertEquals(n("-20.00"), row.difference());
    }

    /** No rule in force on that day any more: there is no live figure to show, and that is itself a drift. */
    @Test
    void withNoRuleInForceThereIsNoLiveFigure() throws Exception {
        signInWith(AppPermissions.COMMISSION_SHOW);
        approvedLines.add(new CommissionRunRepository.PeriodLine(OCTOBER, approved("20.00")));
        activityNow.add(new DelegateActivity(5, "Ali", true, n("1000"), BigDecimal.ZERO, BigDecimal.ZERO));

        CommissionStatementService.Row row = service.forDelegate(5).get(0);

        assertNull(row.live());
        assertNull(row.difference());
        assertTrue(row.drifted());
    }

    // ---- fakes ---------------------------------------------------------------------------------

    private final class Runs implements CommissionRunRepository {
        @Override
        public List<PeriodLine> linesOfEmployee(int employeeId, int limit) {
            askedLimit = limit;
            return approvedLines;
        }

        @Override
        public Optional<Integer> activeRunId(YearMonth period) {
            return Optional.empty();
        }

        @Override
        public int insertRun(YearMonth period, String notes, int userId) {
            return 0;
        }

        @Override
        public int insertLine(int runId, CommissionLine line) {
            return 0;
        }

        @Override
        public List<CommissionRun> runs() {
            return List.of();
        }

        @Override
        public Optional<CommissionRun> run(int runId) {
            return Optional.empty();
        }

        @Override
        public List<CommissionLine> linesOf(int runId) {
            return List.of();
        }

        @Override
        public int cancel(int runId, int userId, String reason) {
            return 0;
        }

        @Override
        public List<Unposted> unpostedLinesForUpdate(int runId) {
            return List.of();
        }

        @Override
        public int insertLedgerCommission(int employeeId, LocalDate date, BigDecimal amount, String notes, int userId) {
            return 0;
        }

        @Override
        public int insertAccountPosting(int lineId, int ledgerEntryId, int userId) {
            return 0;
        }

        @Override
        public BigDecimal payrollDue(int employeeId, YearMonth period) {
            return BigDecimal.ZERO;
        }

        @Override
        public int insertPayrollPostings(int payrollRunId, int userId, int employeeId, YearMonth period) {
            return 0;
        }

        @Override
        public boolean ruleOfDayUsed(int employeeId, LocalDate effectiveFrom) {
            return false;
        }

        @Override
        public boolean ruleUsed(int ruleId) {
            return false;
        }
    }

    private final class Activity implements DelegateActivityRepository {
        @Override
        public List<DelegateActivity> activity(LocalDate from, LocalDate to) {
            return activityNow;
        }

        @Override
        public BigDecimal unattributedCollections(LocalDate from, LocalDate to) {
            return BigDecimal.ZERO;
        }

        @Override
        public int attributeCollection(long accountNumber) {
            return 0;
        }
    }

    private final class Rules implements CommissionRuleRepository {
        @Override
        public List<CommissionRule> history(int employeeId) {
            return List.of();
        }

        @Override
        public Optional<CommissionRule> inForceOn(int employeeId, LocalDate day) {
            askedRuleDay = day;
            return Optional.ofNullable(ruleNow);
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
