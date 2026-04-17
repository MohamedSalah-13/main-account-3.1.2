package com.hamza.account.dash;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.controller.users.UserShiftController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.view.LogApplication;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class OpenShift extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        LogApplication.usersVo = new Users(1, "salah");
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        var connection = new ConnectionToDatabase().getDbConnection().getConnection();
        daoFactory.setConnection(connection);
        var controller = new UserShiftController(daoFactory);
        Scene scene = new Scene(new OpenFxmlApplication(controller).getPane());

        stage.setResizable(false);
        stage.setScene(scene);
        stage.setTitle("OpenShift Dashboard");
        stage.show();
    }
}
