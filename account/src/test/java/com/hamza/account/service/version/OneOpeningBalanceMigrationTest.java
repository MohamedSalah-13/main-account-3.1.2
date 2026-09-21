package com.hamza.account.service.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V78: one opening balance per item per warehouse, in {@code items_stock}, and nothing else holding
 * one (decision §9.2 of {@code docs/warehouse-plan.md}).
 * <p>
 * Read as text, which is all a build can do; that the migration applies - over an empty schema and
 * over one holding items - is the acceptance classes' to say, run against a schema built from nothing.
 */
class OneOpeningBalanceMigrationTest {

    private static final String V78 = read("V78__one_opening_balance.sql");

    @Test
    @DisplayName("the trigger that copied the item's opening over warehouse 1 is dropped, before the column")
    void theMirrorTriggerGoesFirst() {
        int trigger = V78.indexOf("drop trigger if exists after_items_update;");
        assertTrue(trigger >= 0, "V78 must drop after_items_update");
        assertTrue(trigger < V78.indexOf("alter table items drop column first_balance"));
    }

    /**
     * A trigger naming a dropped column fails every write to its table until it is recreated, and the
     * repeatables run only after every versioned migration - so V78 takes the item audit triggers down
     * with the column and {@code R__triggers.sql} puts them back without it.
     */
    @Test
    @DisplayName("the item audit triggers go with the column, and come back from R__triggers without it")
    void theAuditTriggersAreRecreatedWithoutTheColumn() {
        for (String name : List.of("audit_items_insert", "audit_items_update", "audit_items_delete")) {
            assertTrue(V78.contains("drop trigger if exists " + name + ";"), name);
        }
        String triggers = read("R__triggers.sql");
        String items = triggers.substring(triggers.indexOf("create trigger audit_items_insert"),
                triggers.indexOf("create trigger audit_items_delete"));
        items = items + triggers.substring(triggers.indexOf("create trigger audit_items_delete"),
                triggers.indexOf("end;", triggers.indexOf("create trigger audit_items_delete")));
        assertFalse(items.contains("first_balance"), "an item audit trigger still names the dropped column");
        assertFalse(triggers.contains("create trigger after_items_update"));
    }

    @Test
    @DisplayName("both columns are dropped, each only where it exists")
    void bothColumnsAreDroppedWhereTheyExist() {
        assertTrue(V78.contains("alter table items drop column first_balance;"));
        assertTrue(V78.contains("alter table items_stock drop column current_quantity;"));
        assertTrue(V78.contains("table_name = 'items' and column_name = 'first_balance'"));
        assertTrue(V78.contains("table_name = 'items_stock' and column_name = 'current_quantity'"));
    }

    /** The one row whose figure lived on the item alone: it is moved before the column goes. */
    @Test
    @DisplayName("an item with no default-warehouse row keeps its opening, moved there before the drop")
    void anOpeningNothingElseHeldIsMovedFirst() {
        int backfill = V78.indexOf("insert into items_stock (item_id, stock_id, first_balance) select i.id, 1, i.first_balance");
        assertTrue(backfill >= 0, "the backfill is missing");
        assertTrue(V78.indexOf("where ist.id is null", backfill) > backfill);
        assertTrue(backfill < V78.indexOf("alter table items drop column first_balance"));
    }

    @Test
    @DisplayName("a change to a warehouse's opening is audited where the opening now lives")
    void theOpeningIsAuditedOnItsWarehouseRow() {
        String triggers = read("R__triggers.sql");
        int trigger = triggers.indexOf("create trigger audit_items_stock_opening after update on items_stock");
        assertTrue(trigger >= 0, "items_stock has no audit trigger for its opening");
        assertTrue(triggers.indexOf("not (old.first_balance <=> new.first_balance)", trigger) > trigger,
                "only a change of the figure is recorded");
        // After the cut DelegateActivityDatabaseAcceptanceTest makes, with the other late sections.
        assertTrue(trigger > triggers.indexOf("-- commission run (v72)"));
    }

    /** Neither column may come back through the application's own statements. */
    @Test
    @DisplayName("nothing in the application writes either dropped column")
    void nothingWritesTheDroppedColumns() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            List<String> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String source = withoutComments(readFile(path));
                        return source.contains("current_quantity")
                                || source.matches("(?s).*UPDATE items SET[^\"]*first_balance.*")
                                || source.contains("items.first_balance");
                    })
                    .map(Path::toString)
                    .toList();
            assertTrue(offenders.isEmpty(), offenders.toString());
        }
    }

    private static String read(String name) {
        try (InputStream in = OneOpeningBalanceMigrationTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
