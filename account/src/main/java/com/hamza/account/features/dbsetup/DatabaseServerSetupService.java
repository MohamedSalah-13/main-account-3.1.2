package com.hamza.account.features.dbsetup;

import java.sql.SQLException;
import java.util.regex.Pattern;

/** Validates the privileged server operation before handing it to JDBC. */
public final class DatabaseServerSetupService {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,32}");
    private static final Pattern IPV4 = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern IPV4_CIDR = Pattern.compile("((?:\\d{1,3}\\.){3}\\d{1,3})/(\\d|[12]\\d|3[0-2])");

    private final DatabaseServerProvisioner provisioner;

    public DatabaseServerSetupService(DatabaseServerProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    public DatabaseServerProvisioningRequest validate(
            String serverHost,
            String port,
            String database,
            String administratorUsername,
            String administratorPassword,
            String applicationUsername,
            String applicationPassword,
            String allowedHost
    ) throws DatabaseSetupException {
        String cleanServerHost = clean(serverHost);
        String cleanDatabase = clean(database);
        String cleanAdministrator = clean(administratorUsername);
        String cleanApplicationUser = clean(applicationUsername);
        String cleanAllowedHost = clean(allowedHost);

        if (!HOST.matcher(cleanServerHost).matches()) {
            throw new DatabaseSetupException("dbsetup.validation.host");
        }
        int parsedPort = parsePort(port);
        if (!DATABASE.matcher(cleanDatabase).matches()) {
            throw new DatabaseSetupException("dbsetup.validation.database");
        }
        if (!USERNAME.matcher(cleanAdministrator).matches()) {
            throw new DatabaseSetupException("dbsetup.provision.validation.admin.username");
        }
        if (administratorPassword == null || administratorPassword.isEmpty()) {
            throw new DatabaseSetupException("dbsetup.provision.validation.admin.password");
        }
        if (!USERNAME.matcher(cleanApplicationUser).matches()) {
            throw new DatabaseSetupException("dbsetup.provision.validation.application.username");
        }
        if (cleanApplicationUser.equalsIgnoreCase(cleanAdministrator)
                || isReservedAccount(cleanApplicationUser)) {
            throw new DatabaseSetupException("dbsetup.provision.validation.application.reserved");
        }
        if (applicationPassword == null || applicationPassword.length() < 12) {
            throw new DatabaseSetupException("dbsetup.provision.validation.application.password");
        }
        if (!validAllowedHost(cleanAllowedHost)) {
            throw new DatabaseSetupException("dbsetup.provision.validation.allowed.host");
        }

        return new DatabaseServerProvisioningRequest(cleanServerHost, parsedPort, cleanDatabase,
                cleanAdministrator, administratorPassword, cleanApplicationUser, applicationPassword,
                cleanAllowedHost);
    }

    public DatabaseServerProvisioningResult provision(DatabaseServerProvisioningRequest request)
            throws DatabaseSetupException {
        try {
            return provisioner.provision(request);
        } catch (SQLException failure) {
            String state = failure.getSQLState();
            String key;
            if (state != null && state.startsWith("28")) {
                key = "dbsetup.provision.admin.authentication.failed";
            } else if (failure.getErrorCode() == 1819) {
                key = "dbsetup.provision.password.policy.failed";
            } else if (failure.getErrorCode() == 1044 || failure.getErrorCode() == 1142
                    || failure.getErrorCode() == 1227 || failure.getErrorCode() == 1410) {
                key = "dbsetup.provision.permission.failed";
            } else {
                key = "dbsetup.provision.failed";
            }
            throw new DatabaseSetupException(key, failure);
        }
    }

    private static int parsePort(String port) throws DatabaseSetupException {
        try {
            int parsed = Integer.parseInt(clean(port));
            if (parsed >= 1 && parsed <= 65535) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // The localized validation error below is the public result.
        }
        throw new DatabaseSetupException("dbsetup.validation.port");
    }

    private static boolean validAllowedHost(String value) {
        if ("localhost".equalsIgnoreCase(value)) {
            return true;
        }
        if (IPV4.matcher(value).matches()) {
            return validIpv4(value);
        }
        var cidr = IPV4_CIDR.matcher(value);
        return cidr.matches() && validIpv4(cidr.group(1));
    }

    private static boolean isReservedAccount(String username) {
        return username.equalsIgnoreCase("root")
                || username.equalsIgnoreCase("mysql.sys")
                || username.equalsIgnoreCase("mysql.session")
                || username.equalsIgnoreCase("mysql.infoschema");
    }

    private static boolean validIpv4(String value) {
        for (String octet : value.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
