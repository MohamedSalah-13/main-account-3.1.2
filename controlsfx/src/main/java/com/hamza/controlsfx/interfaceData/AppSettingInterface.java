package com.hamza.controlsfx.interfaceData;

import javafx.beans.binding.BooleanBinding;
import javafx.scene.layout.Pane;

import java.io.InputStream;

public interface AppSettingInterface extends ActionSave {

    Pane pane() throws Exception;

    default String title() {
        return null;
    }

    default String header() {
        return null;
    }

    default InputStream inputStream() {
        return null;
    }

    default BooleanBinding checkDataToEnableButton() {
        return new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return false;
            }
        };
    }

    default boolean resize() {
        return false;
    }

    default boolean closeAfterSave() {
        return false;
    }

    default boolean addLastPane() {
        return false;
    }

    default double minHeight() {
        return 200;
    }

    default double minWidth() {
        return 400;
    }

    default boolean addInitModality() {
        return true;
    }
}
