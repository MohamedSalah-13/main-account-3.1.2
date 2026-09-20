package com.hamza.account.features.stocktransfer;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A warehouse transfer end to end, against a real MySQL, through the service a screen calls.
 * <p>
 * {@code StockTransferDatabaseAcceptanceTest} was the only thing standing for this and it proves
 * nothing: it has no fixture, it asserts one boolean, and CI finishes it in four milliseconds. The
 * transfer is one of the four writers of a stock balance and it had never been run.
 * <p>
 * <b>Signed in as an ordinary user, deliberately.</b>
 * {@code UserSessionContext.isSystemAdministrator()} is {@code currentUserId() == 1} and bypasses
 * every permission there is, so a guard asserted while signed in as the owner passes whatever the
 * service does. The rows are still written under user 1, which is a real row in every database;
 * only the session is somebody else.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. Each case runs in one transaction and rolls it
 * back, and {@code @AfterAll} queries for this class's own {@code TRF-} stamp rather than trusting
 * that it did - safe on a database with data in it.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class StockTransferEndToEndAcceptanceTest {

    /** Who writes the rows: user 1 exists everywhere, and a foreign key would refuse anyone else. */
    private static final int USER = 1;

    /** Who is signed in: not user 1, or every permission below is granted by the bypass. */
    private static final int OPERATOR = 4243;

    private static final double OPENING = 20;
    private static final double MOVED = 8;

    private static StockTransferService service;

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) {
            configFile = new File("../config.xml");
        }
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
        signIn(AppPermissions.STOCK_TRANSFER_POST);
        service = new StockTransferService(DaoFactory.INSTANCE);
    }

    /** The rollback is checked, not trusted. */
    @AfterAll
    static void leaveNothingBehind() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "stocks", "stock_name LIKE 'TRF-%'");
            assertNoResidue(connection, "items", "barcode LIKE 'TRF-%'");
            assertNoResidue(connection, "stock_transfer_list",
                    "item_id IN (SELECT id FROM items WHERE barcode LIKE 'TRF-%')");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    @Test
    @DisplayName("a transfer takes the quantity out of one warehouse and puts it in the other")
    void aTransferMovesTheBalance() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);

            service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                    LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), MOVED)), USER));

            assertEquals(OPENING - MOVED, balance(connection, fixture.itemId(), DefaultStock.ID), 0.0001,
                    "the source warehouse did not lose what left it");
            assertEquals(MOVED, balance(connection, fixture.itemId(), fixture.destination()), 0.0001,
                    "the destination warehouse did not gain what arrived");
        });
    }

    /**
     * The phase A rule, against the database rather than against a record: two cartons and three
     * pieces of one item is one demand of twenty-seven, not two demands of twelve and three.
     */
    @Test
    @DisplayName("one item in two units is one demand on the source balance")
    void oneItemInTwoUnitsIsSummed() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);

            service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                    LocalDate.now(), List.of(
                    new StockTransferLine(fixture.itemId(), 1, 1, 12),
                    new StockTransferLine(fixture.itemId(), 3, 1, 1)), USER));

            assertEquals(OPENING - 15, balance(connection, fixture.itemId(), DefaultStock.ID), 0.0001);
            assertEquals(15, balance(connection, fixture.itemId(), fixture.destination()), 0.0001);
        });
    }

    /**
     * And the reason that rule had to come with a change to the check: two lines of fifteen are
     * thirty against a balance of twenty, and each line on its own would have passed.
     */
    @Test
    @DisplayName("two lines of one item cannot together exceed the source balance")
    void twoLinesOfOneItemCannotExceedTheBalance() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);

            assertThrows(BusinessRuleException.class, () ->
                    service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                            LocalDate.now(), List.of(
                            new StockTransferLine(fixture.itemId(), 15),
                            new StockTransferLine(fixture.itemId(), 15)), USER)));

            assertEquals(OPENING, balance(connection, fixture.itemId(), DefaultStock.ID), 0.0001,
                    "a refused transfer moved stock anyway");
        });
    }

    @Test
    @DisplayName("a transfer of more than the source holds is refused, and moves nothing")
    void aTransferAboveTheBalanceIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);

            assertThrows(BusinessRuleException.class, () ->
                    service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                            LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), OPENING + 1)),
                            USER)));

            assertEquals(OPENING, balance(connection, fixture.itemId(), DefaultStock.ID), 0.0001);
            assertEquals(0, balance(connection, fixture.itemId(), fixture.destination()), 0.0001);
        });
    }

    /**
     * The permission is what allows it, not the signed-in user's id. Run as user 1 this case
     * cannot fail, which is the trap {@code ItemGroupMoveDatabaseAcceptanceTest} was caught in.
     */
    @Test
    @DisplayName("a user without stock.transfer.post is refused before anything is written")
    void aUserWithoutThePermissionIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            signIn(AppPermissions.STOCK_SHOW);
            try {
                assertThrows(BusinessRuleException.class, () ->
                        service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                                LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), 1)), USER)));
                assertEquals(OPENING, balance(connection, fixture.itemId(), DefaultStock.ID), 0.0001);
            } finally {
                signIn(AppPermissions.STOCK_TRANSFER_POST);
            }
        });
    }

    /** A transfer arrives in a warehouse whose {@code items_stock} row does not exist yet. */
    @Test
    @DisplayName("a destination with no row for the item is given one")
    void aDestinationWithoutARowIsGivenOne() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seedWithoutDestinationRow(connection);

            service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                    LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), MOVED)), USER));

            assertEquals(MOVED, balance(connection, fixture.itemId(), fixture.destination()), 0.0001,
                    "without a row in items_stock the arriving quantity has nothing to add onto");
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    private record Fixture(int itemId, int destination) {
    }

    private static Fixture seed(Connection connection) throws Exception {
        Fixture fixture = seedWithoutDestinationRow(connection);
        insertItemStock(connection, fixture.itemId(), fixture.destination(), 0);
        return fixture;
    }

    private static Fixture seedWithoutDestinationRow(Connection connection) throws Exception {
        String stamp = "TRF-" + UUID.randomUUID().toString().substring(0, 8);
        int destination = insertStock(connection, stamp);
        int itemId = insertItem(connection, stamp);
        insertItemStock(connection, itemId, DefaultStock.ID, OPENING);
        return new Fixture(itemId, destination);
    }

    /** The view's own balance, so the assertions read the definition every screen reads. */
    private static double balance(Connection connection, int itemId, int stockId) throws Exception {
        String sql = "SELECT first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment"
                + " - quantitySales - quantityPurchaseRe - fromStock AS balance"
                + " FROM quantity_items_table WHERE item_id = ? AND stock_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getDouble(1) : 0;
            }
        }
    }

    private static int insertStock(Connection connection, String stamp) throws Exception {
        String sql = "INSERT INTO stocks(stock_name, stock_address, user_id) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, stamp);
            statement.setString(2, stamp);
            statement.setInt(3, USER);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int insertItem(Connection connection, String stamp) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,first_balance,user_id) VALUES (?,?,1,1,10,10,10,1,0,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, stamp);
            statement.setString(2, stamp);
            statement.setDouble(3, OPENING);
            statement.setInt(4, USER);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertItemStock(Connection connection, int itemId, int stockId, double opening)
            throws Exception {
        String sql = "INSERT INTO items_stock(item_id,stock_id,first_balance,current_quantity) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            statement.setDouble(3, opening);
            statement.setDouble(4, opening);
            assertEquals(1, statement.executeUpdate());
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static void assertNoResidue(Connection connection, String table, String where) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1),
                    "this class left rows behind in " + table + " - the rollback did not hold");
        }
    }

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
