package com.hamza.account.features.employee.payroll;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.employee.EmployeeEntryKind;
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

    public PayrollService() {
        this(new JdbcPayrollRepository());
    }

    public PayrollService(PayrollRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
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
            lines.add(PayrollCalculator.calculate(period, input));
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

    private int fill(int runId, PayrollPeriod period) throws DaoException {
        int written = 0;
        for (PayrollInput input : repository.candidatesFor(period)) {
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
