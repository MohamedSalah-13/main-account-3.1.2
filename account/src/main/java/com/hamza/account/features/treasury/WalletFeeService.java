package com.hamza.account.features.treasury;

import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.expense.ExpenseEntry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.JdbcExpenseHeadingRepository;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.WalletFee;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * Posts the e-wallet fee that goes with a movement of cash, as an expense on the same
 * treasury.
 * <p>
 * It writes nothing on its own account: it is called from inside the movement's own
 * transaction ({@code AccountCustomerService.save}, {@code AccountSupplierService.save},
 * {@code InvoiceSaveService}), so the movement and its fee commit together or not at all. A
 * fee posted without its payment would be an unexplained expense; a payment without its fee
 * would leave the treasury holding money the wallet kept.
 * <p>
 * <b>No permission of its own.</b> The fee is a consequence of a movement the user has
 * already been authorized to make, not an expense they chose to enter - guarding it
 * with {@code expenses.create} would stop a cashier collecting on a wallet at all. So it
 * writes through its own repository rather than {@code ExpenseService}, and applies the
 * period lock itself.
 * <p>
 * <b>A fee knows the movement it was paid for</b> ({@link WalletFeeSource}, V67), and that is
 * what makes the rest safe. It used to be an expense row with nothing on it saying whose:
 * deleting a collection left its fee standing, and the correction the plan prescribed - delete
 * the payment and enter it again - wrote the fee a second time. {@link #removeFor} goes with the
 * delete of the source, in the source's own transaction.
 * <p>
 * <b>A hand-entered payment's fee is posted on insert only.</b> The person typed it off the
 * wallet's receipt, and an edit of the payment must not silently rewrite a figure somebody
 * entered. <b>A document's fee is computed, so it follows the document</b>:
 * {@link #syncDocument} rewrites or removes it when the cash, the treasury or the date changes.
 */
public final class WalletFeeService {

    private final WalletFeeRepository repository;
    private final ExpenseHeadingRepository headingRepository;
    private final ShiftGate shiftGate;
    private final ShiftCashLedger ledger;
    private final ChangeAnnouncer announcer;

    public WalletFeeService() {
        this(new JdbcWalletFeeRepository(), new JdbcExpenseHeadingRepository(), ShiftGate.jdbc(),
                ShiftCashLedger.jdbc(), ChangeAnnouncer.jdbc());
    }

    WalletFeeService(WalletFeeRepository repository, ExpenseHeadingRepository headingRepository,
                     ShiftGate shiftGate, ShiftCashLedger ledger, ChangeAnnouncer announcer) {
        this.repository = repository;
        this.headingRepository = headingRepository;
        this.shiftGate = shiftGate;
        this.ledger = ledger;
        this.announcer = announcer;
    }

    /** What the screen should suggest for this treasury, before the user overrides it. */
    public BigDecimal suggestedFee(TreasuryBalanceSummary treasury, BigDecimal amount) {
        return treasury == null ? WalletFee.on(null, null)
                : WalletFee.on(amount, treasury.feePercent());
    }

    /**
     * Writes the fee of a new movement. A zero or missing fee writes nothing at all - the
     * ordinary case, since a cash drawer has no percentage.
     *
     * @return the rows written: 1 for a fee, 0 for none
     */
    public int post(WalletFeeSource source, int treasuryId, LocalDate date, BigDecimal amount,
                    BigDecimal fee, String note, OptionalInt shiftId) throws DaoException {
        if (fee == null || fee.signum() <= 0) {
            return 0;
        }
        if (!WalletFee.isPlausible(amount, fee)) {
            throw new BusinessRuleException(message("treasury.fee.error.too.large"));
        }

        LocalDate day = date == null ? LocalDate.now() : date;
        PeriodLock.require(day, PeriodLockRegistry.EXPENSE.label());
        BigDecimal stored = fee.setScale(2, RoundingMode.HALF_UP);

        OptionalInt effectiveShift = shiftId == null ? OptionalInt.empty() : shiftId;
        Integer attributed = effectiveShift.isPresent() ? effectiveShift.getAsInt() : null;
        int actor = currentUserId();
        int id = repository.insert(source, feeHeadingId(), day, stored, clean(note), treasuryId, actor,
                attributed);
        ledger.created(effectiveShift, actor,
                ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, id, treasuryId, attributed, stored));
        announcer.announce(new ExpensesChanged());
        return 1;
    }

    /**
     * Takes the fee away with the movement it was paid for. Called from inside the delete of
     * that movement, so the two go together or not at all.
     * <p>
     * The fee row answers to the expenses' period lock on its own date, and to the shift it was
     * filed under, exactly as deleting it from the expenses screen would - what is skipped is
     * only {@code expenses.delete}, for the reason the class gives: whoever may delete the
     * collection may delete its consequence.
     *
     * @return the rows deleted: 1 when the movement had a fee, 0 when it had none
     */
    public int removeFor(WalletFeeSource source, String correctionReason) throws DaoException {
        WalletFeeRepository.StoredFee stored = repository.lockFor(source);
        return stored == null ? 0 : remove(stored, correctionReason);
    }

    /**
     * Makes a document's fee say what the document now says: {@code percent} of {@code paid}, on
     * {@code treasuryId}, dated {@code date}. Written when there is none, rewritten when the cash,
     * the treasury or the date moved, removed when nothing is owed any more - a cash drawer, a
     * deferred document with nothing paid.
     * <p>
     * <b>A fee already posted is not re-rated.</b> When the cash, the treasury and the date are
     * all what they were, the row is left alone even if the treasury's percentage has changed
     * since: the wallet charged what it charged on the day, and re-opening an old invoice to fix
     * a note must not rewrite an expense of a month already reported.
     *
     * @param previous what the document held before this save, or {@code null} for a new one
     * @param percent  the treasury's {@code fee_percent}; {@code null} or zero means no fee
     */
    public void syncDocument(WalletFeeSource source, int treasuryId, LocalDate date, BigDecimal paid,
                             PreviousCash previous, BigDecimal percent, OptionalInt shiftId,
                             String correctionReason) throws DaoException {
        BigDecimal due = WalletFee.on(paid, percent);
        boolean cashMoved = previous == null || previous.treasuryId() != treasuryId
                || paid == null || previous.paid().compareTo(paid) != 0;
        WalletFeeRepository.StoredFee stored = previous == null ? null : repository.lockFor(source);
        if (stored == null) {
            // A document saved before V67 has no fee row, and one whose cash is where it was is not
            // charged retroactively because somebody corrected a note on it.
            if (cashMoved) {
                post(source, treasuryId, date, paid, due, WalletFee.EXPENSE_NAME, shiftId);
            }
            return;
        }
        if (!cashMoved && stored.date().equals(date)) {
            return;
        }
        if (due.signum() <= 0) {
            remove(stored, correctionReason);
            return;
        }

        PeriodLock.require(stored.date(), PeriodLockRegistry.EXPENSE.label());
        PeriodLock.require(date, PeriodLockRegistry.EXPENSE.label());
        int actor = currentUserId();
        OptionalInt oldShift = shiftGate.requireCashCorrection(actor, stored.treasuryId(), stored.amount(),
                stored.shiftId());
        OptionalInt newShift = shiftGate.requireCashCorrection(actor, treasuryId, due, stored.shiftId());
        if (repository.update(stored.expenseId(), date, due, treasuryId) == 1) {
            ledger.updated(oldShift, newShift, actor, effectOf(stored),
                    ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, stored.expenseId(), treasuryId, null, due),
                    correctionReason);
            announcer.announce(new ExpensesChanged());
        }
    }

    /** What a document held in cash, and where, before the save that is being written. */
    public record PreviousCash(BigDecimal paid, int treasuryId) {
        public PreviousCash {
            paid = paid == null ? BigDecimal.ZERO : paid;
        }
    }

    private int remove(WalletFeeRepository.StoredFee stored, String correctionReason) throws DaoException {
        PeriodLock.require(stored.date(), PeriodLockRegistry.EXPENSE.label());
        int actor = currentUserId();
        OptionalInt shift = shiftGate.requireCashCorrection(actor, stored.treasuryId(), stored.amount(),
                stored.shiftId());
        int rows = repository.delete(stored.expenseId());
        if (rows == 1) {
            ledger.deleted(shift, actor, effectOf(stored), correctionReason);
            announcer.announce(new ExpensesChanged());
        }
        return rows;
    }

    private static ShiftCashEffect effectOf(WalletFeeRepository.StoredFee stored) {
        return ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, stored.expenseId(), stored.treasuryId(),
                stored.shiftId(), stored.amount());
    }

    /** The notes column is VARCHAR(255), and the note may be built from a collection's own notes. */
    private static String clean(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String stripped = note.strip();
        return stripped.substring(0, Math.min(stripped.length(), ExpenseEntry.NOTES_MAX));
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

    /** No session is a refusal, not user 1: a fee filed under the administrator is one nobody entered. */
    private static int currentUserId() {
        return CurrentUser.get().getId();
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
