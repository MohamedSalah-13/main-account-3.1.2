package com.hamza.account.features.dbsetup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the bundled {@code mysqld} as a child process, for the minutes a first install takes.
 * <p>
 * The Windows service is the installer's to create; this never touches it. Here the server is
 * started only long enough to give root a password and create the application's account, then
 * shut down, so that the first thing the service ever opens is a finished data directory.
 * <p>
 * Nothing secret is ever among the arguments: both commands take the option file and nothing else.
 */
public final class ProcessLocalServerControl implements LocalServerControl {

    private static final Duration INITIALIZE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration STOP_TIMEOUT = Duration.ofMinutes(2);

    @Override
    public void initialize(LocalServerLayout layout) throws IOException, InterruptedException {
        Process process = launch(layout, "--initialize-insecure");
        if (!process.waitFor(INITIALIZE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("mysqld --initialize-insecure did not finish; see " + layout.logFile());
        }
        if (process.exitValue() != 0) {
            throw new IOException("mysqld --initialize-insecure exited with " + process.exitValue()
                    + "; see " + layout.logFile());
        }
    }

    @Override
    public RunningServer start(LocalServerLayout layout, int port) throws IOException, InterruptedException {
        Process process = launch(layout);
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("mysqld stopped while starting, exit " + process.exitValue()
                        + "; see " + layout.logFile());
            }
            if (accepts(port)) {
                return new Running(process);
            }
            Thread.sleep(500);
        }
        process.destroyForcibly();
        throw new IOException("mysqld did not accept connections on " + port + "; see " + layout.logFile());
    }

    private static Process launch(LocalServerLayout layout, String... extra) throws IOException {
        Files.createDirectories(layout.shared());
        List<String> command = new java.util.ArrayList<>();
        command.add(layout.mysqld().toString());
        // --defaults-file has to come first, or mysqld reads the machine's other option files too.
        command.add("--defaults-file=" + layout.iniFile());
        command.addAll(List.of(extra));
        command.add("--console");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(layout.mysqlHome().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(layout.logFile().toFile()));
        return builder.start();
    }

    private static boolean accepts(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(MysqlIni.LOOPBACK, port), 1000);
            return true;
        } catch (IOException notYet) {
            return false;
        }
    }

    private record Running(Process process) implements RunningServer {

        @Override
        public void awaitExit() throws InterruptedException {
            if (!process.waitFor(STOP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                kill();
            }
        }

        @Override
        public void kill() {
            process.destroyForcibly();
            try {
                process.waitFor(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
