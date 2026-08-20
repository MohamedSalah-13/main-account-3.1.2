package com.hamza.account.features.itemmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Holds {@link ItemReferenceRegistry} against the schema it claims to describe.
 * <p>
 * A merge repoints every reference the registry names and then deletes the item. A
 * reference it does not name is not a visible failure: four of them cascade, so those
 * rows are destroyed with the source, and the item's whole history in that table
 * disappears with no error anywhere. The other outcome - the delete being refused -
 * only looks like a bug in the merge.
 * <p>
 * So the keys are read out of the migration files and checked both ways: nothing in the
 * schema points at an item without being declared, and nothing is declared that the
 * schema does not have. No database is needed; the migrations are on the classpath, and
 * that is where the schema is defined.
 * <p>
 * The reader is deliberately the same one {@code WipeCatalogTest} uses, minus its filter
 * on {@code ON DELETE CASCADE}: a cascading key is exactly what this list must not miss.
 */
class ItemReferenceRegistryTest {

    /** {@code FOREIGN KEY (col) REFERENCES parent (col)}, with whatever follows up to the comma. */
    private static final Pattern FOREIGN_KEY = Pattern.compile(
            "FOREIGN KEY\\s*\\((\\w+)\\)\\s*REFERENCES\\s*(\\w+)\\s*\\(\\w+\\)([^,]*)");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS\\s+(\\w+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL);

    /**
     * V4 is left out on purpose: it re-creates the same tables for a client coming from
     * the old manual bundle, so every key in it is a duplicate of V1's.
     */
    private static final List<String> MIGRATIONS = List.of(
            "V1__baseline.sql", "V3__item_barcodes.sql", "V5__item_units.sql", "V8__stock_count.sql");

    private static final List<ItemKey> ITEM_KEYS = readItemKeys();

    private record ItemKey(String table, String column) {
        @Override
        public String toString() {
            return table + "." + column;
        }
    }

    private static List<ItemKey> readItemKeys() {
        List<ItemKey> keys = new ArrayList<>();
        for (String migration : MIGRATIONS) {
            Matcher tables = CREATE_TABLE.matcher(read("db/migration/" + migration));
            while (tables.find()) {
                String child = tables.group(1);
                Matcher key = FOREIGN_KEY.matcher(tables.group(2));
                while (key.find()) {
                    if (key.group(2).equalsIgnoreCase("items")) {
                        keys.add(new ItemKey(child, key.group(1)));
                    }
                }
            }
        }
        return List.copyOf(keys);
    }

    private static String read(String resource) {
        try (InputStream in = ItemReferenceRegistryTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("the migrations really were read - the rest of this class is not passing vacuously")
    void theSchemaWasRead() {
        assertFalse(ITEM_KEYS.isEmpty(), "no foreign keys to items were parsed");
        assertTrue(ITEM_KEYS.contains(new ItemKey("sales", "num")),
                "expected sales.num -> items among the parsed keys, found " + ITEM_KEYS);
    }

    @Test
    @DisplayName("every foreign key to items is declared")
    void everySchemaKeyIsDeclared() {
        List<String> missing = ITEM_KEYS.stream()
                .filter(key -> !ItemReferenceRegistry.isDeclared(key.table(), key.column()))
                .map(ItemKey::toString)
                .distinct()
                .toList();

        if (!missing.isEmpty()) {
            fail("""
                    These columns point at items and no ItemReference declares them, so a merge \
                    would leave their rows behind - and the cascading ones would be destroyed with \
                    the item, silently: %s
                    Add them to ItemReferenceRegistry with the MergeAction their unique keys call for."""
                    .formatted(String.join(", ", missing)));
        }
    }

    @Test
    @DisplayName("every declared reference is a foreign key that exists")
    void everyDeclarationIsInTheSchema() {
        Set<String> schema = ITEM_KEYS.stream().map(ItemKey::toString).collect(java.util.stream.Collectors.toSet());
        List<String> invented = ItemReferenceRegistry.ALL.stream()
                .map(ItemReference::qualified)
                .filter(qualified -> !schema.contains(qualified))
                .toList();

        assertTrue(invented.isEmpty(),
                "declared but not in the schema (a typo here moves nothing and reports nothing): " + invented);
    }

    @Nested
    @DisplayName("actions")
    class Actions {

        /**
         * A plain move is only safe where no unique key can already hold the target's own
         * row for the same thing. These four are the ones that can, and each is handled by
         * a step of its own in the service.
         */
        @Test
        @DisplayName("the constrained references are not treated as plain moves")
        void constrainedReferencesAreNotPlainMoves() {
            for (ItemReference reference : List.of(
                    ItemReferenceRegistry.STOCK_COUNT_LINES,
                    ItemReferenceRegistry.ITEMS_STOCK,
                    ItemReferenceRegistry.ITEMS_UNITS,
                    ItemReferenceRegistry.ITEM_BARCODES,
                    ItemReferenceRegistry.ITEMS_PACKAGE_ITEM,
                    ItemReferenceRegistry.ITEMS_PACKAGE_PACKAGE)) {
                assertFalse(ItemReferenceRegistry.MOVABLE.contains(reference),
                        reference.qualified() + " has a unique key or a second item column; a plain UPDATE would fail"
                        + " or duplicate, so it must not be in MOVABLE");
            }
        }

        @Test
        @DisplayName("MOVABLE is exactly the references marked MOVE")
        void movableIsWhatItSays() {
            assertEquals(ItemReferenceRegistry.ALL.stream()
                            .filter(reference -> reference.action() == MergeAction.MOVE)
                            .toList(),
                    ItemReferenceRegistry.MOVABLE);
        }

        @Test
        @DisplayName("the documents are the four invoice families")
        void documentsAreTheFourFamilies() {
            assertEquals(List.of("sales.num", "sales_re.item_id", "purchase.num", "purchase_re.item_id"),
                    ItemReferenceRegistry.DOCUMENTS.stream().map(ItemReference::qualified).toList());
        }

        @Test
        @DisplayName("items_package is declared twice, once per column")
        void bothPackageColumnsAreDeclared() {
            assertEquals(2, ItemReferenceRegistry.ALL.stream()
                    .filter(reference -> reference.table().equals("items_package"))
                    .count(), "an item is both the thing packaged and the package");
        }
    }
}
