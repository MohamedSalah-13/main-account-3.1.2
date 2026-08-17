package com.hamza.account.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code V16__return_source.sql}: what a return now points at, and how firmly.
 * <p>
 * No database - the migration is on the classpath, in the manner of
 * {@code WipeCatalogTest}. What matters here is not read back by any existing
 * reader: {@code WipeCatalogTest}'s foreign-key parser only looks inside
 * {@code CREATE TABLE} blocks in a fixed list of files, and this migration adds its
 * keys with {@code ALTER TABLE} - deliberately outside that list, since a nullable
 * {@code ON DELETE SET NULL} key never blocks a delete and so needs no entry in
 * {@code WipeCatalog}'s closure. This test is what stands in its place.
 */
class ReturnSourceMigrationTest {

    private static final String MIGRATION = read("db/migration/V16__return_source.sql");
    private static final String NORMALISED =
            stripComments(MIGRATION).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

    /** {@code --} comments would otherwise be counted along with the SQL they describe. */
    private static String stripComments(String sql) {
        StringBuilder stripped = new StringBuilder();
        for (String line : sql.split("\\R")) {
            int comment = line.indexOf("--");
            stripped.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return stripped.toString();
    }

    private static String read(String resource) {
        try (InputStream in = ReturnSourceMigrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("the header columns")
    class Headers {

        @ParameterizedTest
        @CsvSource({
                "total_sales_re, source_invoice_number, BIGINT NULL",
                "total_sales_re, return_reason,          VARCHAR(32) NULL",
                "total_buy_re,   source_invoice_number,   BIGINT NULL",
                "total_buy_re,   return_reason,            VARCHAR(32) NULL",
        })
        void isAddedNullable(String table, String column, String type) {
            assertTrue(NORMALISED.contains(
                    ("alter table " + table + " add column " + column + " " + type)
                            .toLowerCase(Locale.ROOT)),
                    () -> "expected a nullable " + column + " on " + table + "\n" + MIGRATION);
        }

        @ParameterizedTest
        @CsvSource({
                "total_sales_re_source_invoice_fk, source_invoice_number, total_sales",
                "total_buy_re_source_invoice_fk,   source_invoice_number, total_buy",
        })
        void referencesItsInvoiceFamilyAndClearsRatherThanBlocksOnDelete(
                String constraintName, String column, String parentTable) {
            assertTrue(NORMALISED.contains(constraintName.toLowerCase(Locale.ROOT)), MIGRATION);
            assertTrue(NORMALISED.contains(
                    ("foreign key (" + column + ") references " + parentTable)
                            .toLowerCase(Locale.ROOT)),
                    MIGRATION);
        }
    }

    @Nested
    @DisplayName("the line columns")
    class Lines {

        @ParameterizedTest
        @CsvSource({
                "sales_re,    source_line_id, sales",
                "purchase_re, source_line_id, purchase",
        })
        void pointsAtTheLineNotTheItem(String table, String column, String sourceTable) {
            // sales.num / purchase.num name the item, not the line - the line's own key
            // is `id`, and that is what a return line must reverse.
            assertTrue(NORMALISED.contains(
                    ("alter table " + table + " add column " + column + " int null")
                            .toLowerCase(Locale.ROOT)),
                    MIGRATION);
            assertTrue(NORMALISED.contains(
                    ("foreign key (" + column + ") references " + sourceTable + " (id)")
                            .toLowerCase(Locale.ROOT)),
                    MIGRATION);
        }
    }

    @Test
    @DisplayName("every foreign key added here clears rather than blocks a delete")
    void everyNewKeyIsSetNullNotCascadeOrRestrict() {
        // A return must never be dragged down by, or hold back, the deletion of the
        // invoice or line it reverses - it keeps standing, exactly as an unmigrated
        // historical return already does.
        int fks = countOccurrences(NORMALISED, "foreign key (");
        int setNulls = countOccurrences(NORMALISED, "on delete set null");
        assertTrue(fks > 0, "no foreign keys found; the test would pass vacuously\n" + MIGRATION);
        assertTrue(fks == setNulls,
                () -> fks + " foreign key(s) but " + setNulls + " ON DELETE SET NULL clause(s)\n"
                        + MIGRATION);
        assertTrue(!NORMALISED.contains("on delete cascade"), MIGRATION);
    }

    @Test
    @DisplayName("every ALTER TABLE is guarded, so a re-run does not fail on an existing column")
    void isIdempotent() {
        int alters = countOccurrences(NORMALISED, "alter table");
        int guards = countOccurrences(NORMALISED, "information_schema.columns")
                + countOccurrences(NORMALISED, "information_schema.table_constraints");
        assertTrue(alters > 0, MIGRATION);
        assertTrue(guards >= alters, () -> alters + " ALTER statement(s) but only " + guards
                + " information_schema guard(s)\n" + MIGRATION);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
