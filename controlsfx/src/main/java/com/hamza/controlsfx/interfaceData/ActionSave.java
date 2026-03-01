package com.hamza.controlsfx.interfaceData;

public interface ActionSave {

    /**
     * Saves the current state or changes.
     *
     * @return an integer status code, typically 0 for no changes and 1 for successful save.
     * @throws Exception if an error occurs during the save process.
     */
    default int save() throws Exception {
        return 0;
    }

    /**
     * Invoked after a save operation is successfully completed.
     * This method can be overridden to perform additional actions
     * or cleanup tasks post-save.
     *
     * @throws Exception if any error occurs during after-save operations
     */
    default void afterSaved() throws Exception {

    }
}
