package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.account.features.dbsetup.LocalProvisioningException.Step;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provisioner's rules, against fakes: no MySQL is started here. That a real server comes up,
 * takes the passwords and lets the restricted account in is the gated acceptance test's to say.
 */
class LocalServerProvisionerTest {

    private static final String ROOT_SECRET = "root-secret-0123456789-abcdefghij";
    private static final String APP_SECRET = "app-secret-0123456789-abcdefghijk";

    @TempDir
    Path temp;

    private final List<String> events = new ArrayList<>();
    private final List<DatabaseServerProvisioningRequest> accounts = new ArrayList<>();
    private DatabaseConnectionSettings savedConfig;
    private Step failAt;
    private LocalServerLayout layout;
    private DatabaseConfigFiles.Location config;

    @BeforeEach
    void aBundledServerOnDisk() throws IOException {
        Path home = temp.resolve("program/mysql");
        Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("bin/mysqld.exe"));
        layout = new LocalServerLayout(home, temp.resolve("shared"));
        config = DatabaseConfigFiles.at(layout.shared());
    }

    @Test
    @DisplayName("a first install: initialize, start, secure root, create the account, stop, then write config.xml")
    void aFirstInstall() throws Exception {
        LocalProvisioningResult result = provisioner().provision(request(null));

        assertEquals(LocalProvisioningResult.Outcome.PROVISIONED, result.outcome());
        assertEquals(3307, result.port());
        assertEquals(List.of("initialize", "start:3307", "secure:3307", "account:localhost",
                "root-secret", "shutdown:3307", "awaitExit", "config"), events,
                "the server is stopped before config.xml exists to point at it");
        assertEquals(MysqlIni.LOOPBACK, savedConfig.host());
        assertEquals(3307, savedConfig.port());
        assertEquals(APP_SECRET, savedConfig.password());
        assertTrue(Files.readString(layout.iniFile()).contains("bind-address=127.0.0.1"));
    }

    @Test
    @DisplayName("one account for this machine and one for the tills, with one password - never one wide account")
    void aServerTheTillsReach() throws Exception {
        provisioner().provision(request("192.168.1.0/24"));

        assertEquals(List.of("localhost", "192.168.1.0/255.255.255.0"),
                accounts.stream().map(DatabaseServerProvisioningRequest::allowedHost).toList());
        assertEquals(1, accounts.stream().map(DatabaseServerProvisioningRequest::applicationPassword)
                .distinct().count());
        assertTrue(accounts.stream().noneMatch(DatabaseServerProvisioningRequest::allowStoredPrograms),
                "my.ini carries the setting; SET PERSIST would write a second copy");
        assertTrue(Files.readString(layout.iniFile()).contains("bind-address=0.0.0.0"));
    }

    @Test
    @DisplayName("data already there: nothing is initialized, created or written - the rule that protects the books")
    void inertOverExistingData() throws Exception {
        Files.createDirectories(layout.dataDirectory());
        Files.writeString(layout.dataDirectory().resolve("ibdata1"), "a customer's invoices");
        Files.writeString(layout.shared().resolve("license.dat"), "not the installer's");

        LocalProvisioningResult result = provisioner().provision(request(null));

        assertEquals(LocalProvisioningResult.Outcome.ALREADY_PROVISIONED, result.outcome());
        assertTrue(events.isEmpty(), "not even a port was probed");
        assertFalse(Files.exists(layout.iniFile()));
        assertEquals("a customer's invoices", Files.readString(layout.dataDirectory().resolve("ibdata1")));
        assertEquals("not the installer's", Files.readString(layout.shared().resolve("license.dat")));
    }

    @Test
    @DisplayName("a failure removes what the run made and nothing else, so the next run is not inert over a broken server")
    void aFailureLeavesNothingBehind() throws Exception {
        Files.createDirectories(layout.shared());
        Files.writeString(layout.shared().resolve("license.dat"), "not the installer's");
        failAt = Step.CREATE_ACCOUNT;

        LocalProvisioningException failure = assertThrows(LocalProvisioningException.class,
                () -> provisioner().provision(request(null)));

        assertEquals(Step.CREATE_ACCOUNT, failure.step());
        assertTrue(events.contains("kill"), "a server left running would hold the port for the retry");
        assertFalse(Files.exists(layout.dataDirectory()));
        assertFalse(Files.exists(layout.iniFile()));
        assertFalse(Files.exists(layout.rootSecretFile()));
        assertFalse(events.contains("config"));
        assertEquals("not the installer's", Files.readString(layout.shared().resolve("license.dat")));
        assertFalse(provisionerLayoutHasData(), "and so a second attempt starts from nothing");
    }

    @Test
    @DisplayName("a server that will not stop is a failure with no config.xml pointing at it")
    void aServerThatWillNotStop() {
        failAt = Step.SHUTDOWN;

        LocalProvisioningException failure = assertThrows(LocalProvisioningException.class,
                () -> provisioner().provision(request(null)));

        assertEquals(Step.SHUTDOWN, failure.step());
        assertFalse(events.contains("config"));
        assertFalse(Files.exists(layout.dataDirectory()));
    }

    @Test
    @DisplayName("no bundled server, or no free port: refused before anything is written")
    void refusedBeforeWriting() {
        assertEquals(Step.NO_FREE_PORT, assertThrows(LocalProvisioningException.class,
                () -> provisioner(new PortProbe(port -> false)).provision(request(null))).step());

        LocalServerLayout empty = new LocalServerLayout(temp.resolve("nowhere"), temp.resolve("shared2"));
        assertEquals(Step.NO_MYSQLD, assertThrows(LocalProvisioningException.class,
                () -> provisioner().provision(new LocalServerRequest(empty, DatabaseConfigFiles.at(empty.shared()),
                        3307, "account", "accountk", null))).step());
        assertFalse(Files.exists(empty.shared()));
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("the port that was free is the one in my.ini, in config.xml and in the result")
    void thePortIsOneNumber() throws Exception {
        LocalProvisioningResult result = provisioner(new PortProbe(port -> port == 3309)).provision(request(null));

        assertEquals(3309, result.port());
        assertEquals(3309, savedConfig.port());
        assertTrue(Files.readString(layout.iniFile()).contains("port=3309"));
    }

    @Test
    @DisplayName("neither password is in the result, its file form, or the failure")
    void noSecretInWhatIsReported() throws Exception {
        LocalProvisioningResult result = provisioner().provision(request(null));

        String reported = result + result.toProperties();
        assertFalse(reported.contains(ROOT_SECRET));
        assertFalse(reported.contains(APP_SECRET));
    }

    private boolean provisionerLayoutHasData() {
        return layout.hasData();
    }

    private LocalServerRequest request(String allowFrom) {
        return new LocalServerRequest(layout, config, 3307, "account", "accountk", allowFrom);
    }

    private LocalServerProvisioner provisioner() {
        return provisioner(new PortProbe(port -> true));
    }

    private LocalServerProvisioner provisioner(PortProbe ports) {
        Iterator<String> secrets = List.of(ROOT_SECRET, APP_SECRET).iterator();
        LocalServerControl control = new LocalServerControl() {
            @Override
            public void initialize(LocalServerLayout layout) throws IOException {
                events.add("initialize");
                Files.createDirectories(layout.dataDirectory());
                Files.writeString(layout.dataDirectory().resolve("ibdata1"), "fresh");
            }

            @Override
            public RunningServer start(LocalServerLayout layout, int port) {
                events.add("start:" + port);
                return new RunningServer() {
                    @Override
                    public void awaitExit() {
                        events.add("awaitExit");
                    }

                    @Override
                    public void kill() {
                        events.add("kill");
                    }
                };
            }
        };
        LocalRootAccount root = new LocalRootAccount() {
            @Override
            public void secure(int port, String newPassword) {
                assertEquals(ROOT_SECRET, newPassword);
                events.add("secure:" + port);
            }

            @Override
            public void shutdown(int port, String password) throws SQLException {
                if (failAt == Step.SHUTDOWN) {
                    throw new SQLException("will not stop");
                }
                events.add("shutdown:" + port);
            }
        };
        DatabaseServerSetupService accountService = new DatabaseServerSetupService(request -> {
            if (failAt == Step.CREATE_ACCOUNT) {
                throw new SQLException("refused", "42000", 1044);
            }
            accounts.add(request);
            events.add("account:" + (request.allowedHost().equals("localhost") ? "localhost" : "network"));
            return new DatabaseServerProvisioningResult(request.database(), request.applicationUsername(),
                    DatabaseServerProvisioningResult.PasswordOutcome.CREATED, StoredProgramLogging.DECLINED);
        });
        return new LocalServerProvisioner(control, root, accountService, ports, secrets::next,
                (file, secret) -> {
                    events.add("root-secret");
                    Files.writeString(file, secret);
                },
                (settings, target) -> {
                    events.add("config");
                    savedConfig = settings;
                });
    }
}
