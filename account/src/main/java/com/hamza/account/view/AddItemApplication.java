package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.AddItemController;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddItemApplication extends Application {

    private final int num;

    @Override
    public void start(Stage stage) throws Exception {
        var addItemController = new AddItemController(num);
        Scene scene = new SceneAll(addItemController.pane());
        stage.setScene(scene);
        var lm = LanguageManager.getInstance();
        stage.setTitle(num == 0 ? lm.getString("addItem") : lm.getString("updateItem"));
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
        stage.setResizable(true);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);

        KeyCodeCombination KEY_BTN_SAVE = new KeyCodeCombination(KeyCode.F10);
        var btnSave = addItemController.getBtnSave();
        scene.getAccelerators().put(KEY_BTN_SAVE, btnSave::fire);
    }
}
