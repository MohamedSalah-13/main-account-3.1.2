package com.hamza.account.controller.users;

import com.hamza.account.model.domain.Users_Permission;
import com.hamza.account.type.UserPermissionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a permission out of the rows {@code user_permission} holds.
 *
 * <p>The case that matters is {@link #aRowWithAnUnknownPermissionIdIsIgnored()}. The ids
 * of {@link UserPermissionType} are hand-matched to that table's rows, and
 * {@code UserPermissionType.getUserPermissionById} answers <b>null</b> for an id it has
 * no constant for - so any install whose table carries such a row produced a null type
 * on the model. The check used to call {@code equals} on it, and the throw happened
 * inside {@code UserPermissionController.initialize()}, which meant the permissions
 * screen would not open at all for that user.
 */
class UserPermissionGrantTest {

    @Test
    void readsTheStatusOfAPermissionTheUserHolds() {
        var rows = List.of(row(UserPermissionType.SALES_SHOW, true));

        assertTrue(UserPermissionController.isPermissionGranted(rows, UserPermissionType.SALES_SHOW));
    }

    @Test
    void aPermissionRecordedAsUncheckedIsNotGranted() {
        var rows = List.of(row(UserPermissionType.SALES_SHOW, false));

        assertFalse(UserPermissionController.isPermissionGranted(rows, UserPermissionType.SALES_SHOW));
    }

    @Test
    void aPermissionWithNoRowAtAllIsNotGranted() {
        var rows = List.of(row(UserPermissionType.SALES_SHOW, true));

        assertFalse(UserPermissionController.isPermissionGranted(rows, UserPermissionType.SALES_DELETE));
    }

    /**
     * The client's crash: a row this build cannot name. It must be stepped over rather
     * than compared, and it must not hide a real row that follows it.
     */
    @Test
    void aRowWithAnUnknownPermissionIdIsIgnored() {
        var rows = List.of(row(null, true), row(UserPermissionType.SALES_SHOW, true));

        assertTrue(UserPermissionController.isPermissionGranted(rows, UserPermissionType.SALES_SHOW));
        assertFalse(UserPermissionController.isPermissionGranted(rows, UserPermissionType.SALES_DELETE));
    }

    @Test
    void rowsThatAreAllUnknownGrantNothingAndDoNotThrow() {
        var rows = List.of(row(null, true), row(null, true));

        assertFalse(UserPermissionController.isPermissionGranted(rows, UserPermissionType.SALES_SHOW));
    }

    private static Users_Permission row(UserPermissionType type, boolean status) {
        Users_Permission permission = new Users_Permission();
        permission.setUserPermissionType(type);
        permission.setStatus(status);
        return permission;
    }
}
