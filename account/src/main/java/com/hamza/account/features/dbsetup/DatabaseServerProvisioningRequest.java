package com.hamza.account.features.dbsetup;

/**
 * One administrator-authorized request to prepare a schema and a restricted
 * MySQL account. Passwords are deliberately excluded from {@link #toString()}.
 */
public record DatabaseServerProvisioningRequest(
        String serverHost,
        int port,
        String database,
        String administratorUsername,
        String administratorPassword,
        String applicationUsername,
        String applicationPassword,
        String allowedHost
) {

    @Override
    public String toString() {
        return "DatabaseServerProvisioningRequest[serverHost=" + serverHost
                + ", port=" + port
                + ", database=" + database
                + ", administratorUsername=" + administratorUsername
                + ", administratorPassword=<hidden>"
                + ", applicationUsername=" + applicationUsername
                + ", applicationPassword=<hidden>"
                + ", allowedHost=" + allowedHost + "]";
    }
}
