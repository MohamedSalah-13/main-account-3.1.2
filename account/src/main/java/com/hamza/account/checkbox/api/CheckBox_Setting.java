package com.hamza.account.checkbox.api;

import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;

public class CheckBox_Setting {

    /**
     * Constructor for initializing a CheckBox with the specified CheckBoxInterface.
     *
     * @param checkBox          The CheckBox to be initialized.
     * @param checkBoxInterface The interface providing the configuration and actions for the CheckBox.
     */
    public CheckBox_Setting(CheckBox checkBox, CheckBoxInterface checkBoxInterface) {
        initialize(checkBox, checkBoxInterface);
    }

    /**
     * Configures a CheckMenuItem with specified settings and behaviors. It initializes
     * the checkbox with the text from the CheckBoxInterface and sets its initial selected
     * state. Additionally, it adds a listener to handle changes in the checkbox's state.
     *
     * @param checkBox          An instance of CheckMenuItem to be configured.
     * @param checkBoxInterface An implementation of CheckBoxInterface that provides the
     *                          necessary methods for setting up the checkbox.
     */
    public CheckBox_Setting(CheckMenuItem checkBox, CheckBoxInterface checkBoxInterface) {
        initialize(checkBox, checkBoxInterface);
    }

    /**
     * Initializes the provided {@code checkBox} with values and behaviors defined by the {@code checkBoxInterface}.
     * Depending on the type of the {@code checkBox}, it sets its text, selected state, and action listener.
     *
     * @param checkBox          the UI component to be initialized; either a {@code CheckBox} or {@code CheckMenuItem}
     * @param checkBoxInterface an interface providing the values and actions to be applied to the {@code checkBox}
     */
    private void initialize(Object checkBox, CheckBoxInterface checkBoxInterface) {
        if (checkBox instanceof CheckBox cb) {
            cb.setText(checkBoxInterface.text_name());
            cb.setSelected(checkBoxInterface.getBoolean_saved());
            cb.selectedProperty().addListener((observable, oldValue, newValue) -> checkBoxInterface.action(newValue));
        } else if (checkBox instanceof CheckMenuItem cb) {
            cb.setText(checkBoxInterface.text_name());
            cb.selectedProperty().setValue(checkBoxInterface.getBoolean_saved());
            cb.selectedProperty().addListener((observable, oldValue, newValue) -> checkBoxInterface.action(newValue));
        }
    }
}
