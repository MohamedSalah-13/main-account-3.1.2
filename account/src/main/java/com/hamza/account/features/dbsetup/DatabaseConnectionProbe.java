package com.hamza.account.features.dbsetup;

import java.sql.SQLException;

@FunctionalInterface
public interface DatabaseConnectionProbe {
    DatabaseProbeResult test(DatabaseConnectionSettings settings) throws SQLException;
}
