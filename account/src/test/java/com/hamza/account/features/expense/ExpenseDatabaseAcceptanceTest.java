package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.employee.EmployeeCashPurpose;
import com.hamza.account.features.expense.budget.ExpenseBudgetDraft;
import com.hamza.account.features.expense.budget.ExpenseBudgetReport;
import com.hamza.account.features.expense.budget.ExpenseBudgetService;
import com.hamza.account.features.expense.recurring.ExpenseFrequency;
import com.hamza.account.features.expense.recurring.ExpenseRecurringDraft;
import com.hamza.account.features.expense.recurring.ExpenseRecurringDue;
import com.hamza.account.features.expense.recurring.ExpenseRecurringService;
import com.hamza.account.features.expense.report.ExpenseByHeadingReport;
import com.hamza.account.features.expense.report.ExpenseDimension;
import com.hamza.account.features.expense.report.ExpenseHeadingLineKind;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.features.expense.report.ExpenseSalesRatio;
import com.hamza.account.features.expense.report.ExpenseTrend;
import com.hamza.account.features.expense.report.ExpenseYearMatrix;
import com.hamza.account.features.profitloss.ProfitLossDao;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.employee.EmployeePayment;
import com.hamza.account.features.employee.EmployeePaymentService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.features.treasury.JdbcWalletFeeRepository;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.Users;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.account.features.treasury.WalletFeeService;
import com.hamza.account.features.treasury.WalletFeeSource;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-MySQL acceptance for the expenses rework: V64 on a schema built from nothing, V64 over a V63
 * database that already holds expenses, and {@link ExpenseService} against both halves of a transaction.
 * <p>
 * <b>Two scratch schemas and nothing else.</b> Neither is the configured business database: each is
 * created with a unique name and dropped in {@link #dropScratchSchemas()}. If the configured account
 * cannot create databases, supply {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_USER} and
 * {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD} - the shape {@code AuditLogDatabaseAcceptanceTest} set.
 * <p>
 * <b>Why the upgrade path is migrated from a folder.</b> V64 cannot be left out of a classpath run, and
 * {@code Flyway.target} does not hold the repeatables back - they run whatever the target, and this
 * change's {@code R__triggers.sql} names columns V64 adds. So the V63 schema is migrated from a copy of
 * the migrations without V64, with the triggers file cut where the V64 section begins, then migrated
 * again from the classpath - which applies V64 and re-runs only the repeatable whose checksum moved.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} bypasses every permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ExpenseDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_expense_acceptance_";
    private static final String TRIGGERS_V64_MARKER = "-- expenses_details and expenses (V64)";
    private static final int OPERATOR = 4242;
    private static final String STAMP = "EXP-" + System.nanoTime();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String freshSchema;
    private static String upgradedSchema;

    /** Read from the V63 schema just before V64, and again just after. */
    private static final List<String> FIGURES_BEFORE = new ArrayList<>();
    private static final List<String> FIGURES_AFTER = new ArrayList<>();

    @BeforeAll
    static void migrateScratchSchemas() throws Exception {
        File configFile = configSource();
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER", config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD", config.get(CryptoDatabaseConfig.PASSWORD));
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        freshSchema = SCHEMA_PREFIX + "fresh_" + unique;
        upgradedSchema = SCHEMA_PREFIX + "upgrade_" + unique;

        try {
            createDatabase(freshSchema);
            migrate(freshSchema, "classpath:db/migration");

            createDatabase(upgradedSchema);
            Path before = migrationsWithoutV64();
            migrate(upgradedSchema, "filesystem:" + before.toAbsolutePath().toString().replace('\\', '/'));
            try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
                seedTheYearsBeforeV64(connection);
                readFigures(connection, FIGURES_BEFORE);
            }
            migrate(upgradedSchema, "classpath:db/migration");
            try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
                readFigures(connection, FIGURES_AFTER);
            }

            DataSourceProvider.initialize(host, port, freshSchema, username, password);
            seedOperator();
        } catch (Exception failure) {
            try {
                dropScratchSchemas();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterAll
    static void dropScratchSchemas() throws Exception {
        DataSourceProvider.shutdown();
        for (String schema : new String[]{freshSchema, upgradedSchema}) {
            if (schema == null || !schema.startsWith(SCHEMA_PREFIX)) continue;
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    // ---- the migration ------------------------------------------------------------------

    @Test
    @DisplayName("from nothing: the heading is numbered by the database, the fee has its key, salaries are employees'")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'expenses' AND column_name = 'id' AND extra LIKE '%auto_increment%'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.key_column_usage"
                + " WHERE table_schema = DATABASE() AND table_name = 'expenses_details'"
                + " AND column_name = 'type_code' AND referenced_table_name = 'expenses'"),
                "the key onto the heading was taken off to renumber the column, and has to be back");
        // By key and not by name: walletFeeSurvivesARename renames this very row, and the order tests run
        // in is not this file's to decide. The name is checked on the upgraded schema, which nothing renames.
        assertEquals(1, scalar("SELECT COUNT(*) FROM expenses WHERE system_key = 'WALLET_FEE'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM expenses WHERE employee_payment = 1"
                + " AND expenses_name IN ('مرتبات', 'سلف')"));
        assertEquals(4, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key IN"
                + " ('expenses.show', 'expenses.headings.update', 'expenses.export', 'expenses.reports')"));
        assertEquals(0, scalar("""
                SELECT COUNT(*) FROM auth_role_permission shown
                    JOIN auth_permission show_key ON show_key.id = shown.permission_id
                                                 AND show_key.permission_key = 'expenses.show'
                WHERE NOT EXISTS (SELECT 1 FROM auth_role_permission reported
                                      JOIN auth_permission report_key ON report_key.id = reported.permission_id
                                                                     AND report_key.permission_key = 'expenses.reports'
                                  WHERE reported.role_id = shown.role_id)"""),
                "V65: whoever may see the list may see its reports after the upgrade");
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE()"
                + " AND routine_name LIKE '%expense%'"), "V64 leaves none of its helpers behind");
    }

    @Test
    @DisplayName("over data: no figure moves - not a till's balance, not a month's expenses, not an employee's account")
    void noFigureMoves() {
        assertTrue(!FIGURES_BEFORE.isEmpty(), "the V63 schema was read");
        assertEquals(FIGURES_BEFORE, FIGURES_AFTER);
    }

    @Test
    @DisplayName("over data: the fee keeps its row and gains its key; a heading all employees becomes theirs, a mixed one does not")
    void upgradeFlagsHeadings() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
            assertEquals("عمولات تحويل", string(connection,
                    "SELECT expenses_name FROM expenses WHERE system_key = 'WALLET_FEE'"));
            assertEquals(1, scalar(connection, "SELECT employee_payment FROM expenses WHERE expenses_name = 'مرتبات'"));
            assertEquals(1, scalar(connection, "SELECT employee_payment FROM expenses WHERE expenses_name = '"
                    + STAMP + " بدل'"), "every row on it names an employee");
            assertEquals(0, scalar(connection, "SELECT employee_payment FROM expenses WHERE expenses_name = 'أخرى'"),
                    "one stray employee row does not take a heading off the expenses screen");
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM employee_cash_purpose"),
                    "decision م-٣: no old advance is re-labelled by the migration");
        }
    }

    // ---- the service, against a real transaction ------------------------------------------

    @Test
    @DisplayName("an expense is written with its payee, audited on insert and update, and moves the till by its amount")
    void writeAuditAndBalance() throws Exception {
        signIn(AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_CREATE, AppPermissions.EXPENSES_UPDATE);
        ExpenseService service = new ExpenseService(DaoFactory.INSTANCE);
        int heading = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        int till = scalar("SELECT MIN(id) FROM treasury");
        BigDecimal before = decimal("SELECT balance FROM treasury_current_balance WHERE id = " + till);

        int id = service.create(ExpenseEntry.parse(0, LocalDate.now(), heading, till, new BigDecimal("125.50"),
                "شركة الكهرباء", "R-77", STAMP + " write"));
        ExpenseRow stored = service.find(id);
        assertEquals("شركة الكهرباء", stored.payee());
        assertEquals("R-77", stored.referenceNo());
        assertEquals(OPERATOR, stored.userId());
        assertEquals(before.subtract(new BigDecimal("125.50")),
                decimal("SELECT balance FROM treasury_current_balance WHERE id = " + till));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'expenses_details'"
                + " AND record_id = '" + id + "' AND action_type = 'INSERT'"));

        service.update(ExpenseEntry.parse(id, LocalDate.now(), heading, till, new BigDecimal("100.00"),
                "شركة الكهرباء", "R-77", STAMP + " write"), "typo");
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'expenses_details'"
                + " AND record_id = '" + id + "' AND action_type = 'UPDATE'"));
        assertEquals(before.subtract(new BigDecimal("100.00")),
                decimal("SELECT balance FROM treasury_current_balance WHERE id = " + till));
    }

    @Test
    @DisplayName("a batch refused on its third line leaves the first two unwritten")
    void batchIsAllOrNothing() throws Exception {
        signIn(AppPermissions.EXPENSES_CREATE);
        ExpenseService service = new ExpenseService(DaoFactory.INSTANCE);
        int power = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        int salaries = scalar("SELECT id FROM expenses WHERE expenses_name = 'مرتبات'");
        int till = scalar("SELECT MIN(id) FROM treasury");
        String note = STAMP + " batch";

        ExpenseBatchLineRefused refused = assertThrows(ExpenseBatchLineRefused.class, () -> service.createBatch(List.of(
                ExpenseEntry.parse(0, LocalDate.now(), power, till, BigDecimal.TEN, null, null, note),
                ExpenseEntry.parse(0, LocalDate.now(), power, till, BigDecimal.ONE, null, null, note),
                ExpenseEntry.parse(0, LocalDate.now(), salaries, till, BigDecimal.ONE, null, null, note))));
        assertEquals(3, refused.line());
        assertEquals(0, scalar("SELECT COUNT(*) FROM expenses_details WHERE notes = '" + note + "'"),
                "the transaction took back the two lines written before the refusal");
    }

    @Test
    @DisplayName("renaming the fee's heading does not lose it: a wallet fee still finds it by key")
    void walletFeeSurvivesARename() throws Exception {
        signIn(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        ExpenseHeadingService headings = new ExpenseHeadingService();
        ExpenseHeading fee = headings.all().stream().filter(ExpenseHeading::isSystem).findFirst().orElseThrow();
        headings.save(new ExpenseHeadingDraft(fee.id(), STAMP + " عمولة", null, true, false));

        int till = scalar("SELECT MIN(id) FROM treasury");
        assertEquals(1, new WalletFeeService().post(
                WalletFeeSource.party(PartyKind.CUSTOMER, 2_000_000_000), till, LocalDate.now(), new BigDecimal("1000"),
                new BigDecimal("15"), STAMP + " fee", OptionalInt.empty()));
        assertEquals(fee.id(), scalar("SELECT type_code FROM expenses_details WHERE notes = '" + STAMP + " fee'"));
    }

    @Test
    @DisplayName("V67: deleting a wallet collection takes its fee, so entering it again charges it once")
    void aDeletedCollectionTakesItsFee() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_CREATE, AppPermissions.CUSTOMER_ACCOUNT_DELETE);
        int wallet = walletAtOnePercent();
        int customer = scalar("SELECT MIN(id) FROM custom");
        AccountCustomerService collections = new AccountCustomerService(DaoFactory.INSTANCE);
        String feesOnWallet = "SELECT COUNT(*) FROM expenses_details WHERE treasury_id = " + wallet
                + " AND fee_source_type IS NOT NULL";

        CustomerAccount first = collection(customer, wallet);
        assertEquals(1, collections.save(first, new BigDecimal("10")));
        assertEquals(1, scalar(feesOnWallet + " AND fee_source_type = 5 AND fee_source_id = " + first.getId()),
                "the fee names the collection it was paid for");

        assertEquals(1, collections.delete(first.getId()));
        assertEquals(0, scalar(feesOnWallet), "the fee outlived the collection it was paid for");

        // The correction docs/treasury-plan.md prescribes: enter it again. One fee, not two.
        CustomerAccount again = collection(customer, wallet);
        collections.save(again, new BigDecimal("10"));
        assertEquals(1, scalar(feesOnWallet));
        assertEquals(0, new BigDecimal("990.00").compareTo(decimal(
                "SELECT balance FROM treasury_current_balance WHERE id = " + wallet)),
                "1000 collected, 10 kept by the wallet");

        collections.delete(again.getId());
        assertEquals(0, scalar(feesOnWallet));
    }

    @Test
    @DisplayName("V67: a document has one fee row, rewritten by an edit and removed with the document")
    void aDocumentsFeeFollowsTheDocument() throws Exception {
        signIn();
        int wallet = walletAtOnePercent();
        WalletFeeService fees = new WalletFeeService();
        WalletFeeSource sale = WalletFeeSource.document(DocumentType.SALES, 1_900_000_001);
        String feeOfSale = "SELECT %s FROM expenses_details WHERE fee_source_type = 3 AND fee_source_id = 1900000001";
        BigDecimal one = new BigDecimal("1.00");

        fees.syncDocument(sale, wallet, LocalDate.now(), new BigDecimal("1000"), null, one,
                OptionalInt.empty(), null);
        assertEquals(0, new BigDecimal("10.00").compareTo(decimal(feeOfSale.formatted("amount"))));

        fees.syncDocument(sale, wallet, LocalDate.now(), new BigDecimal("1500"),
                new WalletFeeService.PreviousCash(new BigDecimal("1000"), wallet), one, OptionalInt.empty(), null);
        assertEquals(1, scalar(feeOfSale.formatted("COUNT(*)")), "an edit wrote a second fee");
        assertEquals(0, new BigDecimal("15.00").compareTo(decimal(feeOfSale.formatted("amount"))));

        // The unique index is the last line: a second insert for one document is refused by MySQL.
        assertThrows(DaoException.class, () -> new JdbcWalletFeeRepository().insert(sale,
                scalar("SELECT id FROM expenses WHERE system_key = 'WALLET_FEE'"), LocalDate.now(),
                BigDecimal.ONE, null, wallet, OPERATOR, null));

        assertEquals(1, fees.removeFor(sale, null));
        assertEquals(0, scalar(feeOfSale.formatted("COUNT(*)")));
    }

    @Test
    @DisplayName("V68: a transfer's fee is an expense on the sender, and goes when the transfer does")
    void aTransferPaysItsFeeFromTheSender() throws Exception {
        signIn(AppPermissions.TREASURY_TRANSFER);
        int wallet = treasuryHolding("WALLET", "1000");
        int drawer = treasuryHolding("CASH", "0");
        TreasuryTransferService transfers = new TreasuryTransferService(DaoFactory.INSTANCE);
        String balanceOf = "SELECT balance FROM treasury_current_balance WHERE id = ";

        // 1000 on hand cannot send 1000 and pay 5 for sending it.
        assertThrows(BusinessRuleException.class, () -> transfers.transfer(new TreasuryTransferCommand(
                wallet, drawer, new BigDecimal("1000"), LocalDate.now(), STAMP, OPERATOR, new BigDecimal("5"))));

        assertEquals(1, transfers.transfer(new TreasuryTransferCommand(
                wallet, drawer, new BigDecimal("200"), LocalDate.now(), STAMP, OPERATOR, new BigDecimal("5"))));
        int transfer = scalar("SELECT MAX(id) FROM treasury_transfers WHERE treasury_from = " + wallet);
        assertEquals(0, new BigDecimal("795.00").compareTo(decimal(balanceOf + wallet)), "200 sent and 5 charged");
        assertEquals(0, new BigDecimal("200.00").compareTo(decimal(balanceOf + drawer)), "the whole amount arrives");
        assertEquals(1, scalar("SELECT COUNT(*) FROM expenses_details WHERE fee_source_type = 11"
                + " AND fee_source_id = " + transfer + " AND treasury_id = " + wallet + " AND amount = 5"));
        assertEquals(0, new BigDecimal("5.00").compareTo(transfers.recent(5).stream()
                .filter(row -> row.id() == transfer).findFirst().orElseThrow().fee()), "the list shows what it cost");

        // The slip reads the stored row again: the fee, and the name of whoever entered it.
        var slip = transfers.forVoucher(transfer);
        assertEquals(0, new BigDecimal("5.00").compareTo(slip.transfer().fee()));
        assertEquals(0, new BigDecimal("200.00").compareTo(slip.transfer().amount()));
        assertEquals(STAMP, slip.enteredBy());

        assertEquals(1, transfers.delete(transfer));
        assertThrows(BusinessRuleException.class, () -> transfers.forVoucher(transfer), "a paper for a deleted transfer");
        assertEquals(0, new BigDecimal("1000.00").compareTo(decimal(balanceOf + wallet)), "the fee came back too");
        assertEquals(0, scalar("SELECT COUNT(*) FROM expenses_details WHERE fee_source_type = 11"
                + " AND fee_source_id = " + transfer));
    }

    @Test
    @DisplayName("the history lists: a period, a treasury at either end, a page, and totals of the whole set")
    void historyListsAreFilteredPagedAndTotalledInSql() throws Exception {
        signIn(AppPermissions.TREASURY_TRANSFER, AppPermissions.TREASURY_DEPOSIT);
        int wallet = treasuryHolding("WALLET", "1000");
        int drawer = treasuryHolding("CASH", "1000");
        int other = treasuryHolding("BANK", "1000");
        LocalDate today = LocalDate.now();
        TreasuryTransferService transfers = new TreasuryTransferService(DaoFactory.INSTANCE);
        TreasuryCashService cash = new TreasuryCashService(DaoFactory.INSTANCE);

        transfers.transfer(new TreasuryTransferCommand(wallet, drawer, new BigDecimal("100"), today, STAMP,
                OPERATOR, new BigDecimal("2")));
        transfers.transfer(new TreasuryTransferCommand(drawer, wallet, new BigDecimal("30"), today, STAMP, OPERATOR));
        transfers.transfer(new TreasuryTransferCommand(drawer, other, new BigDecimal("7"), today, STAMP, OPERATOR));
        // Outside the period, so neither the rows nor the totals may carry it.
        transfers.transfer(new TreasuryTransferCommand(wallet, drawer, new BigDecimal("500"),
                today.minusDays(40), STAMP, OPERATOR));

        // The wallet is an end of two of today's three, whichever end - and of none of the third.
        var ofWallet = transfers.history(new TreasuryHistoryFilter(today, today, wallet, null, 0, 50));
        assertEquals(2, ofWallet.rows().size());
        assertEquals(2, ofWallet.totals().count());
        assertEquals(0, new BigDecimal("130.00").compareTo(ofWallet.totals().first()));
        assertEquals(0, new BigDecimal("2.00").compareTo(ofWallet.totals().second()), "one of the two cost a fee");

        // One row a page: the totals still describe the whole set, and the extra row says there is more.
        var firstPage = transfers.history(new TreasuryHistoryFilter(today, today, wallet, null, 0, 1));
        assertEquals(1, firstPage.rows().size());
        assertTrue(firstPage.hasNext());
        assertEquals(2, firstPage.totals().count());
        var secondPage = transfers.history(new TreasuryHistoryFilter(today, today, wallet, null, 1, 1));
        assertEquals(1, secondPage.rows().size());
        assertTrue(!secondPage.hasNext() && secondPage.hasPrevious());
        assertTrue(firstPage.rows().get(0).id() != secondPage.rows().get(0).id(), "the two pages are one list");

        cash.record(new CashMovementCommand(other, CashDirection.DEPOSIT, CashCategory.NORMAL, new BigDecimal("40"),
                today, STAMP, "", OPERATOR));
        cash.record(new CashMovementCommand(other, CashDirection.WITHDRAWAL, CashCategory.NORMAL,
                new BigDecimal("15"), today, STAMP, "", OPERATOR));
        var receipt = cash.forVoucher(scalar("SELECT MIN(id) FROM treasury_deposit_expenses WHERE treasury_id = " + other));
        assertEquals(CashDirection.DEPOSIT, receipt.movement().direction());
        assertEquals(0, new BigDecimal("40.00").compareTo(receipt.movement().amount()));
        assertEquals(STAMP, receipt.enteredBy());

        var both = cash.history(new TreasuryHistoryFilter(today, today, other, null, 0, 50));
        assertEquals(2, both.rows().size());
        assertEquals(0, new BigDecimal("40.00").compareTo(both.totals().first()), "deposited");
        assertEquals(0, new BigDecimal("15.00").compareTo(both.totals().second()), "withdrawn");
        var withdrawals = cash.history(new TreasuryHistoryFilter(today, today, other, CashDirection.WITHDRAWAL, 0, 50));
        assertEquals(1, withdrawals.rows().size());
        assertEquals(0, withdrawals.totals().first().signum(), "a filter on withdrawals totals no deposit");
        assertEquals(2, cash.forPrint(new TreasuryHistoryFilter(today, today, other, null, 3, 1)).rows().size(),
                "printing reads the whole set, whatever page is on screen");
    }

    @Test
    @DisplayName("V67 over a database with expenses in it: every old row is unlinked, and none was lost")
    void theLinkArrivesEmptyOnAnUpgrade() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
            assertTrue(scalar(connection, "SELECT COUNT(*) FROM expenses_details") > 0, "the upgrade fixture is empty");
            assertEquals(0, scalar(connection,
                    "SELECT COUNT(*) FROM expenses_details WHERE fee_source_type IS NOT NULL OR fee_source_id IS NOT NULL"));
        }
    }

    @Test
    @DisplayName("a heading holding an expense is refused a delete; a third level is refused a save")
    void headingRulesAgainstTheSchema() throws Exception {
        signIn(AppPermissions.EXPENSES_HEADINGS_UPDATE, AppPermissions.EXPENSES_CREATE);
        ExpenseHeadingService headings = new ExpenseHeadingService();
        int main = headings.save(new ExpenseHeadingDraft(0, STAMP + " إدارية", null, true, false));
        int sub = headings.save(new ExpenseHeadingDraft(0, STAMP + " نظافة", main, true, false));
        assertThrows(UserValidationException.class,
                () -> headings.save(new ExpenseHeadingDraft(0, STAMP + " عمق", sub, true, false)));

        new ExpenseService(DaoFactory.INSTANCE).create(ExpenseEntry.parse(0, LocalDate.now(), sub,
                scalar("SELECT MIN(id) FROM treasury"), BigDecimal.ONE, null, null, STAMP + " held"));
        assertThrows(DaoException.class, () -> headings.delete(sub), "an expense holds it");
        assertThrows(DaoException.class, () -> headings.delete(main), "a sub-heading holds it");
    }

    @Test
    @DisplayName("an employee is paid under an employee heading, and refused under any other")
    void employeePayments() throws Exception {
        signIn(AppPermissions.EMPLOYEE_PAY, AppPermissions.EXPENSES_CREATE);
        int employee = scalar("SELECT MIN(id) FROM employees");
        int till = scalar("SELECT MIN(id) FROM treasury");
        EmployeePaymentService payments = new EmployeePaymentService(new ExpenseService(DaoFactory.INSTANCE));

        int salaries = scalar("SELECT id FROM expenses WHERE expenses_name = 'مرتبات'");
        int expenseId = payments.pay(EmployeePayment.parse(employee, LocalDate.now(), new BigDecimal("300"),
                EmployeeCashPurpose.ADVANCE, till, salaries, STAMP + " advance"));
        assertEquals(employee, scalar("SELECT emp_id FROM expenses_details WHERE id = " + expenseId));
        assertNotNull(string("SELECT purpose FROM employee_cash_purpose WHERE expense_id = " + expenseId));

        int power = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        assertThrows(UserValidationException.class, () -> payments.pay(EmployeePayment.parse(employee,
                LocalDate.now(), BigDecimal.TEN, EmployeeCashPurpose.SALARY, till, power, STAMP + " wrong")));
    }

    // ---- the reports (phase B) -------------------------------------------------------------

    /**
     * The invariant docs/expenses-plan.md §4 names, on MySQL rather than trusted: the report by heading's
     * total for a period, the expenses column of the profit and loss for that period, and the list filtered
     * by that period alone are one figure - and so is every other report's expense total over it. And the
     * ratio's net sales are the profit and loss's net sales.
     * <p>
     * March 2003, which nothing else in this class writes: the other cases date their expenses today.
     */
    @Test
    @DisplayName("reports: by heading = profit and loss = the list, for one period; net sales agree too")
    void reportsAgreeWithProfitAndLossAndTheList() throws Exception {
        signIn(AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_CREATE, AppPermissions.EXPENSES_HEADINGS_UPDATE,
                AppPermissions.EXPENSES_REPORTS);
        LocalDate from = LocalDate.of(2003, 3, 1);
        LocalDate to = LocalDate.of(2003, 3, 31);
        int till = scalar("SELECT MIN(id) FROM treasury");
        ExpenseHeadingService headings = new ExpenseHeadingService();
        int main = headings.save(new ExpenseHeadingDraft(0, STAMP + " تشغيل", null, true, false));
        int sub = headings.save(new ExpenseHeadingDraft(0, STAMP + " صيانة", main, true, false));
        int power = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        ExpenseService expenses = new ExpenseService(DaoFactory.INSTANCE);
        expenses.create(ExpenseEntry.parse(0, LocalDate.of(2003, 3, 2), main, till, new BigDecimal("40.25"),
                null, null, STAMP + " report"));
        expenses.create(ExpenseEntry.parse(0, LocalDate.of(2003, 3, 15), sub, till, new BigDecimal("300.00"),
                "ورشة", null, STAMP + " report"));
        expenses.create(ExpenseEntry.parse(0, LocalDate.of(2003, 3, 31), power, till, new BigDecimal("159.75"),
                null, null, STAMP + " report"));
        expenses.create(ExpenseEntry.parse(0, LocalDate.of(2003, 4, 1), power, till, new BigDecimal("999.00"),
                null, null, STAMP + " outside"));
        insertSaleAndReturn(till, LocalDate.of(2003, 3, 10));

        ExpenseFilter period = ExpenseFilter.between(from, to);
        ExpenseReportService reports = new ExpenseReportService();
        BigDecimal listed = expenses.search(period).summary().total();
        BigDecimal profitLossExpenses = BigDecimal.ZERO;
        BigDecimal profitLossSales = BigDecimal.ZERO;
        for (ProfitLossRow row : new ProfitLossDao().load(from, to)) {
            profitLossExpenses = profitLossExpenses.add(row.expenses());
            profitLossSales = profitLossSales.add(row.netSales());
        }

        assertEquals(0, new BigDecimal("500.00").compareTo(listed), "the fixture's own three expenses");
        assertEquals(0, listed.compareTo(profitLossExpenses), "the list and the profit and loss");
        ExpenseByHeadingReport byHeading = reports.byHeading(period);
        assertEquals(0, listed.compareTo(byHeading.total()), "the report by heading");
        ExpenseByHeadingReport.Line mainLine = byHeading.lines().stream()
                .filter(line -> line.headingId() == main && line.kind() == ExpenseHeadingLineKind.MAIN)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("340.25").compareTo(mainLine.total()), "a main heading holds its child");
        assertEquals(0, reports.byHeading(period.withHeading(main)).total()
                .compareTo(expenses.search(period.withHeading(main)).summary().total()), "narrowed alike");

        ExpenseYearMatrix year = reports.yearMatrix(period, 2003);
        assertEquals(0, listed.compareTo(year.totals().month(3)), "the year's March");
        assertEquals(0, new BigDecimal("999.00").compareTo(year.totals().month(4)));
        assertEquals(0, listed.compareTo(reports.trend(new ExpenseTrend.Filter(period,
                com.hamza.account.features.party.trend.TrendGranularity.WEEK, true)).total()), "the trend");
        for (ExpenseDimension dimension : ExpenseDimension.values()) {
            assertEquals(0, listed.compareTo(reports.byDimension(period, dimension).total()), dimension.name());
        }

        ExpenseSalesRatio ratio = reports.salesRatio(period);
        assertEquals(0, listed.compareTo(ratio.expenses()), "the ratio's expenses");
        assertEquals(0, new BigDecimal("700.00").compareTo(profitLossSales), "1000 - 100 sold, 200 returned");
        assertEquals(0, profitLossSales.compareTo(ratio.netSales()), "the ratio's sales are document_profit's");
        assertEquals(0, new BigDecimal("71.4").compareTo(ratio.ratio().orElseThrow()));
    }

    /** A cash sale of 1000 less 100 discount, and a return of 200, on one day - headers only. */
    private static void insertSaleAndReturn(int till, LocalDate day) throws Exception {
        try (Connection connection = ConnectionManager.acquire(); Statement statement = connection.createStatement()) {
            int customer = scalar(connection, "SELECT MIN(id) FROM custom");
            int stock = scalar(connection, "SELECT MIN(stock_id) FROM stocks");
            // Not nullable on either header, and a real key: the first run of this case failed here.
            int delegate = scalar(connection, "SELECT MIN(id) FROM employees");
            DocumentTableSpec sales = DocumentTableSpec.of(DocumentType.SALES);
            DocumentTableSpec returns = DocumentTableSpec.of(DocumentType.SALES_RETURN);
            statement.executeUpdate("INSERT INTO " + sales.table() + " (" + sales.key() + ", " + sales.party()
                    + ", invoice_type, invoice_date, total, discount, " + sales.paid()
                    + ", stock_id, delegate_id, treasury_id, notes, user_id) SELECT COALESCE(MAX(" + sales.key() + "), 0) + 1, "
                    + customer + ", 1, '" + day + "', 1000, 100, 900, " + stock + ", " + delegate + ", " + till + ", '" + STAMP
                    + "', 1 FROM " + sales.table());
            statement.executeUpdate("INSERT INTO " + returns.table() + " (" + returns.key() + ", " + returns.party()
                    + ", invoice_type, invoice_date, total, discount, " + returns.paid()
                    + ", stock_id, delegate_id, treasury_id, notes, user_id) SELECT COALESCE(MAX(" + returns.key() + "), 0) + 1, "
                    + customer + ", 1, '" + day + "', 200, 0, 200, " + stock + ", " + delegate + ", " + till + ", '" + STAMP
                    + "', 1 FROM " + returns.table());
        }
    }

    // ---- the budget and the recurring templates (phase C, V66) ------------------------------

    @Test
    @DisplayName("V66 from nothing: the two tables, the link, the permissions, and no helper left behind")
    void budgetAndRecurringSchema() throws Exception {
        assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
                + " AND table_name IN ('expense_budget', 'expense_recurring')"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'expense_budget' AND column_name = 'month_key'"
                + " AND generation_expression <> ''"), "the unique index reads a generated column");
        assertEquals("SET NULL", string("SELECT delete_rule FROM information_schema.referential_constraints"
                + " WHERE constraint_schema = DATABASE() AND table_name = 'expenses_details'"
                + " AND referenced_table_name = 'expense_recurring'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key IN"
                + " ('expenses.budget.manage', 'expenses.recurring.manage')"));
        assertEquals(0, scalar("""
                SELECT COUNT(*) FROM auth_role_permission held
                    JOIN auth_permission heading_key ON heading_key.id = held.permission_id
                                                    AND heading_key.permission_key = 'expenses.headings.update'
                WHERE NOT EXISTS (SELECT 1 FROM auth_role_permission granted
                                      JOIN auth_permission budget_key ON budget_key.id = granted.permission_id
                                                                     AND budget_key.permission_key = 'expenses.budget.manage'
                                  WHERE granted.role_id = held.role_id)"""),
                "whoever manages the headings may set a budget after the upgrade");
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE()"
                + " AND routine_name IN ('add_budget_column_if_missing', 'add_budget_index_if_missing',"
                + " 'add_constraint_if_missing')"), "a stray helper is what the next migration calls");
    }

    @Test
    @DisplayName("MySQL itself refuses a second yearly budget for one heading, which a nullable month would not")
    void oneYearlyBudgetPerHeading() throws Exception {
        signIn(AppPermissions.EXPENSES_BUDGET_MANAGE, AppPermissions.EXPENSES_REPORTS);
        ExpenseBudgetService budgets = new ExpenseBudgetService();
        int heading = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");

        int yearly = budgets.save(new ExpenseBudgetDraft(0, heading, 2031, null, new BigDecimal("12000"), STAMP));
        assertTrue(yearly > 0);
        // The service's own check answers first; the index behind it is what actually holds, and is what
        // a nullable month would have let through - MySQL counts two NULLs as different values.
        assertThrows(UserValidationException.class, () -> budgets.save(
                new ExpenseBudgetDraft(0, heading, 2031, null, new BigDecimal("9000"), STAMP)));
        assertEquals(1, scalar("SELECT COUNT(*) FROM expense_budget WHERE heading_id = " + heading
                + " AND `year` = 2031 AND `month` IS NULL"));

        // A month of the same year is a different budget and is allowed beside it.
        assertTrue(budgets.save(new ExpenseBudgetDraft(0, heading, 2031, 4, new BigDecimal("1500"), STAMP)) > 0);
        assertEquals(2, scalar("SELECT COUNT(*) FROM expense_budget WHERE heading_id = " + heading
                + " AND `year` = 2031"));
    }

    @Test
    @DisplayName("the budget report's actual is the report by heading's own total, on the same period")
    void budgetActualIsTheReportsOwnFigure() throws Exception {
        // The heading this case creates for itself goes through the headings service, which asks its own
        // permission - the first run of these cases was refused there, which is the fixture's fault.
        signIn(AppPermissions.EXPENSES_CREATE, AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_REPORTS,
                AppPermissions.EXPENSES_BUDGET_MANAGE, AppPermissions.EXPENSES_HEADINGS_UPDATE);
        ExpenseService expenses = new ExpenseService(DaoFactory.INSTANCE);
        int heading = expenses(STAMP + " موازنة");
        int till = scalar("SELECT MIN(id) FROM treasury");
        LocalDate day = LocalDate.of(2031, 7, 15);
        expenses.create(ExpenseEntry.parse(0, day, heading, till, new BigDecimal("400"), null, null, STAMP));
        expenses.create(ExpenseEntry.parse(0, day, heading, till, new BigDecimal("150"), null, null, STAMP));

        ExpenseBudgetService budgets = new ExpenseBudgetService();
        budgets.save(new ExpenseBudgetDraft(0, heading, 2031, 7, new BigDecimal("1000"), STAMP));

        ExpenseFilter july = ExpenseFilter.between(LocalDate.of(2031, 7, 1), LocalDate.of(2031, 7, 31));
        ExpenseBudgetReport report = budgets.report(july);
        ExpenseBudgetReport.Line line = report.lines().stream()
                .filter(candidate -> candidate.headingId() == heading).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("550").compareTo(line.actual()));
        assertEquals(0, new BigDecimal("1000").compareTo(line.budget()));
        assertEquals(0, new BigDecimal("450").compareTo(line.remaining()));

        // The same figure the report by heading shows for that heading over the same period - one query.
        ExpenseByHeadingReport byHeading = new ExpenseReportService().byHeading(july);
        BigDecimal fromTheOtherReport = byHeading.lines().stream()
                .filter(candidate -> candidate.headingId() == heading)
                .map(ExpenseByHeadingReport.Line::total).findFirst().orElseThrow();
        assertEquals(0, fromTheOtherReport.compareTo(line.actual()));
    }

    @Test
    @DisplayName("a recorded expense carries its template, stops the reminder, and outlives the template")
    void recurringLink() throws Exception {
        signIn(AppPermissions.EXPENSES_CREATE, AppPermissions.EXPENSES_SHOW,
                AppPermissions.EXPENSES_RECURRING_MANAGE, AppPermissions.EXPENSES_HEADINGS_UPDATE);
        ExpenseRecurringService recurring = new ExpenseRecurringService();
        int heading = expenses(STAMP + " إيجار");
        int till = scalar("SELECT MIN(id) FROM treasury");
        LocalDate today = LocalDate.now();
        LocalDate start = today.withDayOfMonth(1).minusMonths(1);

        int template = recurring.save(new ExpenseRecurringDraft(0, heading, till, new BigDecimal("5000"),
                "المالك", STAMP, ExpenseFrequency.MONTHLY, 1, start, null, true));
        List<ExpenseRecurringDue> due = recurring.due(today);
        assertTrue(due.stream().anyMatch(item -> item.template().id() == template
                        && item.periodStart().equals(today.withDayOfMonth(1))),
                "this month has fallen due and nothing has been recorded against it");

        // The reminder records nothing; the entry path does, and stamps the template on the row.
        ExpenseService expenseService = new ExpenseService(DaoFactory.INSTANCE);
        int expense = expenseService.create(ExpenseEntry.parse(0, today, heading, till, new BigDecimal("5000"),
                "المالك", null, STAMP + " recurring"), template);
        assertEquals(template, scalar("SELECT recurring_id FROM expenses_details WHERE id = " + expense));
        List<ExpenseRecurringDue> after = recurring.due(today).stream()
                .filter(item -> item.template().id() == template).toList();
        assertTrue(after.stream().noneMatch(item -> item.periodStart().equals(today.withDayOfMonth(1))),
                "this month is answered, so it no longer reminds");
        // And the period before it still does: this template started last month and nothing was recorded
        // against it, which is the grace a rent paid on the 3rd needs. The first run of this case asserted
        // the template fell silent altogether, which would have meant the grace period was not working.
        assertEquals(List.of(start), after.stream().map(ExpenseRecurringDue::periodStart).toList(),
                "the grace period is still open and is the only thing left");
        assertEquals(1, recurring.recordedCount(template));

        // A template with history is refused a delete - and if the row goes another way, the money stays.
        assertThrows(UserValidationException.class, () -> recurring.delete(template));
        execute("DELETE FROM expense_recurring WHERE id = " + template);
        assertEquals(1, scalar("SELECT COUNT(*) FROM expenses_details WHERE id = " + expense),
                "ON DELETE SET NULL: that cash really left the drawer");
        assertEquals(1, scalar("SELECT COUNT(*) FROM expenses_details WHERE id = " + expense
                + " AND recurring_id IS NULL"));
    }

    /** A heading of this test's own, created straight through the service. */
    private static int expenses(String name) throws Exception {
        return new ExpenseHeadingService().save(new ExpenseHeadingDraft(0, name, null, true, false));
    }

    // ---- fixtures -----------------------------------------------------------------------

    /**
     * The migrations as they stood before this change: every versioned file but V64, and the triggers
     * file cut where its V64 section begins.
     */
    private static Path migrationsWithoutV64() throws Exception {
        Path source = Paths.get(ExpenseDatabaseAcceptanceTest.class.getResource("/db/migration").toURI());
        Path target = Files.createTempDirectory("expense-migrations-v63-");
        target.toFile().deleteOnExit();
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                // V64 and everything after it: a later migration left in the folder would be applied to the
                // V63 schema first, and Flyway then skips V64 as older than what is already applied.
                if (name.matches("V(\\d+)__.*") && Integer.parseInt(name.substring(1, name.indexOf("__"))) >= 64) {
                    continue;
                }
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                if (name.equals("R__triggers.sql")) {
                    int cut = sql.indexOf(TRIGGERS_V64_MARKER);
                    assertTrue(cut > 0, "the V64 section of R__triggers.sql is where this test expects it");
                    sql = sql.substring(0, cut);
                }
                Files.writeString(target.resolve(name), sql, StandardCharsets.UTF_8);
            }
        }
        return target;
    }

    /**
     * Years of expenses as V63 wrote them: a bill, salaries and an advance with their employee, a wallet
     * fee, a heading holding a stray employee row, and a heading a shop added whose every row is an
     * employee's. {@code expenses.id} is not yet auto-increment, hence the explicit id.
     */
    private static void seedTheYearsBeforeV64(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO employees (column_name, job, hire_date, salary, user_id)"
                    + " VALUES ('" + STAMP + "', (SELECT MIN(id) FROM jobs), '2025-01-01', 3000, 1)");
            statement.executeUpdate("INSERT INTO expenses (id, expenses_name) SELECT MAX(id) + 1, '" + STAMP
                    + " بدل' FROM expenses");
        }
        int employee = scalar(connection, "SELECT id FROM employees WHERE column_name = '" + STAMP + "'");
        int till = scalar(connection, "SELECT MIN(id) FROM treasury");
        insertExpense(connection, "كهرباء", "2026-01-05", "100.00", null, till);
        insertExpense(connection, "مرتبات", "2026-01-31", "3000.00", employee, till);
        insertExpense(connection, "سلف", "2026-02-10", "500.00", employee, till);
        insertExpense(connection, "عمولات تحويل", "2026-02-11", "7.50", null, till);
        insertExpense(connection, "أخرى", "2026-02-12", "20.00", employee, till);
        insertExpense(connection, "أخرى", "2026-02-13", "35.00", null, till);
        insertExpense(connection, STAMP + " بدل", "2026-02-14", "150.00", employee, till);
    }

    private static void insertExpense(Connection connection, String heading, String day, String amount,
                                      Integer employee, int till) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id)"
                        + " VALUES ((SELECT id FROM expenses WHERE expenses_name = ?), ?, ?, ?, ?, ?, 1)")) {
            insert.setString(1, heading);
            insert.setString(2, day);
            insert.setBigDecimal(3, new BigDecimal(amount));
            insert.setString(4, STAMP);
            insert.setObject(5, employee);
            insert.setInt(6, till);
            assertEquals(1, insert.executeUpdate());
        }
    }

    /** What must read the same before and after V64. */
    private static void readFigures(Connection connection, List<String> into) throws Exception {
        for (String sql : List.of(
                "SELECT CONCAT(id, '=', balance) FROM treasury_current_balance ORDER BY id",
                // Grouped in a derived table: only_full_group_by refuses a CONCAT around the grouped
                // expression, which is how the first run of this class failed.
                "SELECT CONCAT(month, '=', total) FROM (SELECT DATE_FORMAT(date, '%Y-%m') AS month,"
                        + " SUM(amount) AS total FROM expenses_details GROUP BY month) monthly ORDER BY month",
                "SELECT CONCAT(employee_id, '=', balance) FROM employee_balance ORDER BY employee_id",
                "SELECT CONCAT(source_id, '=', entry_kind, '=', debit) FROM employee_account_table"
                        + " WHERE source = 'CASH' ORDER BY source_id")) {
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    into.add(rows.getString(1));
                }
            }
        }
    }

    /** The signed-in user has to be a row: {@code expenses_details.user_id} is a foreign key. */
    private static int walletAtOnePercent() throws Exception {
        String name = STAMP + "-wallet-" + System.nanoTime();
        execute("INSERT INTO treasury (t_name, amount, treasury_type, fee_percent, user_id) VALUES ('"
                + name + "', 0, 'WALLET', 1.00, 1)");
        return scalar("SELECT id FROM treasury WHERE t_name = '" + name + "'");
    }

    private static int treasuryHolding(String type, String opening) throws Exception {
        String name = STAMP + "-" + type + "-" + System.nanoTime();
        execute("INSERT INTO treasury (t_name, amount, treasury_type, fee_percent, user_id) VALUES ('"
                + name + "', " + opening + ", '" + type + "', 0, 1)");
        return scalar("SELECT id FROM treasury WHERE t_name = '" + name + "'");
    }

    private static CustomerAccount collection(int customer, int treasury) {
        CustomerAccount account = new CustomerAccount(0, LocalDate.now().toString(), 1000d, STAMP + " collection",
                0, new Customers(customer), new Treasury(treasury));
        account.setUsers(new Users(OPERATOR));
        return account;
    }

    private static void seedOperator() throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO users (id, user_name, user_pass, user_available) VALUES (?, ?, 'not-a-password', 0)")) {
            insert.setInt(1, OPERATOR);
            insert.setString(2, STAMP);
            insert.executeUpdate();
        }
        try (Connection connection = ConnectionManager.acquire(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO employees (column_name, job, hire_date, salary, user_id)"
                    + " VALUES ('" + STAMP + "', (SELECT MIN(id) FROM jobs), '2025-01-01', 3000, 1)");
        }
    }

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static void createDatabase(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
    }

    private static void migrate(String schema, String location) {
        Flyway.configure()
                .dataSource(jdbcUrl(schema), username, password)
                .locations(location)
                .validateOnMigrate(false)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static int scalar(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire()) {
            return scalar(connection, sql);
        }
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getInt(1);
        }
    }

    /** A write of this test's own, for the one case that has to see what the database does by itself. */
    private static void execute(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static BigDecimal decimal(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getBigDecimal(1);
        }
    }

    private static String string(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire()) {
            return string(connection, sql);
        }
    }

    private static String string(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
        }
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL";
    }

    /**
     * Where the credentials come from - the host, the port and the account this class signs in with. It
     * creates its own scratch schemas and never touches the configured one, so this file is a credential
     * carrier and nothing more.
     * <p>
     * The two relative paths are the ordinary ones: surefire's working directory is the module, so it is
     * {@code account/config.xml}, never the repository root's - they are different files.
     * {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} names one explicitly, which is the same escape hatch
     * {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_USER} already is, and is what lets this class run from a
     * worktree - which deliberately has no database configuration of its own
     * (docs/agent-worktree-rules.md §5), so the alternative would be copying a secret into one.
     */
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
