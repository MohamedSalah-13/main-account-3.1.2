package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
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
                return PasswordHasher.matches(candidatePassword, LogApplication.usersVo.getPasswordHash()).matched();
            }

            @Override
            public boolean updatePass(String newPass) throws Exception {
                Users users = daoFactory.usersDao().getDataById(LogApplication.usersVo.getId());
                users.setPasswordHash(PasswordHasher.hash(newPass));
                boolean updated = daoFactory.usersDao().update(users) == 1;
                if (updated) LogApplication.usersVo = users;
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
