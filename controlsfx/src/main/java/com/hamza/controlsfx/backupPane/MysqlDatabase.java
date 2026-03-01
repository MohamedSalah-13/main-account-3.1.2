package com.hamza.controlsfx.backupPane;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class MysqlDatabase implements DatabaseBackup {

    /**
     * The name of the MySQL database to be backed up or restored.
     * This variable is assigned during the creation of a MysqlDatabase object and
     * used in backup and restore operations to specify the target database.
     */
    @NotNull
    private final String dbname;
    /**
     * The username for authenticating with the MySQL database.
     */
    @NotNull
    private final String username;
    /**
     * The password used for MySQL database authentication.
     */
    @NotNull
    private final String pass;
    /**
     * The URL string used to perform a backup operation for the MySQL database.
     * This URL contains connection information, including credentials and other
     * parameters necessary for the backup process.
     */
    @NotNull
    private final String dbUrl_backup;
    /**
     * The URL used for restoring the MySQL database. This URL typically contains information
     * about the database server, such as the hostname, port number, and database name,
     * which is required for connecting to the database during the restoration process.
     */
    @NotNull
    private final String dbUrl_restore;
    /**
     * The port number on which the MySQL database is running.
     */
    @NotNull
    private final String port;
    @NotNull
    private final String host;

    /**
     * Constructs a MysqlDatabase object with the specified parameters.
     *
     * @param dbname        the name of the database
     * @param username      the username for database authentication
     * @param pass          the password for database authentication
     * @param port          the port number on which the database is running
     * @param dbUrl_backup  the URL used for backing up the database
     * @param dbUrl_restore the URL used for restoring the database
     */
    public MysqlDatabase(@NotNull String dbname, @NotNull String username, @NotNull String pass, @NotNull String port, @NotNull String dbUrl_backup, @NotNull String dbUrl_restore, @NotNull String host) {
        this.dbname = dbname;
        this.username = username;
        this.pass = pass;
        this.dbUrl_backup = dbUrl_backup.isEmpty() ? "mysqldump" : dbUrl_backup;
        this.dbUrl_restore = dbUrl_restore.isEmpty() ? "mysql" : dbUrl_restore;
        this.port = port;
        this.host = host;
    }


    /**
     * Creates a backup of the MySQL database to the specified path.
     *
     * @param backupPath the path where the backup should be stored
     * @return true if the backup was successful, false otherwise
     * @throws IOException          if an I/O error occurs during the backup process
     * @throws InterruptedException if the backup process is interrupted
     */
    @Override
    public boolean backup(@NotNull String backupPath) throws IOException, InterruptedException {
        String command = dbUrl_backup + " -u" + username + " -p" + pass + " -P" + port + " -h" + host + " " + dbname + " -r " + backupPath;
        return new DatabaseProcessHandler(command).isProcessComplete();
    }

    /**
     * Restores the MySQL database from a specified backup file.
     *
     * @param filePath the path to the backup file from which to restore the database.
     * @return true if the restore operation was successful; false otherwise.
     * @throws IOException          if an I/O error occurs during the restore process.
     * @throws InterruptedException if the restore process is interrupted.
     */
    @Override
    public boolean restore(@NotNull String filePath) throws IOException, InterruptedException {
        String[] command = new String[]{dbUrl_restore, "-u" + username, "-p" + pass, "-P" + port, "-h" + host, dbname, "-e", "source " + filePath};
        return new DatabaseProcessHandler(command).isProcessComplete();
    }


    public boolean backup(@NotNull String backupPath, boolean excludeImages) throws IOException, InterruptedException {
        if (excludeImages) {
            // استخدام where clause لتحديد البيانات المراد نسخها أو استبدال الصور بـ NULL
            String command = dbUrl_backup + " -u" + username + " -p" + pass + " -P" + port + " -h" + host +
                    " --single-transaction --routines --triggers " + dbname +
                    " --ignore-table=" + dbname + ".items" +
                    " -r " + backupPath;

            boolean mainBackup = new DatabaseProcessHandler(command).isProcessComplete();

            if (mainBackup) {
                // نسخ جدول items بدون عمود item_image
                String itemsStructure = dbUrl_backup + " -u" + username + " -p" + pass + " -P" + port + " -h" + host +
                        " --no-data " + dbname + " items >> " + backupPath;
                new DatabaseProcessHandler(itemsStructure).isProcessComplete();

                // إضافة البيانات بدون الصور (يتطلب استعلام مخصص)
                String itemsData = "mysql -u" + username + " -p" + pass + " -P" + port + " -h" + host +
                        " -e \"SELECT id, barcode, nameItem, buyPrice, selPrice1, selPrice2, selPrice3, " +
                        "mini_quantity, firstBalanceForStock, activeItem " +
                        "FROM " + dbname + ".items\" >> " + backupPath;
                new DatabaseProcessHandler(itemsData).isProcessComplete();
            }

            return mainBackup;
        } else {
            String command = dbUrl_backup + " -u" + username + " -p" + pass + " -P" + port + " -h" + host +
                    " " + dbname + " -r " + backupPath;
            return new DatabaseProcessHandler(command).isProcessComplete();
        }
    }
}
