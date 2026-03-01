package com.hamza.account.table;

public interface ActionButtonToolBar<T> {

    /**
     * Initiates an action to open a new item or entity.
     *
     * @throws Exception if an error occurs while attempting to open the new item.
     */
    default void openNew() throws Exception {
    }

    /**
     * Initiates the print operation usually tied to the ActionButtonToolBar.
     *
     * @throws Exception if an error occurs during the print operation
     */
    default void print() throws Exception {
    }

    /**
     * Updates the specified instance of type T.
     *
     * @param t the instance to be updated
     * @throws Exception if an error occurs during the update process
     */
    default void update(T t) throws Exception {
    }

    /**
     * Deletes the specified object.
     *
     * @param t the object to be deleted
     * @return an integer indicating the result of the delete operation
     * @throws Exception if an error occurs during deletion
     */
    default int delete(T t) throws Exception {
        return 0;
    }

    /**
     * A method to be called after a delete operation.
     * This method is intended to perform any post-deletion tasks
     * such as cleanup, logging, or triggering other actions.
     */
    void afterDelete();
}
