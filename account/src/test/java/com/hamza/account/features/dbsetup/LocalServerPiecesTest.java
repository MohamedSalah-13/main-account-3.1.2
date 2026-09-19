package com.hamza.account.features.dbsetup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The small parts of a first install, each with the rule it exists for. */
class LocalServerPiecesTest {

    @Nested
    class Secrets {

        @Test
        @DisplayName("32 symbols, none of which means anything to a shell, an ini file or a SQL literal")
        void shape() {
            String secret = new SecretGenerator().next();
            assertEquals(SecretGenerator.LENGTH, secret.length());
            assertTrue(secret.chars().allMatch(symbol -> SecretGenerator.ALPHABET.indexOf(symbol) >= 0));
            for (char unsafe : "'\"\\ #;`$%&|<>".toCharArray()) {
                assertTrue(SecretGenerator.ALPHABET.indexOf(unsafe) < 0, "unsafe: " + unsafe);
            }
        }

        @Test
        @DisplayName("long enough for the account rule, and never the same twice")
        void strengthAndUniqueness() {
            assertTrue(SecretGenerator.LENGTH >= 12, "DatabaseServerSetupService refuses anything shorter");
            SecretGenerator generator = new SecretGenerator();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                assertTrue(seen.add(generator.next()));
            }
        }
    }

    @Nested
    class Ports {

        @Test
        @DisplayName("the preferred port when free; otherwise the next free one, in a range a technician expects")
        void search() {
            assertEquals(OptionalInt.of(3307), new PortProbe(port -> true).firstFree(3307));
            assertEquals(OptionalInt.of(3310), new PortProbe(port -> port >= 3310).firstFree(3307));
            assertEquals(OptionalInt.empty(), new PortProbe(port -> port > PortProbe.LAST).firstFree(3307));
        }

        @Test
        @DisplayName("3306 is not where the search starts")
        void notTheDefaultPort() {
            assertTrue(PortProbe.FIRST > 3306);
            assertThrows(IllegalArgumentException.class, () -> new PortProbe(port -> true).firstFree(0));
        }

        @Test
        @DisplayName("a port somebody is listening on is not free")
        void aRealSocket() throws Exception {
            try (java.net.ServerSocket taken = new java.net.ServerSocket(0)) {
                int port = taken.getLocalPort();
                OptionalInt found = new PortProbe().firstFree(port);
                assertTrue(found.isEmpty() || found.getAsInt() != port);
            }
        }
    }

    @Nested
    class Ini {

        @Test
        @DisplayName("my.ini, character for character - loopback, our port, no collation and no time zone")
        void pinned() {
            assertEquals("""
                    [mysqld]
                    basedir=C:/Program Files/AccountK/mysql
                    datadir=C:/ProgramData/AccountK/mysql-data
                    port=3307
                    bind-address=127.0.0.1
                    mysqlx=0
                    character-set-server=utf8mb4
                    innodb_buffer_pool_size=256M
                    log_bin_trust_function_creators=1

                    [client]
                    port=3307
                    """, MysqlIni.render(Path.of("C:\\Program Files\\AccountK\\mysql"),
                    Path.of("C:\\ProgramData\\AccountK\\mysql-data"), 3307, false));
        }

        @Test
        @DisplayName("every interface only when other machines will connect")
        void reachable() {
            String ini = MysqlIni.render(Path.of("C:\\a"), Path.of("C:\\b"), 3308, true);
            assertTrue(ini.contains("bind-address=0.0.0.0"));
            assertTrue(ini.contains("port=3308"));
        }

        @Test
        @DisplayName("no backslash reaches the file: in an option file it starts an escape")
        void forwardSlashes() {
            assertFalse(MysqlIni.render(Path.of("C:\\ProgramData\\new"), Path.of("C:\\ProgramData\\table"),
                    3307, false).contains("\\"));
        }

        @Test
        @DisplayName("what V63 taught: the server keeps its own collation, and its own clock")
        void namesNeither() {
            String ini = MysqlIni.render(Path.of("C:\\a"), Path.of("C:\\b"), 3307, false);
            assertFalse(ini.contains("collation"));
            assertFalse(ini.contains("time-zone"));
            assertFalse(ini.contains("time_zone"));
        }
    }

    @Nested
    class Layout {

        @TempDir
        Path temp;

        @Test
        @DisplayName("the data is under the shared folder, never under the program's")
        void twoRoots() {
            LocalServerLayout layout = new LocalServerLayout(temp.resolve("program/mysql"), temp.resolve("shared"));
            assertTrue(layout.dataDirectory().startsWith(layout.shared()));
            assertFalse(layout.dataDirectory().startsWith(layout.mysqlHome()));
            assertTrue(layout.mysqld().startsWith(layout.mysqlHome()));
        }

        @Test
        @DisplayName("an empty folder is not data; a folder with anything in it is")
        void hasData() throws Exception {
            LocalServerLayout layout = new LocalServerLayout(temp.resolve("m"), temp.resolve("s"));
            assertFalse(layout.hasData());
            Files.createDirectories(layout.dataDirectory());
            assertFalse(layout.hasData(), "a folder the installer made in advance does not block a first install");
            Files.createFile(layout.dataDirectory().resolve("auto.cnf"));
            assertTrue(layout.hasData());
        }
    }

    @Nested
    class CommandLine {

        @Test
        @DisplayName("the flag, the required folder, and nothing that could carry a password")
        void options() {
            assertTrue(LocalProvisioningCli.requestedBy(new String[]{"--provision-local", "--mysql-home", "x"}));
            assertFalse(LocalProvisioningCli.requestedBy(new String[]{}));

            Map<String, String> parsed = LocalProvisioningCli.parse(new String[]{
                    "--provision-local", "--mysql-home", "C:\\m", "--port", "3310", "--allow-from", "10.0.0.0/8"});
            assertEquals("C:\\m", parsed.get("--mysql-home"));
            assertEquals("3310", parsed.get("--port"));

            assertThrows(IllegalArgumentException.class,
                    () -> LocalProvisioningCli.parse(new String[]{"--provision-local"}));
            assertThrows(IllegalArgumentException.class,
                    () -> LocalProvisioningCli.parse(new String[]{"--mysql-home", "x", "--port", "70000"}));
            for (String secretBearing : new String[]{"--password", "--root-password", "--app-password"}) {
                assertThrows(IllegalArgumentException.class,
                        () -> LocalProvisioningCli.parse(new String[]{"--mysql-home", "x", secretBearing, "s"}),
                        "an argument is visible in the process list");
            }
        }

        @Test
        @DisplayName("a server found ready and a server made ready are both exit 0; the file says which")
        void exitCodes(@TempDir Path temp) throws Exception {
            Path shared = temp.resolve("shared");
            Files.createDirectories(shared.resolve("mysql-data"));
            Files.createFile(shared.resolve("mysql-data/ibdata1"));

            int code = new LocalProvisioningCli(LocalServerProvisioner.standard()).run(new String[]{
                    "--provision-local", "--mysql-home", temp.resolve("none").toString(),
                    "--shared", shared.toString()});

            assertEquals(LocalProvisioningCli.READY, code);
            assertTrue(Files.readString(shared.resolve("provision-result.txt")).contains("outcome=ALREADY_PROVISIONED"));
        }

        @Test
        @DisplayName("a failure is exit 1, names its step, and leaves the shared folder without a server")
        void failure(@TempDir Path temp) throws Exception {
            Path shared = temp.resolve("shared");

            int code = new LocalProvisioningCli(LocalServerProvisioner.standard()).run(new String[]{
                    "--provision-local", "--mysql-home", temp.resolve("none").toString(),
                    "--shared", shared.toString()});

            assertEquals(LocalProvisioningCli.FAILED, code);
            assertTrue(Files.readString(shared.resolve("provision-result.txt")).contains("step=NO_MYSQLD"));
            assertFalse(Files.exists(shared.resolve("mysql-data")));
        }
    }
}
