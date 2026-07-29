package com.hamza.controlsfx.interfaceData;

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

    default boolean resize() {
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

}
