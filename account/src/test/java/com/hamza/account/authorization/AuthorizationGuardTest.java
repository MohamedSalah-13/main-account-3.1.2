package com.hamza.account.authorization;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationGuardTest {

    private UserSessionContext session;

    @BeforeEach
    void setUp() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    void signedOutSessionOnlyReceivesExplicitPublicAccess() {
        assertTrue(AuthorizationGuard.isGranted(AppPermissions.PUBLIC_ACCESS));
        assertFalse(AuthorizationGuard.isGranted(AppPermissions.SALES_SHOW));
        assertFalse(AuthorizationGuard.isGranted(null));
        assertThrows(DaoException.class, () -> AuthorizationGuard.require(AppPermissions.SALES_SHOW));
    }

    @Test
    void roleSnapshotGrantsOnlyItsResolvedPermissionSet() throws Exception {
        session.signIn(7, "sales", Set.of(
                AppPermissions.SALES_SHOW,
                AppPermissions.SALES_CREATE,
                AppPermissions.CUSTOMER_SHOW));

        assertDoesNotThrow(() -> AuthorizationGuard.require(AppPermissions.SALES_CREATE));
        assertTrue(AuthorizationGuard.isGranted(AppPermissions.CUSTOMER_SHOW));
        assertFalse(AuthorizationGuard.isGranted(AppPermissions.SALES_DELETE));
        assertFalse(AuthorizationGuard.isGranted(AppPermissions.PURCHASE_CREATE));
    }

    @Test
    void protectedAdministratorRetainsRecoveryAccessToEveryCataloguePermission() {
        session.signIn(1, "admin", Set.of());

        AppPermissions.definitions().forEach(definition ->
                assertTrue(AuthorizationGuard.isGranted(definition.key()), definition.key().value()));
    }

    /**
     * A refusal is read by the person refused, so it names the permission as the roles screen does.
     * It used to pass the key, and "stock.count.create" sat in the middle of an Arabic sentence -
     * seen on screen, signed in as an ordinary user, 2026-09-21.
     */
    @Test
    void aRefusalNamesThePermissionNotItsKey() {
        session.signIn(7, "soha", Set.of(AppPermissions.STOCK_COUNT_SHOW));

        DaoException refusal = assertThrows(DaoException.class,
                () -> AuthorizationGuard.require(AppPermissions.STOCK_COUNT_CREATE));

        String name = PermissionLabels.describe(AppPermissions.STOCK_COUNT_CREATE);
        assertNotEquals(AppPermissions.STOCK_COUNT_CREATE.value(), name, "the bundle has no name for the key");
        assertTrue(refusal.getMessage().contains(name), refusal.getMessage());
        assertFalse(refusal.getMessage().contains(AppPermissions.STOCK_COUNT_CREATE.value()), refusal.getMessage());
    }

    @Test
    void denyMarkerAlwaysWinsEvenForAdministrator() {
        session.signIn(1, "admin", Set.of());

        assertFalse(AuthorizationGuard.isGranted(AppPermissions.DISABLE_BUTTON));
        assertThrows(DaoException.class, () -> AuthorizationGuard.require(AppPermissions.DISABLE_BUTTON));
    }
}
