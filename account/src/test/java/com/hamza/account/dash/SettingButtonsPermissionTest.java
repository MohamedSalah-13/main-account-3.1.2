package com.hamza.account.dash;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.wipe.WipePlan;
import com.hamza.account.wipe.WipeService;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What each button of the sidebar's settings section asks.
 * <p>
 * The section used to be hidden without {@code setting.show}, and with it four of its buttons asked
 * that same key - so a cashier given only their own shift screen could not reach it, and one given the
 * settings to reach it could also open the delete-data screen. Each button now asks for exactly what it
 * opens, and these are the answers.
 */
class SettingButtonsPermissionTest {

    private final SettingButtons buttons = new SettingButtons(null, null);
    private UserSessionContext session;

    @BeforeEach
    void signIn() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    @Test
    void homeAboutAndCloseAreOpenToEverybody() {
        assertEquals(AppPermissions.PUBLIC_ACCESS, buttons.home().getPermissionType());
        assertEquals(AppPermissions.PUBLIC_ACCESS, buttons.about().getPermissionType());
        assertEquals(AppPermissions.PUBLIC_ACCESS, buttons.close().getPermissionType(),
                "it asked user.shift.manage, so a cashier could not leave the program from the menu");
    }

    @Test
    void settingShowOpensTheSettingsScreenAndNothingElse() {
        assertEquals(AppPermissions.SETTING_SHOW, buttons.setting().getPermissionType());
        assertEquals(AppPermissions.SETTING_BACKUP_SHOW, buttons.backup().getPermissionType());
        assertEquals(AppPermissions.SETTING_DATA_DELETE, buttons.deleteData().getPermissionType());
    }

    @Test
    void theShiftAdministrationOpensForAnyOfItsKeysAndForNobodyWithout() {
        session.signIn(7, "cashier", Set.of(AppPermissions.SETTING_SHOW, AppPermissions.SHIFT_SELF_VIEW,
                AppPermissions.SHIFT_SELF_OPEN, AppPermissions.SHIFT_SELF_CLOSE));
        assertFalse(AuthorizationGuard.isGranted(buttons.adminShifts().getPermissionType()),
                "the cashier's own shift keys are not the administration's");

        session.signIn(8, "supervisor", Set.of(AppPermissions.SHIFT_REPORT_REPRINT));
        assertTrue(AuthorizationGuard.isGranted(buttons.adminShifts().getPermissionType()));
    }

    /** The button is a hint; the wipe itself refuses, before it reads the plan. */
    @Test
    void theWipeRefusesAUserWhoMayOpenTheSettings() {
        session.signIn(7, "cashier", Set.of(AppPermissions.SETTING_SHOW));

        assertThrows(BusinessRuleException.class, () -> new WipeService().run(WipePlan.of(Set.of())));
    }
}
