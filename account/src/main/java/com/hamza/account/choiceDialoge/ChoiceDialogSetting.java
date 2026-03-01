package com.hamza.account.choiceDialoge;

import com.hamza.account.config.Style_Sheet;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;

import java.util.List;

public class ChoiceDialogSetting extends ChoiceDialog<String> {

    public ChoiceDialogSetting(List<String> stringList, ChoiceData choiceData) {
        getItems().addAll(stringList);
        setTitle(choiceData.titleName());
        setHeaderText(choiceData.HeaderText());
        setContentText(choiceData.contentName());
        setGraphic(new ImageDesign(choiceData.graphic(), 80)); // Custom graphic

        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        stage.getIcons().add(choiceData.stageGraphic());
        Scene scene = stage.getScene();

        Style_Sheet.changeStyle(scene);
        ChangeOrientation.sceneOrientation(scene);

        final Button button = (Button) getDialogPane().lookupButton(ButtonType.OK);
        button.setDefaultButton(false);
        button.setText(Setting_Language.OK);

        final Button buttonCancel = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        buttonCancel.setText(Setting_Language.WORD_CLOSE);
//        buttonCancel.getStyleClass().add(Css_Names.PANE_BOX_LAST_CSS);
        buttonCancel.setId("btnClose");
//        buttonCancel.getStyleClass().add(Css_Names.BUTTON_CLOSE_CSS);
    }
}
