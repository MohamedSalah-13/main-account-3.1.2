package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Defers a locked-date variance and posts it only on the supervisor's open date. */
public final class ShiftVarianceSettlementService {
    private final ShiftVarianceSettlementRepository repository;
    private final ShiftCashHandoverService cash;
    private final UserSessionContext session;
    private final Clock clock;

    public ShiftVarianceSettlementService(ShiftVarianceSettlementRepository repository,
                                          ShiftCashHandoverService cash,
                                          UserSessionContext session, Clock clock) {
        this.repository = repository;
        this.cash = cash;
        this.session = session;
        this.clock = clock;
    }

    public boolean settleOrDefer(int shiftId, int treasuryId, BigDecimal expected,
                                 BigDecimal actual, int actorUserId,
                                 LocalDateTime closeTime) throws DaoException {
        // Round each balance before subtracting, as reconcileVariance does. The row stores the
        // rounded balances and V61's CHECK demands difference = actual - expected of exactly
        // those; the rounded difference of raw balances can differ by a cent from that.
        BigDecimal expectedMoney = MoneyMath.money(expected);
        BigDecimal actualMoney = MoneyMath.money(actual);
        BigDecimal difference = MoneyMath.subtract(actualMoney, expectedMoney);
        if (difference.signum() == 0) return false;
        LocalDate lockedUntil = PeriodLock.lockedUntil();
        if (lockedUntil != null && !closeTime.toLocalDate().isAfter(lockedUntil)) {
            repository.append(shiftId, treasuryId, expectedMoney, actualMoney,
                    difference, closeTime.toLocalDate(), actorUserId, closeTime);
            return true;
        }
        cash.settleCloseVariance(shiftId, treasuryId, expected, actual, actorUserId, closeTime);
        return false;
    }

    public List<ShiftVarianceSettlement> pending() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        return repository.loadPending();
    }

    public int settle(long requestId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        int actor = requireActor();
        return TransactionTemplate.execute(() -> {
            ShiftVarianceSettlement request = repository.findPendingForUpdate(requestId);
            if (request == null) {
                throw new BusinessRuleException(message("user.shift.settlement.error.not.pending"));
            }
            LocalDateTime now = LocalDateTime.now(clock);
            cash.settleCloseVariance(request.shiftId(), request.treasuryId(),
                    request.expectedBalance(), request.actualBalance(), actor, now);
            return request.shiftId();
        });
    }

    public boolean isPendingForShift(int shiftId) throws DaoException {
        return repository.isPendingForShift(shiftId);
    }

    private int requireActor() throws DaoException {
        if (session == null || !session.isSignedIn()) {
            throw new BusinessRuleException(message("user.shift.assignment.error.login.required"));
        }
        return session.currentUserId();
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
