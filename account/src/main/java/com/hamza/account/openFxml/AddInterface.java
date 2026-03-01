package com.hamza.account.openFxml;

import javafx.beans.binding.BooleanBinding;
import org.jetbrains.annotations.NotNull;

/**
 * The AddInterface provides methods for setting up the user interface, handling data insertion,
 * managing post-save operations, selecting data based on an identifier, resetting input fields,
 * and checking conditions to enable buttons. This interface is meant to be extended or implemented
 * by classes that need to handle these functionalities within an application.
 */
public interface AddInterface extends MainData {

    /**
     * This method sets up various UI components with initial values and settings.
     * It initializes UI labels, sets prompt texts for input fields, and requests focus for a primary input field.
     * Additionally, for specific implementations, it might initialize other UI components like combo boxes and date pickers,
     * and attach listeners to these components to handle interactions.
     */
    void otherSetting();

    /**
     * Inserts data into the specified storage or database and returns an integer result.
     *
     * @return an integer indicating the outcome of the insert operation. It could be the number of
     * records inserted, a status code, or another relevant metric.
     * @throws Exception if there is any issue during the insertion process.
     */
    int insertData() throws Exception;

    /**
     * This method is invoked after the data has been saved successfully.
     * Implementing classes should define the actions to be performed post-save.
     * This may include updating the user interface, resetting fields, or other
     * housekeeping tasks necessary after a save operation.
     */
    void afterSaved();

    /**
     * Populates the data fields with the corresponding data retrieved by the identifier.
     * This method is used to select and display data when an identifier is provided.
     * It performs the following actions:
     * - Retrieves the data object associated with the identifier.
     * - If the data object is found, populates the relevant text fields with the data values.
     * - Logs any errors that occur during the data retrieval process.
     */
    void selectData();

    /**
     * Resets the input fields to their initial state.
     * <p>
     * This method is typically used to clear any user input and set
     * default values for the input fields, preparing the interface
     * for a new data entry.
     * <p>
     * It is called during initialization, after data is saved, and
     * whenever a fresh state is needed.
     */
    void resetData();

    /**
     * Checks if the data conditions are met to enable a button.
     * This method provides a binding that evaluates to true or false based on certain predetermined conditions.
     *
     * @return a BooleanBinding object which determines if the data conditions are met to enable a button.
     */
    @NotNull BooleanBinding checkDataToEnableButton();

    default boolean resize() {
        return false;
    }
}
