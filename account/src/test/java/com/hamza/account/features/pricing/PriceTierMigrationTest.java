package com.hamza.account.features.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads V84 and its section of {@code R__triggers.sql} and checks they say what the code assumes. It says
 * nothing about whether MySQL accepts them - that is {@code PriceTierDatabaseAcceptanceTest}'s, which
 * migrates a schema from nothing - but it fails the build on the things a text can show.
 */
class PriceTierMigrationTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    private static String read(String file) throws IOException {
        // Line endings as git stores them, whatever a checkout wrote.
        return Files.readString(MIGRATIONS.resolve(file)).replace("\r\n", "\n");
    }

    private static String sqlOnly(String sql) {
        return sql.lines().filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    @DisplayName("no CHECK names id: MySQL refuses one on an AUTO_INCREMENT column (error 3818)")
    void noCheckOnTheAutoIncrementColumn() throws IOException {
        Matcher checks = Pattern.compile("CHECK \\((.*?)\\)'\\);", Pattern.DOTALL).matcher(sqlOnly(read("V84__price_tiers.sql")));
        int found = 0;
        while (checks.find()) {
            found++;
            assertFalse(Pattern.compile("(?<![_a-z])id\\b").matcher(checks.group(1)).find(),
                    "a CHECK over type_price.id fails on every install: " + checks.group(1));
        }
        assertEquals(3, found, "is_active, the fill rule and the list price");
    }

    @Test
    @DisplayName("the rule's CHECK says IS NOT NULL in every branch - a CHECK passes on NULL (the V70 lesson)")
    void ruleCheckIsExplicit() throws IOException {
        String sql = read("V84__price_tiers.sql");
        assertTrue(sql.contains("OR (rule_source = ''COST'' AND rule_tier_id IS NULL"));
        assertTrue(sql.contains("OR (rule_source = ''TIER'' AND rule_tier_id IS NOT NULL"));
        assertEquals(2, count(sql, "rule_percent IS NOT NULL AND rule_percent > -100"));
        assertEquals(2, count(sql, "rule_rounding IS NOT NULL AND rule_rounding > 0"));
    }

    @Test
    @DisplayName("below.list goes to whoever may create a sale; tier.change to nobody")
    void grants() throws IOException {
        String sql = read("V84__price_tiers.sql");
        assertEquals(2, count(sql, "AND held.permission_key = 'sales.create'"));
        assertEquals(2, count(sql, "ON below.permission_key = 'sales.price.below.list'"));
        assertFalse(sqlOnly(sql).contains("ON change.permission_key"), "nothing grants the tier change");
        assertTrue(sql.contains("WHERE existing.effect = 'ALLOW';"), "a DENY is not copied");
    }

    @Test
    @DisplayName("tier 1 stays on and no tier fills itself - said by a trigger, last in the file")
    void triggers() throws IOException {
        String triggers = read("R__triggers.sql");
        int section = triggers.indexOf("-- price tiers (V84)");
        assertTrue(section > triggers.indexOf("-- currencies and their rates (V80)"),
                "after the currencies: a test building an older schema cuts the file at an earlier marker");
        String ours = triggers.substring(section);
        assertTrue(ours.contains("CREATE TRIGGER type_price_rules_update BEFORE UPDATE ON type_price"));
        assertTrue(ours.contains("IF NEW.id = 1 AND NEW.is_active = 0 THEN"));
        assertTrue(ours.contains("IF NEW.rule_tier_id IS NOT NULL AND NEW.rule_tier_id = NEW.id THEN"));
        assertTrue(ours.contains("CALL write_audit_log('type_price', NEW.id, 'UPDATE'"));
    }

    @Test
    @DisplayName("the columns the rules restate are the columns the table has")
    void limits() throws IOException {
        String sql = read("V84__price_tiers.sql");
        assertTrue(sql.contains("'rule_percent',\n    'DECIMAL(7, 3) NULL"));
        assertTrue(sql.contains("'rule_rounding',\n    'DECIMAL(8, 2) NULL"));
        assertTrue(PriceTierService.PERCENT_MAX.compareTo(new java.math.BigDecimal("9999.999")) < 0,
                "DECIMAL(7, 3) holds the largest percentage the service accepts");
        assertTrue(PriceTierService.ROUNDING_MAX.compareTo(new java.math.BigDecimal("999999.99")) < 0);
        assertTrue(sql.contains("'list_price',\n    'DECIMAL(14, 2) NULL"), "money, like the price beside it");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
