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

    @Test
    void denyMarkerAlwaysWinsEvenForAdministrator() {
        session.signIn(1, "admin", Set.of());

        assertFalse(AuthorizationGuard.isGranted(AppPermissions.DISABLE_BUTTON));
        assertThrows(DaoException.class, () -> AuthorizationGuard.require(AppPermissions.DISABLE_BUTTON));
    }
}
