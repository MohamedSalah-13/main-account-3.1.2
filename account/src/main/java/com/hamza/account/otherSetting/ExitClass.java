package com.hamza.account.otherSetting;

import com.hamza.controlsfx.alert.AllAlerts;
import javafx.stage.Stage;

public class ExitClass {

    public void exit(Stage stage) {
        stage.setOnCloseRequest(windowEvent -> {
            if (AllAlerts.confirm_all("هل تريد الخروج")) {
                stage.close();
                updateData();
            } else windowEvent.consume();
        });
    }

    public void updateData() {

    }
}
