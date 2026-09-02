package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.shift.ShiftClosed;
import com.hamza.account.features.shift.ShiftCloseSnapshotWriter;
import com.hamza.account.features.shift.ShiftMode;
import com.hamza.account.features.shift.ShiftOpened;
import com.hamza.account.features.shift.ShiftPolicyService;
import com.hamza.account.features.shift.ShiftStatus;
import com.hamza.account.features.shift.ShiftTrackingMode;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Shift lifecycle and reconciliation rules. */
public final class UserShiftService {
    private final DaoFactory daoFactory;
    private final UserSessionContext session;
    private final ShiftPolicyService policies;
    private final EventBus events;
    private final Clock clock;

    /** Compatibility constructor used by tests that only exercise authorization. */
    public UserShiftService(DaoFactory daoFactory) {
        this(daoFactory, null, null, null, Clock.systemDefaultZone());
    }

    public UserShiftService(DaoFactory daoFactory, UserSessionContext session,
                            ShiftPolicyService policies, EventBus events, Clock clock) {
        this.daoFactory = daoFactory;
        this.session = session;
        this.policies = policies;
        this.events = events;
        this.clock = clock;
    }

    public int openShift(int userId, int treasuryId, BigDecimal openBalance, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_OPEN);
        requireCurrentUser(userId);
        validateOpen(userId, treasuryId, openBalance);

