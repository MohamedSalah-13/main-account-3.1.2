package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.CardController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.StageDimensions;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CardApplication extends Application {

    private final CardController cardController;

    public CardApplication(ItemsModel itemsModel, DaoFactory daoFactory, DataPublisher dataPublisher, MainItems mainItems) throws Exception {
        cardController = new CardController(itemsModel, daoFactory, dataPublisher, mainItems);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(cardController).getPane());
        stage.setScene(scene);
        stage.setTitle(Setting_Language.WORD_CARD_ITEM);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
        stage.setResizable(true);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);
    }
}
