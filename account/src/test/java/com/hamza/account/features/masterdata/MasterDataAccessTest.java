package com.hamza.account.features.masterdata;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.dash.MasterDataButton;
import com.hamza.account.features.rbac.UserSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MasterDataAccessTest {
    private final UserSessionContext session = new UserSessionContext();

    @AfterEach void signOut() { session.signOut(); }

    @ParameterizedTest @EnumSource(MasterDataKind.class)
    void eachSectionAloneMakesTheUnifiedEntryAvailable(MasterDataKind kind) {
        assertEquals(kind, MasterDataAccess.firstVisible(kind.show::equals).orElseThrow());
        ServiceRegistry.register(UserSessionContext.class, session);
        session.signIn(27, "operator", List.of(kind.show));
        MasterDataButton action = new MasterDataButton();
        assertEquals(kind.show, action.getPermissionType());
        assertTrue(AuthorizationGuard.isGranted(action.getPermissionType()));
        assertTrue(action.showOnTapPane());
    }

    @Test void usersWithoutSectionAccessCannotOpenTheEntry() {
        ServiceRegistry.register(UserSessionContext.class, session);
        session.signIn(27, "operator", List.of(AppPermissions.SALES_SHOW));
        MasterDataButton action = new MasterDataButton();
        assertTrue(action.getPermissionType().isDenyMarker());
        assertFalse(AuthorizationGuard.isGranted(action.getPermissionType()));
        assertThrows(Exception.class, action::action);
        assertThrows(Exception.class, () -> action.actionAddPaneToTabPane(null));
    }

    /**
     * The areas section used to be guarded by {@code items.show} - the permission of the button
     * that opened it - so anyone who could read the catalogue could read the customer areas, and
     * every section of the editor with them. Each section answers for itself now.
     */
    @Test void readingItemsDoesNotOpenTheAreasSection() {
        assertEquals(AppPermissions.AREA_SHOW, MasterDataKind.AREA.show);
        assertTrue(MasterDataAccess.firstVisible(AppPermissions.ITEMS_SHOW::equals).isEmpty());
        ServiceRegistry.register(UserSessionContext.class, session);
        session.signIn(27, "operator", List.of(AppPermissions.ITEMS_SHOW));
        assertTrue(new MasterDataButton().getPermissionType().isDenyMarker());
    }

    @Test void writePermissionAloneDoesNotExposeASection() {
        assertTrue(MasterDataAccess.firstVisible(AppPermissions.UNITS_CREATE::equals).isEmpty());
    }

    @Test void groupsArePreferredWhenSeveralSectionsAreAllowed() {
        assertEquals(MasterDataKind.MAIN, MasterDataAccess.firstVisible(key -> true).orElseThrow());
    }
}
