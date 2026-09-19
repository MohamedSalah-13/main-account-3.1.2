package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;

import java.util.Objects;

/**
 * What the installer asks for.
 *
 * @param allowFrom the network other machines will connect from, as {@code 192.168.1.0/24}, or
 *                  null when this machine is on its own - which keeps the server on loopback and
 *                  creates no account a remote client could use
 */
public record LocalServerRequest(LocalServerLayout layout, DatabaseConfigFiles.Location config,
                                 int preferredPort, String database, String applicationUser,
                                 String allowFrom) {

    public static final String DEFAULT_DATABASE = "account";
    public static final String DEFAULT_APPLICATION_USER = "accountk";

    public LocalServerRequest {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(config);
        Objects.requireNonNull(database);
        Objects.requireNonNull(applicationUser);
        allowFrom = allowFrom == null || allowFrom.isBlank() ? null : allowFrom.strip();
    }

    public boolean reachableFromNetwork() {
        return allowFrom != null;
    }
}
