package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.TreasuryBalancesChanged;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserFacingException;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The one place an expense is read, written, corrected and removed.
 * <p>
 * <b>{@code expenses_details} stays the single table an expense lives in</b> - docs/expenses-plan.md
 * ق-٢. Nine readers depend on it being right today: the treasury balance, the shift cash journal, the
 * period lock, the profit and loss, the yearly report, both dashboards, the employee's account and the
 * payroll. A second, "modern" table beside it would be a second definition of an expense to reconcile
 * with the first in all nine.
 * <p>
 * Every write here passes the same four rules, in the order {@code TreasuryCashService} does:
 * permission, then the period lock, then the shift gate, then the write and its journal row - and the
 * announcement to the other machines inside the same transaction, so a refused save takes it back.
 * <p>
 * <b>Employees are paid through {@link #recordForEmployee}, and only there.</b> The expenses screen
 * cannot name an employee at all (ق-٥); {@code EmployeePaymentService} calls this with the employee
 * and records what the payment was for beside it, in its own transaction around this one.
 * <p>
 * <b>The shift gate is here and not in a writer class of its own.</b> {@code ShiftGateArchitectureTest}
 * and {@code AuthorizationArchitectureTest} read the classes named {@code *Service}; a writer named
 * anything else would have been invisible to both rules at once, which is the one shape a rule exists
 * to prevent.
 */
public final class ExpenseService {

    /** As elsewhere: an extract wide enough to print, narrow enough to hold. */
    public static final int PRINT_LIMIT = 10_000;

    private static final int PAYEE_SUGGESTIONS = 20;

    /** Where the stored cash effect of an expense is read from before it is changed. */
    @FunctionalInterface
    public interface CashEffects {
        /** Locks the row and answers what it moved, or {@code null} when it is gone. */
        ShiftCashEffect lockAndRead(int expenseId) throws DaoException;
    }

    /** The derived till balances, read through {@code treasury_current_balance} and nowhere else. */
    public interface TreasuryBalances {
        List<TreasuryBalanceSummary> active() throws DaoException;

        TreasuryBalanceSummary find(int treasuryId) throws DaoException;
    }

    private final ExpenseRepository repository;
    private final ExpenseHeadingRepository headings;
    private final ShiftGate shiftGate;
    private final ShiftCashLedger ledger;
    private final CashEffects cashEffects;
    private final TreasuryBalances treasuries;
    private final ChangeAnnouncer announcer;
    private final ExpenseTransactions transactions;

    public ExpenseService(DaoFactory daoFactory) {
        this(new JdbcExpenseRepository(), new JdbcExpenseHeadingRepository(),
                ShiftGate.jdbc(daoFactory.userShiftDao()), ShiftCashLedger.jdbc(),
                new JdbcShiftCashEffectReader()::expense, jdbcTreasuries(daoFactory), ChangeAnnouncer.jdbc(),
                ExpenseTransactions.jdbc());
    }

    public ExpenseService(ExpenseRepository repository, ExpenseHeadingRepository headings, ShiftGate shiftGate,
                          ShiftCashLedger ledger, CashEffects cashEffects, TreasuryBalances treasuries,
                          ChangeAnnouncer announcer, ExpenseTransactions transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.headings = Objects.requireNonNull(headings, "headings");
        this.shiftGate = Objects.requireNonNull(shiftGate, "shiftGate");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.cashEffects = Objects.requireNonNull(cashEffects, "cashEffects");
        this.treasuries = Objects.requireNonNull(treasuries, "treasuries");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    // ---- reading ------------------------------------------------------------------------

    /**
     * One page, with the figures for the whole filtered set and the period of equal length before it.
     * One row more than the page holds is fetched, and that row answers "is there another page".
     */
    public ExpensePage search(ExpenseFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        List<ExpenseRow> fetched = repository.search(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<ExpenseRow> rows = hasNext ? fetched.subList(0, filter.pageSize()) : fetched;
        return new ExpensePage(rows, summaryWithPrevious(filter), filter.page(), filter.page() > 0, hasNext,
                false);
    }

    /**
     * Every matching expense, for printing and for export - a query of its own with the same filter, so
     * the file is the list on screen, all of it, rather than the page that happened to be showing.
     */
    public ExpensePage forPrint(ExpenseFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        AuthorizationGuard.require(AppPermissions.EXPENSES_EXPORT);
        ExpenseFilter printable = filter.firstPageWithSize(PRINT_LIMIT);
        List<ExpenseRow> fetched = repository.search(printable);
        boolean truncated = fetched.size() > PRINT_LIMIT;
        List<ExpenseRow> rows = truncated ? fetched.subList(0, PRINT_LIMIT) : fetched;
        return new ExpensePage(rows, summaryWithPrevious(printable), 0, false, false, truncated);
    }

    /** One expense, read from the database - what an edit starts from, never a list row. */
    public ExpenseRow find(int expenseId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        return repository.find(expenseId);
    }

    /**
     * One expense for its printed voucher, read again from the database rather than taken off the list, so
     * the paper says what is stored. A voucher is a file that leaves the shop, so it asks the export
     * permission on top of viewing.
     */
    public ExpenseRow forVoucher(int expenseId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        AuthorizationGuard.require(AppPermissions.EXPENSES_EXPORT);
        return repository.find(expenseId);
    }

    public List<ExpenseUserOption> users() throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        return repository.users();
    }

    /** Names already typed into the payee box, for a suggestion list. Names, not figures - unguarded. */
    public List<String> payeeSuggestions(String prefix) throws DaoException {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return repository.payees(prefix, PAYEE_SUGGESTIONS);
    }

    /**
     * The tills an expense may be paid out of, with their balances.
     * <p>
     * A balance is a figure about the business, so it goes only to a reader who may see treasuries; for
     * anybody else the tills come back with their balance withheld. The warning below still works for
     * them - it answers "short or not", which the cashier holding the drawer already knows.
     */
    public List<TreasuryBalanceSummary> treasuries() throws DaoException {
        List<TreasuryBalanceSummary> active = treasuries.active();
        if (AuthorizationGuard.isGranted(AppPermissions.TREASURY_SHOW)) {
            return active;
        }
        return active.stream().map(ExpenseService::withoutFigures).toList();
    }

    /**
     * What paying {@code amount} out of this till would leave it short by - zero when it fits.
     * Decision م-١: the screen warns with this and the person paying decides; nothing here refuses.
     *
     * @param editingId the expense being corrected, whose own amount is given back when it stays on
     *                  this till, or 0 for a new one
     */
    public BigDecimal shortfall(int treasuryId, BigDecimal amount, int editingId) throws DaoException {
        AuthorizationGuard.require(editingId > 0 ? AppPermissions.EXPENSES_UPDATE : AppPermissions.EXPENSES_CREATE);
        TreasuryBalanceSummary treasury = treasuries.find(treasuryId);
        if (treasury == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal released = BigDecimal.ZERO;
        if (editingId > 0) {
            ExpenseRow stored = repository.find(editingId);
            if (stored != null && stored.treasuryId() == treasuryId) {
                released = stored.amount();
            }
        }
        return ExpenseBalanceCheck.shortfall(treasury.balance(), amount, released);
    }

    // ---- writing ------------------------------------------------------------------------

    /**
     * Records an expense entered on the expenses screen.
     *
     * @return the generated code
     */
    public int create(ExpenseEntry entry) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_CREATE);
        return transactions.execute(() -> record(entry, null));
    }

    /**
     * Records several expenses in one transaction: all of them, or none.
     * <p>
     * A refused line refuses the batch, and the refusal names the line. Saving the lines that passed
     * would leave a person holding a stack of receipts with no way to tell which of them are in.
     *
     * @return how many were written - always the size of the batch
     */
    public int createBatch(List<ExpenseEntry> entries) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_CREATE);
        if (entries == null || entries.isEmpty()) {
            throw new UserValidationException("expense.batch.error.empty");
        }
        List<ExpenseEntry> lines = List.copyOf(entries);
        return transactions.execute(() -> {
            for (int index = 0; index < lines.size(); index++) {
                try {
                    record(lines.get(index), null);
                } catch (DaoException refused) {
                    if (refused instanceof UserFacingException facing) {
                        throw new ExpenseBatchLineRefused(index + 1, facing.userMessage(), refused);
                    }
                    throw refused;
                }
            }
            return lines.size();
        });
    }

    /**
     * Records cash paid to an employee. The one caller is {@code EmployeePaymentService}, which asks
     * {@code employee.pay} on top of the {@code expenses.create} asked here - both are needed, because
     * the base act is creating an expense.
     *
     * @return the generated code, which the payment files its purpose under
     */
    public int recordForEmployee(ExpenseEntry entry, int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_CREATE);
        if (employeeId <= 0) {
            throw new UserValidationException("employee.error.account.employee");
        }
        return transactions.execute(() -> record(entry, employeeId));
    }

    /**
     * Corrects an expense.
     * <p>
     * The stored row decides what may change. The employee on it stays - the update statement does not
     * name the column - and so does the kind of heading: a salary is corrected under a heading employees
     * are paid under, and an electricity bill under one they are not.
     */
    public int update(ExpenseEntry entry, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_UPDATE);
        if (entry.isNew()) {
            throw new UserValidationException("expense.error.not.found");
        }
        PeriodLock.require(PeriodLockRegistry.EXPENSE, entry.id());
        PeriodLock.require(entry.date(), PeriodLockRegistry.EXPENSE.label());
        return transactions.execute(() -> {
            ExpenseRow stored = repository.find(entry.id());
            if (stored == null) {
                throw new UserValidationException("expense.error.not.found");
            }
            requireHeadingFor(entry, stored);

            ShiftCashEffect old = cashEffects.lockAndRead(entry.id());
            if (old == null) {
                throw new UserValidationException("expense.error.not.found");
            }
            int actor = currentUserId();
            OptionalInt oldShift = shiftGate.requireCashCorrection(actor, old.treasuryId(), old.output(),
                    old.originalShiftId());
            OptionalInt newShift = shiftGate.requireCashCorrection(actor, entry.treasuryId(), entry.amount(),
                    old.originalShiftId());
            int rows = repository.update(entry);
            if (rows == 1) {
                ledger.updated(oldShift, newShift, actor, old,
                        ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, entry.id(), entry.treasuryId(), null,
                                entry.amount()), correctionReason);
                announceChange();
            }
            return rows;
        });
    }

    /** Removes an expense entered by mistake; refused inside a closed period. */
    public int delete(int expenseId, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_DELETE);
        PeriodLock.require(PeriodLockRegistry.EXPENSE, expenseId);
        return transactions.execute(() -> {
            ShiftCashEffect old = cashEffects.lockAndRead(expenseId);
            if (old == null) {
                return 0;
            }
            int actor = currentUserId();
            OptionalInt shift = shiftGate.requireCashCorrection(actor, old.treasuryId(), old.output(),
                    old.originalShiftId());
            int rows = DeletionService.shared()
                    .delete(DeleteRegistry.EXPENSES_DETAILS, expenseId, repository::delete)
                    .rowsOrThrow();
            if (rows == 1) {
                ledger.deleted(shift, actor, old, correctionReason);
                announceChange();
            }
            return rows;
        });
    }

    // ---- internals ----------------------------------------------------------------------

    /**
     * The write behind every insert, inside a transaction the caller opened. Private, so the two rules
     * that read public methods see the guard on each entry point rather than a helper they cannot follow.
     */
    private int record(ExpenseEntry entry, Integer employeeId) throws DaoException {
        ExpenseHeading heading = headings.find(entry.headingId());
        if (employeeId == null) {
            ExpenseHeadingRules.requireUsableForExpense(heading);
        } else {
            ExpenseHeadingRules.requireUsableForEmployee(heading);
        }
        PeriodLock.require(entry.date(), PeriodLockRegistry.EXPENSE.label());

        int actor = currentUserId();
        OptionalInt shift = shiftGate.requireCashAction(actor, entry.treasuryId(), entry.amount());
        Integer shiftId = shift.isPresent() ? shift.getAsInt() : null;
        int id = repository.insert(entry, employeeId, shiftId, actor);
        ledger.created(shift, actor,
                ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, id, entry.treasuryId(), shiftId, entry.amount()));
        announceChange();
        return id;
    }

    /**
     * An edit keeps the kind of heading the row was paid under. A heading that was stopped since stays
     * acceptable for the row already on it - refusing would make every expense under a retired heading
     * uncorrectable - but a row may not be moved onto a stopped one.
     */
    private void requireHeadingFor(ExpenseEntry entry, ExpenseRow stored) throws DaoException {
        ExpenseHeading heading = headings.find(entry.headingId());
        if (heading == null) {
            throw new UserValidationException("expense.error.heading");
        }
        if (stored.paidToEmployee() != heading.employeePayment()) {
            throw new UserValidationException(stored.paidToEmployee()
                    ? "expense.error.heading.not.employee" : "expense.error.heading.employee");
        }
        if (!heading.active() && heading.id() != stored.headingId()) {
            throw new UserValidationException("expense.error.heading.stopped");
        }
    }

    private void announceChange() throws DaoException {
        announcer.announce(new ExpensesChanged());
        // The till is lighter, and its balance is derived: the treasury screens on the other machines
        // read a figure this write has just moved.
        announcer.announce(new TreasuryBalancesChanged());
    }

    private ExpenseSummary summaryWithPrevious(ExpenseFilter filter) throws DaoException {
        ExpenseSummary summary = repository.summarize(filter);
        ExpenseFilter previous = filter.previousPeriod();
        return previous == null ? summary : summary.withPreviousTotal(repository.summarize(previous).total());
    }

    private static TreasuryBalanceSummary withoutFigures(TreasuryBalanceSummary treasury) {
        return new TreasuryBalanceSummary(treasury.id(), treasury.name(), treasury.type(), treasury.active(),
                treasury.sortOrder(), treasury.feePercent(), null, null, null, null);
    }

    private static TreasuryBalances jdbcTreasuries(DaoFactory daoFactory) {
        return new TreasuryBalances() {
            @Override
            public List<TreasuryBalanceSummary> active() throws DaoException {
                return daoFactory.treasuryCurrentBalanceDao().loadActive();
            }

            @Override
            public TreasuryBalanceSummary find(int treasuryId) throws DaoException {
                return daoFactory.treasuryCurrentBalanceDao().getDataById(treasuryId);
            }
        };
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
