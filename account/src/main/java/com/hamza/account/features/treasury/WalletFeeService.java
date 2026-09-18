package com.hamza.account.features.treasury;

import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.expense.ExpenseEntry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.ExpenseRepository;
import com.hamza.account.features.expense.JdbcExpenseHeadingRepository;
import com.hamza.account.features.expense.JdbcExpenseRepository;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.WalletFee;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.OptionalInt;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;

/**
 * Posts the e-wallet fee that goes with a collection, as an expense on the same
 * treasury.
 * <p>
 * It writes nothing on its own account: it is called from inside the payment's own
 * transaction ({@code AccountCustomerService.save} / {@code AccountSupplierService.save}),
 * so the payment and its fee commit together or not at all. A fee posted without its
 * payment would be an unexplained expense; a payment without its fee would leave the
 * treasury holding money the wallet kept.
 * <p>
 * <b>No permission of its own.</b> The fee is a consequence of a collection the user
 * has already been authorized to make, not an expense they chose to enter - guarding it
 * with {@code expenses.create} would stop a cashier collecting on a wallet at all. So it
 * writes through the expense repository rather than {@code ExpenseService}, and applies the
 * period lock itself, which the old DAO used to do for it.
 * <p>
 * <b>Posted on insert only.</b> Editing a payment does not touch its fee row: the fee
 * belongs to the transfer that actually happened, and recomputing it on every edit
 * would either double it or silently rewrite an expense somebody has already reported.
 * Correcting one means deleting the payment and entering it again.
 */
public final class WalletFeeService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseHeadingRepository headingRepository;

    public WalletFeeService() {
        this(new JdbcExpenseRepository(), new JdbcExpenseHeadingRepository());
    }

    WalletFeeService(ExpenseRepository expenseRepository, ExpenseHeadingRepository headingRepository) {
        this.expenseRepository = expenseRepository;
        this.headingRepository = headingRepository;
    }

    /** What the screen should suggest for this treasury, before the user overrides it. */
    public BigDecimal suggestedFee(TreasuryBalanceSummary treasury, BigDecimal amount) {
        return treasury == null ? WalletFee.on(null, null)
                : WalletFee.on(amount, treasury.feePercent());
    }

    /**
     * Writes the fee. A zero or missing fee writes nothing at all - the ordinary case,
     * since a cash drawer has no percentage.
     *
     * @return the rows written: 1 for a fee, 0 for none
     */
    public int post(int treasuryId, LocalDate date, BigDecimal amount, BigDecimal fee, String note)
            throws DaoException {
        return post(treasuryId, date, amount, fee, note, OptionalInt.empty());
    }

    public int post(int treasuryId, LocalDate date, BigDecimal amount, BigDecimal fee, String note,
                    OptionalInt shiftId) throws DaoException {
        if (fee == null || fee.signum() <= 0) {
            return 0;
        }
        if (!WalletFee.isPlausible(amount, fee)) {
            throw new BusinessRuleException(message("treasury.fee.error.too.large"));
        }

        LocalDate day = date == null ? LocalDate.now() : date;
        PeriodLock.require(day, PeriodLockRegistry.EXPENSE.label());
        // The notes column is VARCHAR(255), and the note here is built from a collection's own notes.
        String clean = note == null || note.isBlank() ? null
                : note.strip().substring(0, Math.min(note.strip().length(), ExpenseEntry.NOTES_MAX));
        ExpenseEntry expense = new ExpenseEntry(0, day, feeHeadingId(), treasuryId,
                fee.setScale(2, java.math.RoundingMode.HALF_UP), null, null, clean);

        var effectiveShift = shiftId == null ? OptionalInt.empty() : shiftId;
        Integer attributed = effectiveShift.isPresent() ? effectiveShift.getAsInt() : null;
        int actor = currentUserId();
        int id = expenseRepository.insert(expense, null, attributed, actor, null);
        ShiftCashLedger.jdbc().created(effectiveShift, actor,
                ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, id, treasuryId, attributed, expense.amount()));
        ChangeAnnouncer.jdbc().announce(new ExpensesChanged());
        return 1;
    }

    /**
     * The heading a wallet fee is posted under, found by its key since V64.
     * <p>
     * It used to be found by the name {@link WalletFee#EXPENSE_NAME}, which was right only while no
     * screen could rename a heading - and the headings screen can. The key was written onto the row by
     * the migration, reading the name once, at the one moment it was certain.
     */
    private int feeHeadingId() throws DaoException {
        ExpenseHeading heading = headingRepository.bySystemKey(ExpenseHeading.WALLET_FEE);
        if (heading == null || heading.id() <= 0) {
            throw new BusinessRuleException(message("treasury.fee.error.no.heading"));
        }
        return heading.id();
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
