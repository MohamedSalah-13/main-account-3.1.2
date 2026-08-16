package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.UpdateSomeItems;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

public class ConvertItemsGroup extends Application {

    private final List<ItemsModel> itemsModelList;

    public ConvertItemsGroup(List<ItemsModel> itemsModelList) {
        this.itemsModelList = itemsModelList;
    }


    @Override
    public void start(Stage stage) throws Exception {
        var controller = new UpdateSomeItems(itemsModelList);
        Scene scene = new SceneAll(new OpenFxmlApplication(controller).getPane());
        stage.setScene(scene);
        stage.setTitle(LanguageManager.getInstance().getString("item.dialog.convert.title"));
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
        stage.setResizable(false);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }
}
