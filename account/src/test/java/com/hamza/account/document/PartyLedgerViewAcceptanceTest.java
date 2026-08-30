package com.hamza.account.document;

import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.treasury.MovementLabel;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds {@code account_customer_table}, {@code account_suppliers_table} and
 * {@code treasury_balance} to {@link DocumentLedgerEffect}.
 * <p>
 * {@link DocumentLedgerEffectTest} says what a document is supposed to do to an account
 * and to a till; this says that the three views agree, which is the half that cannot be
 * checked without MySQL because the arithmetic is theirs. It is the test that fails on
 * the deferred return before {@code R__views.sql} is corrected and passes after - the
 * before/after evidence for that change, run against whichever database {@code
 * config.xml} points at.
 * <p>
 * Real-MySQL acceptance, opt in with {@code -Daccount.db.acceptance=true}, in the manner
 * of {@code StockLedgerReconciliationAcceptanceTest}: one transaction, always rolled
 * back, so nothing here is ever committed. The headers are written through
 * {@link DocumentTableSpec#insertSql()} rather than hand-written SQL, so a column added
 * to a spec reaches this test rather than quietly bypassing it.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PartyLedgerViewAcceptanceTest {

    /** 1000 less 100 is a net of 900 - enough that a wrong branch cannot land on the right number. */
    private static final double TOTAL = 1000;
    private static final double DISCOUNT = 100;
    private static final double NET = 900;

    /** What a deferred document settled in cash, leaving 650 to reach the account. */
    private static final double PART_PAID = 250;

    /** {@code information = 3} is an invoice row in the party views, {@code 4} a return. */
    private static final int INVOICE_ROW = 3;
    private static final int RETURN_ROW = 4;

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
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    @DisplayName("A cash document: the till takes the whole net and the account does not move")
    void cashDocumentAgreesWithTheDeclaredEffect(DocumentType type) throws Exception {
        // paid = net is what InvoicePaymentTerms writes for InvoiceType.CASH.
        assertViewsAgree(type, 1, NET);
    }

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    @DisplayName("A deferred document settling nothing: the whole net reaches the account")
    void deferredDocumentWithNoCashAgreesWithTheDeclaredEffect(DocumentType type)
            throws Exception {
        // The defect case for the two return types: the account used to see nothing and
        // the treasury used to see the entire net.
        assertViewsAgree(type, 2, 0);
    }

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    @DisplayName("A deferred document part-settled: the net splits between the till and the account")
    void deferredDocumentPartlyPaidAgreesWithTheDeclaredEffect(DocumentType type)
            throws Exception {
        assertViewsAgree(type, 2, PART_PAID);
    }

    private void assertViewsAgree(DocumentType type, int invoiceType, double paid)
            throws Exception {
        DocumentLedgerEffect expected =
                DocumentLedgerEffect.of(type, TOTAL, DISCOUNT, paid);
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int documentId = insertHeader(transaction, type, invoiceType, paid);

            LedgerRow ledger = readPartyLedger(transaction, type, documentId);
            assertEquals(expected.ledgerPurchase(), ledger.purchase(),
                    () -> "purchase column of " + type + " in "
                            + PartyLedgerSpec.of(type.partyKind()).view());
            assertEquals(expected.ledgerDiscount(), ledger.discount(),
                    () -> "discount column of " + type);
            assertEquals(expected.ledgerPaid(), ledger.paid(),
                    () -> "paid column of " + type);
            // The statement's own arithmetic, so a row's three columns and the balance
            // its screen shows cannot part company.
            assertEquals(expected.balanceChange(), ledger.amount(),
                    () -> "balance move of " + type);

            TreasuryRow treasury = readTreasury(transaction, type, documentId);
            assertEquals(expected.treasuryIn(), treasury.income(),
                    () -> "treasury income of " + type);
            assertEquals(expected.treasuryOut(), treasury.output(),
                    () -> "treasury output of " + type);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    // ---- writing the header ----------------------------------------------------------

    /**
     * Written through the spec's own insert, with the values keyed by column name: the
     * four families order their columns differently on purpose (see
     * {@link DocumentTableSpec}), and hand-written SQL here would be a fifth order to
     * keep in step.
     */
    private static int insertHeader(Connection connection, DocumentType type,
                                    int invoiceType, double paid) throws Exception {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        int documentId = nextId(connection, spec.table(), spec.key());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(spec.key(), documentId);
        values.put(spec.party(), 1);
        values.put(spec.paid(), paid);
        values.put("invoice_type", invoiceType);
        values.put("invoice_date", java.sql.Date.valueOf(java.time.LocalDate.now()));
        values.put("total", TOTAL);
        values.put("discount", DISCOUNT);
        values.put("stock_id", 1);
        values.put("delegate_id", 1);
        values.put("treasury_id", 1);
        values.put("notes", "party-ledger-acceptance");
        values.put("user_id", 1);

        try (PreparedStatement statement = connection.prepareStatement(spec.insertSql())) {
            int index = 1;
            for (String column : spec.insertColumns()) {
                Object value = values.get(column);
                assertNotNull(value, "No test value for column " + column
                        + " of " + spec.table());
                statement.setObject(index++, value);
            }
            assertEquals(1, statement.executeUpdate());
        }
        return documentId;
    }

    private static int nextId(Connection connection, String table, String key)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+3000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    // ---- reading the views ------------------------------------------------------------

    private record LedgerRow(BigDecimal purchase, BigDecimal discount,
                             BigDecimal paid, BigDecimal amount) {
    }

    private record TreasuryRow(BigDecimal income, BigDecimal output) {
    }

    private static LedgerRow readPartyLedger(Connection connection, DocumentType type,
                                             int documentId) throws Exception {
        String view = PartyLedgerSpec.of(type.partyKind()).view();
        // information tells an invoice row from a return row, so the two families cannot
        // be confused even when they happen to share a number.
        String sql = "SELECT purchase, discount, paid FROM " + view
                + " WHERE account_num = ? AND information = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, documentId);
            statement.setInt(2, type.isReturn() ? RETURN_ROW : INVOICE_ROW);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), () -> "No row in " + view + " for " + type
                        + " #" + documentId);
                BigDecimal purchase = money(rows.getBigDecimal("purchase"));
                BigDecimal discount = money(rows.getBigDecimal("discount"));
                BigDecimal paid = money(rows.getBigDecimal("paid"));
                assertTrue(!rows.next(), () -> "More than one row in " + view
                        + " for " + type + " #" + documentId);
                return new LedgerRow(purchase, discount, paid,
                        purchase.subtract(discount).subtract(paid));
            }
        }
    }

    private static TreasuryRow readTreasury(Connection connection, DocumentType type,
                                            int documentId) throws Exception {
        // treasury_balance labels its rows in Arabic rather than by a code; the label is
        // the only thing that separates a document from a return of the same number.
        String sql = "SELECT income, output FROM treasury_balance"
                + " WHERE id_no = ? AND information = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, documentId);
            statement.setString(2, treasuryLabel(type));
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), () -> "No row in treasury_balance for " + type
                        + " #" + documentId);
                return new TreasuryRow(money(rows.getBigDecimal("income")),
                        money(rows.getBigDecimal("output")));
            }
        }
    }

    /**
     * The label is now declared once, in {@link MovementLabel}, and held against the
     * view by {@code MovementLabelTest} - so a branch renamed in {@code R__views.sql}
     * fails there rather than making this test look for a row that no longer exists.
     */
    private static String treasuryLabel(DocumentType type) {
        return switch (type) {
            case SALES -> MovementLabel.SALES.text();
            case SALES_RETURN -> MovementLabel.SALES_RETURNS.text();
            case PURCHASE -> MovementLabel.PURCHASES.text();
            case PURCHASE_RETURN -> MovementLabel.PURCHASE_RETURNS.text();
        };
    }

    /** The views return DECIMAL(14,2); the effect rounds to the same scale. */
    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }
}
