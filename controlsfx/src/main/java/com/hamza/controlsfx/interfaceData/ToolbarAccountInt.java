package com.hamza.controlsfx.interfaceData;

import com.hamza.controlsfx.observer.Publisher;
import javafx.collections.ObservableList;

public interface ToolbarAccountInt<T> {

    /**
     * Adds a new account to the system.
     * <p>
     * This method is used to facilitate the creation or initialization
     * of a new account in the application. It may perform necessary
     * actions such as data allocation, registration, or preparation
     * required to successfully add an account.
     * <p>
     * Note: The specific implementation details for adding a new
     * account depend on the specific context in which the method is used.
     */
    void addNewAccount();

    /**
     * Deletes an account from the system.
     *
     * @return an integer indicating the result of the deletion operation,
     *         typically 1 if the deletion was successful and 0 if it failed
     * @throws Exception if an error occurs during the deletion process
     */
    int deleteAccount() throws Exception;

    /**
     * Displays account-related details or information.
     * <p>
     * This method is intended to handle the functionality for presenting account data,
     * typically in a user interface or console log. It does not take any parameters
     * and does not return a value.
     * <p>
     * The actual implementation and purpose of this method depend on the specific context
     * of the implementing class. This declaration serves as a contract for implementers
     * to define how the account details should be printed or displayed.
     */
    void printAccount();

    /**
     * Saves the account data to the data source or repository.
     *
     * @return an integer indicating the result of the save operation, which may correspond to the number
     *         of affected records or a status code depending on the implementation details
     * @throws Exception if an error occurs while saving the account data
     */
    T saveAccount() throws Exception;

    /**
     * Navigates to the first page in the pagination of the specified data type.
     *
     * @param t the data or context required for navigating to the first page
     */
    void firstPage(T t);

    /**
     * Navigates to the previous page of the dataset or view associated with the provided instance.
     *
     * @param t the instance of type T representing the element or context for which the previous page needs to be retrieved or displayed
     */
    void previousPage(T t);

    /**
     * Moves to the next page in the context of pagination or similar navigation functionality.
     *
     * @param t an instance of type T used for navigation or determining the next page
     */
    void nextPage(T t);

    /**
     * Navigates to the last page of the given content or data associated with the specified object.
     *
     * @param t the object used to determine or represent the content or data for the last page
     */
    void lastPage(T t);

    /**
     * Retrieves an observable list of items of type T.
     *
     * @return an ObservableList containing items of type T
     */
    ObservableList<T> observableList();

    /**
     * Executes operations or tasks that need to occur after an account
     * has been successfully saved or deleted. This method can be used
     * to handle post-save or post-delete actions such as refreshing
     * user interfaces, updating logs, or triggering notifications.
     */
    void afterSaveOrDelete();

    Publisher<String> publisherTable();
}
