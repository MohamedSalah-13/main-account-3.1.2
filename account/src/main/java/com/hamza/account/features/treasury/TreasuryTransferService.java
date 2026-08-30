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

    public TreasuryTransferService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
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

            return daoFactory.treasuryTransferDao().insert(command);
        });
    }

    /**
     * Undoes a transfer entirely - there is no partial reversal, the same as a
     * document delete elsewhere. Refused inside a closed period for the reason making
     * one is: both change a balance already reported.
     */
    public int delete(int transferId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        PeriodLock.require(PeriodLockRegistry.TREASURY_TRANSFER, transferId);
        return daoFactory.treasuryTransferDao().deleteById(transferId);
    }

    /** Recent history, for the screen that lets a transfer be found and undone. */
    public List<TreasuryTransfer> recent(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_TRANSFER);
        return daoFactory.treasuryTransferDao().recent(limit);
    }

    static void requireEnough(TreasuryBalanceSummary treasury, BigDecimal amount)
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
