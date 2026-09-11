package com.hamza.account.features.party.statement;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.ConnectionManager;
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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half of {@code features/party/statement} that no unit test can reach: the SQL.
 * <p>
 * {@link PartyStatementTest} says what a row means and {@link PartyStatementAgreesWithLedgerEffectTest}
 * holds that meaning to {@link com.hamza.account.document.DocumentLedgerEffect}. Both run on
 * records built in memory. Three things are only true if MySQL says so, and all three are the
 * point of the package:
 * <ul>
 *   <li>the <b>running balance</b> the window function accumulates, and the <b>balance carried
 *       into the period</b> that seeds it — defect خ-2, where the screen restarted the total at
 *       zero for any period shorter than the whole history;</li>
 *   <li>the <b>summary's two totals</b>, which restate {@link PartyStatementRow#debit()} and
 *       {@link PartyStatementRow#credit()} in SQL. Two statements of one rule is exactly the
 *       shape of defect this package removes, so the only honest thing is to compare them:
 *       every case here sums the fetched rows in Java and asserts the database agrees;</li>
 *   <li><b>that a deferred return is on the statement at all</b> — defect خ-1, which was
 *       invisible precisely because the row did not exist on the page.</li>
 * </ul>
 * And one thing about a neighbour: {@code view_customer_receivables} used to compute a
 * customer's debt itself, ignoring every return, so the receivables report and the accounts
 * screen answered differently. It is derived from {@code account_customer_totals} now, and the
 * last case asserts the two cannot differ.
 * <p>
 * <b>Real-MySQL acceptance, gated on {@code -Daccount.db.acceptance=true}</b>, in the manner of
 * {@code PartyLedgerViewAcceptanceTest}: one transaction, rolled back in a {@code finally}, so
 * nothing is committed — the audit triggers' rows go with it. <b>A gated test is not a passing
 * test</b>: run it after touching {@code R__views.sql}, {@link PartyStatementQuery} or
 * {@link PartyStatementRow}. Surefire's working directory is the module, so the config read is
 * {@code account/config.xml}, not the root one.
 * <p>
 * It reads through {@link JdbcPartyStatementRepository} rather than
 * {@link PartyStatementService}, deliberately: the service requires a permission, and a test
 * that signs in to get past it would be signing in as user 1 — whom
 * {@code UserSessionContext.isSystemAdministrator} lets through every check, so the guard would
 * be neither exercised nor honestly bypassed. This test is about the SQL. Authorization is
 * {@code AuthorizationArchitectureTest}'s subject.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PartyStatementViewAcceptanceTest {

    private static final PartyStatementRepository REPOSITORY = new JdbcPartyStatementRepository();

    /** Wide enough apart that a wrong running total cannot land on a right-looking number. */
    private static final LocalDate BEFORE = LocalDate.of(2031, 1, 10);
    private static final LocalDate INSIDE_ONE = LocalDate.of(2031, 3, 5);
    private static final LocalDate INSIDE_TWO = LocalDate.of(2031, 3, 20);

    /**
     * A year with no real documents in it, so the fixture's figures are the only ones the
     * period can contain. {@code ProfitDefinitionDatabaseAcceptanceTest} refuses to run in a
     * year that holds documents for the same reason; here the party is new, so only the dates
     * have to be clear of anything a person would be reading.
     */
    private static final LocalDate PERIOD_FROM = LocalDate.of(2031, 3, 1);
    private static final LocalDate PERIOD_TO = LocalDate.of(2031, 3, 31);

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
     * <b>Defect خ-2, against a database.</b> A customer opens the period owing 500 from before
     * it, buys 1000 on account inside it, and pays 300. The statement for March must carry the
     * 500 in, not start from zero, and each row's balance must follow from the one above.
     */
    @Test
    @DisplayName("the period carries in the balance from before it, and the rows run on from it")
    void theRunningBalanceIsSeededWithWhatWasOwedBeforeThePeriod() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            insertPayment(transaction, customer, BEFORE, -500);      // a debit: see insertPayment
            insertSalesInvoice(transaction, customer, INSIDE_ONE, 1000, 0, 0);
            insertPayment(transaction, customer, INSIDE_TWO, 300);

            PartyStatementFilter march = period(customer);
            PartyStatementSummary summary = REPOSITORY.summarize(march);

            assertEquals(0, new BigDecimal("500.00").compareTo(summary.openingBalance()),
                    "the balance carried into the period is wrong. This is the figure the old "
                            + "screen did not have at all: it filtered a loaded list and restarted "
                            + "the running total at zero. Got " + summary.openingBalance());
            assertEquals(0, new BigDecimal("1200.00").compareTo(summary.closingBalance()),
                    "closing should be 500 + 1000 - 300. Got " + summary.closingBalance());

            List<PartyStatementRow> rows = REPOSITORY.search(march);
            assertEquals(2, rows.size(), "March holds the invoice and the payment only");

            // Oldest first, the order the balance was accumulated in.
            List<PartyStatementRow> ordered =
                    new PartyStatementPrintData(rows, summary, false).rowsOldestFirst();
            assertEquals(0, new BigDecimal("1500.00").compareTo(ordered.get(0).runningBalance()),
                    "after the invoice the customer owes 500 + 1000");
            assertEquals(0, new BigDecimal("1200.00").compareTo(ordered.get(1).runningBalance()),
                    "after the payment, 1500 - 300");
            assertEquals(0, summary.closingBalance()
                            .compareTo(ordered.get(ordered.size() - 1).runningBalance()),
                    "the last row's running balance must be the closing balance; two answers to "
                            + "one question is how this screen came to disagree with the other one");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * <b>Defect خ-1, against a database.</b> A deferred sales return credits the customer its
     * whole value, and therefore appears on the statement.
     * <p>
     * The screen this replaces computed the row's debit as
     * {@code invoiceType == CASH ? totalAfterDiscount : 0} and its credit from the cash
     * column — which a deferred return leaves at zero since {@code V15}. So both columns were
     * zero: the row was on the page with nothing in it, and the customer still appeared to owe
     * what they had given back. The assertion to care about is the closing balance.
     */
    @Test
    @DisplayName("a deferred sales return credits the customer, and is not an empty row")
    void aDeferredReturnReachesTheStatement() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            insertSalesInvoice(transaction, customer, INSIDE_ONE, 1000, 0, 0);
            insertSalesReturn(transaction, customer, INSIDE_TWO, 400, 0, 0);

            PartyStatementFilter march = period(customer);
            PartyStatementSummary summary = REPOSITORY.summarize(march);
            assertEquals(0, new BigDecimal("600.00").compareTo(summary.closingBalance()),
                    "1000 bought on account less 400 returned on account is 600. A closing "
                            + "balance of 1000 is the old defect: the return contributed nothing. "
                            + "Got " + summary.closingBalance());

            PartyStatementRow ret = REPOSITORY.search(march).stream()
                    .filter(row -> row.kind() == PartyMovementKind.RETURN)
                    .findFirst().orElseThrow(() ->
                            new AssertionError("the return is not on the statement at all"));
            assertEquals(0, new BigDecimal("400.00").compareTo(ret.credit()),
                    "the return must be a credit of its whole value, not an empty row");
            assertEquals(0, BigDecimal.ZERO.compareTo(ret.debit()));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * <b>The two statements of one rule, compared.</b> The summary's totals are computed in
     * SQL; {@link PartyStatementRow#debit()} and {@link PartyStatementRow#credit()} compute the
     * same thing in Java. Nothing but a database can say whether they agree, and a package
     * built to remove a second definition of a rule has no business shipping one of its own
     * unchecked.
     * <p>
     * The fixture deliberately includes the cases where a hand-written version breaks: a cash
     * invoice (both columns filled, balance unmoved), a part-paid deferred return (both columns
     * filled, balance moved by the difference), and a payment.
     */
    @Test
    @DisplayName("the summary's totals equal the same sums taken over the fetched rows")
    void theSqlTotalsAgreeWithTheJavaDefinition() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            insertSalesInvoice(transaction, customer, INSIDE_ONE, 1000, 50, 0);     // deferred
            insertSalesInvoice(transaction, customer, INSIDE_ONE, 700, 0, 700);     // cash
            insertSalesReturn(transaction, customer, INSIDE_TWO, 300, 0, 120);      // part refunded
            insertPayment(transaction, customer, INSIDE_TWO, 200);

            PartyStatementFilter march = period(customer);
            PartyStatementSummary summary = REPOSITORY.summarize(march);
            List<PartyStatementRow> rows = REPOSITORY.search(march);
            assertEquals(4, rows.size(), "the fixture's four movements are all in the period");

            BigDecimal debit = rows.stream().map(PartyStatementRow::debit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = rows.stream().map(PartyStatementRow::credit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(0, debit.compareTo(summary.totalDebit()),
                    "PartyStatementQuery.summarySql and PartyStatementRow.debit() disagree. "
                            + "SQL " + summary.totalDebit() + ", Java " + debit);
            assertEquals(0, credit.compareTo(summary.totalCredit()),
                    "PartyStatementQuery.summarySql and PartyStatementRow.credit() disagree. "
                            + "SQL " + summary.totalCredit() + ", Java " + credit);

            // And the whole period adds up: nothing is filtered out, so the rows on the page
            // carry the balance from one end of the period to the other.
            assertTrue(summary.rowsExplainTheBalance(),
                    "opening " + summary.openingBalance() + " + net " + summary.netMovement()
                            + " should be closing " + summary.closingBalance());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * A filter narrows the rows and leaves both balances alone.
     * <p>
     * This is the rule {@link PartyStatementFilter} warns about in its own javadoc, and the one
     * a later filter is most likely to break — because breaking it yields a plausible number
     * rather than an error. A "balance before the period" measured over payments only is not
     * anybody's balance, and it is the figure a customer is asked to agree with.
     */
    @Test
    @DisplayName("filtering to payments hides the invoices and moves neither balance")
    void theBalancesAnswerTheDatesAloneWhateverElseIsFiltered() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 0);
            insertPayment(transaction, customer, BEFORE, -500);
            insertSalesInvoice(transaction, customer, INSIDE_ONE, 1000, 0, 0);
            insertPayment(transaction, customer, INSIDE_TWO, 300);

            PartyStatementSummary all = REPOSITORY.summarize(period(customer));
            PartyStatementFilter paymentsOnly = new PartyStatementFilter(PartyKind.CUSTOMER,
                    customer, PERIOD_FROM, PERIOD_TO, Set.of(PartyMovementKind.PAYMENT),
                    null, null, null, null, "", false, 0, 100);
            PartyStatementSummary filtered = REPOSITORY.summarize(paymentsOnly);

            assertEquals(0, all.openingBalance().compareTo(filtered.openingBalance()),
                    "the opening balance must not change when rows are filtered");
            assertEquals(0, all.closingBalance().compareTo(filtered.closingBalance()),
                    "the closing balance must not change when rows are filtered");

            List<PartyStatementRow> rows = REPOSITORY.search(paymentsOnly);
            assertEquals(1, rows.size(), "only the payment inside the period survives the filter");
            assertEquals(PartyMovementKind.PAYMENT, rows.getFirst().kind());
            assertFalse(filtered.rowsExplainTheBalance(),
                    "a narrowed list cannot account for the whole move from opening to closing, "
                            + "and the screen relies on this to say so");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * <b>Defect خ-3.</b> {@code view_customer_receivables} and {@code account_customer_totals}
     * answer the same question, and must answer it with the same number.
     * <p>
     * The receivables view used to compute its own figure from {@code total_sales} and
     * {@code customers_accounts}, so it ignored every sales return and the {@code purchase}
     * column of the ledger. A customer with a return was shown two different debts on two
     * screens, and {@code CreditLimitSource} raised its credit-limit warning off the wrong one.
     * The fixture below has a return in it precisely so that the old definition would fail this.
     */
    @Test
    @DisplayName("the receivables report and the accounts totals report one number")
    void theReceivablesViewAgreesWithTheTotalsView() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int customer = insertCustomer(transaction, 700);   // an opening balance of 700
            insertSalesInvoice(transaction, customer, INSIDE_ONE, 1000, 0, 0);
            insertSalesReturn(transaction, customer, INSIDE_TWO, 400, 0, 0);
            insertPayment(transaction, customer, INSIDE_TWO, 300);

            BigDecimal totals = scalar(transaction,
                    "SELECT amount FROM account_customer_totals WHERE account_code = ?", customer);
            BigDecimal receivable = scalar(transaction,
                    "SELECT final_balance FROM view_customer_receivables WHERE customer_id = ?",
                    customer);
            assertNotNull(totals, "the customer is missing from account_customer_totals");
            assertNotNull(receivable, "the customer is missing from view_customer_receivables");

            assertEquals(0, new BigDecimal("1000.00").compareTo(totals),
                    "700 opening + 1000 invoiced - 400 returned - 300 paid is 1000. Got " + totals);
            assertEquals(0, totals.compareTo(receivable),
                    "the two views disagree about what the customer owes: totals " + totals
                            + ", receivables " + receivable + ". The receivables view must be "
                            + "derived from the totals view, not compute its own figure.");

            // And its three parts add up to its whole, which they did not before: they came
            // from different sources than final_balance did.
            BigDecimal opening = scalar(transaction,
                    "SELECT opening_balance FROM view_customer_receivables WHERE customer_id = ?", customer);
            BigDecimal charged = scalar(transaction,
                    "SELECT total_invoices_debt FROM view_customer_receivables WHERE customer_id = ?", customer);
            BigDecimal paid = scalar(transaction,
                    "SELECT total_payments FROM view_customer_receivables WHERE customer_id = ?", customer);
            assertEquals(0, opening.add(charged).subtract(paid).compareTo(receivable),
                    "opening + charged - paid must equal the final balance on the same row");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    // ---- the fixture ----------------------------------------------------------------

    private static PartyStatementFilter period(int customer) {
        return new PartyStatementFilter(PartyKind.CUSTOMER, customer, PERIOD_FROM, PERIOD_TO,
                Set.of(), null, null, null, null, "", false, 0, 100);
    }

    private static int insertCustomer(Connection connection, double openingBalance) throws Exception {
        execute(connection, """
                INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)
                VALUES (?, 0, ?, 1, 1, 1)""",
                "acceptance-statement-" + System.nanoTime(), openingBalance);
        return lastId(connection);
    }

    /**
     * A row of {@code customers_accounts}.
     * <p>
     * A positive {@code paid} is a collection. A negative one is written as a positive
     * {@code purchase} instead — a debit on the account — which is the only way to put an
     * opening debt at a chosen date today, since nothing writes that column yet (phase ب of
     * {@code docs/party-plan.md} gives it a screen). The view reads it either way, which is
     * what lets this test seed a balance before the period.
     */
    private static void insertPayment(Connection connection, int customer, LocalDate date,
                                      double paid) throws Exception {
        double credit = paid > 0 ? paid : 0;
        double debit = paid < 0 ? -paid : 0;
        execute(connection, """
                INSERT INTO customers_accounts
                    (account_code, account_date, paid, purchase, notes, numberInv, treasury_id, user_id)
                VALUES (?, ?, ?, ?, 'acceptance', 0, 1, 1)""",
                customer, Date.valueOf(date), credit, debit);
    }

    private static int insertSalesInvoice(Connection connection, int customer, LocalDate date,
                                          double total, double discount, double paid) throws Exception {
        return insertDocument(connection, DocumentType.SALES, customer, date, total, discount, paid);
    }

    private static int insertSalesReturn(Connection connection, int customer, LocalDate date,
                                         double total, double discount, double refunded) throws Exception {
        return insertDocument(connection, DocumentType.SALES_RETURN, customer, date, total, discount,
                refunded);
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

    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private static int lastId(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT LAST_INSERT_ID()");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static BigDecimal scalar(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }
}
