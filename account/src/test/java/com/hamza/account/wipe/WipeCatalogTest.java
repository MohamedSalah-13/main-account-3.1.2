package com.hamza.account.wipe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import com.hamza.account.schema.SchemaForeignKeys;
import com.hamza.account.schema.SchemaForeignKeys.ForeignKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Holds {@link WipeCatalog} against the schema it is describing.
 * <p>
 * The catalog says which tables a wipe empties, in what order, and which targets
 * have to go together. Every one of those is really a statement about the foreign
 * keys, and getting one wrong does not fail visibly - under the stored procedures
 * it left rows pointing at things that no longer existed, because the checking was
 * switched off. So the keys are read out of the migration files and the
 * declarations are checked against them, rather than trusted.
 * <p>
 * No database: the migrations are on the classpath, and that is where the schema
 * is defined.
 */
class WipeCatalogTest {

    /**
     * The keys, read out of the migrations by {@link SchemaForeignKeys} - shared with
     * {@code DeleteRegistryTest}, which asks the same schema a different question.
     */
    private static final List<ForeignKey> FOREIGN_KEYS = SchemaForeignKeys.all();

    private static String read(String resource) {
        try (InputStream in = WipeCatalogTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Which target empties each table. */
    private static Map<String, WipeTarget> ownerByTable() {
        Map<String, WipeTarget> owners = new LinkedHashMap<>();
        for (WipeTarget target : WipeCatalog.TARGETS) {
            for (WipeTable table : target.tables()) {
                owners.put(table.table(), target);
            }
        }
        return owners;
    }

    @Test
    @DisplayName("the migrations really do declare foreign keys - the reader is not silently empty")
    void theSchemaWasRead() {
        assertFalse(FOREIGN_KEYS.isEmpty(), "no foreign keys parsed; the rest of this class would pass vacuously");
        assertTrue(FOREIGN_KEYS.stream()
                        .anyMatch(key -> key.child().equals("sales") && key.parent().equals("items")),
                "expected sales.num -> items among the parsed keys");
    }

    @Nested
    @DisplayName("requires")
    class Requires {

        /**
         * If a wipe empties a table, every table pointing at it must be emptied too -
         * either by the same target or by one it requires. Anything else is a delete
         * the database will refuse, or - as it used to be - an orphan.
         */
        @Test
        @DisplayName("covers every table that points at a wiped table")
        void closureCoversEveryReferencingTable() {
            Map<String, WipeTarget> owners = ownerByTable();
            List<String> problems = new ArrayList<>();

            for (WipeTarget target : WipeCatalog.TARGETS) {
                Set<String> wiped = new HashSet<>();
                WipeCatalog.closureOf(List.of(target))
                        .forEach(inClosure -> inClosure.tables().forEach(table -> wiped.add(table.table())));

                for (WipeTable table : target.tables()) {
                    // users keeps row 1, which is what the user_id columns point at, so
                    // the tables holding them do not have to be emptied with it.
                    if (table.where() != null) {
                        continue;
                    }
                    for (ForeignKey key : FOREIGN_KEYS) {
                        if (!key.parent().equals(table.table())) {
                            continue;
                        }
                        if (key.child().equals(table.table()) || wiped.contains(key.child())) {
                            continue;
                        }
                        problems.add("%s empties %s, but %s.%s still points at it%s"
                                .formatted(target.id(), table.table(), key.child(), key.column(),
                                        owners.containsKey(key.child())
                                                ? " - require '" + owners.get(key.child()).id() + "'"
                                                : " and no target empties " + key.child()));
                    }
                }
            }

            if (!problems.isEmpty()) {
                fail("Foreign keys the catalog does not account for:\n" + String.join("\n", problems));
            }
        }

        @Test
        @DisplayName("every required id names a real target")
        void requiresResolve() {
            for (WipeTarget target : WipeCatalog.TARGETS) {
                for (String required : target.requires()) {
                    assertTrue(WipeCatalog.byId(required).isPresent(),
                            target.id() + " requires unknown target " + required);
                }
            }
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        /**
         * A table may only be emptied once everything pointing at it has been. This is
         * what replaces {@code SET FOREIGN_KEY_CHECKS = 0}: the order is right, so the
         * checking can stay on.
         */
        @Test
        @DisplayName("children are emptied before their parents")
        void childrenFirst() {
            List<String> order = WipePlan.of(WipeCatalog.TARGETS).tablesInOrder();
            List<String> problems = new ArrayList<>();

            for (ForeignKey key : FOREIGN_KEYS) {
                int child = order.indexOf(key.child());
                int parent = order.indexOf(key.parent());
                if (child < 0 || parent < 0 || key.child().equals(key.parent())) {
                    continue;
                }
                if (parent < child) {
                    problems.add("%s is emptied before %s, which points at it via %s.%s"
                            .formatted(key.parent(), key.child(), key.child(), key.column()));
                }
            }

            if (!problems.isEmpty()) {
                fail("Wipe order breaks these foreign keys:\n" + String.join("\n", problems));
            }
        }

        @Test
        @DisplayName("a table is emptied by exactly one target")
        void noTableWipedTwice() {
            List<String> all = WipeCatalog.TARGETS.stream()
                    .flatMap(target -> target.tables().stream())
                    .map(WipeTable::table)
                    .toList();

            assertEquals(all.size(), Set.copyOf(all).size(), "a table appears in more than one target: " + all);
        }
    }

    @Nested
    @DisplayName("closure")
    class Closure {

        @Test
        @DisplayName("pulls in what a target needs, transitively")
        void transitive() {
            // mainGroups needs subGroups, which needs items, which needs all four
            // invoice targets - none of which the caller asked for.
            List<String> ids = WipeCatalog.closureOf(List.of(WipeCatalog.MAIN_GROUPS))
                    .stream().map(WipeTarget::id).toList();

            assertTrue(ids.containsAll(List.of("mainGroups", "subGroups", "items",
                    "sales", "salesReturns", "purchases", "purchaseReturns")), ids.toString());
        }

        @Test
        @DisplayName("answers in execution order however it was asked")
        void alwaysInCatalogOrder() {
            List<String> ids = WipeCatalog.closureOf(List.of(WipeCatalog.USERS, WipeCatalog.SALES))
                    .stream().map(WipeTarget::id).toList();

            assertEquals("salesReturns", ids.getFirst());
            assertEquals("users", ids.getLast());
        }

        @Test
        @DisplayName("a target that needs nothing pulls in nothing")
        void independentTarget() {
            assertEquals(List.of("processes"),
                    WipeCatalog.closureOf(List.of(WipeCatalog.PROCESSES)).stream().map(WipeTarget::id).toList());
        }

        @Test
        @DisplayName("nothing selected is a plan that does nothing")
        void emptySelection() {
            assertTrue(WipePlan.of(List.of()).isEmpty());
        }
    }

    @Nested
    @DisplayName("statements")
    class Statements {

        @Test
        @DisplayName("invoice numbers are never reset or reused by a data wipe")
        void preservesDocumentSequences() {
            assertTrue(WipePlan.of(WipeCatalog.TARGETS).statements().stream()
                            .noneMatch(statement -> statement.contains("document_sequences")),
                    "a data wipe must not reset invoice numbering");
        }

        @Test
        @DisplayName("a table is seeded after it is emptied")
        void deleteThenSeed() {
            List<String> statements = WipePlan.of(List.of(WipeCatalog.PROCESSES, WipeCatalog.CUSTOMERS)).statements();

            int delete = statements.indexOf("DELETE FROM custom");
            int seed = statements.indexOf(
                    "INSERT INTO custom(id, name, limit_num, price_id) VALUES (1, 'بيع نقدى', 5000, 1)");

            assertTrue(delete >= 0, statements.toString());
            assertTrue(seed > delete, "the cash customer must be put back after the table is emptied");
        }

        /**
         * A seed is an INSERT, and the foreign keys judge it exactly as they judge a
         * DELETE - only the other way round. Putting {@code sub_group} row 1 back
         * before {@code main_group} had been emptied is what made the whole wipe fail:
         * the row pointed at {@code main_group} 1, and the DELETE that came next could
         * not remove it.
         */
        @Test
        @DisplayName("nothing is seeded while a table it points at is still to be emptied")
        void seedsComeAfterEveryDelete() {
            List<String> statements = WipePlan.of(WipeCatalog.TARGETS).statements();

            int lastDelete = -1;
            int firstSeed = statements.size();
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i).startsWith("DELETE FROM ")) {
                    lastDelete = i;
                } else if (i < firstSeed) {
                    firstSeed = i;
                }
            }

            assertTrue(lastDelete < firstSeed,
                    "a seed runs at " + firstSeed + " with deletes still to come at " + lastDelete
                    + ": " + statements);
        }

        /**
         * And two seeds can point at each other: the seeded {@code sub_group} row needs
         * the seeded {@code main_group} row to exist first.
         */
        @Test
        @DisplayName("a seeded row's parent is seeded before it")
        void seedsAreParentFirst() {
            List<String> statements = WipePlan.of(WipeCatalog.TARGETS).statements();
            Map<String, Integer> seededAt = new LinkedHashMap<>();

            for (WipeTarget target : WipeCatalog.TARGETS) {
                for (WipeTable table : target.tables()) {
                    table.seeds().stream()
                            .map(statements::indexOf)
                            .filter(index -> index >= 0)
                            .min(Integer::compare)
                            .ifPresent(index -> seededAt.put(table.table(), index));
                }
            }

            assertTrue(seededAt.containsKey("sub_group") && seededAt.containsKey("main_group"),
                    "expected both group tables to be seeded: " + seededAt.keySet());

            List<String> problems = new ArrayList<>();
            for (ForeignKey key : FOREIGN_KEYS) {
                Integer child = seededAt.get(key.child());
                Integer parent = seededAt.get(key.parent());
                if (child == null || parent == null || key.child().equals(key.parent())) {
                    continue;
                }
                if (parent > child) {
                    problems.add("%s is seeded before %s, which its %s points at"
                            .formatted(key.child(), key.parent(), key.column()));
                }
            }

            if (!problems.isEmpty()) {
                fail("Seed order breaks these foreign keys:\n" + String.join("\n", problems));
            }
        }

        @Test
        @DisplayName("users is emptied around the row every user_id points at")
        void usersKeepsRowOne() {
            List<String> statements = WipePlan.of(List.of(WipeCatalog.USERS)).statements();

            assertTrue(statements.contains("DELETE FROM users WHERE id <> 1"), statements.toString());
        }
    }
}
