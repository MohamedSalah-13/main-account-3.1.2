package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A first install against a real {@code mysqld}, which is the only thing that can say the
 * provisioner works: that a server comes up from {@code my.ini} as rendered, takes root's password,
 * lets the restricted account in through the {@code config.xml} that was written, and refuses
 * everybody else.
 * <p>
 * Gated, like every acceptance class here, and needing a MySQL distribution to run:
 * <pre>
 * mvn -o -pl account -am clean test -Dtest=LocalServerProvisioningAcceptanceTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false \
 *     -Daccount.installer.acceptance=true "-Daccount.installer.mysqlHome=F:\accountK\mysql"
 * </pre>
 * <b>It touches no database that exists.</b> The data directory is created under JUnit's temporary
 * folder, the port is the first free one from 3307, and the server is a child process that is shut
 * down before the test returns. A MySQL already running on the machine is neither used nor noticed.
 * <p>
 * The root secret is captured in memory as well as written, because the file it goes to is
 * readable by Administrators only - which a test, not being elevated, is not.
 */
@EnabledIfSystemProperty(named = "account.installer.acceptance", matches = "true")
class LocalServerProvisioningAcceptanceTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("a first install, then the server started again as the service would start it")
    void aFirstInstallAgainstARealServer() throws Exception {
        Path mysqlHome = Path.of(System.getProperty("account.installer.mysqlHome"));
        LocalServerLayout layout = new LocalServerLayout(mysqlHome, temp.resolve("shared"));
        DatabaseConfigFiles.Location config = DatabaseConfigFiles.at(layout.shared());
        AtomicReference<String> rootSecret = new AtomicReference<>();
        ProcessLocalServerControl control = new ProcessLocalServerControl();
        JdbcLocalRootAccount root = new JdbcLocalRootAccount();
        DatabaseSetupService configs = new DatabaseSetupService(new JdbcDatabaseConnectionProbe());
        LocalServerProvisioner provisioner = new LocalServerProvisioner(control, root,
                new DatabaseServerSetupService(new JdbcDatabaseServerProvisioner()), new PortProbe(),
                new SecretGenerator()::next,
                (file, secret) -> {
                    rootSecret.set(secret);
                    RestrictedFile.write(file, secret);
                },
                configs::save);
        LocalServerRequest request = new LocalServerRequest(layout, config, PortProbe.FIRST,
                LocalServerRequest.DEFAULT_DATABASE, LocalServerRequest.DEFAULT_APPLICATION_USER, null);

        LocalProvisioningResult result = provisioner.provision(request);

        assertEquals(LocalProvisioningResult.Outcome.PROVISIONED, result.outcome());
        assertTrue(layout.hasData());
        assertTrue(Files.isRegularFile(config.configFile()));
        assertTrue(Files.isRegularFile(config.keyFile()));
        assertFalse(new PortProbe().firstFree(result.port()).isEmpty(),
                "the temporary server is gone: its port is free again for the service");
        assertRootSecretIsNotReadableByEverybody(layout);

        HashMap<String, String> stored = new CryptoDatabaseConfig(
                Files.readString(config.keyFile(), StandardCharsets.UTF_8).trim())
                .loadAndDecryptConfig(config.configFile().toString());
        assertEquals(Integer.toString(result.port()), stored.get(CryptoDatabaseConfig.PORT));

        // Started again from the same my.ini and nothing else - what the Windows service will do.
        LocalServerControl.RunningServer server = control.start(layout, result.port());
        try {
            String url = JdbcLocalRootAccount.url(result.port());
            try (Connection application = DriverManager.getConnection(url,
                    stored.get(CryptoDatabaseConfig.USERNAME), stored.get(CryptoDatabaseConfig.PASSWORD));
                 Statement statement = application.createStatement()) {
                statement.execute("CREATE TABLE " + LocalServerRequest.DEFAULT_DATABASE + ".probe (id INT)");
                statement.execute("DROP TABLE " + LocalServerRequest.DEFAULT_DATABASE + ".probe");
                // Confined to its own database: the account is not an administrator of the server.
                assertThrows(SQLException.class, () -> statement.execute("CREATE DATABASE somebody_elses"));
                assertThrows(SQLException.class, () -> statement.execute("SELECT user FROM mysql.user"));
                try (ResultSet bind = statement.executeQuery("SELECT @@bind_address, @@port")) {
                    assertTrue(bind.next());
                    assertEquals(MysqlIni.LOOPBACK, bind.getString(1));
                    assertEquals(result.port(), bind.getInt(2));
                }
            }
            assertThrows(SQLException.class, () -> DriverManager.getConnection(url, "root", "").close(),
                    "the passwordless root of --initialize-insecure is gone");

            // A second run over what now exists: inert, and the password is not reset under the tills.
            byte[] configBefore = Files.readAllBytes(config.configFile());
            assertEquals(LocalProvisioningResult.Outcome.ALREADY_PROVISIONED, provisioner.provision(request).outcome());
            assertEquals(new String(configBefore, StandardCharsets.ISO_8859_1),
                    new String(Files.readAllBytes(config.configFile()), StandardCharsets.ISO_8859_1));
        } finally {
            try {
                root.shutdown(result.port(), rootSecret.get());
                server.awaitExit();
            } catch (SQLException unreachable) {
                server.kill();
            }
        }
    }

    private static void assertRootSecretIsNotReadableByEverybody(LocalServerLayout layout) throws Exception {
        Process icacls = new ProcessBuilder("icacls", layout.rootSecretFile().toString())
                .redirectErrorStream(true).start();
        String listing = new String(icacls.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        icacls.waitFor();
        assertTrue(listing.contains("SYSTEM"), listing);
        assertFalse(listing.contains("BUILTIN\\Users"), listing);
        assertFalse(listing.contains("Everyone"), listing);
        assertFalse(listing.contains("Authenticated Users"), listing);
        assertFalse(listing.contains("(I)"), "no inherited entry survives: " + listing);
    }
}
