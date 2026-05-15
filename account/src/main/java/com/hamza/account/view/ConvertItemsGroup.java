package com.hamza.account.view;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.UpdateSomeItems;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SupGroupService;
import com.hamza.controlsfx.database.DaoException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class ConvertItemsGroup extends Application {

    public static final String HEADER_TEXT = "تعديل مجموعة أصناف";
    private final List<ItemsModel> itemsModelList;

    public ConvertItemsGroup(List<ItemsModel> itemsModelList) {
        this.itemsModelList = itemsModelList;
    }
    public ConvertItemsGroup() throws DaoException {
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        var connection = new ConnectionToDatabase().getDbConnection().getConnection();
        daoFactory.setConnection(connection);
        ServiceRegistry.register(ItemsService.class, new ItemsService(daoFactory));
        ServiceRegistry.register(MainGroupService.class, new MainGroupService(daoFactory));
        ServiceRegistry.register(SupGroupService.class, new SupGroupService(daoFactory));
        ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
        this.itemsModelList = itemsService.getFilterItems("م").stream().toList();
    }


    @Override
    public void start(Stage stage) throws Exception {
        var controller = new UpdateSomeItems(itemsModelList);
        Scene scene = new SceneAll(new OpenFxmlApplication(controller).getPane());
        stage.setScene(scene);
        stage.setTitle(HEADER_TEXT);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
//        stage.setResizable(false);
//        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }

    public static void main(String[] args) throws Exception {
        launch(args);
    }
}
