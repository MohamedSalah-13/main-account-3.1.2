package com.hamza.account.openFxml;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;

@Log4j2
public class AddForAllApplication {

    public AddForAllApplication(int codeId, AddInterface controller) throws Exception {
        Pane load = new OpenFxmlApplication(controller).getPane();
        load.getStylesheets().add(controller.styleSheet());
        AppSettingInterface appSettingInterface = new AppSettingInterface() {
            @Override
            public Pane pane() {
                return load;
            }

            @Override
            public String title() {
                return codeId > 0 ? Setting_Language.WORD_UPDATE : Setting_Language.WORD_ADD;
            }

            @Override
            public InputStream inputStream() {
                return new Image_Setting().tools;
            }

            @Override
            public BooleanBinding checkDataToEnableButton() {
                return controller.checkDataToEnableButton();
            }

            @Override
            public boolean resize() {
                return controller.resize();
            }

            @Override
            public boolean closeAfterSave() {
                return codeId > 0;
            }

            @Override
            public boolean addLastPane() {
                return true;
            }

            @Override
            public int save() throws Exception {
                return controller.insertData();
            }

            @Override
            public void afterSaved() {
                controller.afterSaved();
            }
        };

        new OpenApplication<>(appSettingInterface);
    }
}
