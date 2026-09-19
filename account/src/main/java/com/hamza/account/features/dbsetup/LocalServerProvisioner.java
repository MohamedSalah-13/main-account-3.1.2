package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.account.features.dbsetup.LocalProvisioningException.Step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The first install of the bundled server: a new data directory, a root nobody else knows, the
 * application's own account, and this machine's {@code config.xml}. See
 * {@code docs/installer-plan.md}; the rules below are its ق-٤, ق-٥ and ق-٦.
 * <p>
 * <b>It is inert over existing data.</b> A data directory with anything in it means a server was
 * provisioned here before, so an upgrade or a reinstall changes nothing at all - no
 * initialization, no account, no {@code config.xml}. That check is the first line, before the
 * layout is even validated, because it is the one that protects a customer's books.
 * <p>
 * <b>A failure leaves nothing behind.</b> The inert rule has a sharp edge: a half-made data
 * directory would make the next run inert over a server that never worked. So whatever this run
 * created - and only that - is removed when it fails, which is safe for exactly one reason: the
 * first line proved the data directory was not there. Nothing else in the shared folder is
 * listed, moved or deleted; a {@code license.dat} there is not this class's to know about.
 * <p>
 * <b>No secret leaves the JVM</b> except into the two files that exist to hold one. Both
 * passwords are generated here and used over JDBC; none is an argument, an environment variable,
 * a log line or a field of the result.
 * <p>
 * The order of the last steps is deliberate: the server is shut down <i>before</i>
 * {@code config.xml} is written, so a server that will not stop is a failure with nothing
 * pointing at it, rather than a working-looking configuration over a data directory that was
 * killed.
 */
public final class LocalServerProvisioner {

    /** Where the root password goes. A seam because restricting a file needs a real Windows. */
    @FunctionalInterface
    public interface SecretStore {
        void write(Path file, String secret) throws IOException, InterruptedException;
    }

    /** Writes {@code config.xml} and {@code config.key}; the real one is {@code DatabaseSetupService.save}. */
    @FunctionalInterface
    public interface ConfigWriter {
        void write(DatabaseConnectionSettings settings, DatabaseConfigFiles.Location target)
                throws DatabaseSetupException;
    }

    private final LocalServerControl control;
    private final LocalRootAccount root;
    private final DatabaseServerSetupService accounts;
    private final PortProbe ports;
    private final Supplier<String> secrets;
    private final SecretStore secretStore;
    private final ConfigWriter configWriter;

    public LocalServerProvisioner(LocalServerControl control, LocalRootAccount root,
                                  DatabaseServerSetupService accounts, PortProbe ports,
                                  Supplier<String> secrets, SecretStore secretStore,
                                  ConfigWriter configWriter) {
        this.control = control;
        this.root = root;
        this.accounts = accounts;
        this.ports = ports;
        this.secrets = secrets;
        this.secretStore = secretStore;
        this.configWriter = configWriter;
    }

    /** The provisioner the installer runs. */
    public static LocalServerProvisioner standard() {
        SecretGenerator generator = new SecretGenerator();
        DatabaseSetupService configs = new DatabaseSetupService(new JdbcDatabaseConnectionProbe());
        return new LocalServerProvisioner(new ProcessLocalServerControl(), new JdbcLocalRootAccount(),
                new DatabaseServerSetupService(new JdbcDatabaseServerProvisioner()), new PortProbe(),
                generator::next, RestrictedFile::write, configs::save);
    }

    public LocalProvisioningResult provision(LocalServerRequest request) throws LocalProvisioningException {
        LocalServerLayout layout = request.layout();
        if (layout.hasData()) {
            return LocalProvisioningResult.alreadyProvisioned(request.config().directory());
        }
        if (!Files.isRegularFile(layout.mysqld())) {
            throw new LocalProvisioningException(Step.NO_MYSQLD, null);
        }
        OptionalInt free = ports.firstFree(request.preferredPort());
        if (free.isEmpty()) {
            throw new LocalProvisioningException(Step.NO_FREE_PORT, null);
        }
        int port = free.getAsInt();

        LocalServerControl.RunningServer server = null;
        Step step = Step.WRITE_INI;
        try {
            Files.createDirectories(layout.shared());
            Files.writeString(layout.iniFile(), MysqlIni.render(layout.mysqlHome(), layout.dataDirectory(),
                    port, request.reachableFromNetwork()), StandardCharsets.UTF_8);

            step = Step.INITIALIZE;
            control.initialize(layout);

            step = Step.START;
            server = control.start(layout, port);

            step = Step.SECURE_ROOT;
            String rootPassword = secrets.get();
            root.secure(port, rootPassword);

            step = Step.CREATE_ACCOUNT;
            String applicationPassword = secrets.get();
            createAccount(request, port, rootPassword, applicationPassword, "localhost");
            if (request.reachableFromNetwork()) {
                // A second account, not a wider first one: this machine reaches its own server as
                // localhost, which a subnet pattern does not match, and the tills match nothing else.
                createAccount(request, port, rootPassword, applicationPassword, request.allowFrom());
            }

            step = Step.SAVE_ROOT_SECRET;
            secretStore.write(layout.rootSecretFile(), rootPassword);

            step = Step.SHUTDOWN;
            root.shutdown(port, rootPassword);
            server.awaitExit();
            server = null;

            step = Step.SAVE_CONFIG;
            configWriter.write(new DatabaseConnectionSettings(MysqlIni.LOOPBACK, port, request.database(),
                    request.applicationUser(), applicationPassword), request.config());

            return new LocalProvisioningResult(LocalProvisioningResult.Outcome.PROVISIONED, port,
                    request.database(), request.applicationUser(), request.config().directory());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            undo(layout, server);
            throw new LocalProvisioningException(step, interrupted);
        } catch (Exception failure) {
            undo(layout, server);
            throw new LocalProvisioningException(step, failure);
        }
    }

    private void createAccount(LocalServerRequest request, int port, String rootPassword,
                               String applicationPassword, String allowedHost) throws DatabaseSetupException {
        // allowStoredPrograms is false: my.ini already carries the setting, and SET PERSIST would
        // write a second copy of it into the data directory.
        accounts.provision(accounts.validate(MysqlIni.LOOPBACK, Integer.toString(port), request.database(),
                JdbcLocalRootAccount.ROOT, rootPassword, request.applicationUser(), applicationPassword,
                allowedHost, false, false));
    }

    /** Removes what this run made. Reached only when {@code hasData()} was false at the start. */
    private static void undo(LocalServerLayout layout, LocalServerControl.RunningServer server) {
        if (server != null) {
            server.kill();
        }
        deleteTree(layout.dataDirectory());
        deleteQuietly(layout.iniFile());
        deleteQuietly(layout.rootSecretFile());
    }

    private static void deleteTree(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    deleteQuietly(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path visited, IOException failure) {
                    deleteQuietly(visited);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best effort: the failure being reported is the one that matters.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // As above.
        }
    }
}
