package com.hamza.account.features.employee.payroll;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.employee.EmployeeEntryKind;
import com.hamza.account.features.employee.attendance.AttendanceService;
import com.hamza.account.features.employee.attendance.AttendanceSummary;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The month's payroll: build it, correct it, approve it, and record that it was paid.
 *
 * <h2>What approval does, and why it is the only moment that matters</h2>
 * Approval writes the run into the employees' ledgers - one {@code ENTITLEMENT} for what each
 * employee earned and one {@code DEDUCTION} for what was taken off - and freezes the lines.
 * Everything before it is a working document; everything after it is somebody's balance. That
 * is why {@code R__triggers.sql} refuses to change a line once its run leaves {@code DRAFT},
 * and why a run cannot be cancelled after approval: the rows it wrote are already counted.
 *
 * <h2>Two entries, not one netted row</h2>
 * A single row for the net would make {@code ENTITLEMENT} mean the entitlement in a month with
 * no deductions and the remainder in a month with them - two meanings for one kind, which is
 * exactly the defect V58 removed by putting the direction in the kind rather than in a sign.
 *
 * <h2>An advance is not deducted here either</h2>
 * {@code advancesOutstanding} is printed and never subtracted: the advance left the till on
 * its own day and has been a debit since (rule ق-٥). The balance squares because the
 * entitlement and the cash are each recorded once, in their own place.
 *
 * <h2>Paying is not done here</h2>
 * This service records that a run has been paid. <b>The cash itself goes through
 * {@code EmployeePaymentService}</b>, one expense row per employee - never one lump row, or no
 * employee's statement could show their share of it. Keeping the two apart is also what makes
 * {@code payroll.approve} and {@code payroll.pay} different permissions mean something: who
 * computes does not disburse.
 */
public final class PayrollService {

    private final PayrollRepository repository;

    /**
     * Where the worked days, the absences and the hours come from.
     * <p>
     * It is an interface rather than the service itself so the payroll can be built and tested
     * without attendance at all - which is also the state a shop that has not started
     * recording it is in. {@link AttendanceSource#NONE} answers every month as unrecorded, and
     * an unrecorded month leaves the line exactly as the salary alone makes it.
     */
    private final AttendanceSource attendance;

    /**
     * Where a delegate's approved commission comes from, and where this run says it paid it.
     * {@link CommissionSource#NONE} is a shop that approves no commission runs: nothing is due,
     * and the commission box on a draft line is typed by hand as it always was.
     */
    private final CommissionSource commission;

    public PayrollService() {
        this(new JdbcPayrollRepository(), AttendanceSource.fromService(new AttendanceService()),
                new com.hamza.account.features.delegate.JdbcPayrollCommissionSource());
    }

    public PayrollService(PayrollRepository repository) {
        this(repository, AttendanceSource.NONE);
    }

    public PayrollService(PayrollRepository repository, AttendanceSource attendance) {
        this(repository, attendance, CommissionSource.NONE);
    }

