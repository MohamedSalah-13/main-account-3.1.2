package com.hamza.account.features.dbsetup;

import java.nio.file.Path;

/**
 * The {@code my.ini} the bundled server runs with - ours, written once, pinned by its test.
 * <p>
 * The 4.1.3 installer had none, so its server listened on every interface on the default port.
 * What each line is for:
 * <ul>
 *   <li>{@code bind-address} is loopback unless other machines will connect. A till on its own
 *       has no reason to answer the network.</li>
 *   <li>{@code mysqlx=0} - the X plugin wants port 33060 whatever {@code port} says, so a machine
 *       that already has a MySQL would have two servers asking for it. Nothing here speaks X.</li>
 *   <li>{@code log_bin_trust_function_creators} - the migrations create triggers and procedures
 *       through an account that is deliberately not SUPER, which MySQL refuses while the binary
 *       log is on unless this is set.</li>
 *   <li><b>No collation is named.</b> {@code V63} exists because a database was created with one
 *       collation and its tables restored with another; the server keeps its own default and
 *       {@code DatabaseMigrationService.alignDatabaseCollationWithItsTables} settles the rest.</li>
 *   <li>No time zone is named either: the JDBC URL says {@code connectionTimeZone=LOCAL}, which
 *       is only right while the server runs on the machine's own clock - its default.</li>
 * </ul>
 * Paths are written with forward slashes: a backslash in an option file starts an escape, and
 * {@code C:\ProgramData\new} is read as a path with a newline in it.
 */
public final class MysqlIni {

    public static final String LOOPBACK = "127.0.0.1";
    public static final String EVERY_INTERFACE = "0.0.0.0";

    private MysqlIni() {
    }

    public static String render(Path mysqlHome, Path dataDirectory, int port, boolean reachableFromNetwork) {
        return """
                [mysqld]
                basedir=%s
                datadir=%s
                port=%d
                bind-address=%s
                mysqlx=0
                character-set-server=utf8mb4
                innodb_buffer_pool_size=256M
                log_bin_trust_function_creators=1

                [client]
                port=%d
                """.formatted(slashes(mysqlHome), slashes(dataDirectory), port,
                reachableFromNetwork ? EVERY_INTERFACE : LOOPBACK, port);
    }

    static String slashes(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
