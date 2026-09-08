package com.hamza.account.features.dbsetup;

import java.util.Objects;

/** Connection values entered by the installer. Password is deliberately absent from toString. */
public final class DatabaseConnectionSettings {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public DatabaseConnectionSettings(String host, int port, String database, String username, String password) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.database = Objects.requireNonNull(database);
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
    }

    public String host() { return host; }
    public int port() { return port; }
    public String database() { return database; }
    public String username() { return username; }
    public String password() { return password; }

    public boolean sameConnectionAs(DatabaseConnectionSettings other) {
        return other != null && port == other.port
                && host.equals(other.host)
                && database.equals(other.database)
                && username.equals(other.username)
                && password.equals(other.password);
    }

    public String jdbcUrl(boolean includeDatabase) {
        String catalog = includeDatabase ? "/" + database : "/";
        return "jdbc:mysql://" + host + ":" + port + catalog
                + "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL"
                + "&connectTimeout=5000&socketTimeout=5000&tcpKeepAlive=true";
    }

    @Override
    public String toString() {
        return "DatabaseConnectionSettings[host=" + host + ", port=" + port
                + ", database=" + database + ", username=" + username + ", password=<hidden>]";
    }
}
