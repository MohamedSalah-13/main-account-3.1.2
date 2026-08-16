package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.setting.SettingController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SettingApplication extends Application {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new SettingController(daoFactory, dataPublisher).pane());
        stage.setScene(scene);
        // PROGRAM_TITLE is the app's fixed brand name ("AccountK / حساباتك"), not a
        // translatable UI string, so it is left as-is here.
        stage.setTitle(LanguageManager.getInstance().getString("settings.title") + " - " + Setting_Language.PROGRAM_TITLE);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().setting));
        stage.setResizable(true);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }
}
