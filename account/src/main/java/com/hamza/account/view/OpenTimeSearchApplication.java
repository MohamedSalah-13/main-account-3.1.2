package com.hamza.account.view;

import com.hamza.account.controller.others.TimeSearchController;
import com.hamza.account.controller.pos.DialogButtons;
import com.hamza.account.openFxml.OpenFxmlApplication;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import lombok.Getter;

import java.io.IOException;

public class OpenTimeSearchApplication extends Dialog {

    @Getter
    private final TimeSearchController timeSearchController;

    public OpenTimeSearchApplication() throws IOException {
        timeSearchController = new TimeSearchController();
        DialogPane dialogPane = this.getDialogPane();
        dialogPane.setContent(new OpenFxmlApplication(timeSearchController).getPane());
        var ok = ButtonType.OK;
        var cancel = ButtonType.CANCEL;

        dialogPane.getButtonTypes().addAll(ok, cancel);
        DialogButtons.changeNameAndGraphic(dialogPane);

    }

}