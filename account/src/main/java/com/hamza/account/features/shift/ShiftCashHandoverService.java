package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Business boundary for optional, two-person cash handover after shift close. */
public final class ShiftCashHandoverService {
    private final ShiftCashHandoverRepository repository;
    private final DaoFactory daoFactory;
    private final UserSessionContext session;
    private final Clock clock;

    public ShiftCashHandoverService(ShiftCashHandoverRepository repository, DaoFactory daoFactory,
                                    UserSessionContext session, Clock clock) {
        this.repository = repository;
        this.daoFactory = daoFactory;
        this.session = session;
        this.clock = clock;
    }

    public List<ShiftCashHandoverPolicy> policies() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        return repository.loadPolicies();
    }

    public void savePolicy(int sourceTreasuryId, int targetTreasuryId, BigDecimal retainedFloat,
                           boolean enabled) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_POLICY_MANAGE);
        if (retainedFloat == null) {
            throw new UserValidationException(message("user.shift.handover.error.float"));
        }
        BigDecimal normalizedFloat = MoneyMath.money(retainedFloat);
        if (sourceTreasuryId <= 0 || targetTreasuryId <= 0 || sourceTreasuryId == targetTreasuryId) {
            throw new UserValidationException(message("user.shift.handover.error.treasury"));
        }
        if (normalizedFloat.signum() < 0) {
            throw new UserValidationException(message("user.shift.handover.error.float"));
        }
        int actor = requireActor();
        TransactionTemplate.execute(() -> {
            var source = daoFactory.treasuryCurrentBalanceDao().getDataById(sourceTreasuryId);
            var target = daoFactory.treasuryCurrentBalanceDao().getDataById(targetTreasuryId);
            if (source == null || target == null) {
                throw new UserValidationException(message("user.shift.handover.error.treasury"));
            }
            repository.savePolicy(sourceTreasuryId, targetTreasuryId, normalizedFloat, enabled, actor);
            return null;
        });
    }

    public List<ShiftCashHandover> pending() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        return repository.loadPending();
    }

    /**
     * The refusal {@link #settleCloseVariance} would make, asked before anything is written.
     * <p>
     * The settlement posts a dated cash movement, so a locked period refuses it - and that
     * refusal used to arrive from the far side of the close: the shift row was already
     * updated and its immutable snapshot already appended when the lock threw and rolled the
     * lot back. The cashier read a message about a treasury period while trying to close a
     * drawer. Asked here it is an ordinary refusal, before the first write.
     */
    public void requireSettlementAllowed(BigDecimal difference, LocalDate settlementDate)
            throws DaoException {
        if (difference == null || MoneyMath.money(difference).signum() == 0) return;
        PeriodLock.require(settlementDate, PeriodLockRegistry.TREASURY_DEPOSIT.label());
    }

    /**
     * Brings the till's book balance to what the cashier actually counted.
     * <p>
     * Called inside the close transaction, after the immutable snapshot was stored and
     * <em>before</em> {@link #requestForClosedShift}. It is deliberately independent of the
     * handover policy: reconciling a till is what {@code RECONCILE} tracking means, and a
     * branch that never hands its cash on to a safe still has to answer for its own drawer.
     * Gating it on a handover policy left such a treasury carrying its shortage for ever.
     * <p>
     * A treasury that is not on {@code RECONCILE} closes at its expected balance, so the
     * difference is zero and this does nothing - the mode needs no second test here.
     */
    public void settleCloseVariance(int shiftId, int treasuryId, BigDecimal expectedBalance,
                                    BigDecimal actualBalance, int actorUserId,
                                    LocalDateTime settledAt) throws DaoException {
        if (expectedBalance == null || actualBalance == null || settledAt == null) {
            throw new IllegalArgumentException("Expected balance, close balance and settle time are required");
        }
        reconcileVariance(shiftId, treasuryId, MoneyMath.money(expectedBalance),
                MoneyMath.money(actualBalance), actorUserId, settledAt);
    }

    /**
     * Records the cashier's declaration that the drawer is ready to hand over.
     * <p>
     * A no-op unless the source treasury has an enabled handover policy and holds more than
     * its retained float - the insert selects from the policy row, so no policy means no row.
     */
    public boolean requestForClosedShift(int shiftId, int sourceTreasuryId,
                                         BigDecimal actualBalance, int cashierUserId,
                                         LocalDateTime requestedAt) throws DaoException {
        if (actualBalance == null || requestedAt == null) {
            throw new IllegalArgumentException("Close balance and request time are required");
        }
        return repository.appendForClosedShift(shiftId, sourceTreasuryId,
                MoneyMath.money(actualBalance), cashierUserId, requestedAt) == 1;
    }

    public void requireTreasuryReadyForOpen(int treasuryId) throws DaoException {
        if (repository.hasBlockingPendingHandover(treasuryId)) {
            throw new BusinessRuleException(message("user.shift.handover.error.open.blocked"));
        }
    }

    public void approveOpenOverride(long handoverId, String reason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        String normalizedReason = clean(reason);
        if (normalizedReason == null) {
            throw new UserValidationException(message("user.shift.handover.override.reason.required"));
        }
        int actor = requireActor();
        TransactionTemplate.execute(() -> {
            ShiftCashHandover handover = repository.findForUpdate(handoverId);
            if (handover == null || !handover.pending()) {
                throw new BusinessRuleException(message("user.shift.handover.error.not.pending"));
            }
            if (!handover.blocksOpening()) {
                throw new BusinessRuleException(message("user.shift.handover.error.override.exists"));
            }
            if (handover.handedByUserId() == actor) {
                throw new BusinessRuleException(message("user.shift.handover.error.second.user"));
            }
            repository.insertOpenOverride(handover.id(), actor, normalizedReason,
                    LocalDateTime.now(clock));
            return null;
        });
    }

    public int receive(long handoverId, String note) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        int actor = requireActor();
        LocalDate today = LocalDate.now(clock);
        PeriodLock.require(today, PeriodLockRegistry.TREASURY_TRANSFER.label());
        return TransactionTemplate.execute(() -> {
            ShiftCashHandover handover = repository.findForUpdate(handoverId);
            if (handover == null || !handover.pending()) {
                throw new BusinessRuleException(message("user.shift.handover.error.not.pending"));
            }
            if (handover.handedByUserId() == actor) {
                throw new BusinessRuleException(message("user.shift.handover.error.second.user"));
            }

            var source = daoFactory.treasuryCurrentBalanceDao()
                    .lockAndRead(handover.sourceTreasuryId());
            var target = daoFactory.treasuryCurrentBalanceDao()
                    .getDataById(handover.targetTreasuryId());
            if (source == null || target == null) {
                throw new BusinessRuleException(message("treasury.error.not.found"));
            }
            TreasuryTransferService.requireEnough(source, handover.handoverAmount());

            // This transfer happens after the immutable close snapshot.  It is deliberately
            // not attributed to either shift: doing so would create a post-close ledger entry.
            int transferId = daoFactory.treasuryTransferDao().insertReturningId(
                    new TreasuryTransferCommand(handover.sourceTreasuryId(),
                            handover.targetTreasuryId(), handover.handoverAmount(), today,
                            "shift-handover:" + handover.id(), actor), null, null);
            if (repository.insertReceipt(handover.id(), actor, LocalDateTime.now(clock),
                    transferId, clean(note)) != 1) {
                throw new BusinessRuleException(message("user.shift.handover.error.not.pending"));
            }
            return transferId;
        });
    }

    private void reconcileVariance(int shiftId, int treasuryId, BigDecimal expected,
                                   BigDecimal actual, int actorUserId,
                                   LocalDateTime adjustedAt) throws DaoException {
        BigDecimal difference = MoneyMath.subtract(actual, expected);
        if (difference.signum() == 0) return;

        LocalDate date = adjustedAt.toLocalDate();
        PeriodLock.require(date, PeriodLockRegistry.TREASURY_DEPOSIT.label());
        var treasury = daoFactory.treasuryCurrentBalanceDao().lockAndRead(treasuryId);
        if (treasury == null) {
            throw new BusinessRuleException(message("treasury.error.not.found"));
        }
        BigDecimal amount = difference.abs();
        CashDirection direction = difference.signum() > 0
                ? CashDirection.DEPOSIT : CashDirection.WITHDRAWAL;
        if (direction.leavesTheTreasury()) {
            TreasuryTransferService.requireEnough(treasury, amount);
        }
        int movementId = daoFactory.cashMovementDao().insertReturningId(
                new CashMovementCommand(treasuryId, direction, CashCategory.NORMAL, amount, date,
                        message("user.shift.variance.movement", shiftId),
                        message("user.shift.variance.description", expected, actual), actorUserId),
                null);
        repository.appendVarianceAdjustment(shiftId, treasuryId, expected, actual,
                difference, movementId, actorUserId, adjustedAt);
    }

    private int requireActor() throws DaoException {
        if (session == null || !session.isSignedIn()) {
            throw new BusinessRuleException(message("user.shift.assignment.error.login.required"));
        }
        return session.currentUserId();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
