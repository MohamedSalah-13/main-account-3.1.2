package com.hamza.account.interfaces;

import com.hamza.account.controller.login.LoginResult;

@FunctionalInterface
public interface ActionLogin {

    /**
     * Checks the validity of the provided username and password by matching them
     * against stored credentials in the database for login authentication.
     *
     * @param username the username to be validated
     * @param password the password associated with the provided username
     * @return the authentication outcome
     * @throws Exception if an error occurs while checking the credentials in the database
     */
    // this use to get username and password  from database to check
    // and login or not
    LoginResult action(String username, String password) throws Exception;
}
