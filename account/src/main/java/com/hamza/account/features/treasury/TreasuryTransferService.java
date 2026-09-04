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

import java.math.BigDecimal;
import java.util.List;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.rbac.CurrentUser;

/**
 * Moves money from one treasury to another.
 * <p>
 * The table, the view over it and the rules protecting it have all existed since the
 * baseline; this is the first thing that writes a row. Which means the rules that
 * were only <i>declared</i> - {@code PeriodLockRegistry.TREASURY_TRANSFER} above all
 * - are enforced here for the first time, and enforcement is the point: a transfer
 * dated into a closed month rewrites a treasury balance already reported.
 * <p>
 * The order of the refusals is deliberate and is what {@code TreasuryTransferServiceTest}
 * pins: permission first, because a user who may not transfer should not learn from
 * the error message what the balances are; then the period; then the arithmetic; and
 * only then the balance, which is the only check that needs the database.
 */
public final class TreasuryTransferService {

    private final DaoFactory daoFactory;
    private final ShiftGate shiftGate;

    public TreasuryTransferService(DaoFactory daoFactory) {
        this(daoFactory, daoFactory == null ? ShiftGate.disabled() : ShiftGate.jdbc(daoFactory.userShiftDao()));
    }

    TreasuryTransferService(DaoFactory daoFactory, ShiftGate shiftGate) {
        this.daoFactory = daoFactory;
        this.shiftGate = shiftGate;
    }

    public int transfer(TreasuryTransferCommand command) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        PeriodLock.require(command.transferDate(), PeriodLockRegistry.TREASURY_TRANSFER.label());

        if (command.fromTreasuryId() == command.toTreasuryId()) {
            throw new BusinessRuleException(message("treasury.transfer.error.same"));
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new BusinessRuleException(message("treasury.transfer.error.amount"));
        }

        return TransactionTemplate.execute(() -> {
            var sourceShift = shiftGate.requireCashAction(
                    command.userId(), command.fromTreasuryId(), command.amount());
            var destinationShift = shiftGate.requireTreasuryAction(
                    command.toTreasuryId(), command.amount());
            // Locked and re-read inside the transaction: the balance is derived, so a
            // check taken before it would be a number nothing was holding still.
            TreasuryBalanceSummary source =
                    daoFactory.treasuryCurrentBalanceDao().lockAndRead(command.fromTreasuryId());
            if (source == null) {
                throw new BusinessRuleException(message("treasury.error.not.found"));
            }
            requireEnough(source, command.amount());

            TreasuryBalanceSummary destination =
                    daoFactory.treasuryCurrentBalanceDao().getDataById(command.toTreasuryId());
            if (destination == null) {
                throw new BusinessRuleException(message("treasury.error.not.found"));
            }

            int id = daoFactory.treasuryTransferDao().insertReturningId(command,
                    sourceShift.isPresent() ? sourceShift.getAsInt() : null,
                    destinationShift.isPresent() ? destinationShift.getAsInt() : null);
            ShiftCashLedger ledger = ShiftCashLedger.jdbc();
            ledger.created(sourceShift, command.userId(),
                    ShiftCashEffect.outgoing(ShiftCashSource.TRANSFER_OUT, id,
                            command.fromTreasuryId(), sourceShift.isPresent() ? sourceShift.getAsInt() : null,
                            command.amount()));
            ledger.created(destinationShift, command.userId(),
                    ShiftCashEffect.incoming(ShiftCashSource.TRANSFER_IN, id,
                            command.toTreasuryId(), destinationShift.isPresent() ? destinationShift.getAsInt() : null,
                            command.amount()));
            return 1;
        });
    }

    /**
     * Undoes a transfer entirely - there is no partial reversal, the same as a
     * document delete elsewhere. Refused inside a closed period for the reason making
     * one is: both change a balance already reported.
     */
    public int delete(int transferId) throws DaoException {
        return delete(transferId, null);
    }

    public int delete(int transferId, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        PeriodLock.require(PeriodLockRegistry.TREASURY_TRANSFER, transferId);
        return TransactionTemplate.execute(() -> {
            List<ShiftCashEffect> effects = new JdbcShiftCashEffectReader().transfer(transferId);
            if (effects.isEmpty()) return 0;
            int actor = CurrentUser.get().getId();
            ShiftCashEffect outgoing = effects.get(0);
            ShiftCashEffect incoming = effects.get(1);
            var sourceShift = shiftGate.requireCashCorrection(actor, outgoing.treasuryId(),
                    outgoing.output(), outgoing.originalShiftId());
            var destinationShift = shiftGate.requireTreasuryCorrection(incoming.treasuryId(),
                    incoming.income(), incoming.originalShiftId());
            int rows = daoFactory.treasuryTransferDao().deleteById(transferId);
            if (rows == 1) {
                ShiftCashLedger ledger = ShiftCashLedger.jdbc();
                ledger.deleted(sourceShift, actor, outgoing, correctionReason);
                ledger.deleted(destinationShift, actor, incoming, correctionReason);
            }
            return rows;
        });
    }

    /** Recent history, for the screen that lets a transfer be found and undone. */
    public List<TreasuryTransfer> recent(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        return daoFactory.treasuryTransferDao().recent(limit);
    }

    public static void requireEnough(TreasuryBalanceSummary treasury, BigDecimal amount)
            throws BusinessRuleException {
        if (treasury.balance().compareTo(amount) < 0) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString(
                    "treasury.error.insufficient", treasury.name(), treasury.balance().toPlainString()));
        }
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
