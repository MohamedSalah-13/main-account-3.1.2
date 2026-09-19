package com.hamza.account.features.dbsetup;

import java.io.IOException;

/**
 * Starting and stopping {@code mysqld} as a child process, as a seam.
 * <p>
 * The provisioner's rules - inert over existing data, nothing left behind by a failure, no secret
 * in the result - are all testable without a MySQL on the machine, and are, against a fake of
 * this. The real one is {@link ProcessLocalServerControl}.
 */
public interface LocalServerControl {

    /** Runs {@code mysqld --initialize-insecure}: a new data directory whose root has no password yet. */
    void initialize(LocalServerLayout layout) throws IOException, InterruptedException;

    /** Starts the server and returns once it accepts connections on {@code port}. */
    RunningServer start(LocalServerLayout layout, int port) throws IOException, InterruptedException;

    interface RunningServer {
        /** Waits for a server that has been told to shut down; kills it if it will not go. */
        void awaitExit() throws InterruptedException;

        /** For the failure path: the server is stopped however it can be. */
        void kill();
    }
}
