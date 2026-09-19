package com.hamza.account.features.dbsetup;

import java.sql.SQLException;

/** What is done as {@code root} on the freshly initialized server, as a seam. */
public interface LocalRootAccount {

    /** Gives the passwordless root of {@code --initialize-insecure} its password. */
    void secure(int port, String newPassword) throws SQLException;

    /** Asks the server to shut down cleanly, so the data directory is closed, not abandoned. */
    void shutdown(int port, String password) throws SQLException;
}
