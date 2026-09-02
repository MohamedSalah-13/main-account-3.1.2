package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may read whose shift.
 * <p>
 * A shift row carries a cashier's opening cash, their closing cash, and the difference
 * between them - the number they are answerable for. {@code getAllShifts} returns every
 * user's, and required nothing at all: any signed-in user could read the lot. The
 * architecture test that holds service write paths to a guard cannot see this, because
 * reading is not writing.
 * <p>
 * The asymmetry with {@code getUserShifts} is deliberate and is asserted here rather than
 * left to be inferred: your own shifts are yours to read.
 * <p>
 * No database, in the manner of {@code TreasuryTransferServiceTest}: the service is built
 * on a {@code null} factory, so a guard that ever moved below the DAO access would fail
 * these with a {@code NullPointerException} instead of passing quietly.
 */
class UserShiftServiceTest {

    private static UserShiftService serviceWithoutDatabase() {
        return new UserShiftService(null);
    }

    private void signInWith(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "cashier", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    @DisplayName("Reading every user's shifts is refused without the shift permission")
    void everyUsersShiftsAreRefusedWithoutThePermission() {
        signInWith(AppPermissions.USERS_SHOW);

        Exception refusal =
                assertThrows(Exception.class, () -> serviceWithoutDatabase().getAllShifts());

        assertTrue(refusal instanceof BusinessRuleException,
                "a permission denial is a business rule, not a technical failure: " + refusal
                        + ". A NullPointerException here means the guard is below the DAO "
                        + "access, so the rows were read before anyone asked.");
    }

    @Test
    @DisplayName("With the permission, the read is allowed through to the database")
    void thePermissionAdmitsTheRead() {
        signInWith(AppPermissions.USER_SHIFT_MANAGE);

        // USER_SHIFT_MANAGE is what force-closing and deleting a shift already require, so
        // this grants nobody anything new; it is the same door. Reaching the null factory
        // is the proof that the guard passed - there is nothing else past it to reach.
        assertThrows(NullPointerException.class,
                () -> serviceWithoutDatabase().getAllShifts(),
                "the guard refused a caller holding USER_SHIFT_MANAGE, which would lock the "
                        + "administrator out of the screen instead of locking everyone else out");
    }

    @Test
    @DisplayName("Your own shifts require the explicit self-view permission")
    void ownShiftsNeedTheSelfViewPermission() {
        signInWith();
        assertTrue(assertThrows(Exception.class,
                () -> serviceWithoutDatabase().getUserShifts(2)) instanceof BusinessRuleException);

        signInWith(AppPermissions.SHIFT_SELF_VIEW);
        assertThrows(NullPointerException.class, () -> serviceWithoutDatabase().getUserShifts(2));
    }

    @Test
    @DisplayName("Opening a shift is refused before any database access without self-open permission")
    void openingNeedsExplicitPermission() {
        signInWith(AppPermissions.SHIFT_SELF_VIEW);
        assertTrue(assertThrows(Exception.class, () ->
                serviceWithoutDatabase().openShift(2, 1, BigDecimal.ZERO, "")) instanceof BusinessRuleException);
    }

    @Test
    @DisplayName("Closing a shift is refused before any database access without self-close permission")
    void closingNeedsExplicitPermission() {
        signInWith(AppPermissions.SHIFT_SELF_VIEW);
        assertTrue(assertThrows(Exception.class, () ->
                serviceWithoutDatabase().closeShift(2, BigDecimal.ZERO, "")) instanceof BusinessRuleException);
    }
}
