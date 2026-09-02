package com.hamza.account.features.treasury;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Expenses;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.account.model.domain.Treasury;
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
 * with {@code expenses.create} would stop a cashier collecting on a wallet at all.
 * <p>
 * <b>Posted on insert only.</b> Editing a payment does not touch its fee row: the fee
 * belongs to the transfer that actually happened, and recomputing it on every edit
 * would either double it or silently rewrite an expense somebody has already reported.
 * Correcting one means deleting the payment and entering it again.
 */
public final class WalletFeeService {

    private final DaoFactory daoFactory;

    public WalletFeeService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
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

        ExpensesDetails expense = new ExpensesDetails();
        expense.setLocalDate(date == null ? LocalDate.now() : date);
        expense.setAmount(fee.doubleValue());
        expense.setNotes(note);
        expense.setEmployees(new Employees(0));
        expense.setTreasuryModel(new Treasury(treasuryId));
        expense.setExpenses(new Expenses(feeHeadingId()));

        // Through the DAO rather than ExpensesDetailsService: the service guards on
        // expenses.create, and this is not the user entering an expense - see the class
        // comment. The DAO still applies the accounting period lock on its own.
        int id = daoFactory.expensesDetailsDao().insertReturningId(expense,
                shiftId != null && shiftId.isPresent() ? shiftId.getAsInt() : null);
        var effectiveShift = shiftId == null ? OptionalInt.empty() : shiftId;
        int actor = expense.getUsers().getId();
        ShiftCashLedger.jdbc().created(effectiveShift, actor,
                ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, id, treasuryId,
                        effectiveShift.isPresent() ? effectiveShift.getAsInt() : null, fee));
        return 1;
    }

    /**
     * The seeded {@code عمولات تحويل} heading, looked up by name rather than by a
     * number written into the code: {@code expenses.id} is not auto-increment, so
     * {@code V21} could not know in advance which id it would take.
     */
    private int feeHeadingId() throws DaoException {
        Expenses heading = daoFactory.expensesDao().getDataByString(WalletFee.EXPENSE_NAME);
        if (heading == null || heading.getId() <= 0) {
            throw new BusinessRuleException(message("treasury.fee.error.no.heading"));
        }
        return heading.getId();
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
