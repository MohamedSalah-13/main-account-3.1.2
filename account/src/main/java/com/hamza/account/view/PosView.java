package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.pos.PosController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PosView extends Application {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public PosView() {
        this.daoFactory = DownLoadApplication.getDaoFactory();
        this.dataPublisher = new DataPublisher();
    }

    @Override
    public void start(Stage stage) throws Exception {
        var controller = new PosController(daoFactory, dataPublisher);
        var pane = new OpenFxmlApplication(controller).getPane();
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setTitle("POS System - Hamza");
        stage.setMaximized(true);
        Scene scene = new Scene(pane, bounds.getWidth() - 20, bounds.getHeight() - 20);
        ChangeOrientation.sceneOrientation(scene);
        stage.setScene(scene);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().shoppingSalesPOS));
        stage.show();

        var btnPay = controller.getBtnPay();
        btnPay.setText(btnPay.getText() + " (F10)");
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F10), btnPay::fire);

        dataPublisher.getCloseStageFromLogout().addObserver(message -> {
            if (message) {
                stage.close();
            }
        });
    }
}
