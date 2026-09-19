package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The monthly commission run: preview it, approve it, cancel it, post it.
 *
 * <p><b>Approval is what freezes a month.</b> Before it, the figure is the performance report's
 * preview - computed when read, from the month and the rule as they stand. After it, the figure
 * is a row that nothing may change: a rate amended in March does not move January, which is the
 * defect (ع-١٠) this exists to end.
 *
 * <p><b>A month is approved once it is over.</b> A run of a month still in progress would freeze
 * a figure that tomorrow's invoices contradict.
 *
 * <p>Posting to the delegates' accounts is one of two roads a commission reaches a ledger by; the
 * payroll is the other ({@link JdbcPayrollCommissionSource}). {@code commission_posting}'s primary
 * key is what keeps a line from taking both - this class does not have to be right about it, only
 * to say so in a sentence when the database refuses.
 */
public final class CommissionRunService {

    private final CommissionRunRepository runs;
    private final DelegateActivityRepository activity;
    private final CommissionRuleRepository rules;
    private final Supplier<LocalDate> today;

    public CommissionRunService() {
        this(new JdbcCommissionRunRepository(), new JdbcDelegateActivityRepository(),
                new JdbcCommissionRuleRepository(), LocalDate::now);
    }

    public CommissionRunService(CommissionRunRepository runs, DelegateActivityRepository activity,
                                CommissionRuleRepository rules, Supplier<LocalDate> today) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.today = Objects.requireNonNull(today, "today");
    }

    // ---- reading ---------------------------------------------------------------------------

    public List<CommissionRun> runs() throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        return runs.runs();
    }

    public List<CommissionLine> linesOf(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        return runs.linesOf(runId);
    }

    /**
     * What approving the month now would write: a line for every delegate with a rule in force on
     * its first day, whatever he earned - nothing included. A delegate with no rule gets no line.
     * It writes nothing.
     */
    public List<CommissionLine> preview(YearMonth month) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_SHOW);
        return compute(month);
    }

    // ---- approving -------------------------------------------------------------------------

    public int approve(YearMonth month, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_RUN_CREATE);
        Objects.requireNonNull(month, "month");
        if (!today.get().isAfter(month.atEndOfMonth())) {
            throw new UserValidationException("commission.error.run.month.open");
        }
        if (runs.activeRunId(month).isPresent()) {
            throw new UserValidationException("commission.error.run.exists");
        }
        List<CommissionLine> lines = compute(month);
        if (lines.isEmpty()) {
            throw new UserValidationException("commission.error.run.empty");
        }
        return TransactionTemplate.execute(() -> approveWithin(month, notes, lines));
    }

    /**
     * The run and its lines, as one unit - called only from inside a transaction. A method of its
     * own so what it writes can be tested without a database; that a failure halfway leaves
     * nothing is {@code CommissionRunDatabaseAcceptanceTest}'s to prove.
     */
    int approveWithin(YearMonth month, String notes, List<CommissionLine> lines) throws DaoException {
        int runId;
        try {
            runId = runs.insertRun(month, blankToNull(notes), currentUserId());
        } catch (DaoException raced) {
            // The pre-check is a courtesy; the unique key on active_key is the decision. Two tills
            // approving at once both pass the check, and the second one arrives here.
            if (runs.activeRunId(month).isPresent()) {
                throw new UserValidationException("commission.error.run.exists");
            }
            throw raced;
        }
        for (CommissionLine line : lines) {
            runs.insertLine(runId, line);
        }
        return runId;
    }

    // ---- cancelling ------------------------------------------------------------------------

    /**
     * Withdraws an approved run so the month can be approved again - the correction for a run
     * approved too early or over a wrong rule. Only while nothing of it has been posted.
     */
    public int cancel(int runId, String reason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_RUN_UPDATE);
        if (reason == null || reason.isBlank()) {
            throw new UserValidationException("commission.error.run.cancel.reason");
        }
        CommissionRun run = requireRun(runId);
        if (run.status() != CommissionRun.Status.APPROVED) {
            throw new UserValidationException("commission.error.run.not.approved");
        }
        if (!run.mayBeCancelled()) {
            throw new UserValidationException("commission.error.run.posted");
        }
        int moved = runs.cancel(runId, currentUserId(), reason.strip());
        if (moved != 1) {
            throw new UserValidationException("commission.error.run.not.approved");
        }
        return moved;
    }

    // ---- posting ---------------------------------------------------------------------------

    /**
     * Writes every line of the run not yet posted into its delegate's account as a
     * {@code COMMISSION} movement - no cash, so no shift and no till. For a shop that does not
     * run the payroll; one that does never needs this, because approving a payroll takes the same
     * lines.
     *
     * @return how many lines were posted; zero when the payroll, or an earlier press, took them all
     */
    public int postToAccounts(int runId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.COMMISSION_RUN_POST);
        CommissionRun run = requireRun(runId);
        if (run.status() != CommissionRun.Status.APPROVED) {
            throw new UserValidationException("commission.error.run.not.approved");
        }
        return TransactionTemplate.execute(() -> postWithin(run));
    }

    int postWithin(CommissionRun run) throws DaoException {
        int posted = 0;
        String note = "commission " + run.period();
        for (CommissionRunRepository.Unposted line : runs.unpostedLinesForUpdate(run.id())) {
            int entry = runs.insertLedgerCommission(line.employeeId(), run.period().atEndOfMonth(),
                    line.amount(), note, currentUserId());
            runs.insertAccountPosting(line.lineId(), entry, currentUserId());
            posted++;
        }
        return posted;
    }

    // ---- the parts -------------------------------------------------------------------------

    private List<CommissionLine> compute(YearMonth month) throws DaoException {
        LocalDate first = month.atDay(1);
        List<CommissionLine> lines = new ArrayList<>();
        for (DelegateActivity delegate : activity.activity(first, month.atEndOfMonth())) {
            Optional<CommissionRule> rule = rules.inForceOn(delegate.employeeId(), first);
            CommissionLine.preview(DelegatePerformanceRow.of(delegate, rule)).ifPresent(lines::add);
        }
        return lines;
    }

    private CommissionRun requireRun(int runId) throws DaoException {
        return runs.run(runId).orElseThrow(() -> new UserValidationException("commission.error.run.missing"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
