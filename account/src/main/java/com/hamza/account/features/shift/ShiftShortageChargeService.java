package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Authorized, investigated conversion of one closed-shift shortage into a deduction. */
public final class ShiftShortageChargeService {
    private static final int REASON_MAX = 180;
    private final ShiftShortageChargeRepository repository;
    private final UserSessionContext session;
    private final Clock clock;

    public ShiftShortageChargeService(ShiftShortageChargeRepository repository,
                                      UserSessionContext session, Clock clock) {
        this.repository = repository;
        this.session = session;
        this.clock = clock;
    }

    public int charge(int shiftId, int employeeId, String reason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_FORCE_CLOSE);
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        String approvalReason = reason == null ? "" : reason.strip();
        if (approvalReason.isEmpty() || approvalReason.length() > REASON_MAX) {
            throw new UserValidationException("user.shift.shortage.reason.required");
        }
        int actor = requireActor();
        return TransactionTemplate.execute(() -> {
            ShiftShortageCase shortage = repository.findForUpdate(shiftId);
            if (shortage == null || shortage.open() || shortage.difference() == null
                    || shortage.difference().signum() >= 0) {
                throw new BusinessRuleException(message("user.shift.shortage.error.not.eligible"));
            }
            if (shortage.alreadyCharged()) {
                throw new BusinessRuleException(message("user.shift.shortage.error.already.charged"));
            }
            if (shortage.cashierUserId() == actor) {
                throw new BusinessRuleException(message("user.shift.shortage.error.self.approval"));
            }
            if (employeeId <= 0 || !repository.activeEmployeeExists(employeeId)) {
                throw new UserValidationException("employee.error.account.employee");
            }
            BigDecimal amount = shortage.difference().abs().setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() == 0) {
                throw new BusinessRuleException(message("user.shift.shortage.error.not.eligible"));
            }
            LocalDateTime approvedAt = LocalDateTime.now(clock);
            String note = message("user.shift.shortage.ledger.note", shiftId, approvalReason);
            int ledgerId = repository.insertDeduction(employeeId, LocalDate.now(clock), amount,
                    note, actor);
            if (repository.appendCharge(shiftId, employeeId, ledgerId, amount, approvalReason,
                    actor, approvedAt) != 1) {
                throw new DaoException("Could not link employee deduction to shift " + shiftId);
            }
            return ledgerId;
        });
    }

    private int requireActor() throws DaoException {
        if (session == null || !session.isSignedIn()) {
            throw new BusinessRuleException(message("user.shift.assignment.error.login.required"));
        }
        return session.currentUserId();
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
