package com.hamza.account.features.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.WalletFee;
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
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.TreasuryBalancesChanged;

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
        // Arithmetic, so before the database: a negative fee would credit the source, and one
        // as large as the transfer is a figure typed into the wrong box.
        if (command.fee().signum() < 0
                || (command.fee().signum() > 0 && !WalletFee.isPlausible(command.amount(), command.fee()))) {
            throw new BusinessRuleException(message("treasury.transfer.error.fee"));
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
            // The source gives up the transfer and what it was charged for it.
            requireEnough(source, command.amount().add(command.fee()));

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
            if (command.fee().signum() > 0) {
                // An expense on the sending treasury, tied to this transfer (V68) so deleting the
                // transfer takes it - the rule a collection's wallet fee follows, for its reasons.
                new WalletFeeService().post(WalletFeeSource.transfer(id), command.fromTreasuryId(),
                        command.transferDate(), command.amount(), command.fee(), command.notes(), sourceShift);
            }
            ChangeAnnouncer.jdbc().announce(new TreasuryBalancesChanged());
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
                new WalletFeeService().removeFor(WalletFeeSource.transfer(transferId), correctionReason);
                ShiftCashLedger ledger = ShiftCashLedger.jdbc();
                ledger.deleted(sourceShift, actor, outgoing, correctionReason);
                ledger.deleted(destinationShift, actor, incoming, correctionReason);
                ChangeAnnouncer.jdbc().announce(new TreasuryBalancesChanged());
            }
            return rows;
        });
    }

    /** One page of the period's transfers, with the totals of everything the filter matches. */
    public TreasuryHistoryPage<TreasuryTransfer> history(TreasuryHistoryFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        return TreasuryHistoryPage.of(daoFactory.treasuryTransferDao().page(filter),
                daoFactory.treasuryTransferDao().totals(filter), filter);
    }

    /** The whole filtered set for paper or a file - what is printed is never just the page on screen. */
    public TreasuryHistoryPage<TreasuryTransfer> forPrint(TreasuryHistoryFilter filter) throws DaoException {
        return history(filter.forPrint());
    }

    /**
     * The movement as the database holds it now, for its voucher - read again rather than printed
     * from the list, so the paper says what is stored on the machine that prints it.
     */
    public TreasuryVoucherLayout.TransferVoucher forVoucher(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        TreasuryVoucherLayout.TransferVoucher stored = daoFactory.treasuryTransferDao().voucher(id);
        if (stored == null) {
            throw new BusinessRuleException(message("treasury.voucher.error.not.found"));
        }
        return stored;
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
