package com.hamza.account.features.currency;

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
 * Reads V80 and {@code R__triggers.sql} and checks they say what the code assumes. It says nothing about
 * whether MySQL accepts them - that is {@code CurrencyDatabaseAcceptanceTest}'s, which migrates a schema
 * from nothing - but it fails the build on the things a text can show.
 */
class CurrencyMigrationTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    private static String read(String file) throws IOException {
        return Files.readString(MIGRATIONS.resolve(file));
    }

    @Test
    @DisplayName("seeds the three currencies asked for, and names the base in one statement with a fallback")
    void seeds() throws IOException {
        String sql = read("V80__currencies.sql");
        // The pound's English symbol is the "L.E." the English dashboard wrote before V80.
        assertTrue(sql.contains("('EGP', 'جنيه مصري', 'ج.م', 'L.E.', 2, 1)"));
        assertTrue(sql.contains("('SAR', 'ريال سعودي', 'ر.س', 'SAR', 2, 2)"));
        assertTrue(sql.contains("('USD', 'دولار أمريكي', '$', '$', 2, 3)"));
        assertEquals(1, count(sql, "UPDATE currency\nSET is_base = 1"), "the base is set exactly once");
        assertTrue(sql.contains("setting_key = 'setting.currency'"), "the base follows the settings screen");
        assertTrue(sql.contains("'EGP');"), "and falls back to what that setting fell back to");
    }

    @Test
    @DisplayName("one base is the database's rule, not only the code's")
    void oneBase() throws IOException {
        String sql = read("V80__currencies.sql");
        assertTrue(sql.contains("base_key       TINYINT GENERATED ALWAYS AS (IF(is_base = 1, 1, NULL)) STORED"));
        assertTrue(sql.contains("CONSTRAINT currency_one_base_uk UNIQUE (base_key)"));
        assertTrue(sql.contains("CONSTRAINT currency_base_active_chk CHECK (is_base = 0 OR is_active = 1)"));
        assertTrue(sql.contains("CONSTRAINT currency_code_chk CHECK (REGEXP_LIKE(code, '^[A-Z]{3}$', 'c'))"));
        assertTrue(sql.contains("CONSTRAINT currency_decimals_chk CHECK (decimal_places BETWEEN 0 AND "
                + CurrencyRules.DECIMALS_MAX + ")"));
    }

    @Test
    @DisplayName("a rate is DECIMAL(20, 10) above zero, once a day, and not swept away with its currency")
    void rates() throws IOException {
        String sql = read("V80__currencies.sql");
        assertTrue(sql.contains("rate           DECIMAL(" + (ExchangeRateRules.INTEGER_DIGITS + ExchangeRateRules.SCALE)
                + ", " + ExchangeRateRules.SCALE + ")"));
        assertTrue(sql.contains("CONSTRAINT currency_rate_day_uk UNIQUE (currency_id, effective_date)"));
        assertTrue(sql.contains("CONSTRAINT currency_rate_positive_chk CHECK (rate > 0)"));
        assertTrue(sql.contains(
                "CONSTRAINT currency_rate_currency_id_fk FOREIGN KEY (currency_id) REFERENCES currency (id),"),
                "RESTRICT on purpose - DeleteRegistry.CURRENCIES declares it");
        assertTrue(sql.contains("notes          VARCHAR(" + ExchangeRateRules.NOTES_MAX + ")"));
    }

    @Test
    @DisplayName("the columns the rules restate are the columns the table has")
    void limits() throws IOException {
        String sql = read("V80__currencies.sql");
        assertTrue(sql.contains("name           VARCHAR(" + CurrencyRules.NAME_MAX + ")"));
        assertTrue(sql.contains("symbol         VARCHAR(" + CurrencyRules.SYMBOL_MAX + ")"));
        assertTrue(sql.contains("symbol_latin   VARCHAR(" + CurrencyRules.SYMBOL_MAX + ")                             NULL"),
                "the English symbol is optional - Currency.symbolFor falls back");
    }

    @Test
    @DisplayName("the audit records the English symbol beside the Arabic one")
    void englishSymbolAudited() throws IOException {
        String triggers = read("R__triggers.sql");
        String section = triggers.substring(triggers.indexOf("-- currencies and their rates (V80)"));
        assertEquals(4, section.split("'symbol_latin', ", -1).length - 1,
                "insert, update's two sides and delete each record the English symbol");
    }

    @Test
    @DisplayName("every permission description fits auth_permission.description, VARCHAR(50) - V65's lesson")
    void descriptionsFit() throws IOException {
        String sql = read("V80__currencies.sql");
        // A row of the VALUES list, not the grant's IN list further down, which names the keys again.
        Matcher rows = Pattern.compile("(?m)(?:VALUES |^\\s+)\\('(currency\\.[.\\w]*)', '([^']*)'").matcher(sql);
        int found = 0;
        while (rows.find()) {
            found++;
            String description = rows.group(2);
            assertTrue(description.codePointCount(0, description.length()) <= 50,
                    rows.group(1) + " has a " + description.length() + "-character description");
        }
        assertEquals(3, found, "the three keys were read - the rest of this test would pass on nothing");
    }

    @Test
    @DisplayName("the temporary table is dropped and no helper procedure is created")
    void leavesNothingBehind() throws IOException {
        String sql = read("V80__currencies.sql");
        assertTrue(sql.contains("DROP TEMPORARY TABLE v80_settings_currency;"));
        assertFalse(sql.contains("CREATE PROCEDURE"));
        assertFalse(sql.contains("ILS"), "the settings screen never offered the shekel");
    }

    @Test
    @DisplayName("both tables are audited, in the last section of R__triggers - after every marker a test cuts at")
    void audited() throws IOException {
        String triggers = read("R__triggers.sql");
        int section = triggers.indexOf("-- currencies and their rates (V80)");
        assertTrue(section > 0);
        for (String marker : new String[]{"-- commission run (V72)", "-- warehouses switched off (V77)",
                "-- a warehouse's opening balance (V78)"}) {
            assertTrue(triggers.indexOf(marker) < section, marker + " comes before the currency section");
        }
        for (String trigger : new String[]{"audit_currency_insert", "audit_currency_update",
                "audit_currency_delete", "audit_currency_rate_insert", "audit_currency_rate_update",
                "audit_currency_rate_delete"}) {
            assertTrue(triggers.indexOf("DROP TRIGGER IF EXISTS " + trigger + ";", section) > 0, trigger);
            assertTrue(triggers.indexOf("CREATE TRIGGER " + trigger + " ", section) > 0, trigger);
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
