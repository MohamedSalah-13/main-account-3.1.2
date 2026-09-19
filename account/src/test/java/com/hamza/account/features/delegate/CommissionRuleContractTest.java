package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The places where the rule is said twice and the two have to agree: the record and V70's
 * CHECKs, the enums and the lists V70 restates, the permissions and the rows V70 seeds, and
 * the message keys that no static scan can see.
 */
class CommissionRuleContractTest {

    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V70__commission_rule.sql");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "hamza", "account");
    private static final Path BUNDLE_DIR = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
    private static final String[] BUNDLES = {"messages.properties", "messages_ar.properties", "messages_en.properties"};

    private static final CommissionTiers FLAT = new CommissionTiers(List.of(
            new CommissionTiers.Tier(BigDecimal.ZERO, new BigDecimal("2"))));
    private static final CommissionTiers TIERED = new CommissionTiers(List.of(
            new CommissionTiers.Tier(new BigDecimal("80"), new BigDecimal("1")),
            new CommissionTiers.Tier(new BigDecimal("100"), new BigDecimal("2"))));

    private static CommissionRule rule(BigDecimal target, CommissionTiers tiers) {
        return new CommissionRule(0, 5, LocalDate.of(2026, 10, 1), CommissionBasis.SALES,
                TierMode.WHOLE, target, tiers, null);
    }

    // ---- the record ----------------------------------------------------------------------

    @Test
    void aFlatRateNeedsNoTargetAndTiersNeedOne() {
        assertFalse(rule(BigDecimal.ZERO, FLAT).hasTarget());
        assertEquals(new BigDecimal("800.00"), rule(BigDecimal.ZERO, FLAT).calculate(new BigDecimal("40000")).amount());

        assertEquals("commission.error.target.missing", assertThrows(IllegalArgumentException.class,
                () -> rule(BigDecimal.ZERO, TIERED)).getMessage());
        assertEquals("commission.error.target.negative", assertThrows(IllegalArgumentException.class,
                () -> rule(new BigDecimal("-1"), FLAT)).getMessage());
    }

    @Test
    void aRuleComputesWithItsOwnTargetAndMode() {
        CommissionRule tiered = rule(new BigDecimal("100000"), TIERED);
        assertEquals(new BigDecimal("0.00"), tiered.calculate(new BigDecimal("79000")).amount());
        assertEquals(new BigDecimal("900.00"), tiered.calculate(new BigDecimal("90000")).amount());
        assertEquals(new BigDecimal("2400.00"), tiered.calculate(new BigDecimal("120000")).amount());
    }

    // ---- V70 -----------------------------------------------------------------------------

    /** The names are what is stored, and V70 restates both lists as CHECKs. */
    @Test
    void theMigrationListsExactlyTheEnumNames() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertEquals(names(CommissionBasis.values()), quotedListAfter(sql, "CHECK (basis IN ("));
        assertEquals(names(TierMode.values()), quotedListAfter(sql, "CHECK (tier_mode IN ("));
    }

    /**
     * {@code auth_permission.description} is VARCHAR(50). V65's first draft carried 62 characters
     * and failed on the first database it met, which no green build could have seen.
     */
    @Test
    void theSeededPermissionsAreTheDeclaredOnesAndTheirDescriptionsFit() throws IOException {
        String sql = Files.readString(MIGRATION);
        Matcher rows = Pattern.compile("\\('(commission\\.[a-z.]+)', '([^']+)'").matcher(sql);
        Set<String> seeded = new TreeSet<>();
        while (rows.find()) {
            seeded.add(rows.group(1));
            assertTrue(rows.group(2).length() <= 50, rows.group(1) + " is described in "
                    + rows.group(2).length() + " characters");
        }
        assertEquals(Set.of(AppPermissions.COMMISSION_SHOW.value(), AppPermissions.COMMISSION_RULE_UPDATE.value()),
                seeded);
    }

    /**
     * A MySQL CHECK <b>passes when it evaluates to NULL</b>, so a branch that compares a
     * nullable column has to say {@code IS NOT NULL} itself - without it a tier with a
     * threshold and no rate is accepted in silence.
     */
    @Test
    void theOptionalTierChecksCannotPassOnNull() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String tier : List.of("tier2", "tier3")) {
            assertTrue(sql.contains(tier + "_from IS NOT NULL AND " + tier + "_rate IS NOT NULL"), tier);
        }
    }

    /** It defines no helper, so it can leave none behind and call none that a baseline dropped. */
    @Test
    void theMigrationCallsNoHelperProcedure() throws IOException {
        String sql = Files.readString(MIGRATION).replaceAll("(?m)^\\s*--.*$", "");
        assertFalse(sql.contains("CALL "));
        assertFalse(sql.contains("CREATE PROCEDURE"));
    }

    // ---- message keys --------------------------------------------------------------------

    /**
     * {@code MessageKeyArchitectureTest} reads the arguments of {@code getString}/{@code text}.
     * A key handed to an exception, or reached through {@code messageKey()}, is neither - so
     * every {@code commission.} literal in the sources is checked here instead.
     */
    @Test
    void everyCommissionKeyIsInAllThreeBundles() throws IOException {
        Set<String> keys = new TreeSet<>();
        Pattern literal = Pattern.compile("\"((?:commission|employee\\.action\\.commission)[a-z.]*)\"");
        List<Path> roots = List.of(MAIN_JAVA.resolve(Path.of("features", "delegate")),
                MAIN_JAVA.resolve(Path.of("controller", "employee", "CommissionRulesController.java")),
                MAIN_JAVA.resolve(Path.of("controller", "employee", "EmployeesScreenController.java")));
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher found = literal.matcher(Files.readString(file));
                    while (found.find()) {
                        keys.add(found.group(1));
                    }
                }
            }
        }
        // Permission keys share the prefix and are not sentences.
        keys.remove(AppPermissions.COMMISSION_SHOW.value());
        keys.remove(AppPermissions.COMMISSION_RULE_UPDATE.value());
        assertTrue(keys.size() > 20, "the scan found " + keys);

        List<String> missing = new ArrayList<>();
        for (String bundle : BUNDLES) {
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
