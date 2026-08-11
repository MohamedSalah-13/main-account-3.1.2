package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.PassCheckController;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
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

    /**
     * The loaded content, kept rather than the loader.
     * <p>
     * {@code pane()} used to call {@code load()} on the same loader a second time,
     * which builds a second copy of the form with a controller nobody holds - the
     * password would have been read off the first. Nothing calls it today; keeping
     * the pane is what stops the next caller finding out the hard way.
     */
    private final Pane content;

    public PassCheckApplication(String pass) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("pass-check.fxml"));
        PassCheckController passCheckController = new PassCheckController();
        fxmlLoader.setController(passCheckController);
        content = fxmlLoader.load();
        this.getDialogPane().setContent(content);
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
            public Pane pane() {
                return content;
            }


            @Override
            public String title() {
                return Error_Text_Show.PASSWORD;
            }

            @Override
            public String header() {
                return Error_Text_Show.PLEASE_ENTER_YOUR_SYSTEM_PASSWORD;
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
