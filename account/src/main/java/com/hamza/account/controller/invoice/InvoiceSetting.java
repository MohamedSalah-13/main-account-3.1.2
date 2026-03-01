package com.hamza.account.controller.invoice;

import com.hamza.account.view.MainScreenApplication;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

public class InvoiceSetting {

    private void buttonAccelerators() {
        Scene sceneMainScreen = MainScreenApplication.sceneMainScreen;
        if (sceneMainScreen != null) {
            KeyCodeCombination KEY_BTN_PRINT_SAVE = new KeyCodeCombination(KeyCode.F12, KeyCombination.CONTROL_DOWN);
            KeyCodeCombination KEY_BTN_SAVE = new KeyCodeCombination(KeyCode.F10, KeyCombination.CONTROL_DOWN);
//            sceneMainScreen.getAccelerators().put(KEY_BTN_PRINT_SAVE, () -> btnPrintSave.fire());
//            sceneMainScreen.getAccelerators().put(KEY_BTN_SAVE, () -> btnSave.fire());
        }
    }

    private void changeColorPriceIfEmpty() {
        Background whiteBG = new Background(new BackgroundFill(Color.WHITE, new CornerRadii(2), new Insets(2)));
        Background colorBG = new Background(new BackgroundFill(Color.GOLD.darker(), new CornerRadii(2), new Insets(2)));
//        txtPrice.backgroundProperty().bind(new When(txtPrice.textProperty().isEqualTo("0.0")).then(colorBG).otherwise(whiteBG));
    }
}
