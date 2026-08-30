package com.hamza.account.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
import com.hamza.account.treasury.WalletFee;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.account.features.profitloss.ProfitLossDao;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one test that says a treasury balance is right, against a real MySQL.
 * <p>
 * Everything else about the treasury is checked without a database - the labels, the
 * statement text, the declarations - and none of it can answer the question the
 * feature exists to answer: run a known scenario through, and does
 * {@code treasury_current_balance} produce the number a person would reach with a
 * pen? That is arithmetic belonging to a view over eleven UNION branches, and no
 * amount of pinned SQL says it.
 * <p>
 * It is also the financial half of item 0.6 in {@code docs/erp-roadmap.md} - a
 * reference snapshot of {@code treasury_balance} after a known scenario - which was
 * left waiting for the general ledger. It did not have to wait: a derived balance
 * can be checked against hand arithmetic today.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. Everything runs inside one
 * transaction that is always rolled back, in the manner of
 * {@code ItemMergeDatabaseAcceptanceTest}, so a developer's database is left exactly
 * as it was - including the two treasuries the fixture creates.
 * <p>
 * The database has to be migrated first: {@code V20} adds the columns and
 * {@code R__views.sql} builds the view, and both are applied by running the
 * application once against it.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class TreasuryBalanceViewAcceptanceTest {

    /**
     * Distinct, non-round-tripping numbers: every leg has to be visible in the total
     * on its own, so a balance landing on the right figure by accident is impossible.
     */
    private static final double OPENING = 1000;
    private static final double CASH_SALE = 500;
    private static final double CUSTOMER_PAID = 200;
    private static final double SUPPLIER_PAID = 150;
    private static final double EXPENSE = 50;
    private static final double DEPOSIT = 300;
    private static final double WITHDRAWAL = 100;
    private static final double TRANSFER_OUT = 75;
    private static final double TRANSFER_IN = 25;

    private static final double EXPECTED_IN = CASH_SALE + CUSTOMER_PAID + DEPOSIT + TRANSFER_IN;
    private static final double EXPECTED_OUT = SUPPLIER_PAID + EXPENSE + WITHDRAWAL + TRANSFER_OUT;
    private static final double EXPECTED_BALANCE = OPENING + EXPECTED_IN - EXPECTED_OUT;

    @BeforeAll
    static void connect() throws Exception {
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

        // The write services are guarded, and user 1 is the system administrator - which
        // is what the shop owner running this is. The guards themselves are checked
        // without a database by TreasuryTransferServiceTest, signed in as an ordinary
        // user, because an administrator passes every one of them.
        UserSessionContext session = new UserSessionContext();
        session.signIn(1, "admin",
                List.of(AppPermissions.TREASURY_TRANSFER, AppPermissions.TREASURY_DEPOSIT,
                        AppPermissions.TREASURY_CAPITAL, AppPermissions.CUSTOMER_ACCOUNT_CREATE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @Test
    @DisplayName("the balance is the opening plus everything in less everything out")
    void theBalanceIsWhatAPersonWouldReachWithAPen() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            Balance main = balance(connection, fixture.main());

            assertEquals(money(OPENING), main.opening(),
                    "the opening balance is not treasury.amount");
            assertEquals(money(EXPECTED_IN), main.totalIn(),
                    "everything that came in, opening excluded");
            assertEquals(money(EXPECTED_OUT), main.totalOut(),
                    "everything that went out");
            assertEquals(money(EXPECTED_BALANCE), main.balance(),
                    "the current balance");
        });
    }

    @Test
    @DisplayName("the opening balance is counted once - a line in the statement, not in the totals")
    void theOpeningIsCountedOnce() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            assertEquals(money(OPENING),
                    statementIncome(connection, fixture.main(), MovementLabel.OPENING),
                    "no opening line in treasury_balance, or it is not the opening amount");

            Balance main = balance(connection, fixture.main());
            assertEquals(main.opening().add(main.totalIn()).subtract(main.totalOut()), main.balance(),
                    "balance is not opening + in - out; the opening is being counted twice or not at all");
        });
    }

    @Test
    @DisplayName("a transfer moves money between two treasuries and creates none")
    void aTransferIsConserved() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            Balance main = balance(connection, fixture.main());
            Balance other = balance(connection, fixture.other());

            assertEquals(money(TRANSFER_OUT - TRANSFER_IN), other.balance(),
                    "the receiving treasury did not end with exactly what was sent to it");

            // What the two hold together cannot depend on transfers at all. This is the
            // check that was impossible before: treasury_balance ignored treasury_transfers
            // outright, so a transfer showed up in neither treasury.
            assertEquals(money(OPENING + CASH_SALE + CUSTOMER_PAID + DEPOSIT
                            - SUPPLIER_PAID - EXPENSE - WITHDRAWAL),
                    main.balance().add(other.balance()),
                    "the two treasuries together moved when only money between them did");

            assertEquals(money(TRANSFER_OUT),
                    statementOutput(connection, fixture.main(), MovementLabel.TRANSFER_OUT));
            assertEquals(money(TRANSFER_IN),
                    statementIncome(connection, fixture.main(), MovementLabel.TRANSFER_IN));
        });
    }

    @Test
    @DisplayName("a closed treasury keeps its balance and leaves the active list")
    void aClosedTreasuryIsStillCounted() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            execute(connection, "UPDATE treasury SET is_active = 0 WHERE id = ?", fixture.main());

            Balance main = balance(connection, fixture.main());
            assertEquals(money(EXPECTED_BALANCE), main.balance(),
                    "closing a treasury changed what it holds");
            assertTrue(!main.active(), "is_active did not reach the view");
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    private record Fixture(int main, int other) {
    }

    private Fixture fixture(Connection connection) throws Exception {
        requireView(connection);

        int main = insertTreasury(connection, "acceptance-main", OPENING);
        int other = insertTreasury(connection, "acceptance-other", 0);

        insertCashSale(connection, main);

        execute(connection, """
                INSERT INTO customers_accounts (account_code, account_date, paid, notes, treasury_id,
                                                purchase, numberInv, user_id)
                VALUES (?, ?, ?, 'treasury-acceptance', ?, 0, 0, 1)
                """, firstId(connection, "custom"), today(), CUSTOMER_PAID, main);

        execute(connection, """
                INSERT INTO suppliers_accounts (account_code, account_date, purchase, paid, numberInv,
                                                notes, treasury_id, user_id)
                VALUES (?, ?, 0, ?, 0, 'treasury-acceptance', ?, 1)
                """, firstId(connection, "suppliers"), today(), SUPPLIER_PAID, main);

        execute(connection, """
                INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id)
                VALUES (?, ?, ?, 'treasury-acceptance', 0, ?, 1)
                """, expenseType(connection), today(), EXPENSE, main);

        // deposit_or_expenses: 1 = deposit, 2 = withdrawal.
        execute(connection, """
                INSERT INTO treasury_deposit_expenses (statement, date_inter, amount, description_data,
                                                       deposit_or_expenses, treasury_id, user_id)
                VALUES ('acceptance', ?, ?, NULL, 1, ?, 1)
                """, today(), DEPOSIT, main);
        execute(connection, """
                INSERT INTO treasury_deposit_expenses (statement, date_inter, amount, description_data,
                                                       deposit_or_expenses, treasury_id, user_id)
                VALUES ('acceptance', ?, ?, NULL, 2, ?, 1)
                """, today(), WITHDRAWAL, main);

        execute(connection, """
                INSERT INTO treasury_transfers (treasury_from, treasury_to, amount, transfer_date, notes, user_id)
                VALUES (?, ?, ?, ?, 'treasury-acceptance', 1)
                """, main, other, TRANSFER_OUT, today());
        execute(connection, """
                INSERT INTO treasury_transfers (treasury_from, treasury_to, amount, transfer_date, notes, user_id)
                VALUES (?, ?, ?, ?, 'treasury-acceptance', 1)
                """, other, main, TRANSFER_IN, today());

        return new Fixture(main, other);
    }

    /**
     * The view is built by {@code R__views.sql} and its columns by {@code V20}, both
     * applied when the application runs. Saying so plainly beats an unknown-column
     * error from inside a mapper.
     */
    private void requireView(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW TABLES LIKE 'treasury_current_balance'")) {
            assertTrue(rows.next(),
                    "treasury_current_balance is missing: run the application once against this "
                            + "database so Flyway applies V20 and R__views.sql");
        }
    }

    private int insertTreasury(Connection connection, String name, double opening) throws Exception {
        // The name is unique, and the transaction is rolled back, so a suffix keeps a
        // half-finished earlier run from colliding with this one.
        String unique = name + "-" + System.nanoTime();
        execute(connection, """
                INSERT INTO treasury (t_name, amount, treasury_type, is_active, sort_order,
                                      fee_percent, opening_date, user_id)
                VALUES (?, ?, 'CASH', 1, 0, 0, ?, 1)
                """, unique, opening, today());
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    /** A cash sale stores paid = net, so its whole net is what the till took. */
    private void insertCashSale(Connection connection, int treasury) throws Exception {
        DocumentTableSpec spec = DocumentTableSpec.of(DocumentType.SALES);
        int documentId = nextId(connection, spec.table(), spec.key());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(spec.key(), documentId);
        values.put(spec.party(), firstId(connection, "custom"));
        values.put(spec.paid(), CASH_SALE);
        values.put("invoice_type", 1);
        values.put("invoice_date", java.sql.Date.valueOf(LocalDate.now()));
        values.put("total", CASH_SALE);
        values.put("discount", 0);
        values.put("stock_id", 1);
        values.put("delegate_id", 1);
        values.put("treasury_id", treasury);
        values.put("notes", "treasury-acceptance");
        values.put("user_id", 1);

        try (PreparedStatement statement = connection.prepareStatement(spec.insertSql())) {
            int index = 1;
            for (String column : spec.insertColumns()) {
                Object value = values.get(column);
                assertNotNull(value, "No test value for column " + column + " of " + spec.table());
                statement.setObject(index++, value);
            }
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int expenseType(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT MIN(id) FROM expenses")) {
            if (rows.next() && rows.getInt(1) > 0) {
                return rows.getInt(1);
            }
        }
        int id = 9_000_001;
        execute(connection, "INSERT INTO expenses (id, expenses_name) VALUES (?, ?)",
                id, "acceptance-" + id);
        return id;
    }

    private int firstId(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT MIN(id) FROM " + table)) {
            assertTrue(rows.next() && rows.getInt(1) > 0,
                    "no row in " + table + " to hang a movement on");
            return rows.getInt(1);
        }
    }

    private int nextId(Connection connection, String table, String key) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+3000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    // ------------------------------------------------------------------
    // Reading back
    // ------------------------------------------------------------------

    private record Balance(BigDecimal opening, BigDecimal totalIn, BigDecimal totalOut,
                           BigDecimal balance, boolean active) {
    }

    private Balance balance(Connection connection, int treasuryId) throws Exception {
        try (PreparedStatement statement =
                     connection.prepareStatement(TreasuryStatements.SELECT_BALANCE_BY_ID)) {
            statement.setInt(1, treasuryId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "no row in treasury_current_balance for treasury " + treasuryId);
                return new Balance(money(rows.getBigDecimal("opening")),
                        money(rows.getBigDecimal("total_in")),
                        money(rows.getBigDecimal("total_out")),
                        money(rows.getBigDecimal("balance")),
                        rows.getBoolean("is_active"));
            }
        }
    }

    private BigDecimal statementIncome(Connection connection, int treasuryId, MovementLabel label)
            throws Exception {
        return statementSum(connection, treasuryId, label, "income");
    }

    private BigDecimal statementOutput(Connection connection, int treasuryId, MovementLabel label)
            throws Exception {
        return statementSum(connection, treasuryId, label, "output");
    }

    private BigDecimal statementSum(Connection connection, int treasuryId, MovementLabel label,
                                    String column) throws Exception {
        String sql = "SELECT COALESCE(SUM(" + column + "), 0) FROM treasury_balance"
                + " WHERE treasury_id = ? AND information = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, treasuryId);
            statement.setString(2, label.text());
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return money(rows.getBigDecimal(1));
            }
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    private java.sql.Date today() {
        return java.sql.Date.valueOf(LocalDate.now());
    }

    /** The views return DECIMAL(14,2); compare at that scale rather than by double. */
    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2);
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

    // ------------------------------------------------------------------
    // The write path (phase B) - the services, not hand-written SQL
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a transfer through the service moves both balances and creates nothing")
    void theTransferServiceMovesMoney() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            BigDecimal before = balance(connection, fixture.main()).balance();
            BigDecimal otherBefore = balance(connection, fixture.other()).balance();

            // The service opens its own TransactionTemplate, which joins this open
            // transaction rather than committing inside it - so this is rolled back too.
            new TreasuryTransferService(DaoFactory.INSTANCE).transfer(new TreasuryTransferCommand(
                    fixture.main(), fixture.other(), money(40), LocalDate.now(), "service", 1));

            assertEquals(before.subtract(money(40)), balance(connection, fixture.main()).balance());
            assertEquals(otherBefore.add(money(40)), balance(connection, fixture.other()).balance());
        });
    }

    @Test
    @DisplayName("a transfer larger than the source holds is refused, and writes nothing")
    void theTransferServiceRefusesWhatIsNotThere() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            BigDecimal before = balance(connection, fixture.main()).balance();

            assertThrows(BusinessRuleException.class, () ->
                    new TreasuryTransferService(DaoFactory.INSTANCE).transfer(new TreasuryTransferCommand(
                            fixture.main(), fixture.other(), before.add(money(1)),
                            LocalDate.now(), "too much", 1)));

            assertEquals(before, balance(connection, fixture.main()).balance(),
                    "the refused transfer still moved money");
        });
    }

    @Test
    @DisplayName("a deposit raises the balance and a withdrawal beyond it is refused")
    void theCashServiceRespectsTheBalance() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            TreasuryCashService service = new TreasuryCashService(DaoFactory.INSTANCE);
            BigDecimal before = balance(connection, fixture.main()).balance();

            service.record(new CashMovementCommand(fixture.main(), CashDirection.DEPOSIT,
                    CashCategory.NORMAL, money(60), LocalDate.now(), "service", null, 1));
            assertEquals(before.add(money(60)), balance(connection, fixture.main()).balance());

            BigDecimal now = balance(connection, fixture.main()).balance();
            assertThrows(BusinessRuleException.class, () ->
                    service.record(new CashMovementCommand(fixture.main(), CashDirection.WITHDRAWAL,
                            CashCategory.NORMAL, now.add(money(1)), LocalDate.now(), "too much", null, 1)));
            assertEquals(now, balance(connection, fixture.main()).balance());
        });
    }

    @Test
    @DisplayName("capital paid in raises the treasury and leaves the profit untouched")
    void capitalDoesNotReachTheProfit() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            LocalDate today = LocalDate.now();

            BigDecimal treasuryBefore = balance(connection, fixture.main()).balance();
            BigDecimal profitBefore = netProfit(today);

            new TreasuryCashService(DaoFactory.INSTANCE).record(new CashMovementCommand(
                    fixture.main(), CashDirection.DEPOSIT, CashCategory.CAPITAL_IN,
                    money(5000), today, "raas maal", null, 1));

            assertEquals(treasuryBefore.add(money(5000)),
                    balance(connection, fixture.main()).balance(),
                    "capital did not reach the treasury");
            assertEquals(profitBefore, netProfit(today),
                    "capital paid in changed the profit - it is not income, and "
                            + "docs/treasury-plan.md §4 is the reason");
        });
    }

    @Test
    @DisplayName("the owner's drawings lower the treasury and are not an expense")
    void drawingsAreNotAnExpense() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            LocalDate today = LocalDate.now();

            BigDecimal treasuryBefore = balance(connection, fixture.main()).balance();
            BigDecimal profitBefore = netProfit(today);

            new TreasuryCashService(DaoFactory.INSTANCE).record(new CashMovementCommand(
                    fixture.main(), CashDirection.WITHDRAWAL, CashCategory.OWNER_DRAW,
                    money(100), today, "moshabat", null, 1));

            assertEquals(treasuryBefore.subtract(money(100)),
                    balance(connection, fixture.main()).balance());
            assertEquals(profitBefore, netProfit(today),
                    "the owner's drawings were counted as an expense");
        });
    }

    @Test
    @DisplayName("a category that contradicts its direction is refused")
    void theCategoryMustMatchTheDirection() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            assertThrows(BusinessRuleException.class, () ->
                    new TreasuryCashService(DaoFactory.INSTANCE).record(new CashMovementCommand(
                            fixture.main(), CashDirection.WITHDRAWAL, CashCategory.CAPITAL_IN,
                            money(10), LocalDate.now(), "impossible", null, 1)),
                    "capital withdrawn is not a thing");
        });
    }

    /** The report's own number for one day, so the comparison is the report's, not a re-derivation. */
    private BigDecimal netProfit(LocalDate day) throws Exception {
        return new ProfitLossDao().load(day, day).stream()
                .map(ProfitLossRow::netProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("a wallet collection: the customer settles all of it, the treasury nets the fee")
    void theWalletFeeIsAnExpenseNotADeduction() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            int customer = firstId(connection, "custom");
            LocalDate today = LocalDate.now();

            BigDecimal treasuryBefore = balance(connection, fixture.main()).balance();
            BigDecimal owedBefore = owedBy(connection, customer);

            // 1000 collected on a wallet charging 1%: 10 kept, 990 credited.
            CustomerAccount payment = new CustomerAccount(
                    nextId(connection, "customers_accounts", "account_num"),
                    today.toString(), 1000d, "wallet-acceptance", 0,
                    new Customers(customer), new Treasury(fixture.main()));

            new AccountCustomerService(DaoFactory.INSTANCE).save(payment, money(10));

            assertEquals(treasuryBefore.add(money(990)),
                    balance(connection, fixture.main()).balance(),
                    "the treasury did not end with the collection less the fee");
            assertEquals(owedBefore.subtract(money(1000)), owedBy(connection, customer),
                    "the customer was charged the fee - their account must close by the whole 1000");
            assertEquals(1, countOf(connection,
                            "SELECT COUNT(*) FROM expenses_details d JOIN expenses e ON e.id = d.type_code"
                                    + " WHERE d.treasury_id = ? AND d.amount = 10 AND e.expenses_name = ?",
                            fixture.main(), WalletFee.EXPENSE_NAME),
                    "the fee was not posted as an expense under its own heading");
        });
    }

    @Test
    @DisplayName("an impossible fee is refused, and the save fails rather than posting it")
    void anImpossibleFeeStopsTheSave() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            int customer = firstId(connection, "custom");
            int id = nextId(connection, "customers_accounts", "account_num");

            CustomerAccount payment = new CustomerAccount(id, LocalDate.now().toString(), 1000d,
                    "wallet-acceptance", 0, new Customers(customer), new Treasury(fixture.main()));

            assertThrows(Exception.class, () ->
                            new AccountCustomerService(DaoFactory.INSTANCE).save(payment, money(2000)),
                    "a fee larger than the amount was accepted");

            assertEquals(0, countOf(connection,
                            "SELECT COUNT(*) FROM expenses_details d JOIN expenses e ON e.id = d.type_code"
                                    + " WHERE d.treasury_id = ? AND e.expenses_name = ? AND d.amount = 2000",
                            fixture.main(), WalletFee.EXPENSE_NAME),
                    "the refused fee was posted anyway");
        });
    }

    /*
     * What is deliberately NOT asserted above: that the payment row went with the
     * refused fee.
     *
     * It does, in the application - TransactionTemplate is the outermost boundary there
     * and rolls both back. It cannot be observed from here: ConnectionManager makes a
     * nested begin join the transaction already open, leaving the commit and the
     * rollback to the outer one, which in this class is the test's own (see CLAUDE.md,
     * "Database access"). So inside this test the insert stays visible until the test
     * rolls it back, and asserting otherwise would be asserting a fact about the test
     * harness rather than about the code.
     *
     * Proving it properly needs a test that owns the outermost transaction itself -
     * items 0.4 and 0.5 of docs/erp-roadmap.md, which are still unwritten.
     */

    /** What the customer still owes, from the view the statement screen reads. */
    private BigDecimal owedBy(Connection connection, int customerId) throws Exception {
        String sql = "SELECT COALESCE(SUM(purchase) - SUM(discount) - SUM(paid), 0)"
                + " FROM account_customer_table WHERE account_code = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return money(rows.getBigDecimal(1));
            }
        }
    }

    private int countOf(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("a new collection is inserted, though the screen supplies its own number")
    void aNewCollectionIsActuallyWritten() throws Exception {
        inTransaction(connection -> {
            int customer = firstId(connection, "custom");
            // Exactly what Add_AccountController builds: txtCode holds
            // generateNextAccountCode() = max + 1, and that becomes the model's id. So a
            // new payment never has id 0, and a service that decides "is this new?" by
            // asking whether the id is zero writes nothing at all - which is what
            // happened between f2b4baf and this test.
            int code = nextId(connection, "customers_accounts", "account_num");
            CustomerAccount payment = new CustomerAccount(code, LocalDate.now().toString(), 5d,
                    "insert-path", 0, new Customers(customer), new Treasury(1));

            int rows = new AccountCustomerService(DaoFactory.INSTANCE).save(payment);

            assertEquals(1, rows, "the collection reported no rows written");
            assertEquals(1, countOf(connection,
                            "SELECT COUNT(*) FROM customers_accounts WHERE account_num = ?", code),
                    "the payment was not written at all - the save took the UPDATE branch "
                            + "against a row that does not exist");
        });
    }

    @Test
    @DisplayName("editing an existing collection still updates it rather than inserting a second")
    void anExistingCollectionIsUpdated() throws Exception {
        inTransaction(connection -> {
            int customer = firstId(connection, "custom");
            int code = nextId(connection, "customers_accounts", "account_num");
            AccountCustomerService service = new AccountCustomerService(DaoFactory.INSTANCE);

            service.save(new CustomerAccount(code, LocalDate.now().toString(), 5d,
                    "before", 0, new Customers(customer), new Treasury(1)));
            service.save(new CustomerAccount(code, LocalDate.now().toString(), 7d,
                    "after", 0, new Customers(customer), new Treasury(1)));

            assertEquals(1, countOf(connection,
                            "SELECT COUNT(*) FROM customers_accounts WHERE account_num = ?", code),
                    "the edit inserted a second row instead of updating the first");
            assertEquals(1, countOf(connection,
                            "SELECT COUNT(*) FROM customers_accounts WHERE account_num = ? AND paid = 7",
                            code),
                    "the edit did not take");
        });
    }

    @Test
    @DisplayName("earlier runs left nothing behind - checked, not trusted")
    void thisClassLeavesNoResidue() throws Exception {
        inTransaction(connection -> {
            // Every fixture treasury is named "acceptance-*", every movement it writes
            // points at one, and the whole thing is rolled back in a finally. That is the
            // claim; this is the check. CLAUDE.md records that an acceptance run has left
            // rows in a development database before, which is why it is worth asserting
            // rather than believing.
            assertEquals(0, countOf(connection,
                            "SELECT COUNT(*) FROM treasury WHERE t_name LIKE 'acceptance-%'"),
                    "a previous run committed its treasuries");
            assertEquals(0, countOf(connection,
                            "SELECT COUNT(*) FROM treasury_transfers WHERE notes = 'treasury-acceptance'"),
                    "a previous run committed its transfers");
            assertEquals(0, countOf(connection,
                            "SELECT COUNT(*) FROM customers_accounts WHERE notes IN ('wallet-acceptance', 'insert-path', 'before', 'after')"),
                    "a previous run committed its collections");
            assertEquals(0, countOf(connection,
                            "SELECT COUNT(*) FROM treasury_deposit_expenses WHERE statement IN ('acceptance', 'service')"),
                    "a previous run committed its deposits");
        });
    }
}
