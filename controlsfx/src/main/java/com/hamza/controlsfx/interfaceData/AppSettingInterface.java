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

    /**
     * An optional semantic CSS class for the dialog shell and its action bar.
     *
     * <p>Most dialogs use the shared appearance. Feature-specific dialogs can opt into a
     * palette without exposing their feature types to the common dialog implementation.</p>
     */
    default String dialogStyleClass() {
        return null;
    }

    /**
     * Keeps a successful data-entry dialog open so the caller can immediately
     * enter another record. The default preserves the usual save-and-close flow.
     */
    default boolean keepDialogOpenAfterSave() {
        return false;
    }

    default double minHeight() {
        return 200;
    }

    default double minWidth() {
        return 400;
    }

}
