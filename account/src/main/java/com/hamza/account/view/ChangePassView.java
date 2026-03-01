package com.hamza.account.view;

import com.hamza.account.config.Style_Sheet;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
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
            public String actualPass() {
                return LogApplication.usersVo.getPasswordHash();
            }

            @Override
            public boolean updatePass(String newPass) throws Exception {
                Users users = daoFactory.usersDao().getDataById(LogApplication.usersVo.getId());
                users.setPasswordHash(newPass);
                return daoFactory.usersDao().update(users) == 1;
            }
        };
        var changePassApplication = new ChangePassApplication(changePassInt);

        var dialogApplication = changePassApplication.getDialogApplication();
        dialogApplication.getDialogPane().getStylesheets().add(Style_Sheet.getStyle());
        var b = dialogApplication.showAndWait();
        if (b.isPresent() && b.get()) {
            Thread thread = new Thread(() -> Platform.runLater(AllAlerts::alertSave));
            thread.start();
        }
    }

}
