package com.hamza.account.controller.main;

import com.hamza.account.model.domain.Users_Permission;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.LogApplication;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import lombok.RequiredArgsConstructor;

import java.util.List;

@lombok.extern.log4j.Log4j2
public class DisableButtons {

    @FunctionalInterface
    public interface Disableable {
        void setDisable(boolean disabled);
    }

    @RequiredArgsConstructor
    static public class PermissionDisableService {

        public void applyPermissionBasedDisable(Disableable uiElement, UserPermissionType permissionType) {
            var isEnabled = getABoolean(permissionType);
            uiElement.setDisable(!isEnabled);
        }

        public void applyPermissionBasedDisable(Node node, UserPermissionType permissionType) {
            var isEnabled = getABoolean(permissionType);
            node.setVisible(isEnabled);
        }

        public void applyPermissionBasedDisable(Menu menu, UserPermissionType permissionType) {
            var isEnabled = getABoolean(permissionType);
            menu.setVisible(isEnabled);
        }

        public boolean getABoolean(UserPermissionType permissionType) {
            if (permissionType == UserPermissionType.DISABLE_BUTTON) return false;
            // Deny rather than throw when nobody is signed in. This used to
            // dereference usersVo straight away, so any caller reached before login -
            // a background rule, a screen opened early - died on an NPE instead of
            // simply being refused.
            if (LogApplication.usersVo == null) return false;

            var id = LogApplication.usersVo.getId();
            if (permissionType == null || id == 1) return true;

            return isGranted(LogApplication.usersPermissionList, permissionType);
        }

        /**
         * Whether this user holds the permission, over the rows read from
         * {@code user_permission}.
         *
         * <p>{@code ==}, not {@code equals}, and the row's type is never dereferenced: a
         * row whose {@code permission_id} matches no constant of {@link UserPermissionType}
         * maps to <b>null</b>, because {@code UserPermissionType.getUserPermissionById}
         * answers null for an id it does not know. The enum's ids are hand-matched to the
         * table's rows, so any install whose {@code user_permission} carries an id this
         * build has no constant for produced a null here. Calling {@code equals} on it
         * threw - and the throw was inside {@code MainScreenController.initialize()}, so
         * <b>the main screen failed to open and the user could not sign in at all</b>.
         * This is the same defect the permissions screen carried; fixing it there left
         * this second caller behind.
         *
         * <p>An unknown row simply is not the permission being asked about, so skipping it
         * is also the right answer: the control falls back to disabled, which is what an
         * ungranted permission looks like.
         *
         * <p>The absent case answers {@code false} on its own rather than through a field.
         * {@code show} was an instance field assigned only when a row <i>was</i> found, and
         * callers reuse one service for several controls - so a permission the user simply
         * has no row for inherited the previous control's answer, and could be silently
         * <b>enabled</b>.
         */
        static boolean isGranted(List<Users_Permission> userPermissions, UserPermissionType permissionType) {
            if (userPermissions == null) return false;
            return userPermissions.stream()
                    .filter(usersPermission -> usersPermission.getUserPermissionType() == permissionType)
                    .map(Users_Permission::isStatus)
                    .findFirst()
                    .orElse(false);
        }
    }
}
