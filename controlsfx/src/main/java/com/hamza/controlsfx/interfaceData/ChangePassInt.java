package com.hamza.controlsfx.interfaceData;

public interface ChangePassInt {

    /**
     * Verifies a candidate password against the currently stored password.
     *
     * @param candidatePassword the password entered by the user to verify
     * @return true if it matches the current password
     */
    boolean verifyCurrentPassword(String candidatePassword);

    /**
     * Updates the current password with a new password.
     *
     * @param newPass the new password to replace the current password
     * @return true if the password was successfully updated, false otherwise
     * @throws Exception if an error occurs during the update process
     */
    boolean updatePass(String newPass) throws Exception;
}
