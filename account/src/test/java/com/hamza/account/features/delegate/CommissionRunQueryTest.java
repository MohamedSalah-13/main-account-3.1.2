package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run's statements, and the places where phase C is said twice and the two must agree: the
 * payroll's "due" and "paid", the enums and V72's CHECKs, the permissions and the rows V72
 * seeds, the triggers' place in their file, and the message keys no static scan can see.
 */
class CommissionRunQueryTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "hamza", "account");
    private static final Path BUNDLE_DIR = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");

    private static int parameters(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    // ---- the payroll road ----------------------------------------------------------------------

    /**
     * What the payroll adds up and what it marks as paid are the same lines by construction: the
     * two statements share everything from {@code FROM} on. A condition added to one alone would
     * pay a line and not mark it - posted a second time from the commission screen - or mark one
     * and not pay it.
     */
    @Test
    void whatThePayrollSumsIsExactlyWhatItMarks() {
        String due = CommissionRunQuery.PAYROLL_DUE_SQL;
        String marked = CommissionRunQuery.INSERT_PAYROLL_POSTINGS_SQL;
        assertEquals(due.substring(due.indexOf("FROM commission_line")),
                marked.substring(marked.indexOf("FROM commission_line")));
        assertEquals(2, parameters(due));
        assertEquals(4, parameters(marked));
    }

    /** Approved, unposted, positive, his, and of that period or an earlier one - never a later one. */
    @Test
    void whatIsDueIsPinned() {
        assertEquals("""
                SELECT COALESCE(SUM(l.amount), 0)
                FROM commission_line l
                         JOIN commission_run r ON r.id = l.run_id
                         LEFT JOIN commission_posting p ON p.line_id = l.id
                WHERE r.status = 'APPROVED'
                  AND p.line_id IS NULL
                  AND l.amount > 0
                  AND l.employee_id = ?
                  AND r.period_year * 100 + r.period_month <= ?""", CommissionRunQuery.PAYROLL_DUE_SQL);
    }

    @Test
    void theMonthKeyIsTheGeneratedColumnsOwnArithmetic() throws IOException {
        assertEquals(202610, JdbcCommissionRunRepository.key(YearMonth.of(2026, 10)));
        assertTrue(migration("V72__commission_run.sql").contains("period_year * 100 + period_month"));
    }

    // ---- the run -------------------------------------------------------------------------------

    @Test
    void theInsertsArePinned() {
        assertEquals("""
                INSERT INTO commission_run (period_year, period_month, notes, user_id)
                VALUES (?, ?, ?, ?)""", CommissionRunQuery.INSERT_RUN_SQL);
        assertEquals("""
                INSERT INTO commission_line (run_id, employee_id, rule_id, basis, tier_mode, target, tiers_snapshot,
                       sales, sales_returns, collected, base_amount, achievement_percent, tier, rate_percent, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", CommissionRunQuery.INSERT_LINE_SQL);
        assertEquals(15, parameters(CommissionRunQuery.INSERT_LINE_SQL));
        assertEquals("""
                INSERT INTO employee_ledger (employee_id, entry_date, kind, amount, notes, user_id)
                VALUES (?, ?, 'COMMISSION', ?, ?, ?)""", CommissionRunQuery.INSERT_LEDGER_COMMISSION_SQL);
        assertEquals("""
                INSERT INTO commission_posting (line_id, ledger_entry_id, user_id)
                VALUES (?, ?, ?)""", CommissionRunQuery.INSERT_ACCOUNT_POSTING_SQL);
    }

    /** The status the caller read is in the WHERE, so two cancellations give one and a refusal. */
    @Test
    void theCancellationCarriesTheStatusItRead() {
        assertEquals("""
                UPDATE commission_run
                SET status = 'CANCELLED', cancelled_at = NOW(), cancelled_by = ?, cancel_reason = ?
                WHERE id = ?
                  AND status = 'APPROVED'""", CommissionRunQuery.CANCEL_RUN_SQL);
        assertEquals(3, parameters(CommissionRunQuery.CANCEL_RUN_SQL));
    }

    @Test
    void theLinesToPostAreLockedAndOnlyThoseOfAnApprovedRun() {
        String sql = CommissionRunQuery.UNPOSTED_LINES_SQL;
        assertTrue(sql.contains("r.status = 'APPROVED'"));
        assertTrue(sql.contains("l.amount > 0"), "a line of nothing is not an entitlement");
        assertTrue(sql.contains("p.line_id IS NULL"));
        assertTrue(sql.endsWith("FOR UPDATE OF l"));
        assertEquals(1, parameters(sql));
        assertEquals(1, parameters(CommissionRunQuery.RUN_SQL));
        assertEquals(0, parameters(CommissionRunQuery.RUNS_SQL));
        assertEquals(1, parameters(CommissionRunQuery.LINES_SQL));
        assertEquals(2, parameters(CommissionRunQuery.RULE_OF_DAY_USED_SQL));
    }

    // ---- the line ------------------------------------------------------------------------------

    @Test
    void aLineCarriesTheTiersAsTheyStood() {
        CommissionTiers tiers = new CommissionTiers(List.of(
                new CommissionTiers.Tier(new BigDecimal("50.00"), new BigDecimal("1.00")),
                new CommissionTiers.Tier(new BigDecimal("80"), new BigDecimal("2.50"))));
        assertEquals("50:1|80:2.5", CommissionLine.snapshot(tiers));
        assertTrue(CommissionLine.snapshot(new CommissionTiers(List.of(
                new CommissionTiers.Tier(new BigDecimal("999.99"), new BigDecimal("99.99")),
                new CommissionTiers.Tier(new BigDecimal("9999.98"), new BigDecimal("99.98")),
                new CommissionTiers.Tier(new BigDecimal("9999.99"), new BigDecimal("99.97"))))).length() <= 120,
                "tiers_snapshot is VARCHAR(120)");
    }

    @Test
    void aDelegateWithNoRuleHasNoLine() {
        DelegateActivity activity = new DelegateActivity(5, "Ali", true, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        assertTrue(CommissionLine.preview(DelegatePerformanceRow.of(activity, Optional.empty())).isEmpty());
    }

    // ---- V72 -----------------------------------------------------------------------------------

    @Test
    void theMigrationListsExactlyTheEnumNames() throws IOException {
        String sql = migration("V72__commission_run.sql");
        assertEquals(names(CommissionRun.Status.values()), quotedListAfter(sql, "CHECK (status IN ("));
        assertEquals(names(CommissionBasis.values()), quotedListAfter(sql, "CHECK (basis IN ("));
        assertEquals(names(TierMode.values()), quotedListAfter(sql, "CHECK (tier_mode IN ("));
    }

    @Test
    void theSeededPermissionsAreTheDeclaredOnesAndTheirDescriptionsFit() throws IOException {
        Matcher rows = Pattern.compile("\\('(commission\\.[a-z.]+)', '([^']+)'")
                .matcher(migration("V72__commission_run.sql"));
        Set<String> seeded = new TreeSet<>();
        while (rows.find()) {
            seeded.add(rows.group(1));
            assertTrue(rows.group(2).length() <= 50, rows.group(1) + " is described in "
                    + rows.group(2).length() + " characters");
        }
        assertEquals(Set.of(AppPermissions.COMMISSION_RUN_CREATE.value(), AppPermissions.COMMISSION_RUN_UPDATE.value(),
                AppPermissions.COMMISSION_RUN_POST.value()), seeded);
    }

    /** "Exactly one road" as arithmetic: a comparison of two nullable columns could pass on NULL. */
    @Test
    void theOneRoadCheckCannotPassOnNull() throws IOException {
        assertTrue(migration("V72__commission_run.sql")
                .contains("CHECK ((payroll_run_id IS NULL) + (ledger_entry_id IS NULL) = 1)"));
    }

    /**
     * The V72 triggers are the last section of their file, because a test that builds an older
     * schema cuts the file there: a trigger cannot be created on a table that does not exist yet.
     */
    @Test
    void theCommissionTriggersAreTheLastSectionAndTheUpdateGuardsTakeNoEscape() throws IOException {
        String triggers = migration("R__triggers.sql");
        int section = triggers.indexOf("-- commission run (V72)");
        assertTrue(section > 0);
        String commission = triggers.substring(section);
        String before = triggers.substring(0, section);
        assertFalse(before.contains("commission_line") || before.contains("commission_posting"));
        for (String guard : List.of("prevent_commission_line_update", "prevent_commission_posting_update")) {
            String body = commission.substring(commission.indexOf("CREATE TRIGGER " + guard));
            body = body.substring(0, body.indexOf("END|"));
            assertFalse(body.contains("@app_bulk_wipe"), guard + " must not be switched off by a wipe");
        }
        assertTrue(commission.contains("CREATE TRIGGER prevent_commission_line_delete"));
        assertTrue(commission.contains("A commission run with a posted line cannot be cancelled"));
    }

    // ---- message keys --------------------------------------------------------------------------

    @Test
    void everyKeyOfThePhaseIsInAllThreeBundles() throws IOException {
        Set<String> keys = new TreeSet<>();
        Pattern literal = Pattern.compile(
                "\"((?:commission\\.(?:run|posting|error\\.run|error\\.rule\\.used)|payroll\\.error\\.commission"
                        + "|delete\\.ref\\.commission)[a-z.]*)\"");
        for (Path file : List.of(
                MAIN_JAVA.resolve(Path.of("features", "delegate", "CommissionRunService.java")),
                MAIN_JAVA.resolve(Path.of("features", "delegate", "CommissionRuleService.java")),
                MAIN_JAVA.resolve(Path.of("features", "delegate", "CommissionRun.java")),
                MAIN_JAVA.resolve(Path.of("features", "delegate", "CommissionLine.java")),
                MAIN_JAVA.resolve(Path.of("features", "employee", "payroll", "PayrollService.java")),
                MAIN_JAVA.resolve(Path.of("delete", "DeleteRegistry.java")),
                MAIN_JAVA.resolve(Path.of("controller", "employee", "CommissionRunController.java")))) {
            Matcher found = literal.matcher(Files.readString(file));
            while (found.find()) {
                keys.add(found.group(1));
            }
        }
        for (String permission : List.of("commission.run.create", "commission.run.update", "commission.run.post")) {
            keys.remove(permission);
        }
        assertTrue(keys.size() >= 28, "the scan found " + keys);

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

    // ---- helpers -------------------------------------------------------------------------------

    private static String migration(String name) throws IOException {
        return Files.readString(MIGRATIONS.resolve(name));
    }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> names = new TreeSet<>();
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return names;
    }

    private static Set<String> quotedListAfter(String sql, String opening) {
        int start = sql.indexOf(opening);
        assertTrue(start >= 0, opening);
        String list = sql.substring(start + opening.length(), sql.indexOf(')', start + opening.length()));
        Set<String> names = new TreeSet<>();
        Matcher quoted = Pattern.compile("'([A-Z_]+)'").matcher(list);
        while (quoted.find()) {
            names.add(quoted.group(1));
        }
        return names;
    }
}
