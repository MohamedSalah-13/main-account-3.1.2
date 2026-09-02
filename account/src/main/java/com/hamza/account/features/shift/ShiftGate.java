package com.hamza.account.features.shift;

import com.hamza.account.model.dao.UserShiftDao;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.util.OptionalInt;

import com.hamza.controlsfx.database.ConnectionManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** One service-layer gate used by every operation that can move treasury cash. */
public final class ShiftGate {
    private final ShiftPolicyRepository policies;
    private final OpenShiftLookup openShifts;
    private final TreasuryShiftLookup treasuryShifts;

    public ShiftGate(ShiftPolicyRepository policies, OpenShiftLookup openShifts) {
        this(policies, openShifts, treasuryId -> null);
    }

    public ShiftGate(ShiftPolicyRepository policies, OpenShiftLookup openShifts,
                     TreasuryShiftLookup treasuryShifts) {
        this.policies = policies;
        this.openShifts = openShifts;
        this.treasuryShifts = treasuryShifts;
    }

    public static ShiftGate jdbc(UserShiftDao dao) {
        return new ShiftGate(new JdbcShiftPolicyRepository(),
                dao::getOpenShiftByUserIdForUpdate, dao::getOpenShiftByTreasuryIdForUpdate);
    }

    public static ShiftGate jdbc() {
        return new ShiftGate(new JdbcShiftPolicyRepository(), ShiftGate::findOpenShift);
    }

    public static ShiftGate disabled() {
        return new ShiftGate(new FixedPolicyRepository(), userId -> null);
    }

    /** Returns the applicable open shift id, or empty when policy permits an unassigned movement. */
    public OptionalInt requireCashAction(int userId, int treasuryId, BigDecimal amount) throws DaoException {
        return requireCashAction(userId, treasuryId, amount, false);
    }

    /** A shifted historical movement may never disappear through OPTIONAL mode. */
    public OptionalInt requireCashCorrection(int userId, int treasuryId, BigDecimal amount,
                                             Integer originShiftId) throws DaoException {
        return requireCashAction(userId, treasuryId, amount, originShiftId != null);
    }

    private OptionalInt requireCashAction(int userId, int treasuryId, BigDecimal amount,
                                          boolean shiftedCorrection) throws DaoException {
        if (amount == null || amount.signum() == 0) return OptionalInt.empty();
        ShiftPolicy policy = policies.load();
        if (policy.mode() == ShiftMode.DISABLED
                || policies.trackingMode(treasuryId) == ShiftTrackingMode.NONE) {
            if (shiftedCorrection) throw correctionTrackingRequired();
            return OptionalInt.empty();
        }
        UserShift open = openShifts.find(userId);
        if (open != null && open.getTreasuryId() == treasuryId) {
            return OptionalInt.of(open.getId());
        }
        if (policy.mode() == ShiftMode.REQUIRED || shiftedCorrection) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString("user.shift.error.required"));
        }
        return OptionalInt.empty();
    }

    /** Resolves the open shift that owns the receiving side of a treasury transfer. */
    public OptionalInt requireTreasuryAction(int treasuryId, BigDecimal amount) throws DaoException {
        return requireTreasuryAction(treasuryId, amount, false);
    }

    public OptionalInt requireTreasuryCorrection(int treasuryId, BigDecimal amount,
                                                  Integer originShiftId) throws DaoException {
        return requireTreasuryAction(treasuryId, amount, originShiftId != null);
    }

    private OptionalInt requireTreasuryAction(int treasuryId, BigDecimal amount,
                                              boolean shiftedCorrection) throws DaoException {
        if (amount == null || amount.signum() == 0) return OptionalInt.empty();
        ShiftPolicy policy = policies.load();
        if (policy.mode() == ShiftMode.DISABLED
                || policies.trackingMode(treasuryId) == ShiftTrackingMode.NONE) {
            if (shiftedCorrection) throw correctionTrackingRequired();
            return OptionalInt.empty();
        }
        UserShift open = treasuryShifts.find(treasuryId);
        if (open != null) return OptionalInt.of(open.getId());
        if (policy.mode() == ShiftMode.REQUIRED || shiftedCorrection) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString("user.shift.error.required"));
        }
        return OptionalInt.empty();
    }

    @FunctionalInterface
    public interface OpenShiftLookup { UserShift find(int userId) throws DaoException; }

    @FunctionalInterface
    public interface TreasuryShiftLookup { UserShift find(int treasuryId) throws DaoException; }

    private static BusinessRuleException correctionTrackingRequired() {
        return new BusinessRuleException(LanguageManager.getInstance().getString(
                "user.shift.error.correction.tracking.required"));
    }

    private static UserShift findOpenShift(int userId) throws DaoException {
        java.sql.Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, user_id, treasury_id FROM user_shifts WHERE user_id=? AND is_open=TRUE "
                            + "ORDER BY open_time DESC LIMIT 1 FOR UPDATE")) {
                statement.setInt(1, userId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) return null;
                    UserShift shift = new UserShift(rs.getInt("user_id"), rs.getInt("treasury_id"));
                    shift.setId(rs.getInt("id"));
                    shift.setOpen(true);
                    return shift;
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not read the open shift", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private static final class FixedPolicyRepository implements ShiftPolicyRepository {
        public ShiftPolicy load() { return ShiftPolicy.DISABLED; }
        public java.util.List<TreasuryShiftPolicy> loadTreasuries() { return java.util.List.of(); }
        public ShiftTrackingMode trackingMode(int treasuryId) { return ShiftTrackingMode.NONE; }
        public boolean hasOpenShifts() { return false; }
        public void save(ShiftPolicy policy) { throw new UnsupportedOperationException(); }
        public void saveTreasury(TreasuryShiftPolicy policy) { throw new UnsupportedOperationException(); }
    }
}