    public PayrollService(PayrollRepository repository, AttendanceSource attendance,
                          CommissionSource commission) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.attendance = Objects.requireNonNull(attendance, "attendance");
        this.commission = Objects.requireNonNull(commission, "commission");
    }

    // ---- reading ---------------------------------------------------------------------------

    public List<PayrollRun> recentRuns(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_SHOW);
        return repository.recentRuns(Math.max(1, limit));
    }

    public Optional<PayrollRun> findRun(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_SHOW);
        return repository.findRun(runId);
    }

    public Optional<PayrollRun> findRunForPeriod(PayrollPeriod period) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_SHOW);
        return repository.findRunForPeriod(period);
    }

    public List<PayrollLine> linesOf(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_SHOW);
        return repository.linesOf(runId);
    }

    /**
     * What the run would look like if it were built now, without writing anything.
     * <p>
     * The screen shows this before a draft exists, so the month can be looked at before it is
     * committed to. It is the same calculation {@link #createDraft} persists, from the same
     * inputs, so the preview and the draft cannot disagree.
     */
    public List<PayrollCalculation> preview(PayrollPeriod period) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_SHOW);
        List<PayrollCalculation> lines = new ArrayList<>();
        for (PayrollInput input : repository.candidatesFor(period)) {
            lines.add(PayrollCalculator.calculate(period, withAttendance(period, input)));
        }
        return lines;
    }

    // ---- writing ---------------------------------------------------------------------------

    /**
     * Creates the month's draft and fills it from the dated salaries.
     *
     * @throws UserValidationException if the month already has a run - {@code UNIQUE(year,
     *                                 month)} would refuse it anyway, and a message naming the
     *                                 reason is better than a constraint violation
     */
    public int createDraft(PayrollPeriod period, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_CREATE);
        if (repository.findRunForPeriod(period).isPresent()) {
            throw new UserValidationException("payroll.error.period.exists");
        }
        return TransactionTemplate.execute(() -> {
            int runId = repository.insertRun(period, notes, currentUserId());
            fill(runId, period);
            return runId;
        });
    }

    /**
     * Throws the lines away and builds them again from the current salaries.
     * <p>
     * A draft is meant to be rebuilt: somebody records a raise or a new employee halfway
     * through preparing the month. It is refused once the run is approved, by this check and
     * again by the trigger - the second is what protects against a caller that is not this
     * service.
     */
    public int rebuildDraft(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_CREATE);
        PayrollRun run = requireRun(runId);
        requireEditable(run);
        return TransactionTemplate.execute(() -> {
            repository.deleteLines(runId);
            return fill(runId, run.period());
        });
    }

    /**
     * Corrects one line of a draft, and <b>recalculates it rather than storing what it was told</b>.
     * <p>
     * The screen types an absence, a deduction, a commission; the basic and the net follow from
     * them through {@link PayrollCalculator}, the same way they did when the draft was built.
     * Letting a screen write a net it computed itself would be a second definition of a month's
     * pay - which is the defect this whole area exists to avoid.
     *
     * @param edit the figures a person may type; everything else comes from the stored line
     */
    public int updateLine(int runId, PayrollLineEdit edit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_CREATE);
        PayrollRun run = requireRun(runId);
        requireEditable(run);

        PayrollLine stored = repository.linesOf(runId).stream()
                .filter(line -> line.id() == edit.lineId())
                .findFirst()
                .orElseThrow(() -> new UserValidationException("payroll.error.line.missing"));

        PayrollInput input = new PayrollInput(stored.employeeId(), stored.employeeName(),
                stored.salaryKind(), stored.rate(), null, null,
                edit.absenceDays(), edit.workedDays(), edit.workedHours(),
                stored.allowances(), edit.commission(), edit.deductions(),
                stored.advancesOutstanding());

        PayrollCalculation line = PayrollCalculator.calculate(run.period(), input);
        return repository.updateLine(runId, edit.lineId(), line, input, edit.notes());
    }

    /**
     * Approves the run: freezes it and writes it into the ledgers.
     * <p>
     * The status move carries the status the caller read ({@code AND status = ?}), so two
     * people approving at once produce one approval and one refusal rather than two sets of
     * ledger rows. Everything is in one transaction: a run that is approved has its entries,
     * and a run whose entries failed is not approved.
     */
    public int approve(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_APPROVE);
        PayrollRun run = requireRun(runId);
        if (!run.status().mayMoveTo(PayrollRunStatus.APPROVED)) {
            throw new UserValidationException("payroll.error.not.draft");
        }
        List<PayrollLine> lines = repository.linesOf(runId);
        if (lines.isEmpty()) {
            throw new UserValidationException("payroll.error.empty");
        }
        return TransactionTemplate.execute(() -> approveWithin(runId, run, lines));
    }

    /**
     * The move and the ledger rows, as one unit - called only from inside a transaction.
     * <p>
     * It is a method of its own so the decisions it makes can be tested without a database:
     * which rows approval writes, with which kinds, dated when, and that nothing is written
     * when the status move loses its race. The <b>transaction</b> itself - that a failure
     * halfway leaves the run a draft with no rows - is not provable from here, and is proven
     * by {@code PayrollDatabaseAcceptanceTest} against MySQL instead.
     */
    int approveWithin(int runId, PayrollRun run, List<PayrollLine> lines) throws DaoException {
        int moved = repository.moveStatus(runId, PayrollRunStatus.DRAFT,
                PayrollRunStatus.APPROVED, currentUserId());
        if (moved != 1) {
            throw new UserValidationException("payroll.error.not.draft");
        }
        settleCommission(runId, run, lines);
        writeLedger(runId, run, lines);
        return moved;
    }

    /**
     * Records that an approved run has been paid.
     * <p>
     * It does not move money: the expense rows are written by {@code EmployeePaymentService},
     * one per employee, before this is called. What this does is close the run so nothing
     * pays it twice.
     */
    public int markPaid(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_PAY);
        PayrollRun run = requireRun(runId);
        if (!run.status().mayMoveTo(PayrollRunStatus.PAID)) {
            throw new UserValidationException("payroll.error.not.approved");
        }
        int moved = repository.moveStatus(runId, PayrollRunStatus.APPROVED,
                PayrollRunStatus.PAID, currentUserId());
        if (moved != 1) {
            throw new UserValidationException("payroll.error.not.approved");
        }
        return moved;
    }

    /**
     * Abandons a draft.
     * <p>
     * Only a draft, and the reason is not tidiness: an approved run has written entitlements
     * into people's ledgers, and cancelling it would leave those rows belonging to a run that
     * says it never happened.
     */
    public int cancelDraft(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_CREATE);
        PayrollRun run = requireRun(runId);
        if (!run.status().mayMoveTo(PayrollRunStatus.CANCELLED)) {
            throw new UserValidationException("payroll.error.not.draft");
        }
        int moved = repository.moveStatus(runId, PayrollRunStatus.DRAFT,
                PayrollRunStatus.CANCELLED, currentUserId());
        if (moved != 1) {
            throw new UserValidationException("payroll.error.not.draft");
        }
        return moved;
    }

    /** Deletes a draft outright, lines and all. Refused for anything else, twice over. */
    public int deleteDraft(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.PAYROLL_CREATE);
        PayrollRun run = requireRun(runId);
        requireEditable(run);
        if (repository.countRunPayments(runId) > 0) {
            throw new UserValidationException("payroll.error.paid.rows");
        }
        return TransactionTemplate.execute(() -> {
            repository.deleteRunLedgerEntries(runId);
            return repository.deleteDraftRun(runId);
        });
    }

    // ---- the parts ---------------------------------------------------------------------------

    /**
     * Folds the month's attendance into a candidate.
     * <p>
     * A month with nothing recorded returns the input untouched, so a shop that does not keep
     * attendance gets exactly what it got before phase D: a monthly salary, whole. That is the
     * difference that matters - attendance <b>adds</b> a deduction where days were recorded,
     * and never turns an unkept grid into a month of absences.
     */
    private PayrollInput withAttendance(PayrollPeriod period, PayrollInput input)
            throws DaoException {
        AttendanceSummary summary = attendance.summaryFor(input.employeeId(),
                period.firstDay(), period.lastDay());
        if (summary.isEmpty()) {
            return input;
        }
        return new PayrollInput(input.employeeId(), input.employeeName(), input.salaryKind(),
                input.rate(), input.hiredOn(), input.endedOn(),
                summary.absenceDays(), summary.workedDays(), summary.workedHours(),
                input.allowances(), input.commission(), input.manualDeductions(),
                input.advancesOutstanding());
    }

    /**
     * Puts a delegate's approved commission on his draft line, in place of typing it.
     * <p>
     * Nothing due leaves the input untouched - which is every employee of a shop that approves
     * no commission runs, so this changes nobody's payroll the day it is installed.
     */
    private PayrollInput withCommission(PayrollPeriod period, PayrollInput input) throws DaoException {
        BigDecimal due = commission.dueFor(input.employeeId(), period);
        if (due == null || due.signum() <= 0) {
            return input;
        }
        return new PayrollInput(input.employeeId(), input.employeeName(), input.salaryKind(),
                input.rate(), input.hiredOn(), input.endedOn(),
                input.absenceDays(), input.workedDays(), input.workedHours(),
                input.allowances(), due, input.manualDeductions(),
                input.advancesOutstanding());
    }

    /**
     * Marks the commission this run pays as paid, so the commission screen cannot post it to
     * the same account a second time. Called inside the approval's transaction.
     * <p>
     * <b>The line has to say what is due, to the piaster.</b> The {@code ENTITLEMENT} this run
     * is about to write is built from the line, while what gets marked as paid is the approved
     * commission - so a draft built before the commission was approved, or a box somebody typed
     * over, would mark 500 as paid and pay 0, or the other way about. It is refused instead, and
     * the sentence says how to put it right: build the draft again.
     * <p>
     * An employee with nothing due is not looked at: a hand-typed commission there is what it
     * always was.
     */
    private void settleCommission(int runId, PayrollRun run, List<PayrollLine> lines) throws DaoException {
        for (PayrollLine line : lines) {
            BigDecimal due = commission.dueFor(line.employeeId(), run.period());
            if (due == null || due.signum() <= 0) {
                continue;
            }
            if (due.compareTo(line.commission()) != 0) {
                throw new UserValidationException("payroll.error.commission.differs");
            }
            commission.paidBy(runId, line.employeeId(), run.period(), currentUserId());
        }
    }

    private int fill(int runId, PayrollPeriod period) throws DaoException {
        int written = 0;
        for (PayrollInput candidate : repository.candidatesFor(period)) {
            PayrollInput input = withCommission(period, withAttendance(period, candidate));
            PayrollCalculation line = PayrollCalculator.calculate(period, input);
            if (line.isEmpty()) {
                // Somebody who left before the month began, or a commission employee with
                // nothing approved: a row of zeroes is not an entitlement and would only
                // produce a payslip saying nothing.
                continue;
            }
            repository.insertLine(runId, line, input, currentUserId());
            written++;
        }
        return written;
    }

    private void writeLedger(int runId, PayrollRun run, List<PayrollLine> lines)
            throws DaoException {
        String note = "payroll " + run.period();
        for (PayrollLine line : lines) {
            BigDecimal earned = line.earned();
            if (earned.signum() > 0) {
                repository.insertRunLedgerEntry(line.employeeId(), run.period().lastDay(),
                        EmployeeEntryKind.ENTITLEMENT.name(), earned, note, runId, currentUserId());
            }
            BigDecimal deducted = line.totalDeductions();
            if (deducted.signum() > 0) {
                repository.insertRunLedgerEntry(line.employeeId(), run.period().lastDay(),
                        EmployeeEntryKind.DEDUCTION.name(), deducted, note, runId, currentUserId());
            }
        }
    }

    private PayrollRun requireRun(int runId) throws DaoException {
        return repository.findRun(runId)
                .orElseThrow(() -> new UserValidationException("payroll.error.missing"));
    }

    private static void requireEditable(PayrollRun run) throws UserValidationException {
        if (!run.isEditable()) {
            throw new UserValidationException("payroll.error.not.draft");
        }
    }

    private static int currentUserId() {
        return CurrentUser.getOrNull() == null ? 1 : CurrentUser.get().getId();
    }
}
