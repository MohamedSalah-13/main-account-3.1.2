package com.hamza.account.dash;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.dataByName.MasterDataController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.features.masterdata.MasterDataAccess;
import com.hamza.account.features.masterdata.MasterDataKind;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import org.jetbrains.annotations.NotNull;

/** A single sidebar command, still governed by the existing per-section permissions. */
public final class MasterDataButton implements ButtonWithPerm {
    @Override public PermissionKey getPermissionType() {
        return MasterDataAccess.firstVisible(AuthorizationGuard::isGranted)
                .map(kind -> kind.show).orElse(PermissionKey.deny());
    }

    @Override public @NotNull String textName() {
        return LanguageManager.getInstance().getString("masterdata.title");
    }

    @Override public Node imageMenu() { return AppIcon.SETTINGS.graphic(); }
    @Override public boolean showOnTapPane() { return true; }

    @Override public void action() throws Exception {
        MasterDataController.showWindow(authorizedSection());
    }

    @Override public void actionAddPaneToTabPane(TabPane host) throws Exception {
        MasterDataController.open(host, authorizedSection());
    }

    private MasterDataKind authorizedSection() throws DaoException {
        AuthorizationGuard.require(getPermissionType());
        return MasterDataAccess.firstVisible(AuthorizationGuard::isGranted).orElseThrow();
    }
}
