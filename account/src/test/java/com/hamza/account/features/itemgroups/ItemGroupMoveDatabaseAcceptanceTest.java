package com.hamza.account.features.itemgroups;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the group manager does to a real MySQL.
 * <p>
 * Everything else in this package is checked without a database, and none of it can answer
 * the question the feature exists to answer: does a move actually land, and does a batch
 * that must be refused leave every row where it was? {@link ItemGroupMovePolicy} says which
 * commands are well formed; it knows nothing about {@code sub_num}. {@link ItemGroupMoveService}
 * is tested against a mock repository, so the optimistic check passes there by construction -
 * the mock returns whatever the test told it to. The three things only a database decides are
 * whether {@code UPDATE items SET sub_num = ? WHERE id = ? AND sub_num = ?} counts the row it
 * claims to, whether {@code SELECT ... FOR UPDATE} parses and runs inside the service's
 * transaction, and whether the group tree query - a {@code LEFT JOIN} with the search in the
 * join condition and a {@code HAVING} that switches on an empty parameter - returns the counts
 * a person would count by hand.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. Everything runs inside one transaction that
 * is always rolled back, in the manner of {@code ItemMergeDatabaseAcceptanceTest}: the service's
 * own {@link com.hamza.controlsfx.database.TransactionTemplate} joins the open transaction
 * rather than committing inside it, so the assertions see every row it wrote and the developer's
 * database keeps none of them. Every fixture row is stamped {@code GRP-<nanos>} so residue, if
 * the rollback ever failed, is one query away.
 * <p>
 * <b>Two things this deliberately does not claim.</b> The service's last guard - {@code moved !=
 * effective.size()}, a row changing between the lock and the update - is unreachable from one
 * connection, which is the point of taking the lock; testing it would need a second connection
 * racing this one, and a rollback this test could not observe from inside an enclosing
 * transaction anyway. And the lock itself is not proven to block a second writer: that needs two
 * connections and a lock-wait timeout, and a test that waits on one is a test that hangs a build.
 * What is proven is that the statement runs and that the check it exists to protect refuses the
 * stale batch.
 * <p>
 * <b>Not yet run.</b> Written on 2026-09-05 against no database. Run it - against a scratch
 * schema, not a database with data in it - before trusting anything it says.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ItemGroupMoveDatabaseAcceptanceTest {

    private static final int USER = 1;
    private static final int UNIT = 1;

    private static final ItemGroupMoveService SERVICE =
            new ItemGroupMoveService(new JdbcItemGroupRepository());

    @BeforeAll
    static void connect() throws Exception {
        // Surefire's working directory is the module, so this is account/config.xml - a
        // different file from the one in the repository root.
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
        signIn(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_GROUP_MOVE);
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    private static void signIn(com.hamza.account.authorization.PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(USER, "admin", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    // ------------------------------------------------------------------
    // Moving
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a move lands, on every item of the batch, and records who moved them")
    void aMoveLands() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            ItemGroupMoveResult result = SERVICE.move(new ItemGroupMoveCommand(List.of(
                    new ItemGroupChange(fixture.first(), fixture.from(), fixture.to()),
                    new ItemGroupChange(fixture.second(), fixture.from(), fixture.to())), USER));

            assertEquals(2, result.movedCount());
            assertEquals(fixture.to(), groupOf(connection, fixture.first()));
            assertEquals(fixture.to(), groupOf(connection, fixture.second()));
            assertEquals(fixture.from(), groupOf(connection, fixture.third()),
                    "an item outside the batch was moved");
            assertEquals(USER, userOf(connection, fixture.first()),
                    "items.user_id does not say who moved the row");
        });
    }

    @Test
    @DisplayName("undoing a move puts every item back")
    void undoPutsThemBack() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            ItemGroupMoveResult result = SERVICE.move(new ItemGroupMoveCommand(List.of(
                    new ItemGroupChange(fixture.first(), fixture.from(), fixture.to()),
                    new ItemGroupChange(fixture.second(), fixture.from(), fixture.to())), USER));

            assertEquals(2, SERVICE.move(result.undoCommand(USER)).movedCount());

            assertEquals(fixture.from(), groupOf(connection, fixture.first()));
            assertEquals(fixture.from(), groupOf(connection, fixture.second()));
        });
    }

    @Test
    @DisplayName("an item already in the target is dropped rather than counted")
    void aNoOpChangeIsFilteredOut() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            move(connection, fixture.first(), fixture.from(), fixture.to());

            ItemGroupMoveResult result = SERVICE.move(new ItemGroupMoveCommand(List.of(
                    new ItemGroupChange(fixture.first(), fixture.to(), fixture.to()),
                    new ItemGroupChange(fixture.second(), fixture.from(), fixture.to())), USER));

            assertEquals(1, result.movedCount(), "the no-op was counted as a move");
            assertEquals(fixture.to(), groupOf(connection, fixture.second()));
        });
    }

    // ------------------------------------------------------------------
    // Refusing - and the part worth a database: that nothing moved on the way
    // ------------------------------------------------------------------

    /**
     * The case the optimistic check exists for. Someone else files the item elsewhere while
     * the screen is open, and the batch still claims the group it was read from. Both changes
     * are refused, not just the stale one.
     */
    @Test
    @DisplayName("a batch carrying one stale group moves none of its items")
    void aStaleGroupRefusesTheWholeBatch() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            move(connection, fixture.first(), fixture.from(), fixture.other());

            BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                    () -> SERVICE.move(new ItemGroupMoveCommand(List.of(
                            new ItemGroupChange(fixture.first(), fixture.from(), fixture.to()),
                            new ItemGroupChange(fixture.second(), fixture.from(), fixture.to())), USER)));

            assertEquals("item.group.manager.error.concurrent", refused.getMessage());
            assertEquals(fixture.other(), groupOf(connection, fixture.first()),
                    "the stale item was moved anyway");
            assertEquals(fixture.from(), groupOf(connection, fixture.second()),
                    "the sound half of a refused batch was still written");
        });
    }

    @Test
    @DisplayName("a target group that does not exist is refused before anything is written")
    void aMissingTargetIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            int absent = maxOf(connection, "sub_group", "id") + 5000;

            BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                    () -> SERVICE.move(new ItemGroupMoveCommand(List.of(
                            new ItemGroupChange(fixture.first(), fixture.from(), absent)), USER)));

            assertEquals("item.group.manager.error.target.missing", refused.getMessage());
            assertEquals(fixture.from(), groupOf(connection, fixture.first()));
        });
    }

    @Test
    @DisplayName("an item that no longer exists is refused, and its neighbours stay put")
    void aMissingItemIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            int absent = maxOf(connection, "items", "id") + 5000;

            BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                    () -> SERVICE.move(new ItemGroupMoveCommand(List.of(
                            new ItemGroupChange(fixture.first(), fixture.from(), fixture.to()),
                            new ItemGroupChange(absent, fixture.from(), fixture.to())), USER)));

            assertEquals("item.group.manager.error.item.missing", refused.getMessage());
            assertEquals(fixture.from(), groupOf(connection, fixture.first()));
        });
    }

    /**
     * Hiding the button is not enforcement, so the service is asked directly by someone who
     * may read the catalogue and nothing else.
     */
    @Test
    @DisplayName("a user without items.group.move cannot move anything through the service")
    void theGuardHoldsAtTheService() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            signIn(AppPermissions.ITEMS_SHOW);
            try {
                assertThrows(BusinessRuleException.class,
                        () -> SERVICE.move(new ItemGroupMoveCommand(List.of(
                                new ItemGroupChange(fixture.first(), fixture.from(), fixture.to())), USER)));
                assertEquals(fixture.from(), groupOf(connection, fixture.first()));
            } finally {
                signIn(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_GROUP_MOVE);
            }
        });
    }

    // ------------------------------------------------------------------
    // Reading - the two queries the screen is built on
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the tree counts each group's items, and a search narrows it to the groups that match")
    void theGroupTreeCounts() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            Map<Integer, ItemGroupSummary> all = byId(SERVICE.groups(""));
            assertEquals(3, all.get(fixture.from()).itemCount(), "the source group's count is wrong");
            assertEquals(0, all.get(fixture.to()).itemCount(), "an empty group should still be listed");
            assertEquals(fixture.mainName(), all.get(fixture.from()).mainGroupName());

            move(connection, fixture.first(), fixture.from(), fixture.to());

            // A search narrows the tree to groups that still hold a match, so the target the
            // operator is about to drag onto must not disappear the moment it holds one.
            Map<Integer, ItemGroupSummary> matching = byId(SERVICE.groups(fixture.stamp()));
            assertEquals(2, matching.get(fixture.from()).itemCount());
            assertEquals(1, matching.get(fixture.to()).itemCount(),
                    "the group the item was moved into was dropped from the search");
            assertFalse(matching.containsKey(fixture.other()),
                    "a group holding nothing that matches was still listed");
        });
    }

    @Test
    @DisplayName("items page one group at a time, without repeating or skipping a row")
    void itemsPage() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            List<ItemGroupItem> first = SERVICE.items(fixture.from(), fixture.stamp(), 2, 0);
            List<ItemGroupItem> second = SERVICE.items(fixture.from(), fixture.stamp(), 2, 2);

            assertEquals(2, first.size());
            assertEquals(1, second.size());
            assertEquals(3, java.util.stream.Stream.concat(first.stream(), second.stream())
                    .map(ItemGroupItem::id).distinct().count(), "a row was repeated across pages");
            first.forEach(item -> assertEquals(fixture.from(), item.subGroupId()));
        });
    }

    @Test
    @DisplayName("the selected items are read back by id, whichever group they now sit in")
    void itemsByIdSurviveAMove() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            move(connection, fixture.first(), fixture.from(), fixture.to());

            List<ItemGroupItem> read = SERVICE.itemsByIds(Set.of(fixture.first(), fixture.second()));

            assertEquals(2, read.size());
            assertEquals(fixture.to(), read.stream().filter(item -> item.id() == fixture.first())
                    .findFirst().orElseThrow().subGroupId());
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    /** One main group, three sub-groups of its own, and three items sitting in the first. */
    private record Fixture(int from, int to, int other, int first, int second, int third,
                           String stamp, String mainName) {
    }

    private Fixture fixture(Connection connection) throws Exception {
        String stamp = "GRP-" + System.nanoTime() % 1_000_000_000L;
        String mainName = "مجموعة-" + stamp;
        int main = insertMainGroup(connection, mainName);
        int from = insertSubGroup(connection, "من-" + stamp, main);
        int to = insertSubGroup(connection, "إلى-" + stamp, main);
        int other = insertSubGroup(connection, "أخرى-" + stamp, main);
        return new Fixture(from, to, other,
                insertItem(connection, "صنف1-" + stamp, stamp + "-1", from),
                insertItem(connection, "صنف2-" + stamp, stamp + "-2", from),
                insertItem(connection, "صنف3-" + stamp, stamp + "-3", from),
                stamp, mainName);
    }

    private int insertMainGroup(Connection connection, String name) throws Exception {
        return insertReturningKey(connection,
                "INSERT INTO main_group (name_g, user_id) VALUES (?, ?)", name, USER);
    }

    private int insertSubGroup(Connection connection, String name, int mainId) throws Exception {
        return insertReturningKey(connection,
                "INSERT INTO sub_group (name, main_id, user_id) VALUES (?, ?, ?)", name, mainId, USER);
    }

    private int insertItem(Connection connection, String name, String barcode, int subGroup) throws Exception {
        return insertReturningKey(connection, """
                INSERT INTO items (barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2,
                                   sel_price3, unit_id, mini_quantity, first_balance, user_id)
                VALUES (?, ?, ?, 10, 15, 15, 15, ?, 0, 0, ?)""",
                barcode, name, subGroup, UNIT, USER);
    }

    /** A move made behind the service's back, standing in for the other user's screen. */
    private void move(Connection connection, int itemId, int fromGroup, int toGroup) throws Exception {
        assertEquals(1, executeUpdate(connection,
                "UPDATE items SET sub_num = ? WHERE id = ? AND sub_num = ?", toGroup, itemId, fromGroup));
    }

    // ------------------------------------------------------------------
    // Reading back
    // ------------------------------------------------------------------

    private int groupOf(Connection connection, int itemId) throws Exception {
        return intOf(connection, "SELECT sub_num FROM items WHERE id = ?", itemId);
    }

    private int userOf(Connection connection, int itemId) throws Exception {
        return intOf(connection, "SELECT user_id FROM items WHERE id = ?", itemId);
    }

    private int maxOf(Connection connection, String table, String column) throws Exception {
        return intOf(connection, "SELECT COALESCE(MAX(" + column + "), 0) FROM " + table);
    }

    private static Map<Integer, ItemGroupSummary> byId(List<ItemGroupSummary> summaries) {
        return summaries.stream().collect(java.util.stream.Collectors.toMap(
                ItemGroupSummary::subGroupId, summary -> summary, (first, second) -> first));
    }

    private int intOf(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "no row for " + sql);
                return rows.getInt(1);
            }
        }
    }

    private int insertReturningKey(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private int executeUpdate(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws Exception {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    /**
     * One transaction, always rolled back. The service's own transaction joins this one -
     * {@code ConnectionManager} binds it to the thread - so nothing it writes is ever
     * committed, and the assertions still see every row it wrote.
     */
    private void inTransaction(Work work) throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction, "no transaction was opened; another one is already running on this thread");
        try {
            work.run(transaction);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @FunctionalInterface
    private interface Work {
        void run(Connection connection) throws Exception;
    }
}
