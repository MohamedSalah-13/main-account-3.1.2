package com.hamza.controlsfx.button.api;

import org.jetbrains.annotations.NotNull;

public interface ButtonColumnI extends BasicsSettingInterface {

    /**
     * Executes an action associated with the specified index.
     *
     * @param index the index of the item for which the action should be performed
     * @throws Exception if any error occurs during the action execution
     */
    void action(int index) throws Exception;

    /**
     * Retrieves the title for the column in the table.
     *
     * @return a non-null string representing the column title.
     */
    @NotNull String columnTitle();

    /**
     * Returns the default column name.
     *
     * @return the default column name, which is "D".
     */
    default String columnName() {
        return "buttonColumnName";
    }

    /**
     * Indicates whether the button at the given index should be disabled.
     * <p>
     * Return true to disable the button (equivalent to calling setDisable(true)).
     * Return false to keep it enabled.
     *
     * @param index zero-based row index relevant to the button.
     * @return true to disable; false to enable (default).
     */
    default boolean isButtonDisabled(int index) {
        // Delegate to legacy method for backward compatibility with existing overrides.
        return false;
    }
}
