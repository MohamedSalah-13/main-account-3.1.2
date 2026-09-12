package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.statement.EmployeeStatementFilter;
import com.hamza.account.features.employee.statement.EmployeeStatementPage;
import com.hamza.account.features.employee.statement.EmployeeStatementRow;
import com.hamza.account.features.employee.statement.EmployeeStatementService;
import com.hamza.account.features.employee.statement.JdbcEmployeeStatementRepository;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.ExpensesDetailsService;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The employee account against a real MySQL.
 * <p>
 * <b>It exists for the one claim no unit test can make.</b> The statement's summary restates
 * {@link EmployeeStatementRow#debit()} and {@code credit()} in SQL, because a sum cannot be taken
 * in Java over rows that were never fetched — so there are two statements of one rule, one in Java
 * and one in text handed to MySQL. Nothing but a run compares them. It also proves the running
 * balance is seeded with what came before the period, which is the defect the party statement
 * carried for months: a statement for September printed as though the employee began it owed
 * nothing.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. Everything runs inside one transaction that is
 * always rolled back, in the manner of {@code ItemGroupMoveDatabaseAcceptanceTest}: the services'
 * own {@code TransactionTemplate} joins the open transaction rather than committing inside it, so
 * the assertions see every row written and the developer's database keeps none of them. Every
 * fixture row is stamped {@code EMP-<nanos>}, so residue — if the rollback ever failed — is one
 * query away, and {@link #leaveNothingBehind()} asks rather than trusts.
 * <p>
 * <b>The session is not user 1.</b> {@code UserSessionContext.isSystemAdministrator()} is
 * {@code currentUserId() == 1} and bypasses every permission, so a permission case run as user 1
 * cannot fail — the trap {@code ItemGroupMoveDatabaseAcceptanceTest} found in itself.
 * <p>
 * <b>Run it against a scratch schema built from nothing</b>, not against a working database: it
 * writes an employee, a job's worth of ledger rows and real expense rows, and a schema built from
 * nothing is also the only check that a first install migrates.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class EmployeeAccountDatabaseAcceptanceTest {

    /** {@code employees.user_id} carries a foreign key, so the fixture uses the seeded admin. */
    private static final int OWNER = 1;

    /** Who is signed in: not 1, or every guard below would pass by the administrator bypass. */
    private static final int OPERATOR = 9;

    private static final String STAMP = "EMP-" + System.nanoTime();

    private static final EmployeeStatementService STATEMENTS =
            new EmployeeStatementService(new JdbcEmployeeStatementRepository());
    private static final EmployeeLedgerService LEDGER =
            new EmployeeLedgerService(new JdbcEmployeeStatementRepository());
    private static final EmployeePaymentService PAYMENTS = new EmployeePaymentService(
            new ExpensesDetailsService(DaoFactory.INSTANCE), new JdbcEmployeeStatementRepository());

    private static Connection transaction;
    private static int employeeId;

    @BeforeAll
    static void connect() throws Exception {
        // Surefire's working directory is the module, so this is account/config.xml - a different
        // file from the one in the repository root.
        File configFile = new File("config.xml");
        if (!configFile.isFile()) {
            configFile = new File("../config.xml");
        }
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));

        signIn(AppPermissions.EMPLOYEE_ACCOUNT_SHOW, AppPermissions.EMPLOYEE_ACCOUNT_ADJUST,
                AppPermissions.EMPLOYEE_PAY, AppPermissions.EXPENSES_CREATE);

        transaction = ConnectionManager.beginTransaction();
        seedOperator();
        employeeId = seedEmployee();
    }

    @AfterAll
    static void leaveNothingBehind() throws Exception {
        if (transaction != null) {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "employees", "column_name LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "expenses_details", "notes LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "employee_ledger", "notes LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "users", "user_name LIKE '" + STAMP + "%'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    @Test
    @DisplayName("the statement unions the ledger with the cash, and the balance is what a pen reaches")
    void theBalanceIsWhatAPenReaches() throws Exception {
        recordLedger(EmployeeEntryKind.OPENING_DUE, "2026-01-01", "300");
        pay(EmployeeCashPurpose.ADVANCE, "2026-02-10", "1000");
        recordLedger(EmployeeEntryKind.DEDUCTION, "2026-02-20", "150");
        recordLedger(EmployeeEntryKind.BONUS, "2026-02-25", "200");
        recordLedger(EmployeeEntryKind.ENTITLEMENT, "2026-02-28", "5000");
        pay(EmployeeCashPurpose.SALARY, "2026-03-01", "4000");

        EmployeeStatementPage page = STATEMENTS.search(wholeYear());

        assertEquals(6, page.rows().size(), "both halves of the account are on one statement");
        // 300 + 5000 + 200 credited, 150 + 1000 + 4000 debited.
        assertEquals(0, new BigDecimal("5500").compareTo(page.summary().totalCredit()));
        assertEquals(0, new BigDecimal("5150").compareTo(page.summary().totalDebit()));
        assertEquals(0, new BigDecimal("350").compareTo(page.summary().closingBalance()));
        assertEquals(0, new BigDecimal("350").compareTo(STATEMENTS.currentBalance(employeeId)),
                "employee_balance is derived from the same view, so it cannot disagree");
    }

    @Test
    @DisplayName("the summary computed in SQL equals the rows summed in Java")
    void theTwoStatementsOfOneRuleAgree() throws Exception {
        recordLedger(EmployeeEntryKind.ENTITLEMENT, "2026-04-30", "4400");
        pay(EmployeeCashPurpose.SALARY, "2026-05-01", "4400");

        EmployeeStatementPage page = STATEMENTS.search(wholeYear());

        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (EmployeeStatementRow row : page.rows()) {
            debit = debit.add(row.debit());
            credit = credit.add(row.credit());
        }
        assertEquals(0, debit.compareTo(page.summary().totalDebit()),
                "the summary restates the row's debit in SQL; this is the only thing that "
                        + "compares the two");
        assertEquals(0, credit.compareTo(page.summary().totalCredit()));
        assertEquals(page.rows().size(), page.summary().shownCount());
    }

    @Test
    @DisplayName("a statement for one period carries in what came before it")
    void theRunningBalanceIsSeeded() throws Exception {
        recordLedger(EmployeeEntryKind.OPENING_DUE, "2026-06-01", "500");
        pay(EmployeeCashPurpose.ADVANCE, "2026-06-10", "200");
        recordLedger(EmployeeEntryKind.DEDUCTION, "2026-07-05", "100");

        EmployeeStatementFilter july = new EmployeeStatementFilter(employeeId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                java.util.Set.of(), null, null, null, null, "", 0, 50);
        EmployeeStatementPage page = STATEMENTS.search(july);

        BigDecimal before = STATEMENTS.search(new EmployeeStatementFilter(employeeId,
                LocalDate.of(2000, 1, 1), LocalDate.of(2026, 6, 30),
                java.util.Set.of(), null, null, null, null, "", 0, 500)).summary().closingBalance();

        assertEquals(0, before.compareTo(page.summary().openingBalance()),
                "the period opens on the balance the one before it closed on - a total restarted "
                        + "at zero is defect خ-2 of the party plan, at a different table");
        assertEquals(0, page.summary().openingBalance().subtract(new BigDecimal("100"))
                        .compareTo(page.rows().get(0).runningBalance()),
                "and the first row of the period continues from it");
    }

    @Test
    @DisplayName("a payment writes its expense and its purpose together, and the treasury sees it")
    void aPaymentIsAnExpense() throws Exception {
        int before = countExpenses();
        int expenseId = pay(EmployeeCashPurpose.ADVANCE, "2026-08-01", "750");

        assertEquals(before + 1, countExpenses(), "every pound paid is an expenses_details row");
        assertEquals("ADVANCE", purposeOf(expenseId),
                "without the purpose row it would read as a salary on every statement afterwards");
    }

    @Test
    @DisplayName("a ledger row may not carry a treasury - the ledger holds no cash")
    void theLedgerHoldsNoCash() throws Exception {
        try (PreparedStatement columns = transaction.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                        + " AND TABLE_NAME = 'employee_ledger' AND COLUMN_NAME LIKE '%treasury%'");
             ResultSet rs = columns.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "a column here would be a second writer of money the treasury does not know "
                            + "about - ق-١");
        }
    }

    @Test
    @DisplayName("the database refuses a kind Java cannot read")
    void theCheckHolds() throws Exception {
        try (Statement statement = transaction.createStatement()) {
            statement.executeUpdate("INSERT INTO employee_ledger (employee_id, entry_date, kind, "
                    + "amount, user_id) VALUES (" + employeeId + ", '2026-09-01', 'LOAN', 5, 1)");
            throw new AssertionError("the kind CHECK did not refuse an unknown code");
        } catch (java.sql.SQLException refused) {
            assertTrue(refused.getMessage().contains("employee_ledger_kind_chk"),
                    "refused, but not by the constraint that should have: " + refused.getMessage());
        }
    }

    // ---- the fixture ----------------------------------------------------------------------

    private static EmployeeStatementFilter wholeYear() {
        return EmployeeStatementFilter.all(employeeId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    private static void recordLedger(EmployeeEntryKind kind, String day, String amount)
            throws Exception {
        LEDGER.record(EmployeeLedgerEntry.parse(employeeId, LocalDate.parse(day), kind,
                new BigDecimal(amount), STAMP + " " + kind));
    }

    private static int pay(EmployeeCashPurpose purpose, String day, String amount) throws Exception {
        return PAYMENTS.pay(EmployeePayment.parse(employeeId, LocalDate.parse(day),
                new BigDecimal(amount), purpose, firstTreasury(), firstHeading(),
                STAMP + " " + purpose));
    }

    /**
     * The signed-in user has to exist, and this class deliberately is not user 1.
     * <p>
     * Both {@code employee_ledger.user_id} and {@code expenses_details.user_id} are foreign keys
     * to {@code users}, and a schema built from nothing holds only the seeded administrator - so
     * every write this class makes was refused by the key until the row was seeded here. In a
     * running system the signed-in user is a row by construction, which is why nothing but a
     * fresh schema could show it.
     */
    private static void seedOperator() throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO users (id, user_name, user_pass, user_available) VALUES (?, ?, ?, 0) "
                        + "ON DUPLICATE KEY UPDATE user_name = VALUES(user_name)")) {
            insert.setInt(1, OPERATOR);
            insert.setString(2, STAMP);
            insert.setString(3, "not-a-password");
            insert.executeUpdate();
        }
    }

    private static int seedEmployee() throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO employees (column_name, job, hire_date, salary, user_id) "
                        + "VALUES (?, (SELECT MIN(id) FROM jobs), '2026-01-01', 5000, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, STAMP);
            insert.setInt(2, OWNER);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int firstTreasury() throws Exception {
        return scalar("SELECT MIN(id) FROM treasury");
    }

    private static int firstHeading() throws Exception {
        return scalar("SELECT MIN(id) FROM expenses");
    }

    private static int countExpenses() throws Exception {
        return scalar("SELECT COUNT(*) FROM expenses_details WHERE emp_id = " + employeeId);
    }

    private static String purposeOf(int expenseId) throws Exception {
        try (Statement statement = transaction.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT purpose FROM employee_cash_purpose WHERE expense_id = " + expenseId)) {
            assertTrue(rs.next(), "the payment wrote no purpose row");
            String purpose = rs.getString(1);
            assertNotNull(purpose);
            return purpose;
        }
    }

    private static int scalar(String sql) throws Exception {
        try (Statement statement = transaction.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static void assertNoResidue(Connection connection, String table, String where)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1),
                    "this class left rows behind in " + table + " - the rollback did not hold");
        }
    }

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }
}
