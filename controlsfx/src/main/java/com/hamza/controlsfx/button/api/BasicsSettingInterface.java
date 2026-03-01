package com.hamza.controlsfx.button.api;

import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;

public interface BasicsSettingInterface {

    /**
     * Retrieves the text name associated with a specific setting or button. This text
     * is generally used for display purposes in UI elements such as buttons or labels.
     *
     * @return a non-null string representing the text name.
     */
    @NotNull String textName();

    /**
     * Provides a default implementation to return an image node associated with a button.
     * This implementation returns null by default and can be overridden as needed.
     *
     * @return the image node associated with the button, or null if not specified
     */
    default Node imageNode() {
        return null;
    }

    /**
     * Determines the disabled state of a setting.
     *
     * @return false indicating the default state is not disabled.
     */
    default boolean disableBoolean() {
        return false;
    }

}
