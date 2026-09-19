package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The phase B statements pinned, and the places where phase B is said twice: V71 and the
 * permission it seeds, and the message keys no static scan can see.
 */
class DelegateActivityQueryTest {

    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V71__collection_delegate.sql");
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "hamza", "account",
            "controller", "employee", "DelegatePerformanceController.java");
    private static final Path BUNDLE_DIR = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");

    private static int parameters(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    /** Three periods in the statement - sales, returns, collections - and the repository binds one to all. */
    @Test
    void theActivityStatementIsPinned() {
        assertEquals("""
                SELECT e.id,
                       e.column_name,
                       e.is_active,
                       COALESCE(s.net, 0)                                          AS sales,
                       COALESCE(r.net, 0)                                          AS sales_returns,
                       COALESCE(s.cash, 0) - COALESCE(r.cash, 0) + COALESCE(c.paid, 0) AS collected
                FROM employees e
                         JOIN jobs j ON j.id = e.job
                         LEFT JOIN (SELECT delegate_id, SUM(total - discount) AS net, SUM(paid_up) AS cash
                                    FROM total_sales
                                    WHERE invoice_date BETWEEN ? AND ?
                                    GROUP BY delegate_id) s ON s.delegate_id = e.id
                         LEFT JOIN (SELECT delegate_id, SUM(total - discount) AS net, SUM(paid_from_treasury) AS cash
                                    FROM total_sales_re
                                    WHERE invoice_date BETWEEN ? AND ?
                                    GROUP BY delegate_id) r ON r.delegate_id = e.id
                         LEFT JOIN (SELECT delegate_id, SUM(paid) AS paid
                                    FROM customers_accounts
                                    WHERE account_date BETWEEN ? AND ?
                                      AND delegate_id IS NOT NULL
                                    GROUP BY delegate_id) c ON c.delegate_id = e.id
                WHERE (j.is_delegate = 1 AND e.is_active = 1)
                   OR s.delegate_id IS NOT NULL
                   OR r.delegate_id IS NOT NULL
                   OR c.delegate_id IS NOT NULL
                ORDER BY e.column_name""", DelegateActivityQuery.ACTIVITY_SQL);
        assertEquals(6, parameters(DelegateActivityQuery.ACTIVITY_SQL));
    }

    /**
     * {@code purchase} is a note and is not cash: a credit note is nobody's collection. The
     * statement never reads the column, which is the whole of how that rule is kept.
     */
    @Test
    void aNoteIsNeverCountedAsCollected() {
        assertFalse(DelegateActivityQuery.ACTIVITY_SQL.contains("purchase"));
        assertFalse(DelegateActivityQuery.UNATTRIBUTED_COLLECTIONS_SQL.contains("purchase"));
        assertTrue(DelegateActivityQuery.ATTRIBUTE_COLLECTION_SQL.contains("AND ca.paid <> 0"));
    }

    @Test
    void theUnattributedFigureIsPinned() {
        assertEquals("""
                SELECT COALESCE(SUM(paid), 0)
                FROM customers_accounts
                WHERE account_date BETWEEN ? AND ?
                  AND delegate_id IS NULL""", DelegateActivityQuery.UNATTRIBUTED_COLLECTIONS_SQL);
        assertEquals(2, parameters(DelegateActivityQuery.UNATTRIBUTED_COLLECTIONS_SQL));
    }

    /**
     * The invoice's delegate first, the customer's default second, and only over a row that
     * names nobody yet - so it is written once and running it again changes nothing.
     */
    @Test
    void theAttributionIsPinned() {
        assertEquals("""
                UPDATE customers_accounts ca
                SET ca.delegate_id = COALESCE(
                        (SELECT ts.delegate_id
                         FROM total_sales ts
                         WHERE ca.numberInv > 0
                           AND ts.invoice_number = ca.numberInv),
                        (SELECT e.id
                         FROM custom cu
                                  JOIN employees e ON e.id = cu.default_delegate_id
                         WHERE cu.id = ca.account_code))
                WHERE ca.account_num = ?
                  AND ca.paid <> 0
                  AND ca.delegate_id IS NULL""", DelegateActivityQuery.ATTRIBUTE_COLLECTION_SQL);
        assertEquals(1, parameters(DelegateActivityQuery.ATTRIBUTE_COLLECTION_SQL));
    }

    // ---- V71 -------------------------------------------------------------------------------

    /** {@code auth_permission.description} is VARCHAR(50); V65's first draft carried 62. */
    @Test
    void theSeededPermissionIsTheDeclaredOneAndItsDescriptionFits() throws IOException {
        String sql = Files.readString(MIGRATION);
        Matcher rows = Pattern.compile("\\('(commission\\.[a-z.]+)', '([^']+)'").matcher(sql);
        Set<String> seeded = new TreeSet<>();
        while (rows.find()) {
            seeded.add(rows.group(1));
            assertTrue(rows.group(2).length() <= 50, rows.group(1) + " is described in "
                    + rows.group(2).length() + " characters");
        }
        assertEquals(Set.of(AppPermissions.COMMISSION_REPORTS.value()), seeded);
    }

    /**
     * The old rows stay NULL. A delegate guessed for an old collection from the customer's
     * default <em>today</em> is exactly the derivation the column exists to prevent, applied
     * backwards - so the migration must not contain a backfill.
     */
    @Test
    void theMigrationGuessesNoDelegateForOldCollections() throws IOException {
        String sql = Files.readString(MIGRATION).replaceAll("(?m)^\\s*--.*$", "");
        assertFalse(sql.toUpperCase().contains("UPDATE CUSTOMERS_ACCOUNTS"));
        assertTrue(sql.contains("'INT NULL "), "NULL is the answer for everything before V71");
    }

    // ---- message keys ----------------------------------------------------------------------

    /**
     * The screen names its keys through its own {@code card(...)}, {@code stepper(...)} and
     * {@code button(...)}, none of which {@code MessageKeyArchitectureTest} reads.
     */
    @Test
    void everyKeyOfTheReportIsInAllThreeBundles() throws IOException {
        Set<String> keys = new TreeSet<>();
        Matcher found = Pattern.compile("\"(delegate\\.performance\\.[a-z.]+)\"").matcher(Files.readString(CONTROLLER));
        while (found.find()) {
            keys.add(found.group(1));
        }
        assertTrue(keys.size() >= 20, "the scan found " + keys);

        List<String> missing = new ArrayList<>();
        for (String bundle : List.of("messages.properties", "messages_ar.properties", "messages_en.properties")) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(BUNDLE_DIR.resolve(bundle), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            for (String key : keys) {
                if (!properties.containsKey(key)) {
                    missing.add(bundle + ": " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }
}
