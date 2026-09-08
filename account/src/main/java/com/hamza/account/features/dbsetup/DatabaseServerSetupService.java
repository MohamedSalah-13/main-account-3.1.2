package com.hamza.account.features.dbsetup;

import java.sql.SQLException;
import java.util.regex.Matcher;
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
        return validate(serverHost, port, database, administratorUsername, administratorPassword,
                applicationUsername, applicationPassword, allowedHost, false);
    }

    public DatabaseServerProvisioningRequest validate(
            String serverHost,
            String port,
            String database,
            String administratorUsername,
            String administratorPassword,
            String applicationUsername,
            String applicationPassword,
            String allowedHost,
            boolean resetExistingPassword
    ) throws DatabaseSetupException {
        String cleanServerHost = clean(serverHost);
        String cleanDatabase = clean(database);
        String cleanAdministrator = clean(administratorUsername);
        String cleanApplicationUser = clean(applicationUsername);

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

        return new DatabaseServerProvisioningRequest(cleanServerHost, parsedPort, cleanDatabase,
                cleanAdministrator, administratorPassword, cleanApplicationUser, applicationPassword,
                mysqlHostPattern(clean(allowedHost)), resetExistingPassword);
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

    /**
     * The host pattern MySQL will actually match a client against.
     *
     * <p><b>MySQL does not understand a prefix length.</b> An account host is a literal
     * name, a literal address, or {@code address/netmask} with the mask written out in
     * full. {@code CREATE USER} takes anything else as a literal string, without an error,
     * and then no client ever matches it. A till authorized as {@code 192.168.1.0/24} was
     * created successfully and refused at every connection afterwards, with an
     * authentication failure that reads exactly like a wrong password. So the prefix
     * length people know is what this accepts, and the netmask MySQL needs is what it
     * returns.
     *
     * <p>Two prefixes are refused rather than translated. {@code /0} matches every address
     * there is, which is the percent wildcard by another spelling. And an address carrying
     * bits outside its own mask ({@code 192.168.1.25/24}) matches nothing at all, because
     * MySQL compares {@code client_ip & netmask} against it - the same silent failure in a
     * second disguise, so it is named rather than quietly widened to the whole subnet.
     */
    static String mysqlHostPattern(String value) throws DatabaseSetupException {
        if ("localhost".equalsIgnoreCase(value)) {
            return "localhost";
        }
        if (IPV4.matcher(value).matches()) {
            return dotted(requireAddress(value));
        }
        Matcher cidr = IPV4_CIDR.matcher(value);
        if (!cidr.matches()) {
            throw new DatabaseSetupException("dbsetup.provision.validation.allowed.host");
        }
        long address = requireAddress(cidr.group(1));
        int prefix = Integer.parseInt(cidr.group(2));
        if (prefix == 0) {
            throw new DatabaseSetupException("dbsetup.provision.validation.allowed.host");
        }
        if (prefix == 32) {
            return dotted(address);
        }
        long netmask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        if ((address & ~netmask & 0xFFFFFFFFL) != 0) {
            throw new DatabaseSetupException("dbsetup.provision.validation.allowed.host.network");
        }
        return dotted(address) + "/" + dotted(netmask);
    }

    private static boolean isReservedAccount(String username) {
        return username.equalsIgnoreCase("root")
                || username.equalsIgnoreCase("mysql.sys")
                || username.equalsIgnoreCase("mysql.session")
                || username.equalsIgnoreCase("mysql.infoschema");
    }

    /** The address as one number, refusing an octet above 255. */
    private static long requireAddress(String value) throws DatabaseSetupException {
        long address = 0;
        for (String octet : value.split("\\.")) {
            int parsed = Integer.parseInt(octet);
            if (parsed > 255) {
                throw new DatabaseSetupException("dbsetup.provision.validation.allowed.host");
            }
            address = (address << 8) | parsed;
        }
        return address;
    }

    /** Back to dotted quads, which also drops the leading zeros MySQL would take literally. */
    private static String dotted(long address) {
        return (address >>> 24) + "." + ((address >>> 16) & 0xFF)
                + "." + ((address >>> 8) & 0xFF) + "." + (address & 0xFF);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
