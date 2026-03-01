package com.hamza.account.controller.invoice;

import com.hamza.account.controller.pos.DialogButtons;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class ChoiceItemExpireDate extends Dialog<LocalDate> {

    public ChoiceItemExpireDate(ExpireDateInterface expireDateInterface) {

        setTitle("Select Expiry Date");
        setHeaderText("Please choose an expiry date");

        VBox content = new VBox(10);
        content.getChildren().add(expireDateInterface.node());
        getDialogPane().setContent(content);

        getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        DialogButtons.changeNameAndGraphic(getDialogPane());
        setResultConverter(button -> {
            var date = expireDateInterface.getDate();
            if (button == javafx.scene.control.ButtonType.OK && date != null) {
                return date;
            }
            return null;
        });
    }
}

