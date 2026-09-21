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
import com.hamza.controlsfx.error.UserValidationException;
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
            assertNoResidue(connection, "stock_transfer",
                    "notes LIKE 'TRF-note%'");
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
    // Phase D1: the note, the slip and the history (docs/warehouse-plan.md §15)
    // ------------------------------------------------------------------

    /**
     * The slip reads the stored transfer again by its id - the note, who entered it, every line in
     * the unit it was entered in - and never the list's copy.
     */
    @Test
    @DisplayName("a transfer keeps its note, and its slip is the stored row with every line as entered")
    void theSlipIsTheStoredTransfer() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            signIn(AppPermissions.STOCK_TRANSFER_POST, AppPermissions.STOCK_TRANSFER_SHOW);
            try {
                long id = service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                        LocalDate.now(), List.of(
                        new StockTransferLine(fixture.itemId(), 1, 1, 12),
                        new StockTransferLine(fixture.itemId(), 3, 1, 1)), USER, "  TRF-note for the winter fair  "));

                StockTransferSlip slip = service.forSlip((int) id);

                assertEquals("TRF-note for the winter fair", slip.header().notes(), "the note was not kept as written");
                assertEquals(userName(connection, USER), slip.header().enteredBy());
                assertEquals(fixture.destination(), slip.header().toStockId());
                assertEquals(2, slip.lines().size());
                assertEquals(1, slip.lines().get(0).quantity(), 0.0001,
                        "a carton is printed as one carton, not as the twelve pieces it holds");
                assertEquals(3, slip.lines().get(1).quantity(), 0.0001, "the lines are not in the order entered");
                assertEquals(fixture.code(), slip.lines().get(0).code());
            } finally {
                signIn(AppPermissions.STOCK_TRANSFER_POST);
            }
        });
    }

    @Test
    @DisplayName("a note longer than its column is refused with a sentence, and nothing moves")
    void aNoteTooLongIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);

            assertThrows(UserValidationException.class, () ->
                    service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                            LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), 1)), USER,
                            "x".repeat(StockTransferCommand.NOTES_MAX_LENGTH + 1))));

            assertEquals(OPENING, balance(connection, fixture.itemId(), DefaultStock.ID), 0.0001);
        });
    }

    /**
     * The defect the history had: "the last two hundred" and nothing else. A period, a warehouse
     * at either end, and totals of the whole set rather than of the page - read with the same WHERE
     * as the rows, or the footer would describe other transfers than the table.
     */
    @Test
    @DisplayName("the history is a period and a warehouse at either end, totalled over the whole set")
    void theHistoryByPeriodAndWarehouse() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            signIn(AppPermissions.STOCK_TRANSFER_POST, AppPermissions.STOCK_TRANSFER_SHOW);
            try {
                LocalDate today = LocalDate.now();
                int destination = fixture.destination();
                transfer(DefaultStock.ID, destination, today.minusDays(3), fixture.itemId(), 1);
                transfer(DefaultStock.ID, destination, today, fixture.itemId(), 5);
                long lastIn = service.transfer(new StockTransferCommand(DefaultStock.ID, destination, today,
                        List.of(new StockTransferLine(fixture.itemId(), 1), new StockTransferLine(fixture.itemId(), 2)),
                        USER));
                // Out of the destination: the same warehouse at the other end of a transfer.
                long out = transfer(destination, DefaultStock.ID, today, fixture.itemId(), 2);

                StockTransferHistoryPage firstOfTwo = service.history(
                        new StockTransferHistoryFilter(today.minusDays(1), today, destination, null, 0, 1));

                assertEquals(1, firstOfTwo.rows().size());
                assertEquals(out, firstOfTwo.rows().getFirst().id(), "newest first");
                assertTrue(firstOfTwo.hasNext());
                assertEquals(3, firstOfTwo.transfers(),
                        "the totals are the period's, not the page's - and a transfer out of the warehouse is in them");
                assertEquals(4, firstOfTwo.lines());

                StockTransferHistoryPage second = service.history(
                        new StockTransferHistoryFilter(today.minusDays(1), today, destination, null, 1, 1));
                assertEquals(lastIn, second.rows().getFirst().id());
                assertEquals(2, second.rows().getFirst().lineCount());
                assertTrue(second.hasPrevious());

                StockTransferHistoryPage wider = service.history(
                        new StockTransferHistoryFilter(today.minusDays(3), today, destination, null, 0, 50));
                assertEquals(4, wider.transfers(), "a transfer three days old was left out of a period that holds it");
                assertEquals(4, wider.rows().size());
            } finally {
                signIn(AppPermissions.STOCK_TRANSFER_POST);
            }
        });
    }

    /**
     * "Where did this item go" is the question the history is opened to answer. A name is matched by
     * part and a code exactly - a partial code matches half the catalogue - and what the person
     * types is text, so a {@code %} is not "everything".
     */
    @Test
    @DisplayName("the text finds a transfer by its note, an item's name, or that item's exact code")
    void theTextSearch() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            String code = "TRF-code-" + UUID.randomUUID().toString().substring(0, 8);
            String name = "TRF-name-" + UUID.randomUUID().toString().substring(0, 8);
            int other = insertItem(connection, code, name);
            insertItemStock(connection, other, DefaultStock.ID, OPENING);
            signIn(AppPermissions.STOCK_TRANSFER_POST, AppPermissions.STOCK_TRANSFER_SHOW);
            try {
                LocalDate today = LocalDate.now();
                long byNote = service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(), today,
                        List.of(new StockTransferLine(fixture.itemId(), 1)), USER, "TRF-note driver Mahmoud, van 7"));
                long byItem = service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(), today,
                        List.of(new StockTransferLine(other, 1)), USER));

                assertEquals(List.of(byNote), ids(search(fixture.destination(), "van 7")));
                assertEquals(List.of(byItem), ids(search(fixture.destination(), code)), "an exact code finds its item");
                assertEquals(List.of(byItem), ids(search(fixture.destination(), name.substring(4))),
                        "part of a name finds its item");
                assertEquals(List.of(), ids(search(fixture.destination(), code.substring(0, code.length() - 1))),
                        "part of a code matched - it would match half a catalogue");
                assertEquals(List.of(), ids(search(fixture.destination(), "%")),
                        "a percent sign typed into the box matched every transfer");
            } finally {
                signIn(AppPermissions.STOCK_TRANSFER_POST);
            }
        });
    }

    @Test
    @DisplayName("the printed log is every line of the transfers the list shows, with their note")
    void thePrintedLogIsTheListsTransfers() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            signIn(AppPermissions.STOCK_TRANSFER_POST, AppPermissions.STOCK_TRANSFER_SHOW);
            try {
                LocalDate today = LocalDate.now();
                service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(), today,
                        List.of(new StockTransferLine(fixture.itemId(), 1), new StockTransferLine(fixture.itemId(), 2)),
                        USER, "TRF-note winter fair"));
                transfer(DefaultStock.ID, fixture.destination(), today, fixture.itemId(), 3);
                StockTransferHistoryFilter filter = new StockTransferHistoryFilter(today, today,
                        fixture.destination(), null, 0, 1);

                List<StockTransferReportRow> log = service.forPrint(filter.forPrint()).orElseThrow();

                assertEquals(service.history(filter).lines(), log.size(),
                        "the paper and the footer count two different sets of lines");
                assertEquals(3, log.size());
                assertEquals("TRF-note winter fair", log.get(0).notes());
                assertEquals(fixture.code(), log.get(0).code());
            } finally {
                signIn(AppPermissions.STOCK_TRANSFER_POST);
            }
        });
    }

    /**
     * V74's split, proven through the service: seeing what moved is not moving it. Signed in as an
     * ordinary user - as user 1 both halves pass whatever the service asks.
     */
    @Test
    @DisplayName("stock.transfer.show reads the history and posts nothing; without it the history is refused")
    void readingIsNotPosting() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            StockTransferHistoryFilter filter = StockTransferHistoryFilter.thisMonth(LocalDate.now());
            try {
                signIn(AppPermissions.STOCK_TRANSFER_SHOW);
                assertNotNull(service.history(filter));
                assertThrows(BusinessRuleException.class, () ->
                        service.transfer(new StockTransferCommand(DefaultStock.ID, fixture.destination(),
                                LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), 1)), USER)));

                signIn(AppPermissions.STOCK_SHOW);
                assertThrows(BusinessRuleException.class, () -> service.history(filter));
                assertThrows(BusinessRuleException.class, () -> service.forPrint(filter));
                assertThrows(BusinessRuleException.class, () -> service.lines(1));
            } finally {
                signIn(AppPermissions.STOCK_TRANSFER_POST);
            }
        });
    }

    private static long transfer(int from, int to, LocalDate date, int itemId, double quantity) throws Exception {
        return service.transfer(new StockTransferCommand(from, to, date,
                List.of(new StockTransferLine(itemId, quantity)), USER));
    }

    private static StockTransferHistoryPage search(int stockId, String text) throws Exception {
        LocalDate today = LocalDate.now();
        return service.history(new StockTransferHistoryFilter(today, today, stockId, text, 0, 50));
    }

    private static List<Long> ids(StockTransferHistoryPage page) {
        return page.rows().stream().map(row -> (long) row.id()).sorted().toList();
    }

    private static String userName(Connection connection, int userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT user_name FROM users WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    /** @param code the item's barcode, which is also its name - the class's {@code TRF-} stamp */
    private record Fixture(int itemId, int destination, String code) {
    }

    private static Fixture seed(Connection connection) throws Exception {
        Fixture fixture = seedWithoutDestinationRow(connection);
        insertItemStock(connection, fixture.itemId(), fixture.destination(), 0);
        return fixture;
    }

    private static Fixture seedWithoutDestinationRow(Connection connection) throws Exception {
        String stamp = "TRF-" + UUID.randomUUID().toString().substring(0, 8);
        int destination = insertStock(connection, stamp);
        int itemId = insertItem(connection, stamp, stamp);
        insertItemStock(connection, itemId, DefaultStock.ID, OPENING);
        return new Fixture(itemId, destination, stamp);
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

    private static int insertItem(Connection connection, String code, String name) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,user_id) VALUES (?,?,1,1,10,10,10,1,0,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setInt(3, USER);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertItemStock(Connection connection, int itemId, int stockId, double opening)
            throws Exception {
        String sql = "INSERT INTO items_stock(item_id,stock_id,first_balance) VALUES (?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            statement.setDouble(3, opening);
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
