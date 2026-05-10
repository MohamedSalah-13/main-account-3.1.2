package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.controller.reports.*;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.view.LogApplication;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class OpenShift extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        LogApplication.usersVo = new Users(1, "salah");
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        var connection = new ConnectionToDatabase().getDbConnection().getConnection();
        daoFactory.setConnection(connection);


        // مثال لكيفية فتح الشاشة وتمرير الاتصال
//        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/reports/CustomerReceivableView.fxml"));
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/reports/ComprehensiveSalesView.fxml"));
        Parent root = loader.load();

//        CustomerReceivableController controller = loader.getController();
        ComprehensiveSalesController controller = loader.getController();
        controller.setDaoFactory(daoFactory); // تمرير اتصال قاعدة البيانات

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("تقرير المبيعات السنوي");
        stage.show();



//
//        ModernDashboardApp modernDashboardApp = new ModernDashboardApp(daoFactory);
//        modernDashboardApp.showWindow();
    }
}
