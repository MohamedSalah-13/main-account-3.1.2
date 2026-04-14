package com.hamza.account.controller.pos;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.Style_Sheet;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.awt.*;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

public class DialogButtons {

    public static void changeNameAndGraphic(DialogPane dialog) {
        var images = new Image_Setting();
        Button okButton = (Button) dialog.lookupButton(ButtonType.OK);
        okButton.setText(Setting_Language.OK);
        okButton.setGraphic(createIcon(images.save));

        Button cancelButton = (Button) dialog.lookupButton(ButtonType.CANCEL);
        cancelButton.setText(Setting_Language.WORD_CANCEL);
        cancelButton.setGraphic(createIcon(images.cancel));
        cancelButton.setId("btnClose");

        var scene = dialog.getScene();
        ChangeOrientation.sceneOrientation(scene);
        Style_Sheet.changeStyle(scene);

        Toolkit.getDefaultToolkit().beep();

        Stage stage = (Stage) scene.getWindow();
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.close();
        });

        // Set dialog icon
        stage.getIcons().add(new Image(new Image_Setting().tools));
    }
}
