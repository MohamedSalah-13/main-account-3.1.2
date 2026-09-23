package com.hamza.account.features.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.CurrencyDraft;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.expense.ExpenseEntry;
import com.hamza.account.features.expense.ExpenseService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.shift.JdbcShiftPolicyRepository;
import com.hamza.account.features.shift.ShiftPolicyService;
import com.hamza.account.features.shift.ShiftTrackingMode;
import com.hamza.account.features.shift.TreasuryShiftPolicy;
import com.hamza.account.features.treasury.statement.TreasuryMovementKind;
import com.hamza.account.features.treasury.statement.TreasuryStatementFilter;
import com.hamza.account.features.treasury.statement.TreasuryStatementPage;
import com.hamza.account.features.treasury.statement.TreasuryStatementPrintData;
import com.hamza.account.features.treasury.statement.TreasuryStatementRow;
import com.hamza.account.features.treasury.statement.TreasuryStatementService;
import com.hamza.account.features.treasury.statement.TreasuryStatementSummary;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A treasury in a foreign currency against a real MySQL (V81, docs/currency-plan.md §11) - the only place
 * the claims that need the database exist: that V81 applies to a schema built from nothing, that its
 * CHECKs refuse what the rules refuse, that the views give a dollar drawer its balance in dollars beside
 * its book value while every figure in the base is what it was, that an exchange moves one base figure out
 * of one treasury and into the other so the treasuries' total does not move, and that every writer phase B
 * does not open refuses the drawer.
 * <p>
 * The case is worked out by hand. Rates for the dollar: 48 from ten days ago, 50 from today. The drawer
 * opens five days ago with 100 dollars (4,800), takes 50 three days ago (2,400) and gives 20 today (1,000);
 * the main treasury, in the base, is given 20,000 and buys 100 dollars for 4,900, then sells 30 for 1,560.
 * The drawer then holds 200 dollars at a book value of 9,540 - 10,000 at today's rate, a valuation
 * difference of 460 that is shown and posted nowhere.
 * <p>
 * Gated like the others ({@code -Daccount.db.acceptance=true}); a scratch schema is built from nothing,
 * named uniquely, and dropped. The session is user 9, never user 1: {@code isSystemAdministrator()}
 * bypasses every permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ForeignTreasuryDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_foreign_treasury_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "FXT-" + System.nanoTime();
    private static final int MAIN = 1;
    private static final LocalDate TODAY = LocalDate.now();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    private static int usd;
    private static int sar;
    private static int dollarDrawer;
    private static int riyalDrawer;

    private static TreasuryService treasuries;
    private static TreasuryCashService cash;
    private static TreasuryTransferService transfers;
    private static CurrencyService currencies;

    @BeforeAll
    static void migrateAScratchSchemaFromNothing() throws Exception {
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configSource().getAbsolutePath());
        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER", config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD", config.get(CryptoDatabaseConfig.PASSWORD));
        schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            }
            Flyway.configure().dataSource(jdbcUrl(schema), username, password)
                    .locations("classpath:db/migration").validateOnMigrate(false).cleanDisabled(true)
                    .load().migrate();
            DataSourceProvider.initialize(host, port, schema, username, password);
            execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                    + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
            UserSessionContext session = new UserSessionContext();
            session.signIn(OPERATOR, "operator", List.of(AppPermissions.TREASURY_SHOW,
                    AppPermissions.TREASURY_UPDATE, AppPermissions.TREASURY_OPENING, AppPermissions.TREASURY_DEPOSIT,
                    AppPermissions.TREASURY_TRANSFER, AppPermissions.CURRENCY_SHOW, AppPermissions.CURRENCY_UPDATE,
                    AppPermissions.CURRENCY_RATE_UPDATE, AppPermissions.EXPENSES_CREATE,
                    AppPermissions.SHIFT_POLICY_MANAGE));
            ServiceRegistry.register(UserSessionContext.class, session);
            treasuries = new TreasuryService(DaoFactory.INSTANCE);
            cash = new TreasuryCashService(DaoFactory.INSTANCE);
            transfers = new TreasuryTransferService(DaoFactory.INSTANCE);
            currencies = new CurrencyService();
            usd = scalar("SELECT id FROM currency WHERE code = 'USD'");
            sar = scalar("SELECT id FROM currency WHERE code = 'SAR'");
        } catch (Exception failure) {
            try {
                dropTheScratchSchema();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterAll
    static void dropTheScratchSchema() throws Exception {
        ServiceRegistry.register(UserSessionContext.class, null);
        DataSourceProvider.shutdown();
        if (schema == null || !schema.startsWith(SCHEMA_PREFIX)) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    @Test
    @Order(1)
    @DisplayName("from nothing: V81's columns, key and CHECKs, and every treasury still in the base")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '81' AND success = 1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM treasury WHERE currency_id IS NOT NULL"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM information_schema.table_constraints"
                + " WHERE table_schema = DATABASE() AND constraint_type = 'CHECK' AND constraint_name IN"
                + " ('treasury_opening_foreign_chk', 'treasury_deposit_expenses_foreign_chk',"
                + " 'treasury_transfers_foreign_chk')"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.referential_constraints"
                + " WHERE constraint_schema = DATABASE() AND constraint_name = 'treasury_currency_id_fk'"
                + " AND delete_rule IN ('RESTRICT', 'NO ACTION')"), "not cascading: a treasury's history is in its currency");
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines"
                + " WHERE routine_schema = DATABASE() AND routine_name LIKE 'v81\\_%'"));
        // In the base, a treasury's own balance is its balance.
        assertEquals(0, scalar("SELECT COUNT(*) FROM treasury_current_balance"
                + " WHERE balance_own <> balance OR opening_own <> opening"));
    }

    @Test
    @Order(2)
    @DisplayName("the database refuses a foreign figure with no rate, and an amount that is not above zero")
    void theChecksHold() {
        assertSqlRefused(3819, "INSERT INTO treasury (t_name, currency_id, opening_foreign) VALUES ('"
                + STAMP + "-x', " + usd + ", 5)");
        assertSqlRefused(3819, "INSERT INTO treasury (t_name, currency_id) VALUES ('" + STAMP + "-y', " + usd + ")");
        assertSqlRefused(3819, "INSERT INTO treasury (t_name, opening_foreign) VALUES ('" + STAMP + "-z', 5)");
        assertSqlRefused(3819, "INSERT INTO treasury_deposit_expenses (statement, date_inter, amount, treasury_id,"
                + " foreign_amount) VALUES ('x', CURDATE(), 10, 1, 1)");
        assertSqlRefused(3819, "INSERT INTO treasury_transfers (treasury_from, treasury_to, amount, transfer_date,"
                + " amount_to) VALUES (1, 1, 10, CURDATE(), 0)");
    }

    @Test
    @Order(3)
    @DisplayName("a dollar drawer opens with 100 dollars at the opening day's rate, copied - and empty needs none")
    void aDollarDrawerOpens() throws Exception {
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(10), new BigDecimal("48"), null));
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY, new BigDecimal("50"), null));

        Treasury drawer = treasury("USD", usd, "100", TODAY.minusDays(5));
        treasuries.insert(drawer);
        dollarDrawer = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-USD'");
        assertEquals("4800.00|100.000|48.0000000000",
                text("SELECT CONCAT(amount, '|', opening_foreign, '|', opening_rate) FROM treasury WHERE id = "
                        + dollarDrawer));

        // The riyal has no rate at all, and a drawer opened empty is not refused for want of one.
        treasuries.insert(treasury("SAR", sar, "0", TODAY));
        riyalDrawer = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-SAR'");
        assertEquals(1, scalar("SELECT COUNT(*) FROM treasury WHERE id = " + riyalDrawer
                + " AND amount = 0 AND opening_foreign = 0 AND opening_rate IS NULL"));

        // With an opening and no rate for its day, it is refused - never a guess.
        assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                () -> treasuries.insert(treasury("SAR2", sar, "10", TODAY))).getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("deposits and withdrawals are valued at their own day's rate, checked in dollars")
    void cashMovesInDollars() throws Exception {
        cash.record(new CashMovementCommand(dollarDrawer, CashDirection.DEPOSIT, CashCategory.NORMAL,
                new BigDecimal("50"), TODAY.minusDays(3), STAMP, "", OPERATOR));
        assertThrows(BusinessRuleException.class, () -> cash.record(new CashMovementCommand(dollarDrawer,
                CashDirection.WITHDRAWAL, CashCategory.NORMAL, new BigDecimal("500"), TODAY, STAMP, "", OPERATOR)),
                "500 dollars are not in a drawer holding 150, whatever its 7,200 of book value would say");
        cash.record(new CashMovementCommand(dollarDrawer, CashDirection.WITHDRAWAL, CashCategory.NORMAL,
                new BigDecimal("20"), TODAY, STAMP, "", OPERATOR));

        assertEquals("2400.00|50.000|48.0000000000,1000.00|20.000|50.0000000000", text(
                "SELECT GROUP_CONCAT(CONCAT(amount, '|', foreign_amount, '|', exchange_rate) ORDER BY id)"
                        + " FROM treasury_deposit_expenses WHERE treasury_id = " + dollarDrawer));
        TreasuryBalanceSummary drawer = balance(dollarDrawer);
        assertEquals(0, new BigDecimal("130").compareTo(drawer.balanceOwn()));
        assertEquals(0, new BigDecimal("6200").compareTo(drawer.balance()));
    }

    @Test
    @Order(5)
    @DisplayName("an exchange moves one base figure out of one treasury and into the other; the total holds")
    void exchanges() throws Exception {
        cash.record(new CashMovementCommand(MAIN, CashDirection.DEPOSIT, CashCategory.NORMAL,
                new BigDecimal("20000"), TODAY, STAMP, "", OPERATOR));
        BigDecimal before = decimal("SELECT SUM(balance) FROM treasury_current_balance");

        transfers.transfer(new TreasuryTransferCommand(MAIN, dollarDrawer, new BigDecimal("4900"), TODAY, STAMP,
                OPERATOR, BigDecimal.ZERO, new BigDecimal("100")));
        transfers.transfer(new TreasuryTransferCommand(dollarDrawer, MAIN, new BigDecimal("30"), TODAY, STAMP,
                OPERATOR, BigDecimal.ZERO, new BigDecimal("1560")));

        assertEquals(0, before.compareTo(decimal("SELECT SUM(balance) FROM treasury_current_balance")),
                "the difference between buying at 49 and selling at 52 is a valuation, never income (ق-ب٢)");
        TreasuryBalanceSummary drawer = balance(dollarDrawer);
        assertEquals(0, new BigDecimal("200").compareTo(drawer.balanceOwn()));
        assertEquals(0, new BigDecimal("9540").compareTo(drawer.balance()));
        assertEquals(0, new BigDecimal("16660").compareTo(balance(MAIN).balance()));

        assertEquals("4900.00|-|100.000|-|USD,1560.00|30.000|-|USD|-", text(
                "SELECT GROUP_CONCAT(CONCAT(amount, '|', COALESCE(amount_from, '-'), '|', COALESCE(amount_to, '-'),"
                        + " '|', COALESCE(currency_from, '-'), '|', COALESCE(currency_to, '-')) ORDER BY id)"
                        + " FROM treasury_transfers_and_names WHERE notes = '" + STAMP + "'"));

        // The statement's own-currency columns add up to the drawer's dollars, opening line included,
        // and in the base they are the base columns exactly.
        assertEquals(0, new BigDecimal("200").compareTo(decimal("SELECT SUM(income_own - output_own)"
                + " FROM treasury_balance WHERE treasury_id = " + dollarDrawer)));
        assertEquals(0, scalar("SELECT COUNT(*) FROM treasury_balance b JOIN treasury t ON t.id = b.treasury_id"
                + " WHERE t.currency_id IS NULL AND (b.income_own <> b.income OR b.output_own <> b.output)"));
    }

    @Test
    @Order(6)
    @DisplayName("from the dollar drawer: no fee, and nothing beyond the dollars it holds")
    void refusalsFromTheDrawer() {
        assertThrows(BusinessRuleException.class, () -> transfers.transfer(new TreasuryTransferCommand(dollarDrawer,
                MAIN, new BigDecimal("10"), TODAY, STAMP, OPERATOR, BigDecimal.ONE, new BigDecimal("500"))));
        assertThrows(BusinessRuleException.class, () -> transfers.transfer(new TreasuryTransferCommand(dollarDrawer,
                MAIN, new BigDecimal("250"), TODAY, STAMP, OPERATOR, BigDecimal.ZERO, new BigDecimal("12500"))));
        assertEquals("treasury.exchange.error.received", assertThrows(UserValidationException.class,
                () -> transfers.transfer(new TreasuryTransferCommand(MAIN, dollarDrawer, new BigDecimal("500"),
                        TODAY, STAMP, OPERATOR))).getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("dollars for riyals: valued at the dollar's rate, and deleting it puts both drawers back")
    void foreignToForeign() throws Exception {
        transfers.transfer(new TreasuryTransferCommand(dollarDrawer, riyalDrawer, new BigDecimal("10"), TODAY,
                STAMP + "-FF", OPERATOR, BigDecimal.ZERO, new BigDecimal("37.5")));
        TreasuryBalanceSummary riyals = balance(riyalDrawer);
        assertEquals(0, new BigDecimal("37.5").compareTo(riyals.balanceOwn()));
        assertEquals(0, new BigDecimal("500").compareTo(riyals.balance()));
        assertEquals(0, new BigDecimal("190").compareTo(balance(dollarDrawer).balanceOwn()));

        transfers.delete(scalar("SELECT id FROM treasury_transfers WHERE notes = '" + STAMP + "-FF'"), "test");
        assertEquals(0, balance(riyalDrawer).balanceOwn().signum());
        assertEquals(0, new BigDecimal("200").compareTo(balance(dollarDrawer).balanceOwn()));
        assertEquals(0, new BigDecimal("9540").compareTo(balance(dollarDrawer).balance()));
    }

    @Test
    @Order(8)
    @DisplayName("every writer phase B does not open refuses the drawer, and no picker offers it")
    void theOtherWritersRefuse() throws Exception {
        int heading = scalar("SELECT MIN(id) FROM expenses WHERE employee_payment = 0 AND is_active = 1"
                + " AND system_key IS NULL");
        BusinessRuleException expense = assertThrows(BusinessRuleException.class,
                () -> new ExpenseService(DaoFactory.INSTANCE).create(new ExpenseEntry(0, TODAY, heading,
                        dollarDrawer, new BigDecimal("10"), null, null, null)));
        assertEquals(TreasuryCurrencyGuard.refusal(STAMP + "-USD"), expense.getMessage());
        assertEquals(0, scalar("SELECT COUNT(*) FROM expenses_details WHERE treasury_id = " + dollarDrawer));

        assertThrows(BusinessRuleException.class, () -> TreasuryCurrencyGuard.jdbc().requireBaseCurrency(dollarDrawer));
        assertDoesNotThrow(() -> TreasuryCurrencyGuard.jdbc().requireBaseCurrency(MAIN));

        assertTrue(treasuries.getActiveBaseCurrencyTreasuries().stream().noneMatch(t -> t.getId() == dollarDrawer));
        assertTrue(treasuries.getActiveTreasuryModelList().stream().anyMatch(t -> t.getId() == dollarDrawer));
        assertTrue(new JdbcShiftPolicyRepository().loadTreasuries().stream()
                .noneMatch(policy -> policy.treasuryId() == dollarDrawer));
        ShiftPolicyService shifts = new ShiftPolicyService(new JdbcShiftPolicyRepository(), null, null,
                TreasuryCurrencyGuard.jdbc());
        assertThrows(BusinessRuleException.class, () -> shifts.saveTreasury(
                new TreasuryShiftPolicy(dollarDrawer, "drawer", ShiftTrackingMode.TRACK_ONLY)));
    }

    @Test
    @Order(9)
    @DisplayName("the currency is fixed by the first movement, and an unchanged opening keeps its stored value")
    void theCurrencyIsFixed() throws Exception {
        // A rate recorded later for the opening day must not re-rate the opening on an ordinary edit.
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(5), new BigDecimal("49"), null));
        Treasury drawer = treasuries.getTreasuryById(dollarDrawer);
        drawer.setName(STAMP + "-USD2");
        treasuries.update(drawer);
        assertEquals("4800.00|48.0000000000", text(
                "SELECT CONCAT(amount, '|', opening_rate) FROM treasury WHERE id = " + dollarDrawer));

        Treasury moved = treasuries.getTreasuryById(dollarDrawer);
        moved.setCurrencyId(sar);
        assertEquals("treasury.currency.error.changed", assertThrows(UserValidationException.class,
                () -> treasuries.update(moved)).getMessage());
        // The riyal drawer has had no movement since its transfer was deleted: its currency may still change.
        Treasury riyals = treasuries.getTreasuryById(riyalDrawer);
        riyals.setCurrencyId(null);
        riyals.setAmount(BigDecimal.ZERO);
        treasuries.update(riyals);
        assertNull(treasuries.getTreasuryById(riyalDrawer).getCurrencyId(), "back in the base, stored as NULL");
    }

    @Test
    @Order(10)
    @DisplayName("the currency side: not stopped, not deleted, the base not moved while a drawer holds it")
    void theCurrencyIsHeld() throws Exception {
        assertEquals("currency.error.stop.treasury", assertThrows(UserValidationException.class,
                () -> currencies.save(CurrencyDraft.switching(currencies.find(usd), false))).getMessage());
        assertThrows(Exception.class, () -> currencies.delete(usd));
        assertEquals(false, currencies.baseMayChange());
        assertEquals(1, scalar("SELECT COUNT(*) FROM currency WHERE id = " + usd + " AND is_active = 1"));
    }

    @Test
    @Order(11)
    @DisplayName("the minimum is in the drawer's own currency")
    void theMinimumIsInDollars() throws Exception {
        execute("UPDATE treasury SET min_balance = 250 WHERE id = " + dollarDrawer);
        var low = DaoFactory.INSTANCE.treasuryCurrentBalanceDao().belowMinimum();
        var drawer = low.stream().filter(row -> row.treasuryId() == dollarDrawer).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("200").compareTo(drawer.balance()), "200 dollars, not 9,540 pounds");
        execute("UPDATE treasury SET min_balance = 150 WHERE id = " + dollarDrawer);
        assertTrue(DaoFactory.INSTANCE.treasuryCurrentBalanceDao().belowMinimum().stream()
                .noneMatch(row -> row.treasuryId() == dollarDrawer));
    }

    @Test
    @Order(12)
    @DisplayName("the drawer's statement is in dollars with its book value beside it; every treasury at once stays in the base")
    void theStatementIsInDollars() throws Exception {
        TreasuryStatementService statements = new TreasuryStatementService();
        TreasuryStatementFilter drawerOnly = new TreasuryStatementFilter(TODAY.minusDays(4), TODAY, dollarDrawer,
                null, null, 0, 50);

        TreasuryStatementPage page = statements.search(drawerOnly);

        assertEquals("USD", page.currency().code());
        // Brought forward: the opening of 100 dollars five days ago. In: 50 deposited and 100 bought.
        // Out: 20 withdrawn and 30 sold. The exchange into riyals was deleted and is not there.
        assertSummary("100|150|50|200", page.summary());
        assertSummary("4800|7300|2560|9540", page.totals().base());
        assertEquals(4, page.rows().size());
        TreasuryStatementRow newest = page.rows().get(0);
        TreasuryStatementRow oldest = page.rows().get(page.rows().size() - 1);
        assertEquals(TreasuryMovementKind.DEPOSIT, oldest.kind());
        assertEquals(0, new BigDecimal("150").compareTo(page.currency().runningBalance(oldest)));
        assertEquals(0, new BigDecimal("7200").compareTo(oldest.runningBalance()));
        assertEquals(0, new BigDecimal("200").compareTo(page.currency().runningBalance(newest)),
                "the last running balance is the drawer's balance in dollars");
        assertEquals(0, balance(dollarDrawer).balanceOwn().compareTo(page.currency().runningBalance(newest)));
        assertEquals(0, new BigDecimal("9540").compareTo(newest.runningBalance()));
        BigDecimal net = page.rows().stream()
                .map(row -> page.currency().income(row).subtract(page.currency().output(row)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100").compareTo(net), "the rows add up to what the cards say moved");

        // A kind narrows the rows and the period's totals; the two balances still answer the dates alone.
        TreasuryStatementPage deposits = statements.search(new TreasuryStatementFilter(TODAY.minusDays(4), TODAY,
                dollarDrawer, TreasuryMovementKind.DEPOSIT, null, 0, 50));
        assertEquals(1, deposits.rows().size());
        assertSummary("100|50|0|200", deposits.summary());

        // The paper is written in the same currency as the screen.
        TreasuryStatementPrintData paper = statements.forPrint(drawerOnly);
        assertEquals("USD", paper.currency().code());
        assertSummary("100|150|50|200", paper.summary());

        // The main treasury and every treasury at once are in the base, and read the books.
        assertFalse(statements.search(new TreasuryStatementFilter(TODAY.minusDays(4), TODAY, MAIN,
                null, null, 0, 50)).currency().isForeign());
        TreasuryStatementPage everything = statements.search(new TreasuryStatementFilter(TODAY.minusDays(4), TODAY,
                null, null, null, 0, 50));
        assertFalse(everything.currency().isForeign(), "dollars and pounds are not added together");
        assertEquals(0, decimal("SELECT SUM(balance) FROM treasury_current_balance")
                .compareTo(everything.summary().closingBalance()));
    }

    private static void assertSummary(String expected, TreasuryStatementSummary summary) {
        assertEquals(expected, Stream.of(summary.openingBalance(), summary.totalIncome(), summary.totalOutput(),
                        summary.closingBalance())
                .map(value -> value.stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("|")));
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private static Treasury treasury(String suffix, int currencyId, String opening, LocalDate day) {
        Treasury treasury = new Treasury();
        treasury.setName(STAMP + "-" + suffix);
        treasury.setType(TreasuryType.CASH);
        treasury.setActive(true);
        treasury.setCurrencyId(currencyId);
        treasury.setOpeningForeign(new BigDecimal(opening));
        treasury.setOpeningDate(day);
        treasury.setUserId(OPERATOR);
        return treasury;
    }

    private static TreasuryBalanceSummary balance(int treasuryId) throws Exception {
        return DaoFactory.INSTANCE.treasuryCurrentBalanceDao().getDataById(treasuryId);
    }

    private static void assertSqlRefused(int errorCode, String sql) {
        SQLException refused = assertThrows(SQLException.class, () -> execute(sql), sql);
        assertEquals(errorCode, refused.getErrorCode(), sql + " -> " + refused.getMessage());
    }

    private static int scalar(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getInt(1);
        }
    }

    private static BigDecimal decimal(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getBigDecimal(1);
        }
    }

    private static String text(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
        }
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL";
    }

    private static File configSource() {
        String named = System.getenv("ACCOUNT_DB_ACCEPTANCE_CONFIG");
        if (named != null && !named.isBlank()) {
            File explicit = new File(named);
            assertTrue(explicit.isFile(), "ACCOUNT_DB_ACCEPTANCE_CONFIG names no file: " + named);
            return explicit;
        }
        File beside = new File("config.xml");
        return beside.isFile() ? beside : new File("../config.xml");
    }

    private static String environmentOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
