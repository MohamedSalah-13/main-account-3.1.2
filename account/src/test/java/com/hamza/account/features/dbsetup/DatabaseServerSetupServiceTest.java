package com.hamza.account.features.dbsetup;

import com.hamza.account.features.dbsetup.DatabaseServerProvisioningResult.PasswordOutcome;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseServerSetupServiceTest {

    @Test
    void validatesAndNormalizesARestrictedAccountRequest() throws Exception {
        var service = new DatabaseServerSetupService(request ->
                new DatabaseServerProvisioningResult(request.database(), "account", PasswordOutcome.CREATED));

        var request = service.validate(" localhost ", "3306", " account_system_db ",
                " root ", "admin-secret", " account_pc01 ", "app-secret-2026", " 192.168.1.25 ");

        assertEquals("localhost", request.serverHost());
        assertEquals("account_system_db", request.database());
        assertEquals("account_pc01", request.applicationUsername());
        assertEquals("192.168.1.25", request.allowedHost());
        assertFalse(request.resetExistingPassword(), "replacing an existing password is opt-in");
        assertFalse(request.toString().contains("admin-secret"));
        assertFalse(request.toString().contains("app-secret-2026"));
    }

    /**
     * The one that shipped broken: MySQL reads an account host as a literal or as
     * {@code address/netmask}, never as a prefix length, so {@code CREATE USER} accepted
     * {@code 192.168.1.0/24} and then matched no client at all.
     */
    @Test
    void convertsACidrPrefixIntoTheNetmaskMysqlMatchesOn() throws Exception {
        var service = serviceThatSucceeds();

        assertEquals("192.168.1.0/255.255.255.0", allowedHost(service, "192.168.1.0/24"));
        assertEquals("10.0.0.0/255.0.0.0", allowedHost(service, "10.0.0.0/8"));
        assertEquals("172.16.0.0/255.240.0.0", allowedHost(service, "172.16.0.0/12"));
        assertEquals("192.168.1.128/255.255.255.192", allowedHost(service, "192.168.1.128/26"));
    }

    /** A single host is a plain address to MySQL, mask or no mask. */
    @Test
    void writesASingleHostWithoutANetmask() throws Exception {
        var service = serviceThatSucceeds();

        assertEquals("192.168.1.25", allowedHost(service, "192.168.1.25"));
        assertEquals("192.168.1.25", allowedHost(service, "192.168.1.25/32"));
        assertEquals("192.168.1.25", allowedHost(service, "192.168.001.025"));
        assertEquals("localhost", allowedHost(service, "LocalHost"));
    }

    @Test
    void rejectsGlobalWildcardAccessAndTheZeroPrefixThatMeansTheSameThing() {
        var service = serviceThatSucceeds();

        assertEquals("dbsetup.provision.validation.allowed.host", refusal(service, "%"));
        assertEquals("dbsetup.provision.validation.allowed.host", refusal(service, "0.0.0.0/0"));
    }

    /**
     * {@code 192.168.1.25/24} is arithmetically fine and matches nothing: MySQL compares
     * {@code client_ip & netmask} against the stored address. Naming it is the point -
     * widening it to the whole subnet would be deciding on the technician's behalf.
     */
    @Test
    void rejectsANetworkAddressThatDoesNotMatchItsOwnPrefix() {
        var service = serviceThatSucceeds();

        assertEquals("dbsetup.provision.validation.allowed.host.network",
                refusal(service, "192.168.1.25/24"));
    }

    @Test
    void rejectsAnInvalidIpv4Octet() {
        var service = serviceThatSucceeds();

        assertEquals("dbsetup.provision.validation.allowed.host", refusal(service, "192.168.1.999"));
        assertEquals("dbsetup.provision.validation.allowed.host", refusal(service, "192.168.1.999/24"));
    }

    @Test
    void handsTheValidatedRequestToTheProvisioner() throws Exception {
        AtomicReference<DatabaseServerProvisioningRequest> captured = new AtomicReference<>();
        var service = new DatabaseServerSetupService(request -> {
            captured.set(request);
            return new DatabaseServerProvisioningResult(request.database(),
                    "'" + request.applicationUsername() + "'@'" + request.allowedHost() + "'",
                    PasswordOutcome.CREATED);
        });
        var request = service.validate("localhost", "3306", "accounts",
                "root", "admin", "account_pc01", "app-secret-2026", "localhost");

        var result = service.provision(request);

        assertEquals(request, captured.get());
        assertEquals("'account_pc01'@'localhost'", result.account());
    }

    @Test
    void carriesTheDeliberatePasswordResetThroughToTheProvisioner() throws Exception {
        var service = serviceThatSucceeds();

        var request = service.validate("localhost", "3306", "accounts", "root", "admin",
                "account_pc01", "app-secret-2026", "localhost", true);

        assertEquals(true, request.resetExistingPassword());
    }

    @Test
    void distinguishesAdministratorAuthenticationFailures() {
        var service = new DatabaseServerSetupService(request -> {
            throw new SQLException("denied", "28000", 1045);
        });
        var request = request();

        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.provision(request));

        assertEquals("dbsetup.provision.admin.authentication.failed", failure.messageKey());
    }

    @Test
    void refusesToReplaceTheAdministratorAccount() {
        var service = serviceThatSucceeds();

        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.validate("localhost", "3306", "accounts",
                        "root", "admin", "root", "app-secret-2026", "localhost"));

        assertEquals("dbsetup.provision.validation.application.reserved", failure.messageKey());
    }

    @Test
    void requiresANonTrivialApplicationPassword() {
        var service = serviceThatSucceeds();

        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.validate("localhost", "3306", "accounts",
                        "root", "admin", "account_pc01", "short", "localhost"));

        assertEquals("dbsetup.provision.validation.application.password", failure.messageKey());
    }

    @Test
    void generatesOnlyDatabaseScopedGrantsWithoutGrantOption() {
        String account = JdbcDatabaseServerProvisioner.accountLiteral("account_pc01", "192.168.1.25");
        String sql = JdbcDatabaseServerProvisioner.grantSql("account_system_db", account);

        assertEquals("GRANT ALL PRIVILEGES ON `account_system_db`.* TO 'account_pc01'@'192.168.1.25'", sql);
        assertFalse(sql.contains(" ON *.*"));
        assertFalse(sql.contains("GRANT OPTION"));
    }

    private static String allowedHost(DatabaseServerSetupService service, String typed) throws Exception {
        return service.validate("localhost", "3306", "accounts", "root", "admin",
                "account_pc01", "app-secret-2026", typed).allowedHost();
    }

    private static String refusal(DatabaseServerSetupService service, String typed) {
        return assertThrows(DatabaseSetupException.class,
                () -> service.validate("localhost", "3306", "accounts", "root", "admin",
                        "account_pc01", "app-secret-2026", typed)).messageKey();
    }

    private static DatabaseServerSetupService serviceThatSucceeds() {
        return new DatabaseServerSetupService(request ->
                new DatabaseServerProvisioningResult(request.database(), "account", PasswordOutcome.CREATED));
    }

    private static DatabaseServerProvisioningRequest request() {
        return new DatabaseServerProvisioningRequest("localhost", 3306, "accounts",
                "root", "admin", "account_pc01", "app-secret-2026", "localhost", false);
    }
}
