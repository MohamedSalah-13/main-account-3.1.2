package com.hamza.controlsfx.notifications;

/**
 * What clicking a notification does.
 * <p>
 * A plain {@link Runnable} would force every call site to wrap the checked
 * exceptions that opening a screen or reading from a DAO throws, and the usual
 * result of that is an empty catch block. This lets the action throw and leaves
 * the reporting to whoever invokes it.
 */
@FunctionalInterface
public interface NotificationCommand {

    void run() throws Exception;
}
