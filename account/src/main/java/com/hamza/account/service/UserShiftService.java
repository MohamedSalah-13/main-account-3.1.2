package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.shift.ShiftClosed;
import com.hamza.account.features.shift.ShiftCloseAttempt;
import com.hamza.account.features.shift.ShiftCloseRequest;
import com.hamza.account.features.shift.ShiftCloseRequestDao;
import com.hamza.account.features.shift.ShiftCloseDecisionPolicy;
import com.hamza.account.features.shift.ShiftCloseSnapshotWriter;
import com.hamza.account.features.shift.CashierTreasuryAssignmentService;
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
    private final ShiftCloseRequestDao closeRequests;
    private final CashierTreasuryAssignmentService treasuryAssignments;

    /** Compatibility constructor used by tests that only exercise authorization. */
    public UserShiftService(DaoFactory daoFactory) {
        this(daoFactory, null, null, null, Clock.systemDefaultZone());
    }

    public UserShiftService(DaoFactory daoFactory, UserSessionContext session,
                            ShiftPolicyService policies, EventBus events, Clock clock) {
        this(daoFactory, session, policies, events, clock, new ShiftCloseRequestDao(), null);
    }

    public UserShiftService(DaoFactory daoFactory, UserSessionContext session,
                            ShiftPolicyService policies, EventBus events, Clock clock,
                            ShiftCloseRequestDao closeRequests) {
        this(daoFactory, session, policies, events, clock, closeRequests, null);
    }

    public UserShiftService(DaoFactory daoFactory, UserSessionContext session,
                            ShiftPolicyService policies, EventBus events, Clock clock,
                            ShiftCloseRequestDao closeRequests,
                            CashierTreasuryAssignmentService treasuryAssignments) {
        this.daoFactory = daoFactory;
        this.session = session;
        this.policies = policies;
        this.events = events;
        this.clock = clock;
        this.closeRequests = closeRequests;
        this.treasuryAssignments = treasuryAssignments;
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
        return requestCloseShift(userId, closeBalance, notes).shiftId();
    }

    /** Closes immediately or freezes the shift behind an immutable two-person request. */
    public ShiftCloseAttempt requestCloseShift(int userId, BigDecimal closeBalance, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_CLOSE);
        requireCurrentUser(userId);
        CloseResult result = TransactionTemplate.execute(() -> closeTransactional(
                userId, closeBalance, notes, false, 0));
        publish(result.closed());
        return result.attempt();
    }

    private int close(int userId, BigDecimal closeBalance, String notes, boolean forced, int requestedShiftId)
            throws DaoException {
        CloseResult result = TransactionTemplate.execute(() -> closeTransactional(
                userId, closeBalance, notes, forced, requestedShiftId));
        publish(result.closed());
        return result.attempt().shiftId();
    }

    private CloseResult closeTransactional(int userId, BigDecimal closeBalance, String notes,
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

        ShiftCloseRequest pending = closeRequests.pendingForShift(shift.getId(), true);
        if (forced && pending != null) {
            closeRequests.decide(pending.id(), currentActor(userId), "CANCELLED", notes,
                    LocalDateTime.now(clock));
        }
        if (!forced && requiresSupervisorApproval(difference)) {
            if (pending != null || shift.getStatus() == ShiftStatus.PENDING_CLOSE) {
                throw new BusinessRuleException(message("user.shift.error.approval.already.pending"));
            }
            closeRequests.append(shift, summary, effectiveCloseBalance, clean(notes),
                    currentActor(userId), closeTime);
            if (daoFactory.userShiftDao().markPendingClose(shift.getId()) != 1) {
                throw new BusinessRuleException(message("user.shift.msg.already.closed"));
            }
            return new CloseResult(ShiftCloseAttempt.pending(shift.getId()), null);
        }

        applyClose(shift, closeTime, effectiveCloseBalance, difference, summary,
                forced ? ShiftStatus.FORCE_CLOSED : ShiftStatus.CLOSED);
        appendCloseNotes(shift, notes, forced);

        int rows = daoFactory.userShiftDao().close(shift);
        if (rows != 1) throw new BusinessRuleException(message("user.shift.msg.already.closed"));
        int closedBy = currentActor(userId);
        new ShiftCloseSnapshotWriter().append(shift, closedBy);
        ShiftClosed event = new ShiftClosed(shift.getId(), shift.getUserId(), shift.getTreasuryId(), difference, forced);
        return new CloseResult(ShiftCloseAttempt.closed(shift.getId()), event);
    }

    public List<ShiftCloseRequest> getPendingCloseRequests() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        return closeRequests.loadPending();
    }

    public int approveCloseRequest(int shiftId, String note) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        int actor = requireSignedInActor();
        ShiftClosed closed = TransactionTemplate.execute(() -> {
            UserShift shift = requirePendingShift(shiftId);
            ShiftCloseRequest request = requirePendingRequest(shiftId);
            validateDecision(request, actor, closeRequests.currentLedgerLastId(shiftId));
            ShiftSummary captured = capturedSummary(shift, request);
            applyClose(shift, LocalDateTime.now(clock), request.actualBalance(), request.difference(),
                    captured, ShiftStatus.CLOSED);
            appendApprovalNotes(shift, request.reason(), note, actor);
            if (daoFactory.userShiftDao().close(shift) != 1) {
                throw new BusinessRuleException(message("user.shift.msg.already.closed"));
            }
            if (closeRequests.decide(request.id(), actor, "APPROVED", note, LocalDateTime.now(clock)) != 1) {
                throw new BusinessRuleException(message("user.shift.error.approval.already.decided"));
            }
            new ShiftCloseSnapshotWriter().append(shift, actor);
            return new ShiftClosed(shift.getId(), shift.getUserId(), shift.getTreasuryId(),
                    request.difference(), false);
        });
        publish(closed);
        return closed.shiftId();
    }

    public int rejectCloseRequest(int shiftId, String note) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        if (clean(note).isBlank()) {
            throw new UserValidationException(message("user.shift.approval.reject.reason.required"));
        }
        int actor = requireSignedInActor();
        return TransactionTemplate.execute(() -> {
            requirePendingShift(shiftId);
            ShiftCloseRequest request = requirePendingRequest(shiftId);
            validateDecision(request, actor, request.ledgerLastId());
            if (closeRequests.decide(request.id(), actor, "REJECTED", note, LocalDateTime.now(clock)) != 1
                    || daoFactory.userShiftDao().resumeOpen(shiftId) != 1) {
                throw new BusinessRuleException(message("user.shift.error.approval.already.decided"));
            }
            return shiftId;
        });
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
            if (treasuryAssignments != null && !treasuryAssignments.canOpenShift(userId, treasuryId)) {
                throw new BusinessRuleException(message("user.shift.assignment.error.not.allowed"));
            }
        }
    }

    private void validateVariance(BigDecimal difference, String notes, boolean forced) throws DaoException {
        if (policies == null) return;
        var policy = policies.current();
        if (difference.abs().compareTo(policy.varianceTolerance()) <= 0) return;
        if ((policy.requireVarianceReason() || (!forced && policy.requireSupervisorApproval()))
                && clean(notes).isBlank()) {
            throw new UserValidationException(message("user.shift.error.variance.reason"));
        }
    }

    private boolean requiresSupervisorApproval(BigDecimal difference) throws DaoException {
        if (policies == null) return false;
        var policy = policies.current();
        return policy.requireSupervisorApproval()
                && difference.abs().compareTo(policy.varianceTolerance()) > 0;
    }

    private ShiftTrackingMode trackingMode(int treasuryId) throws DaoException {
        if (policies == null) return ShiftTrackingMode.RECONCILE;
        return policies.treasuries().stream()
                .filter(item -> item.treasuryId() == treasuryId)
                .map(item -> item.trackingMode()).findFirst().orElse(ShiftTrackingMode.NONE);
    }

    private UserShift requireOpenShift(int userId) throws DaoException {
        UserShift shift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (shift == null || shift.getStatus() != ShiftStatus.OPEN) {
            throw new BusinessRuleException(message("user.shift.msg.no.open.shift"));
        }
        return shift;
    }

    private ShiftCloseRequest requirePendingRequest(int shiftId) throws DaoException {
        ShiftCloseRequest request = closeRequests.pendingForShift(shiftId, true);
        if (request == null) {
            throw new BusinessRuleException(message("user.shift.error.approval.not.pending"));
        }
        return request;
    }

    private UserShift requirePendingShift(int shiftId) throws DaoException {
        UserShift shift = daoFactory.userShiftDao().getOpenShiftByIdForUpdate(shiftId);
        if (shift == null || shift.getStatus() != ShiftStatus.PENDING_CLOSE) {
            throw new BusinessRuleException(message("user.shift.error.approval.not.pending"));
        }
        return shift;
    }

    private static void validateDecision(ShiftCloseRequest request, int actor, long ledgerLastId)
            throws DaoException {
        switch (ShiftCloseDecisionPolicy.evaluate(request, actor, ledgerLastId)) {
            case SAME_ACTOR -> throw new BusinessRuleException(message("user.shift.error.approval.self"));
            case LEDGER_CHANGED -> throw new BusinessRuleException(
                    message("user.shift.error.approval.ledger.changed"));
            case NONE -> { }
        }
    }

    private int requireSignedInActor() throws DaoException {
        int actor = session == null ? 0 : session.currentUserId();
        if (actor <= 0) throw new BusinessRuleException(message("user.shift.error.approval.login.required"));
        return actor;
    }

    private int currentActor(int fallback) {
        return session == null || session.currentUserId() <= 0 ? fallback : session.currentUserId();
    }

    private static ShiftSummary capturedSummary(UserShift shift, ShiftCloseRequest request) {
        ShiftSummary summary = new ShiftSummary();
        summary.setOpenBalance(shift.getOpenBalance());
        summary.setTotalSales(request.totalSales());
        summary.setTotalSalesReturns(request.totalSalesReturns());
        summary.setTotalExpenses(request.totalExpenses());
        summary.setTotalDeposits(request.totalDeposits());
        summary.setTotalWithdrawals(request.totalWithdrawals());
        summary.setTotalIn(request.totalCashIn());
        summary.setTotalOut(request.totalCashOut());
        summary.setInvoicesCount(request.invoicesCount());
        return summary;
    }

    private static void applyClose(UserShift shift, LocalDateTime closeTime, BigDecimal actualBalance,
                                   BigDecimal difference, ShiftSummary summary, ShiftStatus status) {
        shift.setCloseTime(closeTime);
        shift.setCloseBalance(actualBalance);
        shift.setOpen(false);
        shift.setStatus(status);
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
    }

    private static void appendApprovalNotes(UserShift shift, String reason, String note, int actor) {
        String detail = "[CLOSE_REQUEST] " + clean(reason) + " | [APPROVED_BY:" + actor + "] " + clean(note);
        String current = clean(shift.getNotes());
        shift.setNotes(current.isBlank() ? detail : current + " | " + detail);
    }

    private void publish(ShiftClosed closed) {
        if (events != null && closed != null) events.publish(closed);
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

    private record CloseResult(ShiftCloseAttempt attempt, ShiftClosed closed) { }
}
