package com.hamza.account.features.dbsetup;

/** The non-secret result shown after a database account is prepared. */
public record DatabaseServerProvisioningResult(String database, String account,
                                               PasswordOutcome password) {

    /**
     * What happened to the account's password, which is the one thing the technician
     * cannot see and must not have to guess: whether the password they just typed is
     * the password that account now has.
     */
    public enum PasswordOutcome {
        /** The account did not exist and was created with the password entered here. */
        CREATED,
        /** The account existed and its password was replaced, as explicitly asked. */
        RESET,
        /**
         * The account existed and keeps the password it had. Also the answer when the
         * administrator could not read {@code mysql.user}, so this never claims a
         * password was set when it may not have been.
         */
        UNCHANGED
    }
}
