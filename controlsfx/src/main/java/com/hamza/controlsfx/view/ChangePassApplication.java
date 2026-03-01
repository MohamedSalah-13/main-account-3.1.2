package com.hamza.controlsfx.view;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.controller.ChangePassController;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.interfaceData.ChangePassInt;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ImageSetting;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Pane;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;

@Getter
@Log4j2
public class ChangePassApplication {
    private final DialogApplication<Boolean> dialogApplication;

    public ChangePassApplication(ChangePassInt changePassInt) throws Exception {
        var appSettingInterface = appSettingInterface(changePassInt);
        dialogApplication = new DialogApplication<>(appSettingInterface);
        dialogApplication.setResultConverter(buttonType -> {
            try {
                if (buttonType == ButtonType.OK) {
                    if (AllAlerts.confirmSave()) {
                        return appSettingInterface.save() == 1;
                    }
                }
            } catch (Exception e) {
                AllAlerts.showExceptionDialog(e);
                log.error(e.getMessage());
            }
            return false;
        });
    }

    private AppSettingInterface appSettingInterface(ChangePassInt changePassInt) {
        return new AppSettingInterface() {
            final ChangePassController controller = new ChangePassController(changePassInt);

            @Override
            public Pane pane() throws Exception {
                FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("changePass-view.fxml"));
                fxmlLoader.setController(controller);
                return fxmlLoader.load();
            }

            @Override
            public String title() {
                return Setting_Language.CHANGE_PASS;
            }

            @Override
            public InputStream inputStream() {
                return new ImageSetting().IMAGE_PASS;
            }

            @Override
            public boolean addLastPane() {
                return true;
            }

            @Override
            public int save() throws Exception {
                return controller.saveData();
            }
        };
    }
}
