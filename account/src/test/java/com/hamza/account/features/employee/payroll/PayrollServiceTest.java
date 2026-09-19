package com.hamza.account.features.employee.payroll;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.SalaryKind;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the payroll service decides before it reaches a database.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} is
 * {@code currentUserId() == 1} and bypasses every permission, so a test signed in as user 1
 * proves nothing about authorization.
 * <p>
 * What is <b>not</b> covered here and is written rather than implied: everything inside
 * {@code TransactionTemplate} - that an approval which fails halfway leaves the run a draft
 * with no ledger rows. That needs a real transaction, and the honest proof is a run against
 * MySQL.
 */
class PayrollServiceTest {

    private static final PayrollPeriod SEPTEMBER = new PayrollPeriod(2026, 9);

    private final FakePayrollRepository repository = new FakePayrollRepository();
    private final PayrollService service = new PayrollService(repository);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ---- authorization -----------------------------------------------------------------------

    @Test
    @DisplayName("reading needs payroll.show, not the employees screen's permission")
    void readingIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);

        assertThrows(BusinessRuleException.class, () -> service.recentRuns(10));
        assertThrows(BusinessRuleException.class, () -> service.findRun(1));
        assertThrows(BusinessRuleException.class, () -> service.linesOf(1));
        assertThrows(BusinessRuleException.class, () -> service.preview(SEPTEMBER));
    }

    @Test
    @DisplayName("approving needs its own key - creating a run does not grant it")
    void approvingIsGuarded() {
        signInWith(AppPermissions.PAYROLL_SHOW, AppPermissions.PAYROLL_CREATE);
        repository.run = draft(1);

        assertThrows(BusinessRuleException.class, () -> service.approve(1),
                "who computes the month is not automatically who signs it off");
    }

    @Test
    @DisplayName("paying needs its own key - approving does not grant it")
    void payingIsGuarded() {
        signInWith(AppPermissions.PAYROLL_APPROVE, AppPermissions.PAYROLL_CREATE);
        repository.run = withStatus(1, PayrollRunStatus.APPROVED);

        assertThrows(BusinessRuleException.class, () -> service.markPaid(1),
                "who signs the month off is not automatically who opens the drawer");
    }

    // ---- the status machine ------------------------------------------------------------------

    @Test
    @DisplayName("a draft may be approved or cancelled, and nothing else")
    void draftTransitions() {
        assertTrue(PayrollRunStatus.DRAFT.mayMoveTo(PayrollRunStatus.APPROVED));
        assertTrue(PayrollRunStatus.DRAFT.mayMoveTo(PayrollRunStatus.CANCELLED));
        assertEquals(false, PayrollRunStatus.DRAFT.mayMoveTo(PayrollRunStatus.PAID),
                "a month cannot be paid before it is approved");
        assertEquals(false, PayrollRunStatus.DRAFT.mayMoveTo(PayrollRunStatus.DRAFT));
    }

    @Test
    @DisplayName("an approved run may only be paid - never cancelled, because it wrote balances")
    void approvedTransitions() {
        assertTrue(PayrollRunStatus.APPROVED.mayMoveTo(PayrollRunStatus.PAID));
        assertEquals(false, PayrollRunStatus.APPROVED.mayMoveTo(PayrollRunStatus.CANCELLED));
        assertEquals(false, PayrollRunStatus.APPROVED.mayMoveTo(PayrollRunStatus.DRAFT));
    }

    @Test
    @DisplayName("a paid or cancelled run is the end of the road")
    void terminalTransitions() {
        for (PayrollRunStatus next : PayrollRunStatus.values()) {
            assertEquals(false, PayrollRunStatus.PAID.mayMoveTo(next));
            assertEquals(false, PayrollRunStatus.CANCELLED.mayMoveTo(next));
        }
    }

    @Test
    @DisplayName("only a draft is editable - the single answer every disabled control hangs off")
    void editability() {
        assertTrue(PayrollRunStatus.DRAFT.isEditable());
        assertEquals(false, PayrollRunStatus.APPROVED.isEditable());
        assertEquals(false, PayrollRunStatus.PAID.isEditable());
        assertEquals(false, PayrollRunStatus.CANCELLED.isEditable());
    }

    @Test
    @DisplayName("approving something that is not a draft is refused by its own message")
    void approveRefusesANonDraft() {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = withStatus(1, PayrollRunStatus.APPROVED);

        assertEquals("payroll.error.not.draft",
                assertThrows(UserValidationException.class, () -> service.approve(1)).getMessage());
    }

    @Test
    @DisplayName("approving an empty run is refused rather than writing nothing and saying it did")
    void approveRefusesAnEmptyRun() {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        repository.lines = new ArrayList<>();

        assertEquals("payroll.error.empty",
                assertThrows(UserValidationException.class, () -> service.approve(1)).getMessage());
    }

    @Test
    @DisplayName("a month that already has a run is refused by name, not by a constraint violation")
    void oneRunPerMonth() {
        signInWith(AppPermissions.PAYROLL_CREATE);
        repository.runForPeriod = draft(1);

        assertEquals("payroll.error.period.exists",
                assertThrows(UserValidationException.class,
                        () -> service.createDraft(SEPTEMBER, null)).getMessage());
    }

    // ---- what approval writes ------------------------------------------------------------------

    @Test
    @DisplayName("approval writes an entitlement and a deduction, each with its real kind")
    void approvalWritesTwoEntries() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        repository.lines = List.of(line(7, "3000", "500", "250", "150", "100"));

        service.approveWithin(1, repository.run, repository.lines);

        assertEquals(2, repository.ledgerWrites.size(),
                "a single netted row would make ENTITLEMENT mean the entitlement in one month "
                        + "and the remainder in another");
        assertEquals("ENTITLEMENT", repository.ledgerWrites.get(0).kind());
        assertEquals(money("3750.00"), repository.ledgerWrites.get(0).amount(), "3000 + 500 + 250");
        assertEquals("DEDUCTION", repository.ledgerWrites.get(1).kind());
        assertEquals(money("250.00"), repository.ledgerWrites.get(1).amount(), "100 + 150");
    }

    @Test
    @DisplayName("a line with nothing deducted writes one entry, not a zero deduction")
    void noZeroDeduction() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        repository.lines = List.of(line(7, "3000", "0", "0", "0", "0"));

        service.approveWithin(1, repository.run, repository.lines);

        assertEquals(1, repository.ledgerWrites.size());
        assertEquals("ENTITLEMENT", repository.ledgerWrites.get(0).kind());
    }

    @Test
    @DisplayName("the entitlement is dated the last day of the month - a month is earned once it ends")
    void entitlementIsDatedTheMonthsEnd() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        repository.lines = List.of(line(7, "3000", "0", "0", "0", "0"));

        service.approveWithin(1, repository.run, repository.lines);

        assertEquals(LocalDate.of(2026, 9, 30), repository.ledgerWrites.get(0).date());
    }

    @Test
    @DisplayName("an advance never reaches the ledger through the run")
    void theRunNeverWritesAnAdvance() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        // The line carries 1000 outstanding, and it must change nothing this method writes.
        repository.lines = List.of(new PayrollLine(1, 1, 7, "سها", "موظف", SalaryKind.MONTHLY,
                money("3000"), money("30"), money("0"), money("0"), money("3000"), money("0"),
                money("0"), money("0"), money("0"), money("1000"), money("3000"), null));

        service.approveWithin(1, repository.run, repository.lines);

        assertEquals(1, repository.ledgerWrites.size());
        assertEquals(money("3000.00"), repository.ledgerWrites.get(0).amount(),
                "rule ق-٥: the advance was deducted on the day the cash left");
    }

    @Test
    @DisplayName("the run's id is on every row it writes, so reversing it takes back exactly those")
    void everyRowCarriesTheRunId() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(42);
        repository.lines = List.of(line(7, "3000", "0", "0", "200", "0"));

        service.approveWithin(42, repository.run, repository.lines);

        assertTrue(repository.ledgerWrites.stream().allMatch(w -> w.runId() == 42));
        assertTrue(repository.ledgerWrites.stream().allMatch(w -> w.userId() == 9),
                "who entered it, not user 1");
    }

    @Test
    @DisplayName("a second approval finds the row already moved and is refused")
    void approvalIsARaceTheDatabaseSettles() {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        repository.lines = List.of(line(7, "3000", "0", "0", "0", "0"));
        repository.statusMoveResult = 0;   // somebody else got there first

        assertEquals("payroll.error.not.draft",
                assertThrows(UserValidationException.class,
                        () -> service.approveWithin(1, repository.run, repository.lines))
                        .getMessage());
        assertTrue(repository.ledgerWrites.isEmpty(),
                "and nothing was written on the strength of a move that did not happen");
    }

    // ---- the preview -------------------------------------------------------------------------

    @Test
    @DisplayName("the preview is the same calculation the draft persists, from the same inputs")
    void previewMatchesWhatWouldBeStored() throws Exception {
        signInWith(AppPermissions.PAYROLL_SHOW);
        repository.candidates = List.of(
                new PayrollInput(7, "سها", SalaryKind.MONTHLY, money("3000"), null, null,
                        null, null, null, money("500"), null, null, money("1000")));

        List<PayrollCalculation> preview = service.preview(SEPTEMBER);

        assertEquals(1, preview.size());
        assertEquals(money("3500.00"), preview.get(0).earned(), "3000 + 500, the advance untouched");
        assertEquals(money("3500.00"), preview.get(0).netPay());
    }

    // ---- an approved commission ---------------------------------------------------------------

    /** A commission source that answers one figure for employee 7 and records what it was told was paid. */
    private static final class FakeCommission implements CommissionSource {
        private BigDecimal due = BigDecimal.ZERO;
        private final List<String> paid = new ArrayList<>();

        @Override
        public BigDecimal dueFor(int employeeId, PayrollPeriod period) {
            return employeeId == 7 ? due : BigDecimal.ZERO;
        }

        @Override
        public void paidBy(int payrollRunId, int employeeId, PayrollPeriod period, int userId) {
            paid.add(payrollRunId + ":" + employeeId + ":" + period);
        }
    }

    @Test
    @DisplayName("approving a payroll marks the commission it pays as paid, in the same unit as the entitlement")
    void approvalMarksTheCommissionItPays() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        FakeCommission commission = new FakeCommission();
        commission.due = money("250.00");
        PayrollService paying = new PayrollService(repository, AttendanceSource.NONE, commission);
        repository.run = draft(1);
        repository.lines = List.of(line(7, "3000", "500", "250", "0", "0"), line(8, "2000", "0", "0", "0", "0"));

        paying.approveWithin(1, repository.run, repository.lines);

        assertEquals(List.of("1:7:" + SEPTEMBER), commission.paid,
                "the delegate's lines are marked, and an employee with nothing due is not looked at");
        assertEquals(money("3750.00"), repository.ledgerWrites.get(0).amount(),
                "the entitlement carries the commission - which is why it must not also be posted");
    }

    @Test
    @DisplayName("a draft whose commission is not the approved one is refused, and nothing is marked or written")
    void aStaleCommissionRefusesTheApproval() {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        FakeCommission commission = new FakeCommission();
        commission.due = money("500.00");
        PayrollService paying = new PayrollService(repository, AttendanceSource.NONE, commission);
        repository.run = draft(1);
        // Built before the commission was approved, or typed over: it says 250 and 500 is due.
        repository.lines = List.of(line(7, "3000", "0", "250", "0", "0"));

        assertEquals("payroll.error.commission.differs", assertThrows(UserValidationException.class,
                () -> paying.approveWithin(1, repository.run, repository.lines)).getMessage());
        assertTrue(commission.paid.isEmpty(), "marking 500 as paid while paying 250 is the defect");
        assertTrue(repository.ledgerWrites.isEmpty());
    }

    @Test
    @DisplayName("with no commission runs a hand-typed commission is what it always was")
    void withoutRunsNothingChanges() throws Exception {
        signInWith(AppPermissions.PAYROLL_APPROVE);
        repository.run = draft(1);
        repository.lines = List.of(line(7, "3000", "0", "250", "0", "0"));

        service.approveWithin(1, repository.run, repository.lines);

        assertEquals(money("3250.00"), repository.ledgerWrites.get(0).amount());
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private static PayrollRun draft(int id) {
        return withStatus(id, PayrollRunStatus.DRAFT);
    }

    private static PayrollRun withStatus(int id, PayrollRunStatus status) {
        return new PayrollRun(id, SEPTEMBER, status, null, null, 1, "admin",
                null, null, null, null, null, null, 1, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static PayrollLine line(int employeeId, String basic, String allowances,
                                    String commission, String deductions, String absence) {
        return new PayrollLine(1, 1, employeeId, "سها", "موظف", SalaryKind.MONTHLY,
                money(basic), money("30"), money("0"), money("0"),
                money(basic), money(allowances), money(commission), money(deductions),
                money(absence), money("0"), money("0"), null);
    }

    /** One ledger write, recorded so the service's decisions can be read off it. */
    private record LedgerWrite(int employeeId, LocalDate date, String kind, BigDecimal amount,
                               int runId, int userId) {
    }

    private static final class FakePayrollRepository implements PayrollRepository {

        private PayrollRun run;
        private PayrollRun runForPeriod;
        private List<PayrollLine> lines = new ArrayList<>();
        private List<PayrollInput> candidates = new ArrayList<>();
        private int statusMoveResult = 1;
        private final List<LedgerWrite> ledgerWrites = new ArrayList<>();
        private PayrollCalculation lastEdit;

        @Override
        public List<PayrollRun> recentRuns(int limit) {
            return run == null ? List.of() : List.of(run);
        }

        @Override
        public Optional<PayrollRun> findRun(int runId) {
            return Optional.ofNullable(run);
        }

        @Override
        public Optional<PayrollRun> findRunForPeriod(PayrollPeriod period) {
            return Optional.ofNullable(runForPeriod);
        }

        @Override
        public int insertRun(PayrollPeriod period, String notes, int userId) {
            return 1;
        }

        @Override
        public int moveStatus(int runId, PayrollRunStatus expected, PayrollRunStatus next,
                              int userId) {
            return statusMoveResult;
        }

        @Override
        public int deleteDraftRun(int runId) {
            return 1;
        }

        @Override
        public List<PayrollLine> linesOf(int runId) {
            return lines;
        }

        @Override
        public List<PayrollInput> candidatesFor(PayrollPeriod period) {
            return candidates;
        }

        @Override
        public int deleteLines(int runId) {
            return 0;
        }

        @Override
        public int insertLine(int runId, PayrollCalculation calculation, PayrollInput input,
                              int userId) {
            return 1;
        }

        @Override
        public int updateLine(int runId, int lineId, PayrollCalculation calculation,
                              PayrollInput input, String notes) {
            lastEdit = calculation;
            return 1;
        }

        @Override
        public int insertRunLedgerEntry(int employeeId, LocalDate date, String kind,
                                        BigDecimal amount, String notes, int runId, int userId) {
            ledgerWrites.add(new LedgerWrite(employeeId, date, kind, amount, runId, userId));
            return ledgerWrites.size();
        }

        @Override
        public int deleteRunLedgerEntries(int runId) {
            return 0;
        }

        @Override
        public int countRunPayments(int runId) {
            return 0;
        }
    }

    @Test
    @DisplayName("correcting a line recalculates it rather than storing what the screen typed")
    void anEditIsRecalculated() throws Exception {
        signInWith(AppPermissions.PAYROLL_CREATE, AppPermissions.PAYROLL_SHOW);
        repository.run = draft(1);
        // 3000 basic, 500 allowances, nothing else - the line the draft was built with.
        repository.lines = List.of(line(7, "3000", "500", "0", "0", "0"));

        // Somebody types two days of absence and a 150 deduction.
        service.updateLine(1, PayrollLineEdit.parse(1, null, money("2"), null, null,
                money("150"), null));

        PayrollCalculation stored = repository.lastEdit;
        assertEquals(money("200.00"), stored.absenceDeduction(),
                "3000 over September's 30 days, twice - the screen never works this out");
        assertEquals(money("3150.00"), stored.netPay(), "3000 + 500 - 200 - 150");
    }

    @Test
    @DisplayName("a line of a run that is not a draft is refused")
    void anEditIsRefusedOnceApproved() {
        signInWith(AppPermissions.PAYROLL_CREATE, AppPermissions.PAYROLL_SHOW);
        repository.run = withStatus(1, PayrollRunStatus.APPROVED);

        assertEquals("payroll.error.not.draft",
                assertThrows(UserValidationException.class,
                        () -> service.updateLine(1, PayrollLineEdit.parse(1, null, money("1"),
                                null, null, null, null))).getMessage());
    }

    @Test
    @DisplayName("a negative figure is refused before it reaches a database")
    void anEditRefusesANegative() {
        assertEquals("payroll.error.line.amount",
                assertThrows(UserValidationException.class,
                        () -> PayrollLineEdit.parse(1, null, money("-1"), null, null, null, null))
                        .getMessage());
    }
}
