package com.hamza.account.features.dbsetup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Root over JDBC on loopback.
 * <p>
 * Over JDBC rather than through {@code mysqladmin}, for one reason: a password handed to a child
 * process has to travel in its arguments or its environment, and this one never leaves the JVM.
 */
public final class JdbcLocalRootAccount implements LocalRootAccount {

    static final String ROOT = "root";

    @Override
    public void secure(int port, String newPassword) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(port), ROOT, "")) {
            // Every root account the initialization made, not only 'root'@'localhost': one left
            // without a password is an open server.
            try (Statement list = connection.createStatement();
                 var hosts = list.executeQuery("SELECT host FROM mysql.user WHERE user = 'root'")) {
                java.util.List<String> found = new java.util.ArrayList<>();
                while (hosts.next()) {
                    found.add(hosts.getString(1));
                }
                for (String host : found) {
                    try (PreparedStatement alter = connection.prepareStatement(
                            "ALTER USER 'root'@'" + host.replace("'", "''") + "' IDENTIFIED BY ?")) {
                        alter.setString(1, newPassword);
                        alter.executeUpdate();
                    }
                }
            }
        }
    }

    @Override
    public void shutdown(int port, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(port), ROOT, password);
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    static String url(int port) {
        return "jdbc:mysql://" + MysqlIni.LOOPBACK + ":" + port
                + "/?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL"
                + "&connectTimeout=5000&allowPublicKeyRetrieval=true&useSSL=false";
    }
}
