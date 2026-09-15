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
 * @param allowStoredPrograms    whether the run may persist
 *                               {@code log_bin_trust_function_creators = 1} on the server
 *                               when it would refuse the application's triggers. It is a
 *                               permanent, server-wide relaxation, so it is the technician's
 *                               decision on screen - ticked by default, because without it
 *                               the program cannot finish its first start
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
        boolean resetExistingPassword,
        boolean allowStoredPrograms
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
                + ", resetExistingPassword=" + resetExistingPassword
                + ", allowStoredPrograms=" + allowStoredPrograms + "]";
    }
}
