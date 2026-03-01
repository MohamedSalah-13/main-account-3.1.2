package com.hamza.controlsfx.interfaceData;

@FunctionalInterface
public interface ActionLogin {

    /**
     * Checks the validity of the provided username and password by matching them
     * against stored credentials in the database for login authentication.
     *
     * @param username the username to be validated
     * @param password the password associated with the provided username
     * @return true if the username and password combination exists and is valid, false otherwise
     * @throws Exception if an error occurs while checking the credentials in the database
     */
    // this use to get username and password  from database to check
    // and login or not
    boolean action(String username, String password) throws Exception;
}
