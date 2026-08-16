package com.hamza.account.otherSetting;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.stage.Stage;

public class ExitClass {

    public void exit(Stage stage) {
        stage.setOnCloseRequest(windowEvent -> {
            var lang = LanguageManager.getInstance();
            if (AllAlerts.confirm_all(lang.getString("session.exit.title"), lang.getString("msg.exit.confirm"))) {
                stage.close();
                updateData();
            } else windowEvent.consume();
        });
    }

    public void updateData() {

    }
}
