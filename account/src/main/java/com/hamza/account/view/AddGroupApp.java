package com.hamza.account.view;

import com.hamza.account.controller.groups.AddSubGroupController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.application.Application;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddGroupApp extends Application {

    private final Publisher<String> publisherAddGroup;

    @Override
    public void start(Stage primaryStage) throws Exception {
        final AddSubGroupController areaController = new AddSubGroupController(publisherAddGroup);
        OpenFxmlApplication openFxmlApplication = new OpenFxmlApplication(areaController);
        SceneAll sceneAll = new SceneAll(openFxmlApplication.getPane());
        primaryStage.setScene(sceneAll);
        primaryStage.setResizable(false);
        primaryStage.setTitle(Setting_Language.WORD_SUB_G);
        primaryStage.initModality(Modality.APPLICATION_MODAL);
        primaryStage.show();
    }
}
