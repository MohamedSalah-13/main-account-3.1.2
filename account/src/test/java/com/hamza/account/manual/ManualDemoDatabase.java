package com.hamza.account.manual;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/**
 * Builds the throwaway database the manual's screenshots are taken against, and the config pair
 * that points the application at it.
 * <p>
 * <b>It borrows the server, never the data.</b> The host, port and credentials come from whatever
 * {@code config.xml} this machine already has; the schema name does not - it is
 * {@value #SCHEMA}, created here and dropped by {@code --drop}. Nothing in this class opens the
 * database named in that file, and the config pair it writes goes into a directory of its own that
 * {@code ACCOUNT_CONFIG_DIR} points the harness at, so the machine's real settings are neither read
 * for the run nor written by it.
 * <p>
 * The <b>schema</b> is not created here beyond the empty database: the application's own Flyway
 * migration builds it on first start, which is the point - a manual photographed against a
 * hand-made schema would be a manual of a program nobody runs. This class creates the database,
 * writes the config, and - after the first boot has migrated it - seeds the demo rows.
 */
public final class ManualDemoDatabase {

    /** Deliberately unmistakable: nobody types this by accident when meaning the real one. */
    public static final String SCHEMA = "account_manual_demo";

    private ManualDemoDatabase() {
    }

    /**
     * {@code java ... ManualDemoDatabase <config dir> [--drop]}
     * <p>
     * Without {@code --drop} it creates the database if it is missing and writes the config pair.
     * With it, the database is dropped and the directory's files removed.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: ManualDemoDatabase <config dir> [--drop]");
            System.exit(2);
        }
        Path configDir = Path.of(args[0]);
        boolean drop = args.length > 1 && args[1].equals("--drop");

        Server server = readServerFromThisMachinesConfig();
        if (args.length > 1 && args[1].equals("--exists")) {
            System.out.println("demo database exists: " + exists(server));
            return;
        }
        if (drop) {
            execute(server, "DROP DATABASE IF EXISTS `" + SCHEMA + "`");
            deleteQuietly(configDir.resolve("config.xml"));
            deleteQuietly(configDir.resolve("config.key"));
            System.out.println("dropped " + SCHEMA);
            return;
        }
        execute(server, "CREATE DATABASE IF NOT EXISTS `" + SCHEMA + "`");
        writeConfig(configDir, server);
        System.out.println("demo database " + SCHEMA + " on " + server.host() + ":" + server.port());
        System.out.println("config written to " + configDir.toAbsolutePath());
    }

    /**
     * Reads host, port and credentials out of whichever configuration <em>the application itself</em>
     * would open.
     * <p>
     * It asks {@link DatabaseConfigFiles#locateForRead()} rather than looking for a {@code config.xml}
     * by hand, and takes the key from beside that file. This machine has three pairs - one under
     * {@code %ProgramData%\AccountK} and two older ones in the checkout - and the first draft of this
     * method read one file with the other's key, which fails as {@code BadPaddingException}: a message
     * about padding for what is really "you opened the wrong pair".
     * <p>
     * Once {@code ACCOUNT_CONFIG_DIR} points at the demo directory this resolves to the demo pair,
     * which is what {@link #seed} wants; the server is the same either way, and the schema never
     * comes from the file.
     */
    private static Server readServerFromThisMachinesConfig() throws Exception {
        DatabaseConfigFiles.Location location = DatabaseConfigFiles.locateForRead();
        File file = location.configFile().toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("No config.xml to read the MySQL server from: "
                    + file.getAbsolutePath());
        }
        HashMap<String, String> config = decrypt(file, location.keyFile().toFile());
        return new Server(config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
    }

    /** The key beside the file first, then whatever this install resolves to, then the built-in one. */
    private static HashMap<String, String> decrypt(File config, File keyFile) throws Exception {
        Exception last = null;
        for (String key : new String[]{CryptoDatabaseConfig.resolveConfigKey(keyFile),
                CryptoDatabaseConfig.resolveConfigKey()}) {
            if (key == null) {
                continue;
            }
            try {
                return new CryptoDatabaseConfig(key).loadAndDecryptConfig(config.getAbsolutePath());
            } catch (Exception wrongKey) {
                last = wrongKey;
            }
        }
        try {
            return new CryptoDatabaseConfig().loadAndDecryptConfig(config.getAbsolutePath());
        } catch (Exception wrongKey) {
            throw last == null ? wrongKey : last;
        }
    }

    private static void writeConfig(Path directory, Server server) throws Exception {
        Files.createDirectories(directory);
        // A key of its own, so the demo config is readable only beside itself and cannot be
        // mistaken for - or opened with - the machine's real one.
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String base64 = Base64.getEncoder().encodeToString(key);
        Files.writeString(directory.resolve("config.key"), base64, StandardCharsets.UTF_8);

        new CryptoDatabaseConfig(base64).saveEncryptedConfigToXML(
                directory.resolve("config.xml").toAbsolutePath().toString(),
                url(server),
                SCHEMA,
                server.host(),
                server.username(),
                server.password(),
                server.port(),
                "com.mysql.cj.jdbc.Driver");
    }

    /**
     * {@code connectionTimeZone=LOCAL} is not decoration: this MySQL runs on local wall clock, and
     * a URL claiming UTC shifts every stored timestamp by the offset. {@code CLAUDE.md} records the
     * three defects that came out of the one word, and a demo database is not exempt from them -
     * the screenshots would show times three hours out.
     */
    private static String url(Server server) {
        return "jdbc:mysql://" + server.host() + ":" + server.port() + "/" + SCHEMA
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=LOCAL";
    }

    private static void execute(Server server, String statement) throws Exception {
        String root = "jdbc:mysql://" + server.host() + ":" + server.port()
                + "/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=LOCAL";
        try (Connection connection = DriverManager.getConnection(root, server.username(), server.password());
             Statement sql = connection.createStatement()) {
            sql.execute(statement);
        }
    }

    /** Read-only preflight so a capture run never seeds an existing demo database blindly. */
    private static boolean exists(Server server) throws Exception {
        String root = "jdbc:mysql://" + server.host() + ":" + server.port()
                + "/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=LOCAL";
        try (Connection connection = DriverManager.getConnection(root, server.username(), server.password());
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?")) {
            statement.setString(1, SCHEMA);
            try (var rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        }
    }

    /** Runs the seed script against the demo database, after the application has migrated it. */
    public static void seed(Path script) throws Exception {
        Server server = readServerFromThisMachinesConfig();
        String text = Files.readString(script, StandardCharsets.UTF_8);
        List<String> statements = ManualDemoScript.statements(text);
        try (Connection connection = DriverManager.getConnection(url(server), server.username(), server.password());
             Statement sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            for (String statement : statements) {
                sql.execute(statement);
            }
            connection.commit();
        }
        System.out.println("seeded " + statements.size() + " statements from " + script);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // The directory is a scratch one; a file left behind is not worth failing a build over.
        }
    }

    private record Server(String host, String port, String username, String password) {
    }
}
