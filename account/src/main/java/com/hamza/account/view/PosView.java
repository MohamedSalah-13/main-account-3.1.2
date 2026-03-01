package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.pos.PosController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.hamza.account.controller.pos.PosInvoiceSetting.SUSPENDED_DIR_NAME;

@RequiredArgsConstructor
public class PosView extends Application {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public PosView() {
        this.daoFactory = DownLoadApplication.getDaoFactory();
        this.dataPublisher = new DataPublisher();
    }

    public static void main(String[] args) throws Exception {
        LogApplication.usersVo = new Users(1);
        launch(args);
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

    private void getAVoid(Stage stage) {
        stage.setOnCloseRequest(event -> {

            Path suspendedDir = Path.of(SUSPENDED_DIR_NAME);
            String NO_SUSPENDED_MSG = "لا توجد فواتير معلقة";
            if (!Files.exists(suspendedDir)) {
                AllAlerts.alertError(NO_SUSPENDED_MSG);
                return;
            }

            try (var paths = Files.list(suspendedDir)) {
                paths.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

            event.consume();
            stage.close();
        });
    }
}
