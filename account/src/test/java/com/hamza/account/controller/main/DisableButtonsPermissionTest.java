package com.hamza.account.controller.main;

import com.hamza.account.controller.main.DisableButtons.PermissionDisableService;
import com.hamza.account.model.domain.Users_Permission;
import com.hamza.account.type.UserPermissionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which menus and buttons a signed-in user may reach, read out of the rows
 * {@code user_permission} holds.
 *
 * <p>This is the same question {@code UserPermissionGrantTest} asks of the permissions
 * screen, at the second caller - the one the first fix left behind. The throw happened
 * inside {@code MainScreenController.initialize()}, by way of
 * {@code MenuButtonSetting.disableButton}, so the whole main screen failed to build and
 * <b>the user could not sign in at all</b>.
 */
class DisableButtonsPermissionTest {

    @Test
    void readsTheStatusOfAPermissionTheUserHolds() {
        var rows = List.of(row(UserPermissionType.SALES_SHOW, true));

        assertTrue(PermissionDisableService.isGranted(rows, UserPermissionType.SALES_SHOW));
    }

    @Test
    void aPermissionRecordedAsUncheckedIsNotGranted() {
        var rows = List.of(row(UserPermissionType.SALES_SHOW, false));

        assertFalse(PermissionDisableService.isGranted(rows, UserPermissionType.SALES_SHOW));
    }

    /**
     * The client's crash on 4.3.1: a new user, one permission granted, and a row this
     * build cannot name sitting in the table beside it.
     */
    @Test
    void aRowWithAnUnknownPermissionIdIsIgnoredRatherThanCompared() {
        var rows = List.of(row(null, true), row(UserPermissionType.SALES_SHOW, true));

        assertTrue(PermissionDisableService.isGranted(rows, UserPermissionType.SALES_SHOW));
        assertFalse(PermissionDisableService.isGranted(rows, UserPermissionType.SALES_DELETE));
    }

    @Test
    void rowsThatAreAllUnknownGrantNothingAndDoNotThrow() {
        var rows = List.of(row(null, true), row(null, true));

        assertFalse(PermissionDisableService.isGranted(rows, UserPermissionType.SALES_SHOW));
    }

    /**
     * The quieter defect beside the crash. {@code show} was an instance field assigned
     * only when a row was found, and a caller applies one service to several controls -
     * so a permission with no row of its own answered with the previous control's status.
     * A user granted one permission had every later unrecorded permission enabled too.
     */
    @Test
    void aGrantedPermissionDoesNotLeakIntoTheNextOneAsked() {
        var rows = List.of(row(UserPermissionType.SALES_SHOW, true));
        var service = new PermissionDisableService();

        var granted = new ArrayList<Boolean>();
        for (var type : List.of(UserPermissionType.SALES_SHOW, UserPermissionType.SALES_DELETE)) {
            granted.add(PermissionDisableService.isGranted(rows, type));
        }

        assertTrue(granted.get(0));
        assertFalse(granted.get(1), "a permission with no row must not inherit the previous answer");
        assertFalse(PermissionDisableService.isGranted(null, UserPermissionType.SALES_SHOW));
        assertFalse(service.getABoolean(UserPermissionType.DISABLE_BUTTON));
    }

    private static Users_Permission row(UserPermissionType type, boolean status) {
        Users_Permission permission = new Users_Permission();
        permission.setUserPermissionType(type);
        permission.setStatus(status);
        return permission;
    }
}
