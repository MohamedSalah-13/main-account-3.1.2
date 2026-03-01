package com.hamza.controlsfx.interfaceData;

public interface ChangePassInt {

    /**
     * Retrieves the current actual password.
     *
     * @return the actual password as a String
     */
    String actualPass();

    /**
     * Updates the current password with a new password.
     *
     * @param newPass the new password to replace the current password
     * @return true if the password was successfully updated, false otherwise
     * @throws Exception if an error occurs during the update process
     */
    boolean updatePass(String newPass) throws Exception;
}
