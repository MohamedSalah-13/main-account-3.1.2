package com.hamza.account.view;

import com.hamza.account.controller.name_account.CustomerPurchasedItemsController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;

public class CustomerPurchasedItemsApplication extends Application {

    private final DaoFactory daoFactory;
    private final int customerId;

    @Getter
    private final Scene scene;

    @Setter
    private String stageTitle;

    @Setter
    private InputStream inputStream;

    public CustomerPurchasedItemsApplication(DaoFactory daoFactory, int customerId) throws IOException {
        this.daoFactory = daoFactory;
        this.customerId = customerId;

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/hamza/account/view/customer-purchased-items-view.fxml"));
        fxmlLoader.setController(new CustomerPurchasedItemsController(daoFactory, customerId));
        this.scene = new Scene(fxmlLoader.load());
    }

    @Override
    public void start(Stage stage) throws Exception {
        ChangeOrientation.sceneOrientation(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.setTitle(stageTitle != null ? stageTitle : "الأصناف المشتراة من العميل");
        if (inputStream != null) {
            stage.getIcons().add(new Image(inputStream));
        }
        stage.showAndWait();
    }
}