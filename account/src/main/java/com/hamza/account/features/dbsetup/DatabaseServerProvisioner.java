package com.hamza.account.features.dbsetup;

import java.sql.SQLException;

@FunctionalInterface
public interface DatabaseServerProvisioner {
    DatabaseServerProvisioningResult provision(DatabaseServerProvisioningRequest request) throws SQLException;
}
