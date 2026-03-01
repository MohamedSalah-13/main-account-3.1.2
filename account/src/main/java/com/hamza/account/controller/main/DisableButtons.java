package com.hamza.account.controller.main;

import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.LogApplication;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import lombok.RequiredArgsConstructor;

@lombok.extern.log4j.Log4j2
public class DisableButtons {

    @FunctionalInterface
    public interface Disableable {
        void setDisable(boolean disabled);
    }

    @RequiredArgsConstructor
    static public class PermissionDisableService {

        private boolean show = false;

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

        public Boolean getABoolean(UserPermissionType permissionType) {
            var id = LogApplication.usersVo.getId();
            if (permissionType == UserPermissionType.DISABLE_BUTTON) return false;
            if (permissionType == null || id == 1) return true;

            var permissionValue = LogApplication.usersPermissionList.stream()
                    .filter(usersPermission -> usersPermission.getUserPermissionType().equals(permissionType)).findFirst();
            permissionValue.ifPresent(usersPermission -> show = usersPermission.isStatus());
            return show;

        }
    }
}
