package com.hamza.account.controller.main;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
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

        public void applyPermissionBasedDisable(Disableable uiElement, PermissionKey permissionType) {
            var isEnabled = getABoolean(permissionType);
            uiElement.setDisable(!isEnabled);
        }

        public void applyPermissionBasedDisable(Node node, PermissionKey permissionType) {
            var isEnabled = getABoolean(permissionType);
            node.setVisible(isEnabled);
        }

        public void applyPermissionBasedDisable(Menu menu, PermissionKey permissionType) {
            var isEnabled = getABoolean(permissionType);
            menu.setVisible(isEnabled);
        }

        public Boolean getABoolean(PermissionKey permissionType) {
            return AuthorizationGuard.isGranted(permissionType);
        }
    }
}
