package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the run service decides before it reaches a database. <b>The session is never user 1.</b>
 *
 * <p>Not covered here, and said rather than implied: that a run and its lines are written
 * together or not at all, that the unique key refuses a second run of a month, and that the
 * triggers freeze what was written. Those are {@code CommissionRunDatabaseAcceptanceTest}'s.
 */
class CommissionRunServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate IN_NOVEMBER = LocalDate.of(2026, 11, 3);

    private final FakeRuns runs = new FakeRuns();
    private final FakeActivity activity = new FakeActivity();
    private final FakeRules rules = new FakeRules();
    private LocalDate today = IN_NOVEMBER;
    private final CommissionRunService service = new CommissionRunService(runs, activity, rules, () -> today);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private void twoDelegatesOneWithARule() {
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("100000"), n("10000"), n("30000")));
        activity.rows.add(new DelegateActivity(6, "Omar", true, n("50000"), n("0"), n("50000")));
        rules.rule = new CommissionRule(41, 5, LocalDate.of(2026, 1, 1), CommissionBasis.SALES, TierMode.WHOLE,
                n("100000"), new CommissionTiers(List.of(new CommissionTiers.Tier(n("80"), n("2")))), null);
    }

    // ---- permissions ---------------------------------------------------------------------------

    @Test
    void eachDecisionHasItsOwnPermission() {
        signInWith(AppPermissions.COMMISSION_SHOW, AppPermissions.COMMISSION_RULE_UPDATE);
        assertThrows(BusinessRuleException.class, () -> service.approve(OCTOBER, null));
        assertThrows(BusinessRuleException.class, () -> service.cancel(1, "a reason"));
        assertThrows(BusinessRuleException.class, () -> service.postToAccounts(1));

        signInWith(AppPermissions.COMMISSION_RUN_CREATE);
        assertThrows(BusinessRuleException.class, () -> service.preview(OCTOBER), "a rate is not shown without show");
        assertThrows(BusinessRuleException.class, () -> service.runs());
        assertEquals(0, runs.writes);
    }

    // ---- approving -----------------------------------------------------------------------------

    /** Tomorrow's invoices would contradict a figure frozen today. The last day is still "not over". */
    @Test
    void aMonthIsNotApprovedBeforeItEnds() {
        signInWith(AppPermissions.COMMISSION_RUN_CREATE);
        twoDelegatesOneWithARule();
        today = LocalDate.of(2026, 10, 31);
        assertEquals("commission.error.run.month.open", assertThrows(UserValidationException.class,
                () -> service.approve(OCTOBER, null)).getMessage());
        today = LocalDate.of(2026, 10, 15);
        assertEquals("commission.error.run.month.open", assertThrows(UserValidationException.class,
                () -> service.approve(OCTOBER, null)).getMessage());
        assertEquals(0, runs.writes);
    }

    @Test
    void aMonthIsApprovedOnce() {
        signInWith(AppPermissions.COMMISSION_RUN_CREATE);
        twoDelegatesOneWithARule();
        runs.activeRun = Optional.of(7);
        assertEquals("commission.error.run.exists", assertThrows(UserValidationException.class,
                () -> service.approve(OCTOBER, null)).getMessage());
    }

    @Test
    void aMonthWithNoRuleInForceHasNothingToApprove() {
        signInWith(AppPermissions.COMMISSION_RUN_CREATE);
        activity.rows.add(new DelegateActivity(6, "Omar", true, n("50000"), n("0"), n("50000")));
        assertEquals("commission.error.run.empty", assertThrows(UserValidationException.class,
                () -> service.approve(OCTOBER, null)).getMessage());
    }

    /**
     * A line for the delegate with a rule, carrying what produced the figure; none for the one
     * without. And the month is judged by the rule of its first day.
     */
    @Test
    void approvalWritesALineForEveryDelegateWithARuleAndEverythingThatProducedIt() throws Exception {
        signInWith(AppPermissions.COMMISSION_RUN_CREATE);
        twoDelegatesOneWithARule();

        service.approveWithin(OCTOBER, "  october  ", service_preview());

        assertEquals("october", runs.insertedNotes);
        assertEquals(1, runs.insertedLines.size());
        CommissionLine line = runs.insertedLines.get(0);
        assertEquals(5, line.employeeId());
        assertEquals(41, line.ruleId());
        assertEquals("80:2", line.tiersSnapshot());
        assertEquals(0, n("90000").compareTo(line.baseAmount()), "sales less the month's returns");
        assertEquals(n("90.00"), line.achievementPercent());
        assertEquals(1, line.tier());
        assertEquals(n("1800.00"), line.amount());
        assertEquals(LocalDate.of(2026, 10, 1), rules.askedDay);
    }

    private List<CommissionLine> service_preview() throws DaoException {
        signInWith(AppPermissions.COMMISSION_SHOW, AppPermissions.COMMISSION_RUN_CREATE);
        return service.preview(OCTOBER);
    }

    /** A delegate with a rule who earned nothing still gets a line: nothing is a fact about his month. */
    @Test
    void aDelegateBelowHisLowestTierGetsALineOfZero() throws Exception {
        activity.rows.add(new DelegateActivity(5, "Ali", true, n("10000"), n("0"), n("0")));
        rules.rule = new CommissionRule(41, 5, LocalDate.of(2026, 1, 1), CommissionBasis.SALES, TierMode.WHOLE,
                n("100000"), new CommissionTiers(List.of(new CommissionTiers.Tier(n("80"), n("2")))), null);
        List<CommissionLine> lines = service_preview();
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).tier());
        assertEquals(n("0.00"), lines.get(0).amount());
    }

    /** The pre-check is a courtesy; the unique key decides. The second till gets the sentence, not a code. */
    @Test
    void losingTheRaceForAMonthIsTheSameRefusal() throws Exception {
        twoDelegatesOneWithARule();
        List<CommissionLine> lines = service_preview();
        runs.insertRunFails = true;
        runs.activeRun = Optional.of(7);
        assertEquals("commission.error.run.exists", assertThrows(UserValidationException.class,
                () -> service.approveWithin(OCTOBER, null, lines)).getMessage());
        assertTrue(runs.insertedLines.isEmpty());
    }

    // ---- cancelling ----------------------------------------------------------------------------

    @Test
    void cancellingNeedsAReasonAndAnUnpostedApprovedRun() throws Exception {
        signInWith(AppPermissions.COMMISSION_RUN_UPDATE);
        runs.stored = run(CommissionRun.Status.APPROVED, 0);
        assertEquals("commission.error.run.cancel.reason", assertThrows(UserValidationException.class,
                () -> service.cancel(3, "  ")).getMessage());

        runs.stored = run(CommissionRun.Status.APPROVED, 1);
        assertEquals("commission.error.run.posted", assertThrows(UserValidationException.class,
                () -> service.cancel(3, "wrong rule")).getMessage());

        runs.stored = run(CommissionRun.Status.CANCELLED, 0);
        assertEquals("commission.error.run.not.approved", assertThrows(UserValidationException.class,
                () -> service.cancel(3, "wrong rule")).getMessage());
        assertEquals(0, runs.writes);

        runs.stored = run(CommissionRun.Status.APPROVED, 0);
        assertEquals(1, service.cancel(3, " wrong rule "));
        assertEquals("wrong rule", runs.cancelReason);
    }

    /** Somebody else cancelled it between the read and the write: one cancellation, one refusal. */
    @Test
    void aCancellationThatLostItsRaceIsRefused() {
        signInWith(AppPermissions.COMMISSION_RUN_UPDATE);
        runs.stored = run(CommissionRun.Status.APPROVED, 0);
        runs.cancelResult = 0;
        assertEquals("commission.error.run.not.approved", assertThrows(UserValidationException.class,
                () -> service.cancel(3, "wrong rule")).getMessage());
    }

    // ---- posting -------------------------------------------------------------------------------

    /**
     * One ledger row and one posting per line still unposted, dated the month's last day - a
     * month is earned once it ends, as the payroll's entitlement is. A second press finds nothing.
     */
    @Test
    void postingWritesALedgerRowAndAPostingForEachUnpostedLine() throws Exception {
        runs.unposted.add(new CommissionRunRepository.Unposted(11, 5, n("1800.00")));
        runs.unposted.add(new CommissionRunRepository.Unposted(12, 6, n("19.00")));

        assertEquals(2, service.postWithin(run(CommissionRun.Status.APPROVED, 0)));

        assertEquals(List.of("5:2026-10-31:1800.00", "6:2026-10-31:19.00"), runs.ledgerRows);
        assertEquals(List.of("11->1", "12->2"), runs.accountPostings, "each posting names the ledger row it made");

        runs.unposted.clear();
        assertEquals(0, service.postWithin(run(CommissionRun.Status.APPROVED, 2)));
    }

    @Test
    void aCancelledRunIsNotPosted() {
        signInWith(AppPermissions.COMMISSION_RUN_POST);
        runs.stored = run(CommissionRun.Status.CANCELLED, 0);
        assertEquals("commission.error.run.not.approved", assertThrows(UserValidationException.class,
                () -> service.postToAccounts(3)).getMessage());
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private static CommissionRun run(CommissionRun.Status status, int posted) {
        return new CommissionRun(3, OCTOBER, status, null, LocalDateTime.of(2026, 11, 3, 10, 0), "operator",
                status == CommissionRun.Status.CANCELLED ? "earlier" : null, 2, new BigDecimal("1819.00"), posted);
    }

    private static final class FakeRuns implements CommissionRunRepository {
        Optional<Integer> activeRun = Optional.empty();
        CommissionRun stored;
        boolean insertRunFails;
        int cancelResult = 1;
        int writes;
        String insertedNotes;
        String cancelReason;
        final List<CommissionLine> insertedLines = new ArrayList<>();
        final List<Unposted> unposted = new ArrayList<>();
        final List<String> ledgerRows = new ArrayList<>();
        final List<String> accountPostings = new ArrayList<>();

        @Override
        public Optional<Integer> activeRunId(YearMonth period) {
            return activeRun;
        }

        @Override
        public int insertRun(YearMonth period, String notes, int userId) throws DaoException {
            if (insertRunFails) {
                throw new DaoException("Duplicate entry for key 'commission_run_active_uk'");
            }
            writes++;
            insertedNotes = notes;
            return 3;
        }

        @Override
        public int insertLine(int runId, CommissionLine line) {
            writes++;
            insertedLines.add(line);
            return 1;
        }

        @Override
        public List<CommissionRun> runs() {
            return List.of();
        }

        @Override
        public Optional<CommissionRun> run(int runId) {
            return Optional.ofNullable(stored);
        }

        @Override
        public List<CommissionLine> linesOf(int runId) {
            return List.of();
        }

        @Override
        public int cancel(int runId, int userId, String reason) {
            if (cancelResult == 1) {
                writes++;
                cancelReason = reason;
            }
            return cancelResult;
        }

        @Override
        public List<Unposted> unpostedLinesForUpdate(int runId) {
            return List.copyOf(unposted);
        }

        @Override
        public int insertLedgerCommission(int employeeId, LocalDate date, BigDecimal amount, String notes, int userId) {
            ledgerRows.add(employeeId + ":" + date + ":" + amount);
            return ledgerRows.size();
        }

        @Override
        public int insertAccountPosting(int lineId, int ledgerEntryId, int userId) {
            accountPostings.add(lineId + "->" + ledgerEntryId);
            return 1;
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

    private static final class FakeActivity implements DelegateActivityRepository {
        final List<DelegateActivity> rows = new ArrayList<>();

        @Override
        public List<DelegateActivity> activity(LocalDate from, LocalDate to) {
            return rows;
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

    /** One rule, for whichever employee it names. */
    private static final class FakeRules implements CommissionRuleRepository {
        CommissionRule rule;
        LocalDate askedDay;

        @Override
        public List<CommissionRule> history(int employeeId) {
            return List.of();
        }

        @Override
        public Optional<CommissionRule> inForceOn(int employeeId, LocalDate day) {
            askedDay = day;
            return rule != null && rule.employeeId() == employeeId ? Optional.of(rule) : Optional.empty();
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
