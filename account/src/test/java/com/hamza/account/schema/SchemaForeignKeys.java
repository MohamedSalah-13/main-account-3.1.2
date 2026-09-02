package com.hamza.account.schema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The foreign keys that refuse a delete, read out of the migration files.
 * <p>
 * Two catalogs are checked against these - {@code WipeCatalog}, which empties whole
 * tables, and {@code DeleteRegistry}, which refuses one row - and both were reading
 * the schema through a copy of this parser or, in the registry's case, through
 * nobody at all. A declaration that does not match the schema is not a compile
 * error and not a test failure until something reads the schema and says so.
 * <p>
 * Cascading keys are left out on purpose: they take their rows with them, so
 * declaring one refuses a delete the database performs happily.
 */
public final class SchemaForeignKeys {

    /** {@code FOREIGN KEY (col) REFERENCES parent (col)}, with whatever follows up to the comma. */
    private static final Pattern FOREIGN_KEY = Pattern.compile(
            "FOREIGN KEY\\s*\\((\\w+)\\)\\s*REFERENCES\\s*(\\w+)\\s*\\(\\w+\\)([^,]*)");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS\\s+(\\w+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL);

    /**
     * A key added to a table that already exists, which the later migrations do
     * through a helper rather than in a {@code CREATE TABLE} body:
     * {@code CALL add_constraint_if_missing('child', 'name', 'FOREIGN KEY ...')}.
     */
    private static final Pattern ALTER_CONSTRAINT = Pattern.compile(
            "add_constraint_if_missing\\s*\\(\\s*'(\\w+)'\\s*,\\s*'\\w+'\\s*,\\s*'([^']*)'", Pattern.DOTALL);

    /**
     * The migrations that create or alter a key. Add one here when a migration adds
     * a foreign key, which is the moment both catalogs need checking again.
     */
    private static final List<String> MIGRATIONS = List.of(
            "V1__baseline.sql", "V3__item_barcodes.sql", "V5__item_units.sql",
            "V8__stock_count.sql", "V9__accounting_lock.sql",
            "V22__user_shift_treasury.sql", "V23__expense_employee_link.sql",
            "V24__strong_optional_shift_policy.sql", "V25__shift_cash_attribution.sql");

    private static final List<ForeignKey> KEYS = read();

    private SchemaForeignKeys() {
    }

    /** A key that refuses a delete: the cascading ones take their rows with them. */
    public record ForeignKey(String child, String column, String parent) {
    }

    public static List<ForeignKey> all() {
        return KEYS;
    }

    private static List<ForeignKey> read() {
        List<ForeignKey> keys = new ArrayList<>();
        for (String migration : MIGRATIONS) {
            String sql = readResource("db/migration/" + migration);

            Matcher tables = CREATE_TABLE.matcher(sql);
            while (tables.find()) {
                collect(keys, tables.group(1), tables.group(2));
            }

            Matcher altered = ALTER_CONSTRAINT.matcher(sql);
            while (altered.find()) {
                collect(keys, altered.group(1), altered.group(2));
            }
        }
        return List.copyOf(keys);
    }

    private static void collect(List<ForeignKey> keys, String child, String body) {
        Matcher fk = FOREIGN_KEY.matcher(body);
        while (fk.find()) {
            if (!fk.group(3).contains("ON DELETE CASCADE")) {
                keys.add(new ForeignKey(child, fk.group(1), fk.group(2)));
            }
        }
    }

    private static String readResource(String resource) {
        try (InputStream in = SchemaForeignKeys.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
