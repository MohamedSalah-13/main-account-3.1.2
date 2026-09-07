package com.hamza.account.features.audit;

import org.apache.logging.log4j.LogManager;

/** Optional observer for completed audit operations; notification failures never roll work back. */
@FunctionalInterface
public interface AuditOperationListener {

    AuditOperationListener NONE = event -> { };

    void onCompleted(AuditOperationEvent event);

    default void notifySafely(AuditOperationEvent event) {
        try {
            onCompleted(event);
        } catch (RuntimeException error) {
            LogManager.getLogger(AuditOperationListener.class)
                    .warn("Could not publish the completed audit operation", error);
        }
    }
}
