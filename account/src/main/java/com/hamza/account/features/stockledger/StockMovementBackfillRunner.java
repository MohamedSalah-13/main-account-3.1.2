package com.hamza.account.features.stockledger;

import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;

import java.io.File;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

/**
 * Runs the §8.5 historical backfill against whatever database {@code config.xml} names,
 * and prints {@link StockLedgerReconciliationReport}'s verdict on it.
 * <p>
 * <b>Defaults to a dry run.</b> Everything happens inside one transaction; without
 * {@code --commit} on the command line that transaction is always rolled back, so running
 * this with no arguments is safe against a real install - it reports what backfilling
 * would produce without writing a single row. Pass {@code --commit} only once the printed
 * report shows zero mismatches and the write is actually wanted.
 * <p>
 * Not wired into {@code Main}/{@code DownLoadApplication} startup, and never will be: a
 * backfill is a deliberate, one-time, human-triggered action, run from the command line
 * the way {@code view/*Application} classes are already run standalone for development
 * (see e.g. {@code view.AboutApplication}).
 */
public final class StockMovementBackfillRunner {

    private StockMovementBackfillRunner() {
    }

    public static void main(String[] args) throws Exception {
        boolean commit = args.length > 0 && "--commit".equals(args[0]);

        connect();
        try {
            Connection transaction = ConnectionManager.beginTransaction();
            try {
                int written = new StockMovementBackfillService(new StockMovementDao()).backfillAll();
                List<StockLedgerReconciliationReport.Mismatch> mismatches =
                        new StockLedgerReconciliationReport().run();

                System.out.println(written + " movement(s) written.");
                if (mismatches.isEmpty()) {
                    System.out.println("Reconciliation: OK - every item/stock balance matches "
                            + "quantity_items_table exactly.");
                } else {
                    System.out.println("Reconciliation: " + mismatches.size() + " MISMATCH(ES):");
                    for (StockLedgerReconciliationReport.Mismatch mismatch : mismatches) {
                        System.out.printf("  item %d, stock %d: view=%.3f ledger=%.3f diff=%.3f%n",
                                mismatch.itemId(), mismatch.stockId(),
                                mismatch.viewBalance(), mismatch.ledgerBalance(), mismatch.difference());
                    }
                }

                if (commit && mismatches.isEmpty()) {
                    transaction.commit();
                    System.out.println("COMMITTED.");
                } else {
                    transaction.rollback();
                    System.out.println(commit
                            ? "NOT committed - mismatches found. Fix and re-run."
                            : "DRY RUN - nothing committed. Re-run with --commit to apply.");
                }
            } catch (DaoException | RuntimeException e) {
                transaction.rollback();
                throw e;
            } finally {
                ConnectionManager.endTransaction(transaction);
            }
        } finally {
            DataSourceProvider.shutdown();
        }
    }

    private static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
    }
}
