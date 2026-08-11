package com.hamza.account.perm;

import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

/**
 * Answers whether the signed-in user holds a permission, and refuses the call
 * when they do not.
 * <p>
 * {@code DisableButtons.PermissionDisableService} answers the same question for
 * the toolbar, but a disabled button is a hint rather than a rule: the same
 * delete is reachable from a context menu, from the DELETE key, and from any
 * caller that never consulted the permission at all. The services call
 * {@link #require} so the refusal happens where the row is actually removed.
 * <p>
 * Reading the permission has no state of its own - unlike the UI service, whose
 * {@code show} field carries the previous answer forward and reports a
 * permission the user does not hold whenever the one being asked about is
 * missing from the list.
 */
public final class PermissionGuard {

    private PermissionGuard() {
    }

    /**
     * Whether the signed-in user holds {@code permissionType}.
     * <p>
     * User 1 is the built-in admin and holds everything. Nobody signed in means
     * nobody is granted anything, which matters for the background rules that
     * can run before login.
     */
    public static boolean isGranted(UserPermissionType permissionType) {
        if (permissionType == null) return true;
        if (permissionType == UserPermissionType.DISABLE_BUTTON) return false;
        if (LogApplication.usersVo == null) return false;
        if (LogApplication.usersVo.getId() == 1) return true;
        if (LogApplication.usersPermissionList == null) return false;

        return LogApplication.usersPermissionList.stream()
                .filter(permission -> permissionType.equals(permission.getUserPermissionType()))
                .findFirst()
                .map(permission -> permission.isStatus())
                .orElse(false);
    }

    /**
     * Throws unless the signed-in user holds {@code permissionType}. Thrown as a
     * {@link DaoException} because that is what every delete path already
     * declares and every screen already reports.
     */
    public static void require(@NotNull UserPermissionType permissionType) throws DaoException {
        if (!isGranted(permissionType)) {
            throw new DaoException("ليس لديك صلاحية لتنفيذ هذه العملية");
        }
    }
}
