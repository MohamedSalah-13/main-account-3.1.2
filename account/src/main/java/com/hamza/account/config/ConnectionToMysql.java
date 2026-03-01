package com.hamza.account.config;

import com.hamza.controlsfx.backupPane.MysqlDatabase;

import static com.hamza.account.config.PropertiesName.getDatabaseUsePathVariableSetting;

public class ConnectionToMysql {

    /**
     * The default command for invoking the `mysqldump` utility, used for creating database backups in MySQL.
     * This command will be used when the path to the `mysqldump` executable is not explicitly specified.
     * It can be overridden by specifying a custom local path if the configuration property `mysql.use.path.variable` is not set to "true".
     */
    private static final String DEFAULT_MYSQLDUMP_COMMAND = "mysqldump";
    /**
     * The default command used to invoke MySQL from the command line.
     * <p>
     * This constant specifies the default command name that will be executed
     * to interact with a MySQL database via the command line interface. Typically,
     * it assumes that the `mysql` command is available in the system's PATH.
     */
    private static final String DEFAULT_MYSQL_COMMAND = "mysql";

    /**
     * Establishes a connection to a MySQL database using configuration settings.
     *
     * @return a MysqlDatabase instance representing the established connection to the database.
     */
    public MysqlDatabase connect() {
        String mysqlDumpPath = DEFAULT_MYSQLDUMP_COMMAND;
        String mysqlPath = DEFAULT_MYSQL_COMMAND;
        if (!getDatabaseUsePathVariableSetting()) {
//            String localMysqlPath = getLocalMysqlPath();
//            mysqlDumpPath = localMysqlPath.concat("/bin/mysqldump");
//            mysqlPath = localMysqlPath.concat("/bin/mysql");
            mysqlDumpPath = "mysql/bin/mysqldump";
            mysqlPath = "mysql/bin/mysql";

        }

        ConnectionToDatabase database = new ConnectionToDatabase();
        return new MysqlDatabase(database.getDbName(), database.getUsername(), database.getPass(), database.getPort(), mysqlDumpPath, mysqlPath, database.getHost());
    }

}
