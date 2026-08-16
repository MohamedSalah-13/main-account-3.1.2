package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.CardController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CardApplication extends Application {

    private final CardController cardController;

    public CardApplication(ItemsModel itemsModel, DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        cardController = new CardController(itemsModel, daoFactory, dataPublisher);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(cardController).getPane());
        stage.setScene(scene);
        stage.setTitle(LanguageManager.getInstance().getString("item.card.title"));
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setResizable(true);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }
}
