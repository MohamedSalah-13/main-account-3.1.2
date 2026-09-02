package com.hamza.account.features.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.List;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.rbac.CurrentUser;

/**
 * Puts cash into a treasury, and takes it out.
 * <p>
 * The other half of what was missing: {@code treasury_deposit_expenses} has been
 * summed by {@code treasury_balance} and reported per shift by {@code UserShiftDao}
 * since the baseline, over rows nothing could create.
 * <p>
 * A withdrawal is checked against the balance and a deposit is not - money can
 * always go in. That asymmetry is the only difference between the two directions
 * here; everything else about them is one code path, which is why
 * {@link CashDirection} carries the sign rather than the service branching on it.
 */
public final class TreasuryCashService {

    private final DaoFactory daoFactory;
    private final ShiftGate shiftGate;

    public TreasuryCashService(DaoFactory daoFactory) {
        this(daoFactory, daoFactory == null ? ShiftGate.disabled() : ShiftGate.jdbc(daoFactory.userShiftDao()));
    }

    TreasuryCashService(DaoFactory daoFactory, ShiftGate shiftGate) {
        this.daoFactory = daoFactory;
        this.shiftGate = shiftGate;
    }

    public int record(CashMovementCommand command) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_DEPOSIT);
        // The owner's own money needs the owner's own permission, on top of the
        // cashier's: whoever may move the till must not be able to call a shortage
        // "capital paid in" and make it disappear.
        if (category(command).isOwnerEquity()) {
            AuthorizationGuard.require(AppPermissions.TREASURY_CAPITAL);
        }
        PeriodLock.require(command.date(), PeriodLockRegistry.TREASURY_DEPOSIT.label());

        if (!category(command).allows(command.direction())) {
            throw new BusinessRuleException(message("treasury.cash.error.category.direction"));
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new BusinessRuleException(message("treasury.cash.error.amount"));
        }
        if (command.statement() == null || command.statement().isBlank()) {
            throw new BusinessRuleException(message("treasury.cash.error.statement"));
        }

        return TransactionTemplate.execute(() -> {
            var shiftId = shiftGate.requireCashAction(command.userId(), command.treasuryId(), command.amount());
            TreasuryBalanceSummary treasury = command.direction().leavesTheTreasury()
                    ? daoFactory.treasuryCurrentBalanceDao().lockAndRead(command.treasuryId())
                    : daoFactory.treasuryCurrentBalanceDao().getDataById(command.treasuryId());
            if (treasury == null) {
                throw new BusinessRuleException(message("treasury.error.not.found"));
            }
            if (command.direction().leavesTheTreasury()) {
                TreasuryTransferService.requireEnough(treasury, command.amount());
            }
            CashMovementCommand stored = withCategory(command);
            int id = daoFactory.cashMovementDao().insertReturningId(stored,
                    shiftId.isPresent() ? shiftId.getAsInt() : null);
            ShiftCashSource sourceType = command.direction() == CashDirection.DEPOSIT
                    ? ShiftCashSource.CASH_DEPOSIT : ShiftCashSource.CASH_WITHDRAWAL;
            ShiftCashEffect effect = command.direction() == CashDirection.DEPOSIT
                    ? ShiftCashEffect.incoming(sourceType, id, command.treasuryId(),
                        shiftId.isPresent() ? shiftId.getAsInt() : null, command.amount())
                    : ShiftCashEffect.outgoing(sourceType, id, command.treasuryId(),
                        shiftId.isPresent() ? shiftId.getAsInt() : null, command.amount());
            ShiftCashLedger.jdbc().created(shiftId, command.userId(), effect);
            return 1;
        });
    }

    /**
     * What the owner put in and took out over a period.
     * <p>
     * Read with the capital permission rather than the deposit one - it is a
     * statement of the owner's equity, not of the till.
     */
    public List<CashMovement> capitalMovements(LocalDate from, LocalDate to) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_CAPITAL);
        return daoFactory.cashMovementDao().capitalBetween(from, to);
    }

    /** A command built before this column existed - or by a screen that does not offer it - is ordinary cash. */
    private static CashCategory category(CashMovementCommand command) {
        return command.category() == null ? CashCategory.NORMAL : command.category();
    }

    private static CashMovementCommand withCategory(CashMovementCommand command) {
        return command.category() != null ? command : new CashMovementCommand(
                command.treasuryId(), command.direction(), CashCategory.NORMAL,
                command.amount(), command.date(), command.statement(),
                command.description(), command.userId());
    }

    /** Removes a movement entered by mistake; refused inside a closed period. */
    public int delete(int movementId) throws DaoException {
        return delete(movementId, null);
    }

    public int delete(int movementId, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_DEPOSIT);
        PeriodLock.require(PeriodLockRegistry.TREASURY_DEPOSIT, movementId);
        return TransactionTemplate.execute(() -> {
            ShiftCashEffect old = new JdbcShiftCashEffectReader().cash(movementId);
            if (old == null) return 0;
            int actor = CurrentUser.get().getId();
            var shift = shiftGate.requireCashCorrection(actor, old.treasuryId(),
                    old.income().add(old.output()).abs(), old.originalShiftId());
            int rows = daoFactory.cashMovementDao().deleteById(movementId);
            if (rows == 1) ShiftCashLedger.jdbc().deleted(shift, actor, old, correctionReason);
            return rows;
        });
    }

    public List<CashMovement> recent(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_DEPOSIT);
        return daoFactory.cashMovementDao().recent(limit);
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
