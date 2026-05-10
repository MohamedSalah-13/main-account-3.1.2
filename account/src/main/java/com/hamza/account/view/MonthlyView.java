package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.controller.reports.MonthlySalesController;
import com.hamza.account.controller.reports.MonthlySalesInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MonthlyView extends Application {
    private final DaoFactory daoFactory;
    private final MonthlySalesInterface monthlySalesInterface;
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/MonthlySalesView.fxml"));
        Parent root = loader.load();
//
        MonthlySalesController controller = loader.getController();
        controller.loadData(daoFactory, monthlySalesInterface); // تمرير اتصال قاعدة البيانات
        Stage stage = new Stage();
        Scene scene = new SceneAll(root);
        ChangeOrientation.sceneOrientation(scene);
        stage.setScene(scene);
        stage.setTitle(monthlySalesInterface.reportName());
        stage.show();
    }
}
