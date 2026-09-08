package com.hamza.account.features.dbsetup;

/**
 * One administrator-authorized request to prepare a schema and a restricted
 * MySQL account. Passwords are deliberately excluded from {@link #toString()}.
 *
 * @param allowedHost            the pattern MySQL matches a client against, already in the
 *                               form the server understands - see
 *                               {@link DatabaseServerSetupService#mysqlHostPattern}
 * @param resetExistingPassword  whether an account that already exists is to have its
 *                               password replaced. Off by default because the account is
 *                               shared: every till already configured holds the old
 *                               password, and rotating it here signs all of them out
 */
public record DatabaseServerProvisioningRequest(
        String serverHost,
        int port,
        String database,
        String administratorUsername,
        String administratorPassword,
        String applicationUsername,
        String applicationPassword,
        String allowedHost,
        boolean resetExistingPassword
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
                + ", allowedHost=" + allowedHost
                + ", resetExistingPassword=" + resetExistingPassword + "]";
    }
}
