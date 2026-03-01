package com.hamza.controlsfx.button.api;

public interface ButtonColumnBoolean extends ButtonColumnI {

    /**
     * Performs an action based on the given index and boolean value.
     *
     * @param index the index to determine the action context
     * @param b the boolean value to influence the action
     * @throws Exception if an error occurs during the action execution
     */
    void action(int index, boolean b) throws Exception;

    /**
     * Selects the button at the specified index.
     *
     * @param index the index of the button to be selected.
     * @return a boolean indicating whether the button at the specified index is selected.
     */
    boolean selectButton(int index);
}
