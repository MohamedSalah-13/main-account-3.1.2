package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.AddItemController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.table.StageDimensions;
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
    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;

    public AddItemApplication() {
        this(0, new DataPublisher(), DownLoadApplication.getDaoFactory());
        LoadDataAndList loadDataAndList = new LoadDataAndList(daoFactory);
        loadDataAndList.get2ItemsLoad();
    }

    public static void main(String[] args) {
        LogApplication.usersVo = new Users(1);
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        var addItemController = new AddItemController(num, dataPublisher, daoFactory);
        Scene scene = new SceneAll(addItemController.pane());
        stage.setScene(scene);
        if (num == 0)
            stage.setTitle("إضافة صنف");
        else stage.setTitle("تعديل صنف");
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().itemWhite));
        stage.setResizable(true);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);

        KeyCodeCombination KEY_BTN_SAVE = new KeyCodeCombination(KeyCode.F10);
        var btnSave = addItemController.getBtnSave();
        scene.getAccelerators().put(KEY_BTN_SAVE, btnSave::fire);
    }
}
