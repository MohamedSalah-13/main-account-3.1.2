package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.pos.DialogButtons;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.others.ChangeOrientation;
import com.hamza.controlsfx.view.DialogApplication;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class OpenApplication<T> {

    public OpenApplication(AppSettingInterface appSettingInterface) throws Exception {
        DialogApplication<Integer> objectDialogApplication = new DialogApplication<>(appSettingInterface);
        var dialogPane = objectDialogApplication.getDialogPane();
        var scene = dialogPane.getScene();
        ChangeOrientation.sceneOrientation(scene);
        ThemeManager.apply(scene);

        if (appSettingInterface.addLastPane()) {
            DialogButtons.changeNameAndGraphic(dialogPane);
        }

        Stage stage = (Stage) scene.getWindow();
        stage.getIcons().add(new Image(new Image_Setting().tools));

        // أزل resultConverter من هنا - أصبح التعامل في DialogApplication

        var resultOpt = objectDialogApplication.showAndWait();
        if (resultOpt.isPresent() && Integer.valueOf(1).equals(resultOpt.get())) {
            // نجاح الحفظ فقط
            appSettingInterface.afterSaved();
            AllAlerts.alertSave();
            log.info("Open Application");
        } else {
            // إلغاء أو فشل - لا تفعل شيئاً
            log.debug("Dialog closed without successful save.");
        }
    }

}