        int shiftId = TransactionTemplate.execute(() -> {
            daoFactory.userShiftDao().lockUserAndTreasury(userId, treasuryId);
            if (daoFactory.userShiftDao().hasOpenShift(userId)) {
                throw new BusinessRuleException(message("user.shift.msg.already.open"));
            }
            if (daoFactory.userShiftDao().hasOpenShiftForTreasury(treasuryId)) {
                throw new BusinessRuleException(message("user.shift.msg.treasury.busy"));
            }
            UserShift shift = new UserShift(userId, treasuryId);
            shift.setOpenTime(LocalDateTime.now(clock));
            shift.setOpenBalance(openBalance);
            shift.setOpen(true);
            shift.setStatus(ShiftStatus.OPEN);
            shift.setNotes(clean(notes));
            return daoFactory.userShiftDao().insertReturningId(shift);
        });
        if (events != null) events.publish(new ShiftOpened(shiftId, userId, treasuryId));
        return shiftId;
    }

    public int closeShift(int userId, BigDecimal closeBalance, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_CLOSE);
        requireCurrentUser(userId);
        return close(userId, closeBalance, notes, false, 0);
    }

    private int close(int userId, BigDecimal closeBalance, String notes, boolean forced, int requestedShiftId)
            throws DaoException {
        ShiftClosed closed = TransactionTemplate.execute(() -> closeTransactional(
                userId, closeBalance, notes, forced, requestedShiftId));
        if (events != null) events.publish(closed);
        return closed.shiftId();
    }

    private ShiftClosed closeTransactional(int userId, BigDecimal closeBalance, String notes,
                                           boolean forced, int requestedShiftId) throws DaoException {
        if (closeBalance == null || closeBalance.signum() < 0) {
            throw new UserValidationException(message("user.shift.msg.close.balance.negative"));
        }
        UserShift shift = requestedShiftId > 0
                ? daoFactory.userShiftDao().getOpenShiftByIdForUpdate(requestedShiftId)
                : daoFactory.userShiftDao().getOpenShiftByUserIdForUpdate(userId);
        if (shift == null || !shift.isOpen()) {
            throw new BusinessRuleException(message("user.shift.msg.no.open.shift"));
        }
        LocalDateTime closeTime = LocalDateTime.now(clock);
        ShiftSummary summary = daoFactory.userShiftDao().calculateShiftSummary(
                shift.getId(), shift.getUserId(), shift.getTreasuryId(), shift.getOpenTime(), closeTime);
        summary.setOpenBalance(shift.getOpenBalance());
        boolean reconcile = trackingMode(shift.getTreasuryId()) == ShiftTrackingMode.RECONCILE;
        BigDecimal effectiveCloseBalance = reconcile ? closeBalance : summary.getExpectedBalance();
        BigDecimal difference = summary.calculateDifference(effectiveCloseBalance);
        if (reconcile) validateVariance(difference, notes, forced);

        shift.setCloseTime(closeTime);
        shift.setCloseBalance(effectiveCloseBalance);
        shift.setOpen(false);
        shift.setStatus(forced ? ShiftStatus.FORCE_CLOSED : ShiftStatus.CLOSED);
        shift.setTotalSales(summary.getTotalSales());
        shift.setTotalSalesReturns(summary.getTotalSalesReturns());
        shift.setTotalExpenses(summary.getTotalExpenses());
        shift.setTotalDeposits(summary.getTotalDeposits());
        shift.setTotalWithdrawals(summary.getTotalWithdrawals());
        shift.setTotalCashIn(summary.getTotalIn());
        shift.setTotalCashOut(summary.getTotalOut());
        shift.setExpectedBalance(summary.getExpectedBalance());
        shift.setDifference(difference);
        shift.setInvoicesCount(summary.getInvoicesCount());
        appendCloseNotes(shift, notes, forced);

        int rows = daoFactory.userShiftDao().close(shift);
        if (rows != 1) throw new BusinessRuleException(message("user.shift.msg.already.closed"));
        int closedBy = session == null ? userId : session.currentUserId();
        new ShiftCloseSnapshotWriter().append(shift, closedBy);
        return new ShiftClosed(shift.getId(), shift.getUserId(), shift.getTreasuryId(), difference, forced);
    }

    public ShiftSummary getCurrentShiftSummary(int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_VIEW);
        requireCurrentUser(userId);
        UserShift shift = requireOpenShift(userId);
        ShiftSummary summary = daoFactory.userShiftDao().calculateShiftSummary(
                shift.getId(), userId, shift.getTreasuryId(), shift.getOpenTime(), LocalDateTime.now(clock));
        summary.setOpenBalance(shift.getOpenBalance());
        return summary;
    }

    public UserShift getOpenShift(int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_VIEW);
        requireCurrentUser(userId);
        return daoFactory.userShiftDao().getOpenShiftByUserId(userId);
    }

    public boolean hasOpenShift(int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_VIEW);
        requireCurrentUser(userId);
        return daoFactory.userShiftDao().hasOpenShift(userId);
    }

    public List<UserShift> getUserShifts(int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_VIEW);
        requireCurrentUser(userId);
        return daoFactory.userShiftDao().getShiftsByUserId(userId);
    }

    public List<UserShift> getAllShifts() throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        return daoFactory.userShiftDao().loadAll();
    }

    public int deleteShift(int shiftId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        if (daoFactory.userShiftDao().hasAttributedCashMovements(shiftId)) {
            throw new BusinessRuleException(message("user.shift.error.delete.attributed"));
        }
        return daoFactory.userShiftDao().deleteById(shiftId);
    }

    public int forceCloseShift(int shiftId, BigDecimal closeBalance, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        UserShift shift = daoFactory.userShiftDao().getDataById(shiftId);
        if (shift == null) throw new BusinessRuleException(message("user.shift.msg.not.found"));
        return close(shift.getUserId(), closeBalance, notes, true, shiftId);
    }

    private void validateOpen(int userId, int treasuryId, BigDecimal openBalance) throws DaoException {
        if (userId <= 0) throw new UserValidationException(message("user.shift.msg.invalid.user"));
        if (treasuryId <= 0) throw new UserValidationException(message("expenses.error.select.treasury"));
        if (openBalance == null || openBalance.signum() < 0) {
            throw new UserValidationException(message("user.shift.msg.open.balance.negative"));
        }
        if (policies != null) {
            if (policies.current().mode() == ShiftMode.DISABLED) {
                throw new BusinessRuleException(message("user.shift.error.disabled"));
            }
            ShiftTrackingMode mode = policies.treasuries().stream()
                    .filter(item -> item.treasuryId() == treasuryId)
                    .map(item -> item.trackingMode()).findFirst().orElse(ShiftTrackingMode.NONE);
            if (mode == ShiftTrackingMode.NONE) {
                throw new BusinessRuleException(message("user.shift.error.treasury.not.tracked"));
            }
        }
    }

    private void validateVariance(BigDecimal difference, String notes, boolean forced) throws DaoException {
        if (policies == null) return;
        var policy = policies.current();
        if (difference.abs().compareTo(policy.varianceTolerance()) <= 0) return;
        if (policy.requireVarianceReason() && clean(notes).isBlank()) {
            throw new UserValidationException(message("user.shift.error.variance.reason"));
        }
        if (!forced && policy.requireSupervisorApproval()) {
            AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        }
    }

    private ShiftTrackingMode trackingMode(int treasuryId) throws DaoException {
        if (policies == null) return ShiftTrackingMode.RECONCILE;
        return policies.treasuries().stream()
                .filter(item -> item.treasuryId() == treasuryId)
                .map(item -> item.trackingMode()).findFirst().orElse(ShiftTrackingMode.NONE);
    }

    private UserShift requireOpenShift(int userId) throws DaoException {
        UserShift shift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (shift == null) throw new BusinessRuleException(message("user.shift.msg.no.open.shift"));
        return shift;
    }

    private void requireCurrentUser(int userId) throws BusinessRuleException {
        if (session != null && session.currentUserId() != userId) {
            throw new BusinessRuleException(message("user.shift.error.self.only"));
        }
    }

    private static void appendCloseNotes(UserShift shift, String notes, boolean forced) {
        String addition = clean(notes);
        if (addition.isBlank()) return;
        String prefix = forced ? "[FORCE_CLOSE] " : "[CLOSE] ";
        String current = clean(shift.getNotes());
        shift.setNotes(current.isBlank() ? prefix + addition : current + " | " + prefix + addition);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
