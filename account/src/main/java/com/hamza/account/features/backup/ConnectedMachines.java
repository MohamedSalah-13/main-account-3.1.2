package com.hamza.account.features.backup;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Which other computers are talking to this database right now.
 *
 * <p>The answer comes from {@code information_schema.processlist} - the server's own
 * account of who is connected - and not from a table this application keeps. That is
 * deliberate, and it is the difference between the two questions a multi-machine install
 * asks:
 *
 * <ul>
 *   <li><b>"Is anyone connected?"</b> is this class. It cannot go stale: a till that was
 *       unplugged mid-sale has no thread on the server a second later, while a heartbeat
 *       row it wrote would keep saying "connected" until it expired.</li>
 *   <li><b>"Which machines are ours?"</b> is {@code workstation_session} - identity,
 *       version, who owns the backup. A process list cannot answer that; it knows an IP
 *       address and nothing else.</li>
 * </ul>
 *
 * <p>Connections are grouped by client address, so this machine's own pool - which holds
 * several at once - counts as one and is then excluded by matching the address the server
 * sees for our own connection. What that cannot separate is a second copy of the program
 * on <em>this</em> computer: both would report the same address. A restore run beside
 * another window of the same install is the one case this guard does not catch.
 *
 * <p>Without the {@code PROCESS} privilege MySQL shows only threads belonging to the same
 * account. That is exactly what the other tills use, so the guard still works; it would
 * only miss a machine signed in as a different database user.
 */
public final class ConnectedMachines extends AbstractDao<Object> {

    private static final String OTHER_CLIENTS = """
            SELECT DISTINCT SUBSTRING_INDEX(HOST, ':', 1) AS client
            FROM information_schema.processlist
            WHERE HOST IS NOT NULL
              AND HOST <> ''
              AND SUBSTRING_INDEX(HOST, ':', 1) <> ?
            """;

    private static final String OWN_CLIENT = """
            SELECT SUBSTRING_INDEX(HOST, ':', 1)
            FROM information_schema.processlist
            WHERE ID = CONNECTION_ID()
            """;

    /**
     * Whether the MySQL server is running on this very computer.
     *
     * <p>Asked of the server rather than worked out from {@code config.xml}: the host in
     * the configuration is whatever somebody typed - {@code localhost}, {@code 127.0.0.1},
     * the machine's own name, or its address on the network, all of which mean this
     * machine and only one of which looks like it. The address the server sees the
     * connection arriving from is the answer without the guesswork.
     */
    public boolean databaseIsOnThisMachine() throws DaoException {
        String own = ownClient();
        return "localhost".equalsIgnoreCase(own) || "127.0.0.1".equals(own) || "::1".equals(own);
    }

    /** The address this machine's connections arrive from, as the server sees them. */
    public String ownClient() throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(OWN_CLIENT);
                 ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String host = resultSet.getString(1);
                    return host == null ? "" : host;
                }
            }
            return "";
        });
    }

    /**
     * The addresses of the machines connected to this database other than this one, in no
     * particular order, empty when this machine is alone.
     */
    public List<String> otherClients() throws DaoException {
        String ownHost = ownClient();
        return withConnection(connection -> {
            String own = ownHost;
            List<String> others = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(OTHER_CLIENTS)) {
                statement.setString(1, own);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String client = resultSet.getString("client");
                        if (client != null && !client.isBlank()) {
                            others.add(client);
                        }
                    }
                }
            }
            return others;
        });
    }
}
