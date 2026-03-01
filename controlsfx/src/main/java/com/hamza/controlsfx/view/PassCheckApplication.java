package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.PassCheckController;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.StringConstants;
import com.hamza.controlsfx.others.ImageSetting;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.awt.*;
import java.io.InputStream;

public class PassCheckApplication extends Dialog<Boolean> {

    private final FXMLLoader fxmlLoader;

    public PassCheckApplication(String pass) throws Exception {
        fxmlLoader = new FXMLLoader(getClass().getResource("pass-check.fxml"));
        PassCheckController passCheckController = new PassCheckController();
        fxmlLoader.setController(passCheckController);
        this.getDialogPane().setContent(fxmlLoader.load());
        this.getDialogPane().setHeaderText(appSettingInterface().header());
        this.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        this.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return passCheckController.getPasswordField().getText().equals(pass);
            }
            return false;
        });

        this.setTitle(appSettingInterface().title());
        this.setResizable(false);

        var window = (Stage) this.getDialogPane().getScene().getWindow();
        window.setTitle(appSettingInterface().title());
        window.getIcons().add(new Image(appSettingInterface().inputStream()));
        Toolkit.getDefaultToolkit().beep();
    }

    private AppSettingInterface appSettingInterface() {
        return new AppSettingInterface() {
            @Override
            public Pane pane() throws Exception {
                return fxmlLoader.load();
            }

            @Override
            public String title() {
                return StringConstants.PASSWORD;
            }

            @Override
            public String header() {
                return StringConstants.PLEASE_ENTER_YOUR_SYSTEM_PASSWORD;
            }

            @Override
            public InputStream inputStream() {
                return new ImageSetting().PASSWORD;
            }

            @Override
            public boolean addLastPane() {
                return true;
            }
        };
    }
}
