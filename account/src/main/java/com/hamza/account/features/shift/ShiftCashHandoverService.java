package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
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

    /** Called inside the same transaction that closes and snapshots the shift. */
    public boolean requestForClosedShift(int shiftId, int sourceTreasuryId,
                                         BigDecimal actualBalance, int cashierUserId,
                                         LocalDateTime requestedAt) throws DaoException {
        if (actualBalance == null || requestedAt == null) {
            throw new IllegalArgumentException("Close balance and request time are required");
        }
        return repository.appendForClosedShift(shiftId, sourceTreasuryId,
                MoneyMath.money(actualBalance), cashierUserId, requestedAt) == 1;
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
}
