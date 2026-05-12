package com.hamza.account.openFxml;

import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.others.CssToColorHelper;

public interface MainData {

    /**
     * Retrieves the main CSS stylesheet used for primary styling rules in the application.
     *
     * @return the path to the main CSS stylesheet as a String.
     */
    default String styleSheet() {
        return ThemeManager.getStylesheet();
    }

    /**
     * A helper method to assist with CSS to color conversion.
     *
     * @param helper an instance of CssToColorHelper that provides the necessary methods
     *               to perform the conversion from CSS styles to color objects.
     */
    default void helper(CssToColorHelper helper) {
    }
}
