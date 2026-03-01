package com.hamza.controlsfx.button.api;

import javafx.scene.Node;
import javafx.scene.input.KeyCodeCombination;

public interface MenuItemInterface {

    default Node imageMenu() {
        return null;
    }

    default KeyCodeCombination acceleratorKey() {
        return null;
    }
}
