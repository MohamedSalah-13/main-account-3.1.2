package com.hamza.account.controller.others;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.ToggleButton;

public class SelectedButton {

    public SelectedButton(ToggleButton toggleButton) {
        toggleButton.setText(Setting_Language.SELECT_ALL);

        toggleButton.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            clearSelection(t1);
            if (t1) {
                toggleButton.setText(Setting_Language.CANCEL_SELECT_ALL);
            } else toggleButton.setText(Setting_Language.SELECT_ALL);
        });
    }

    public void clearSelection(boolean b) {

    }
}
