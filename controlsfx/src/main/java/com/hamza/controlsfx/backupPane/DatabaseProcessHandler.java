package com.hamza.controlsfx.backupPane;

import java.io.IOException;

public class DatabaseProcessHandler {
    /**
     * The exit code that indicates successful completion of the process.
     * This value is typically returned by processes that terminate without any errors.
     */
    private static final int SUCCESS_EXIT_CODE = 0;
    /**
     * A representation of the system process that is executed by the runtime.
     * This is used to handle and manage external processes, such as starting or
     * stopping a database backup or restore operation.
     */
    private final Process runtimeProcess;

    /**
     * Constructs a DatabaseProcessHandler that executes the provided restore command.
     *
     * @param restore an array of strings representing the command to execute for restoring the database
     * @throws IOException if an I/O error occurs when executing the command
     */
    public DatabaseProcessHandler(String[] restore) throws IOException {
        this(Runtime.getRuntime().exec(restore));
    }

    /**
     * Constructs a DatabaseProcessHandler that executes the given backup command.
     *
     * @param backup the command to execute for performing a database backup
     * @throws IOException if an I/O error occurs during the execution of the command
     */
    public DatabaseProcessHandler(String backup) throws IOException {
        this(Runtime.getRuntime().exec(backup));
    }

    /**
     * Constructs a DatabaseProcessHandler with the given Process instance.
     *
     * @param process the process instance to be handled by this handler
     */
    private DatabaseProcessHandler(Process process) {
        this.runtimeProcess = process;
    }

    /**
     * Checks if the process has completed successfully.
     *
     * @return true if the process has completed with a success exit code, false otherwise.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public boolean isProcessComplete() throws InterruptedException {
        int processComplete = runtimeProcess.waitFor();
        return processComplete == SUCCESS_EXIT_CODE;
    }
}
