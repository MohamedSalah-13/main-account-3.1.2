package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads V85 and its section of {@code R__triggers.sql} and checks they say what the code assumes. Whether
 * MySQL accepts them is {@code OfferDatabaseAcceptanceTest}'s, which migrates a schema from nothing.
 */
class OfferMigrationTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    private static String read(String file) throws IOException {
        return Files.readString(MIGRATIONS.resolve(file)).replace("\r\n", "\n");
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }

    @Test
    @DisplayName("each kind's CHECK names its value IS NOT NULL - a CHECK passes on NULL (the V70 lesson)")
    void kindCheckIsExplicit() throws IOException {
        String sql = read("V85__offers.sql");
        assertTrue(sql.contains("(kind = 'PERCENT' AND percent IS NOT NULL AND percent > 0 AND percent <= 100"));
        assertTrue(sql.contains("(kind = 'AMOUNT' AND amount IS NOT NULL AND amount > 0"));
        assertTrue(sql.contains("(kind = 'PRICE' AND offer_price IS NOT NULL AND offer_price > 0"));
        assertTrue(sql.contains("CHECK (offer_discount >= 0 AND (offer_id IS NOT NULL OR offer_discount = 0))"));
        assertEquals(2, count(sql, "CHECK (offer_discount >= 0"), "on the sale lines and the return lines");
    }

    @Test
    @DisplayName("no CHECK names id: MySQL refuses one on an AUTO_INCREMENT column (error 3818)")
    void noCheckOnTheAutoIncrementColumn() throws IOException {
        String sql = read("V85__offers.sql").lines().filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");
        Matcher checks = Pattern.compile("CHECK \\((.*?)\\)[,'\\n]", Pattern.DOTALL).matcher(sql);
        while (checks.find()) {
            assertFalse(Pattern.compile("(?<![_a-z])id\\b").matcher(checks.group(1)).find(), checks.group(1));
        }
    }

    @Test
    @DisplayName("what defines an offer goes with it; what names one never does")
    void keys() throws IOException {
        String sql = read("V85__offers.sql");
        assertTrue(sql.contains("offer_target_offer_fk FOREIGN KEY (offer_id) REFERENCES offer (id) ON DELETE CASCADE"));
        assertTrue(sql.contains("offer_price_tier_offer_fk FOREIGN KEY (offer_id) REFERENCES offer (id) ON DELETE CASCADE"));
        assertTrue(sql.contains("'sales', 'sales_offer_fk',\n    'FOREIGN KEY (offer_id) REFERENCES offer (id)')"));
        assertTrue(sql.contains("'sales_re', 'sales_re_offer_fk',\n    'FOREIGN KEY (offer_id) REFERENCES offer (id)')"));
        assertEquals(2, count(sql, "ON DELETE CASCADE"),
                "the two here, and none on the item, the unit, the groups or the lines");
    }

    @Test
    @DisplayName("each offer key goes to whoever held the matching item key, roles and ALLOW overrides alike")
    void grants() throws IOException {
        String sql = read("V85__offers.sql");
        assertEquals(2, count(sql, "(held.permission_key = 'items.show' AND offer_key.permission_key = 'offer.show')"));
        assertEquals(2, count(sql,
                "(held.permission_key = 'items.update' AND offer_key.permission_key IN ('offer.create', 'offer.update'))"));
        assertEquals(2, count(sql, "(held.permission_key = 'items.delete' AND offer_key.permission_key = 'offer.delete')"));
        assertTrue(sql.contains("WHERE existing.effect = 'ALLOW';"), "a DENY is not copied");
        for (String line : sql.split("\n")) {
            if (line.contains("'offer.") && line.contains("VALUES") || line.startsWith("       ('offer.")) {
                Matcher description = Pattern.compile("'offer\\.[a-z]+', '([^']*)'").matcher(line);
                if (description.find()) {
                    assertTrue(description.group(1).length() <= 50,
                            "auth_permission.description is VARCHAR(50) (the V65 lesson): " + description.group(1));
                }
            }
        }
    }

    @Test
    @DisplayName("the triggers are the last section of the file - and a used offer's terms are refused there too")
    void triggers() throws IOException {
        String triggers = read("R__triggers.sql");
        int section = triggers.indexOf("-- offers (V85)");
        assertTrue(section > triggers.indexOf("-- price tiers (V84)"),
                "after the price tiers: a test building an older schema cuts the file at an earlier marker");
        String ours = triggers.substring(section);
        assertTrue(ours.contains("CREATE TRIGGER offer_terms_update BEFORE UPDATE ON offer"));
        assertTrue(ours.contains("EXISTS (SELECT 1 FROM sales WHERE offer_id = OLD.id)"));
        assertTrue(ours.contains("CALL write_audit_log('offer', NEW.id, 'INSERT'"));
        assertTrue(ours.contains("CALL write_audit_log('offer', OLD.id, 'DELETE'"));
        assertFalse(ours.contains("symbol_latin"), "CurrencyMigrationTest counts those from its marker to the end");
    }

    @Test
    @DisplayName("the columns the form restates are the columns the table has")
    void limits() throws IOException {
        String sql = read("V85__offers.sql");
        assertTrue(sql.contains("name        VARCHAR(100)"));
        assertTrue(sql.contains("notes       VARCHAR(255)"));
        assertTrue(sql.contains("percent     DECIMAL(6, 3)"));
        assertEquals(100, OfferForm.NAME_MAX);
        assertEquals(255, OfferForm.NOTES_MAX);
        assertTrue(OfferForm.MONEY_MAX.compareTo(new BigDecimal("999999999999.99")) == 0, "DECIMAL(14, 2)");
    }
}
