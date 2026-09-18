package com.hamza.account.manual;

import com.hamza.account.service.version.DatabaseMigrationService;
import com.hamza.account.config.ConnectionToDatabase;

import java.nio.file.Path;

/**
 * Migrates the demo database and seeds it, between {@link ManualDemoDatabase} creating the empty
 * schema and {@link ManualCaptureApplication} photographing it.
 * <p>
 * It runs the application's own migration rather than a schema of its own: the manual is a picture
 * of the program customers run, and a screen drawn over a hand-made schema is a picture of
 * something else. Migrating first is also what makes the seed file readable - it can name columns
 * that arrived in {@code V56} without repeating why they exist.
 * <p>
 * No JavaFX is started here. {@code DownLoadApplication.bootstrapForTooling()} would do the
 * migration too, but it also runs the trial check and loads the product profile, both of which can
 * put a dialog on screen - and this step runs before there is a window to put one on.
 */
public final class ManualDemoSetup {

    private ManualDemoSetup() {
    }

    public static void main(String[] args) throws Exception {
        if (System.getenv("ACCOUNT_CONFIG_DIR") == null) {
            System.err.println("Refusing to run without ACCOUNT_CONFIG_DIR: it is what points this "
                    + "at the demo database rather than at the one this machine normally uses.");
            System.exit(2);
        }
        Path seed = Path.of(args.length > 0 ? args[0] : "docs/manual/demo-data.sql");
        System.out.println("migrating " + ManualDemoDatabase.SCHEMA + " ...");
        new DatabaseMigrationService(new ConnectionToDatabase()).updateDatabaseIfNeeded();
        ManualDemoDatabase.seed(seed);
    }
}
