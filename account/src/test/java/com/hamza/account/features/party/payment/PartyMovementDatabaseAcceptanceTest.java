package com.hamza.account.features.party.payment;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.statement.JdbcPartyStatementRepository;
import com.hamza.account.features.party.statement.PartyStatementRepository;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What phase ب claims about a real database, asked of one.
 * <p>
 * Three claims, and none of them can be checked without MySQL:
 * <ul>
 *   <li><b>{@code account_num} is the database's to assign.</b> The insert no longer writes it,
 *       which is only true if the column really is {@code AUTO_INCREMENT} on a live schema and if
 *       the generated key comes back — the shift journal files the movement under that number, so
 *       a zero would attribute cash to movement zero.</li>
 *   <li><b>A note moves the party's account and no treasury.</b> This is the one that matters
 *       most. {@code treasury_balance} unions {@code customers_accounts.paid} as money into the
 *       till; the claim is that it does not read {@code purchase}, so a debit note leaves every
 *       drawer exactly where it was. If that is wrong, a correction entered at a desk shows up as
 *       a surplus somebody has to explain at the close of a shift.</li>
 *   <li><b>An allocation bigger than the invoice owes is refused</b>, measured against the
 *       database rather than against the list a dialog is holding.</li>
 * </ul>
 * <b>Gated on {@code -Daccount.db.acceptance=true}</b>, one transaction, rolled back in a
 * {@code finally} — so even the audit triggers' rows go with it. <b>A gated test is not a passing
 * test.</b> Surefire's working directory is the module, so the config read is
 * {@code account/config.xml}, not the root one.
 * <p>
 * It writes through the DAO rather than through {@code AccountCustomerService}: the service
 * requires a permission, and a test that signed in to get past it would be signing in as user 1,
 * whom {@code UserSessionContext.isSystemAdministrator} lets through every check — so the guard
 * would be neither exercised nor honestly bypassed. What is being checked here is the schema and
 * the views. The permissions are {@code AuthorizationArchitectureTest}'s subject.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PartyMovementDatabaseAcceptanceTest {

    private static final PartyStatementRepository STATEMENTS = new JdbcPartyStatementRepository();
    private static final LocalDate DAY = LocalDate.of(2031, 5, 12);

    @BeforeAll
    static void connect() throws Exception {
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
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    /**
     * The insert leaves {@code account_num} out and MySQL fills it in — with a number nobody had
     * to read the ledger to find, and which no second till could have chosen at the same moment.
     */
    @Test
    @DisplayName("the movement number is assigned by the database, not by the application")
    void theDatabaseNumbersTheMovement() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            int first = insertCollection(transaction, customer, 100);
            int second = insertCollection(transaction, customer, 200);

            assertTrue(first > 0, "the generated key was not returned");
            assertTrue(second > first,
                    "two movements must get two numbers; got " + first + " then " + second);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * <b>The claim the whole of {@link PartyEntryKind} rests on.</b> A debit note raises what the
     * customer owes and leaves every treasury balance untouched.
     * <p>
     * The treasury figure is read before and after, from {@code treasury_current_balance} — the
     * one definition of a balance, per {@code docs/treasury-plan.md} — and must not budge. A
     * collection of the same amount is then entered to show that the same treasury <em>does</em>
     * move when money really changes hands, so a passing first half cannot be an artefact of
     * reading the wrong column.
     */
    @Test
    @DisplayName("a debit note moves the account and not the till; a collection moves both")
    void aNoteLeavesTheTreasuryAloneAndACollectionDoesNot() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            BigDecimal tillBefore = treasuryBalance(transaction, 1);

            insertNote(transaction, customer, 300);

            assertEquals(0, new BigDecimal("300.00")
                            .compareTo(STATEMENTS.currentBalance(PartyKind.CUSTOMER, customer)),
                    "a debit note of 300 must raise the customer's balance by 300");
            assertEquals(0, tillBefore.compareTo(treasuryBalance(transaction, 1)),
                    "a debit note moved a treasury balance. treasury_balance must not read "
                            + "customers_accounts.purchase - if it does, PartyEntryKind is wrong "
                            + "and every correction entered at a desk becomes a till surplus.");

            insertCollection(transaction, customer, 300);

            assertEquals(0, BigDecimal.ZERO
                            .compareTo(STATEMENTS.currentBalance(PartyKind.CUSTOMER, customer)),
                    "collecting the 300 must bring the balance back to nothing");
            assertEquals(0, tillBefore.add(new BigDecimal("300.00"))
                            .compareTo(treasuryBalance(transaction, 1)),
                    "a collection of 300 must raise the till by 300 - the other half of the "
                            + "claim, without which the first half proves nothing");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * A credit note lowers the balance, also without touching a till.
     * <p>
     * It is stored as a negative {@code purchase} rather than as a positive {@code paid}: both
     * would reduce the balance by the same amount and exactly one of them leaves the drawer
     * alone.
     */
    @Test
    @DisplayName("a credit note lowers the balance without touching a till")
    void aCreditNoteLowersTheBalance() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 500);   // an opening balance of 500
            BigDecimal tillBefore = treasuryBalance(transaction, 1);

            insertNote(transaction, customer, -120);

            assertEquals(0, new BigDecimal("380.00")
                            .compareTo(STATEMENTS.currentBalance(PartyKind.CUSTOMER, customer)),
                    "500 opening less a credit note of 120 is 380");
            assertEquals(0, tillBefore.compareTo(treasuryBalance(transaction, 1)));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * The allocation picker offers what is unsettled, and the save-time check refuses more than
     * an invoice owes.
     * <p>
     * The refusal is a {@link UserValidationException} on purpose: the user chose the invoice and
     * the amount, and both are theirs to change. A reference code and "a technical error occurred"
     * would tell them nothing they can act on.
     */
    @Test
    @DisplayName("an invoice offers what it still owes, and refuses more than that")
    void allocationIsOfferedAndBounded() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            int paidInvoice = insertDeferredSalesInvoice(transaction, customer, 400, 400);
            int openInvoice = insertDeferredSalesInvoice(transaction, customer, 1000, 0);

            PartyPaymentAllocationService allocation = new PartyPaymentAllocationService();
            BigDecimal remaining = allocation.remainingOn(PartyKind.CUSTOMER, customer, openInvoice, 0);
            assertEquals(0, new BigDecimal("1000.00").compareTo(remaining));
            assertEquals(0, BigDecimal.ZERO.compareTo(
                            allocation.remainingOn(PartyKind.CUSTOMER, customer, paidInvoice, 0)),
                    "an invoice whose own cash covered it owes nothing");

            // 600 of the 1000 fits, and that payment then reduces what is left to 400.
            allocation.requireAllocationFits(PartyKind.CUSTOMER, customer, openInvoice,
                    new BigDecimal("600"), 0);
            insertAllocatedCollection(transaction, customer, 600, openInvoice);
            assertEquals(0, new BigDecimal("400.00").compareTo(
                    allocation.remainingOn(PartyKind.CUSTOMER, customer, openInvoice, 0)));

            UserValidationException refused = assertThrows(UserValidationException.class,
                    () -> allocation.requireAllocationFits(PartyKind.CUSTOMER, customer,
                            openInvoice, new BigDecimal("500"), 0),
                    "500 against an invoice owing 400 must be refused, not clamped");
            assertTrue(refused.getMessage().contains(String.valueOf(openInvoice)),
                    "the message must name the invoice: " + refused.getMessage());

            // Leaving it on account is always allowed and is never checked.
            allocation.requireAllocationFits(PartyKind.CUSTOMER, customer,
                    PartyPaymentAllocationService.ON_ACCOUNT, new BigDecimal("99999"), 0);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * Editing a payment sees its own invoice as it stood without that payment.
     * <p>
     * Without the exclusion, reopening a payment that settled an invoice in full would find the
     * invoice already settled — by the very row being edited — and refuse to save it again.
     */
    @Test
    @DisplayName("editing a payment does not count that payment against its own invoice")
    void editingAPaymentExcludesItself() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            int invoice = insertDeferredSalesInvoice(transaction, customer, 1000, 0);
            int movement = insertAllocatedCollection(transaction, customer, 1000, invoice);

            PartyPaymentAllocationService allocation = new PartyPaymentAllocationService();
            assertEquals(0, BigDecimal.ZERO.compareTo(
                            allocation.remainingOn(PartyKind.CUSTOMER, customer, invoice, 0)),
                    "to anyone else the invoice is settled");
            assertEquals(0, new BigDecimal("1000.00").compareTo(
                            allocation.remainingOn(PartyKind.CUSTOMER, customer, invoice, movement)),
                    "to the payment being edited, the invoice owes what it owed before it");
            allocation.requireAllocationFits(PartyKind.CUSTOMER, customer, invoice,
                    new BigDecimal("1000"), movement);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    // ---- the fixture ----------------------------------------------------------------

    private static int insertCustomer(Connection connection, double openingBalance) throws Exception {
        return insertReturningId(connection, """
                INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)
                VALUES (?, 0, ?, 1, 1, 1)""",
                "acceptance-movement-" + System.nanoTime(), openingBalance);
    }

    /** A collection: cash in, through {@code paid}, on account. */
    private static int insertCollection(Connection connection, int customer, double amount)
            throws Exception {
        return insertAllocatedCollection(connection, customer, amount,
                (int) PartyPaymentAllocationService.ON_ACCOUNT);
    }

    private static int insertAllocatedCollection(Connection connection, int customer, double amount,
                                                 int invoiceNumber) throws Exception {
        return insertReturningId(connection, """
                INSERT INTO customers_accounts
                    (account_code, account_date, purchase, paid, notes, numberInv, treasury_id, user_id)
                VALUES (?, ?, 0, ?, 'acceptance', ?, 1, 1)""",
                customer, Date.valueOf(DAY), amount, invoiceNumber);
    }

    /** A note: no cash, through {@code purchase}. Negative for a credit note. */
    private static int insertNote(Connection connection, int customer, double signedAmount)
            throws Exception {
        return insertReturningId(connection, """
                INSERT INTO customers_accounts
                    (account_code, account_date, purchase, paid, notes, numberInv, treasury_id, user_id)
                VALUES (?, ?, ?, 0, 'acceptance', 0, 1, 1)""",
                customer, Date.valueOf(DAY), signedAmount);
    }

    /** A deferred sales invoice. Deferred, so what it has been paid is only what is allocated. */
    private static int insertDeferredSalesInvoice(Connection connection, int customer,
                                                 double total, double paid) throws Exception {
        return insertDocument(connection, DocumentType.SALES, customer, DAY, total, 0, paid);
    }

    /**
     * Writes one document header through {@link DocumentTableSpec#insertSql()}.
     * <p>
     * Not hand-written SQL, for the reason {@code PartyLedgerViewAcceptanceTest} gives: the four
     * document tables list their columns in four different orders, and a column added to a spec has
     * to reach this fixture rather than quietly bypass it. Writing the columns out by hand meant
     * discovering the schema's NOT NULL columns one failed run at a time.
     * <p>
     * The document's number is supplied: {@code total_sales.invoice_number} and
     * {@code total_sales_re.id} are plain primary keys the application assigns
     * ({@code InvoiceNumberAllocator} draws them from {@code document_sequences}), so unlike
     * {@code customers_accounts.account_num} they have no default. That difference is the subject of
     * one of these tests, so the fixture had better not blur it.
     */
    private static int insertDocument(Connection connection, DocumentType type, int party,
                                      LocalDate date, double total, double discount, double paid)
            throws Exception {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        int number = nextDocumentNumber(connection, spec.table(), spec.key());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(spec.key(), number);
        values.put(spec.party(), party);
        values.put(spec.paid(), paid);
        values.put("invoice_type", paid > 0 ? 1 : 2);
        values.put("invoice_date", Date.valueOf(date));
        values.put("total", total);
        values.put("discount", discount);
        values.put("stock_id", 1);
        values.put("delegate_id", 1);
        values.put("treasury_id", 1);
        values.put("notes", "acceptance");
        values.put("user_id", 1);

        try (PreparedStatement statement = connection.prepareStatement(spec.insertSql())) {
            int index = 1;
            for (String column : spec.insertColumns()) {
                Object value = values.get(column);
                assertNotNull(value, "No fixture value for column " + column + " of " + spec.table());
                statement.setObject(index++, value);
            }
            assertEquals(1, statement.executeUpdate());
        }
        return number;
    }

    /** Clear of whatever numbers the schema already holds. */
    private static int nextDocumentNumber(Connection connection, String table, String key)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(" + key + "), 0) + 5000 FROM " + table);
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    /** The balance of one treasury, from the one view that defines it. */
    private static BigDecimal treasuryBalance(Connection connection, int treasuryId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM treasury_current_balance WHERE id = ?")) {
            statement.setInt(1, treasuryId);
            try (ResultSet rs = statement.executeQuery()) {
                BigDecimal balance = rs.next() ? rs.getBigDecimal(1) : null;
                return balance == null ? BigDecimal.ZERO : balance;
            }
        }
    }

    private static int insertReturningId(Connection connection, String sql, Object... values)
            throws Exception {
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next(), "no generated key for: " + sql);
                return keys.getInt(1);
            }
        }
    }

}
