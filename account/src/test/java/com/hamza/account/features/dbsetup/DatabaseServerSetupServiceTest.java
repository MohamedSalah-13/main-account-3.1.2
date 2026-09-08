package com.hamza.account.features.dbsetup;

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
                new DatabaseServerProvisioningResult(request.database(), "account"));

        var request = service.validate(" localhost ", "3306", " account_system_db ",
                " root ", "admin-secret", " account_pc01 ", "app-secret-2026", " 192.168.1.25 ");

        assertEquals("localhost", request.serverHost());
        assertEquals("account_system_db", request.database());
        assertEquals("account_pc01", request.applicationUsername());
        assertEquals("192.168.1.25", request.allowedHost());
        assertFalse(request.toString().contains("admin-secret"));
        assertFalse(request.toString().contains("app-secret-2026"));
    }

    @Test
    void acceptsAnIpv4CidrButRejectsGlobalWildcardAccess() throws Exception {
        var service = serviceThatSucceeds();

        assertEquals("192.168.1.0/24", service.validate("localhost", "3306", "accounts",
                "root", "admin", "account_pc01", "app-secret-2026", "192.168.1.0/24").allowedHost());
        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.validate("localhost", "3306", "accounts",
                        "root", "admin", "account_pc01", "app-secret-2026", "%"));

        assertEquals("dbsetup.provision.validation.allowed.host", failure.messageKey());
    }

    @Test
    void rejectsAnInvalidIpv4Octet() {
        var service = serviceThatSucceeds();

        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.validate("localhost", "3306", "accounts",
                        "root", "admin", "account_pc01", "app-secret-2026", "192.168.1.999"));

        assertEquals("dbsetup.provision.validation.allowed.host", failure.messageKey());
    }

    @Test
    void handsTheValidatedRequestToTheProvisioner() throws Exception {
        AtomicReference<DatabaseServerProvisioningRequest> captured = new AtomicReference<>();
        var service = new DatabaseServerSetupService(request -> {
            captured.set(request);
            return new DatabaseServerProvisioningResult(request.database(),
                    "'" + request.applicationUsername() + "'@'" + request.allowedHost() + "'");
        });
        var request = service.validate("localhost", "3306", "accounts",
                "root", "admin", "account_pc01", "app-secret-2026", "localhost");

        var result = service.provision(request);

        assertEquals(request, captured.get());
        assertEquals("'account_pc01'@'localhost'", result.account());
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

    private static DatabaseServerSetupService serviceThatSucceeds() {
        return new DatabaseServerSetupService(request ->
                new DatabaseServerProvisioningResult(request.database(), "account"));
    }

    private static DatabaseServerProvisioningRequest request() {
        return new DatabaseServerProvisioningRequest("localhost", 3306, "accounts",
                "root", "admin", "account_pc01", "app-secret-2026", "localhost");
    }
}
