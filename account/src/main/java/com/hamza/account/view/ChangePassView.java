package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.security.PasswordHasher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.service.UsersService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.ChangePassInt;
import com.hamza.controlsfx.view.ChangePassApplication;
import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ChangePassView {

    public ChangePassView(DaoFactory daoFactory) throws Exception {
        var changePassInt = new ChangePassInt() {
            @Override
            public boolean verifyCurrentPassword(String candidatePassword) {
                return PasswordHasher.matches(candidatePassword, CurrentUser.get().getPasswordHash()).matched();
            }

            @Override
            public boolean updatePass(String newPass) throws Exception {
                Users users = ServiceRegistry.get(UsersService.class).getUsersById(CurrentUser.get().getId());
                // The plain password goes down: the service hashes it, so it can refuse a
                // blank one. Hashing here first handed it something it could not check.
                boolean updated = ServiceRegistry.get(UsersService.class)
                        .updateOwnPassword(users.getId(), newPass) == 1;
                users.setPasswordHash(PasswordHasher.hash(newPass));
                if (updated) ServiceRegistry.get(UserSessionContext.class).updateCurrentUser(users);
                return updated;
            }
        };
        var changePassApplication = new ChangePassApplication(changePassInt);

        var dialogApplication = changePassApplication.getDialogApplication();
        var scene = dialogApplication.getDialogPane().getScene();
        ThemeManager.apply(scene);
        dialogApplication.getDialogPane().getStylesheets().add(ThemeManager.getStylesheet());
        var b = dialogApplication.showAndWait();
        if (b.isPresent() && b.get()) {
            Thread thread = new Thread(() -> Platform.runLater(AllAlerts::alertSave));
            thread.start();
        }
    }

}
