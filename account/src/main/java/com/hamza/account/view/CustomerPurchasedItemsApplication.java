package com.hamza.account.view;

import com.hamza.account.controller.name_account.CustomerPurchasedItemsController;
import com.hamza.account.interfaces.CustomerPurchaseInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class CustomerPurchasedItemsApplication extends Application {

    private final Scene scene;
    private final CustomerPurchaseInterface customerPurchaseInterface;

    public CustomerPurchasedItemsApplication(DaoFactory daoFactory, int customerId, String customerName
            , CustomerPurchaseInterface customerPurchaseInterface) throws IOException {
        this.customerPurchaseInterface = customerPurchaseInterface;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/hamza/account/view/customer-purchased-items-view.fxml"));
        fxmlLoader.setController(new CustomerPurchasedItemsController(daoFactory, customerId, customerName
                , customerPurchaseInterface));
        this.scene = new SceneAll(fxmlLoader.load());
    }

    @Override
    public void start(Stage stage) throws Exception {
        ChangeOrientation.sceneOrientation(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
//        stage.setResizable(false);
        stage.setScene(scene);
        stage.setTitle(customerPurchaseInterface.title());
        stage.showAndWait();
    }
}