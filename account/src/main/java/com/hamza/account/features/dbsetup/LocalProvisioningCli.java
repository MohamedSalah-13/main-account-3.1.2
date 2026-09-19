package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code AccountK-Database-Setup.exe --provision-local ...}: the installer's way in, with no window.
 * <p>
 * <pre>
 * --provision-local
 * --mysql-home  DIR     the bundled server, the folder holding bin\mysqld.exe          (required)
 * --shared      DIR     where the data, my.ini and config.xml go; default ProgramData\AccountK
 * --port        N       the first port to try; default 3307
 * --allow-from  CIDR    the network the tills are on, e.g. 192.168.1.0/24; absent = this machine only
 * --result      FILE    where to write the outcome as key=value lines; default SHARED\provision-result.txt
 * </pre>
 * The exit code is what the installer acts on - 0 for a server that is ready, whether this run made
 * it or found it, and 1 for anything else - and the result file is what it shows and logs. There is
 * deliberately <b>no option that takes a password</b>: an argument is visible in the process list,
 * so both secrets are made inside {@link LocalServerProvisioner} and never come through here.
 */
public final class LocalProvisioningCli {

    public static final String FLAG = "--provision-local";

    public static final int READY = 0;
    public static final int FAILED = 1;
    public static final int BAD_ARGUMENTS = 2;

    private static final List<String> OPTIONS = List.of("--mysql-home", "--shared", "--port", "--allow-from", "--result");

    private final LocalServerProvisioner provisioner;

    public LocalProvisioningCli(LocalServerProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    public static boolean requestedBy(String[] args) {
        return args != null && List.of(args).contains(FLAG);
    }

    public int run(String[] args) {
        Map<String, String> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException bad) {
            System.err.println(bad.getMessage());
            return BAD_ARGUMENTS;
        }

        DatabaseConfigFiles.Location config = options.containsKey("--shared")
                ? DatabaseConfigFiles.at(Path.of(options.get("--shared")))
                : DatabaseConfigFiles.preferred();
        LocalServerLayout layout = new LocalServerLayout(Path.of(options.get("--mysql-home")), config.directory());
        Path resultFile = options.containsKey("--result")
                ? Path.of(options.get("--result"))
                : layout.shared().resolve("provision-result.txt");
        int port = Integer.parseInt(options.getOrDefault("--port", Integer.toString(PortProbe.FIRST)));

        LocalServerRequest request = new LocalServerRequest(layout, config, port,
                LocalServerRequest.DEFAULT_DATABASE, LocalServerRequest.DEFAULT_APPLICATION_USER,
                options.get("--allow-from"));
        try {
            LocalProvisioningResult result = provisioner.provision(request);
            write(resultFile, result.toProperties());
            return READY;
        } catch (LocalProvisioningException failure) {
            write(resultFile, "outcome=FAILED\nstep=" + failure.step() + "\nlog=" + layout.logFile() + "\n");
            log(layout, failure);
            return FAILED;
        }
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (FLAG.equals(argument)) {
                continue;
            }
            if (!OPTIONS.contains(argument)) {
                throw new IllegalArgumentException("unknown option: " + argument);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + argument);
            }
            options.put(argument, args[++i]);
        }
        if (!options.containsKey("--mysql-home")) {
            throw new IllegalArgumentException("--mysql-home is required");
        }
        if (options.containsKey("--port")) {
            try {
                int port = Integer.parseInt(options.get("--port"));
                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException notAPort) {
                throw new IllegalArgumentException("--port is not a port: " + options.get("--port"));
            }
        }
        return options;
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            System.err.println("could not write " + file + ": " + unwritable.getMessage());
        }
    }

    /** The cause goes beside mysqld's own output. A JDBC failure names a host and a user, never a password. */
    private static void log(LocalServerLayout layout, LocalProvisioningException failure) {
        StringWriter trace = new StringWriter();
        trace.append("provisioning failed at ").append(failure.step().name()).append(System.lineSeparator());
        if (failure.getCause() != null) {
            failure.getCause().printStackTrace(new PrintWriter(trace));
        }
        try {
            Files.createDirectories(layout.shared());
            Files.writeString(layout.logFile(), trace.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            System.err.println(trace);
        }
    }
}
