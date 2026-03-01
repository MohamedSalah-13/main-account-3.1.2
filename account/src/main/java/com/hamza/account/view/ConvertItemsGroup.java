package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.UpdateSomeItems;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.OpenFxmlApplication;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class ConvertItemsGroup extends Application {

    public static final String HEADER_TEXT = "تعديل مجموعة أصناف";
    private final DaoFactory daoFactory;
    private final List<ItemsModel> itemsModelList;

    public ConvertItemsGroup(DaoFactory daoFactory, List<ItemsModel> itemsModelList) {
        this.daoFactory = daoFactory;
        this.itemsModelList = itemsModelList;
    }

    private VBox vBox(List<ItemsModel> itemsModelList) {
        ListView<String> listView = new ListView<>();
//        listView.setPrefSize(200, 200);
        listView.setMaxHeight(200);
        for (ItemsModel itemsModel : itemsModelList) {
            listView.getItems().add(itemsModel.getId() + " - " + itemsModel.getNameItem());
        }
        VBox.setVgrow(listView, Priority.SOMETIMES);
        return new VBox(listView);
    }

    @Override
    public void start(Stage stage) throws Exception {
        var controller = new UpdateSomeItems(daoFactory, itemsModelList);
        Scene scene = new SceneAll(new OpenFxmlApplication(controller).getPane());
        stage.setScene(scene);
        stage.setTitle(HEADER_TEXT);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
        stage.setResizable(false);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }
}
