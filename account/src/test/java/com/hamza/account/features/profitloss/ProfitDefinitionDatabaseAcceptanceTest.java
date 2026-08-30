package com.hamza.account.features.profitloss;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
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
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one test that says the profit is right, against a real MySQL.
 * <p>
 * {@code ProfitDefinitionTest} reads {@code R__views.sql} as text and checks that every
 * screen goes to {@code document_profit} for its answer. That is worth having - it is
 * what stops a fifth definition appearing - but it cannot say the view runs, and it
 * certainly cannot say the number is right. The whole change is view SQL: an arithmetic
 * rule spread over a {@code UNION ALL}, two grouped sub-selects and four readers, and no
 * amount of pinned text says what MySQL makes of it.
 * <p>
 * So this runs a scenario a person can check with a pen - two sales lines, an
 * invoice-level discount, a partial return that carries one too, and an expense - and
 * asserts that the screens which report a profit report the same one.
 * <p>
 * <b>The discount is the whole point of the fixture.</b> The four old definitions were
 * each internally consistent and disagreed only where a discount appeared, so a scenario
 * without one passes against every one of them and proves nothing.
 * {@link #theStoredColumnIsTheOldAnswerAndIsNotUsed()} states that gap in figures rather
 * than in prose.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. Everything runs inside one
 * transaction that is always rolled back, in the manner of
 * {@code TreasuryBalanceViewAcceptanceTest} and {@code ItemMergeDatabaseAcceptanceTest},
 * so a developer's database is left exactly as it was.
 * <p>
 * The database has to be migrated first - {@code R__views.sql} builds
 * {@code document_profit} - which running the application once against it does.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ProfitDefinitionDatabaseAcceptanceTest {

    /**
     * A day no real document can be on, so the whole-database views - the statement
     * groups by date, the yearly report by month - see this fixture and nothing else.
     * {@link #requireQuietYear(Connection)} refuses to guess: if anything is already
     * there, the test says so rather than reporting someone else's trade as this one's.
     */
    private static final LocalDate DAY = LocalDate.of(2097, 3, 7);

    // The sale: two lines, 200 of goods, 30 off the invoice.
    private static final double LINE_ONE_REVENUE = 100;   // 10 x 10
    private static final double LINE_ONE_COST = 60;       // 10 x 6
    private static final double LINE_TWO_REVENUE = 100;   // 5 x 20
    private static final double LINE_TWO_COST = 60;       // 5 x 12
    private static final double SALE_TOTAL = LINE_ONE_REVENUE + LINE_TWO_REVENUE;   // 200
    private static final double SALE_DISCOUNT = 30;
    private static final double SALE_COST = LINE_ONE_COST + LINE_TWO_COST;          // 120
    private static final double SALE_NET = SALE_TOTAL - SALE_DISCOUNT;              // 170
    private static final double SALE_PROFIT = SALE_NET - SALE_COST;                 // 50

    /** What the stored per-line column holds: gross of the invoice discount. */
    private static final double SALE_STORED_LINE_PROFIT =
            (LINE_ONE_REVENUE - LINE_ONE_COST) + (LINE_TWO_REVENUE - LINE_TWO_COST);   // 80

    // The return: one line, 20 of goods back, 5 off it.
    private static final double RETURN_TOTAL = 20;        // 2 x 10
    private static final double RETURN_DISCOUNT = 5;
    private static final double RETURN_COST = 12;         // 2 x 6
    private static final double RETURN_NET = RETURN_TOTAL - RETURN_DISCOUNT;        // 15
    private static final double RETURN_PROFIT = RETURN_NET - RETURN_COST;           // 3

    private static final double EXPENSE = 25;

    // What a person reaches with a pen, and what every screen has to say.
    private static final double NET_SALES = SALE_NET - RETURN_NET;                  // 155
    private static final double COST_OF_SALES = SALE_COST - RETURN_COST;            // 108
    private static final double GROSS_PROFIT = NET_SALES - COST_OF_SALES;           // 47
    private static final double NET_PROFIT = GROSS_PROFIT - EXPENSE;                // 22

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

        // ProfitLossService requires the permission now - that is half of what the parent
        // commit fixed - so the report cannot be read without one. The refusal itself
        // needs no database; here the point is to reach the SQL behind it.
        UserSessionContext session = new UserSessionContext();
        session.signIn(1, "admin", List.of(AppPermissions.REPORTS_SHOW_PROFIT));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    // ------------------------------------------------------------------
    // The rule itself
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a sale earns its net revenue less the recorded cost of its lines")
    void theSaleProfitIsNetOfTheInvoiceDiscount() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            Profit sale = documentProfit(connection, "sales", fixture.sale());

            assertEquals(money(SALE_NET), sale.netRevenue(),
                    "net revenue is not total - discount");
            assertEquals(money(SALE_COST), sale.costOfSales(),
                    "cost of sales is not the SUM of the lines' total_buy_price");
            assertEquals(money(SALE_PROFIT), sale.profit(),
                    "the sale's profit is not its net revenue less its cost");
        });
    }

    @Test
    @DisplayName("a return is the same rule, signed negative so it sums with a sale")
    void theReturnIsSignedNegative() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            Profit refund = documentProfit(connection, "sales_return", fixture.refund());

            assertEquals(money(-RETURN_NET), refund.netRevenue(),
                    "a return's revenue is not negative; nothing can sum the two families");
            assertEquals(money(-RETURN_COST), refund.costOfSales(),
                    "a return's cost is not negative");
            assertEquals(money(-RETURN_PROFIT), refund.profit(),
                    "a return's profit is not the reverse of the sale it undoes");
        });
    }

    /**
     * The defect, stated in figures. {@code sales.total_profit} is
     * {@code quantity * price - total_buy_price} per line, so it knows nothing of the
     * invoice's discount - and the gap is exactly that discount, every time. This is why
     * a scenario without one would prove nothing.
     */
    @Test
    @DisplayName("the stored per-line column is the old answer, and is not the one reported")
    void theStoredColumnIsTheOldAnswerAndIsNotUsed() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            BigDecimal stored = scalar(connection,
                    "SELECT COALESCE(SUM(total_profit), 0) FROM sales WHERE invoice_number = ?",
                    fixture.sale());
            assertEquals(money(SALE_STORED_LINE_PROFIT), stored,
                    "the fixture no longer stores the line profits the old way, so this test "
                            + "can no longer tell the two definitions apart");

            BigDecimal reported = documentProfit(connection, "sales", fixture.sale()).profit();
            assertNotEquals(stored, reported,
                    "document_profit is reporting the stored column's answer. The invoice "
                            + "carries a discount, so the two cannot agree - unless the discount "
                            + "has stopped being subtracted somewhere.");
            assertEquals(money(SALE_DISCOUNT), stored.subtract(reported),
                    "the two definitions differ by something other than the invoice discount, "
                            + "which means one of them has changed shape");

            // And it is still on the row, untouched: it is history, not an answer.
            assertEquals(money(LINE_ONE_REVENUE - LINE_ONE_COST), scalar(connection,
                            "SELECT total_profit FROM sales WHERE invoice_number = ? ORDER BY id LIMIT 1",
                            fixture.sale()),
                    "a stored line profit was rewritten; past invoices must not move");
        });
    }

    // ------------------------------------------------------------------
    // The screens
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the invoice list shows the document's profit, and the cost behind it")
    void theInvoiceListAgrees() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            assertEquals(money(SALE_PROFIT), scalar(connection,
                            "SELECT total_profit FROM total_sales_names_table WHERE invoice_number = ?",
                            fixture.sale()),
                    "the sales list is not showing document_profit's answer");
            assertEquals(money(SALE_COST), scalar(connection,
                            "SELECT total_buy_price FROM total_sales_names_table WHERE invoice_number = ?",
                            fixture.sale()),
                    "the cost column moved when it stopped being aggregated in the view itself");

            // Against the net, not the gross: the numerator is net of every discount.
            // 50/170 does not divide, and the view rounds it - so the expectation is
            // rounded here rather than money() being taught to round everything, which
            // is what catches an expected figure that was never exact to begin with.
            BigDecimal percent = BigDecimal.valueOf(SALE_PROFIT * 100 / SALE_NET)
                    .setScale(2, RoundingMode.HALF_UP);
            assertEquals(percent, scalar(connection,
                            "SELECT profit_percent FROM total_sales_names_table WHERE invoice_number = ?",
                            fixture.sale()),
                    "profit_percent is not the profit over the net it was earned on");
        });
    }

    /**
     * The returns list shows the magnitude, because it lists returns on their own and
     * "this return gave back 3 of profit" is the useful reading. The negation is in the
     * view, in the open - and the cost is negated with it, which is the part the second
     * aggregation used to hide.
     */
    @Test
    @DisplayName("the returns list shows the magnitude, cost included")
    void theReturnsListAgrees() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            assertEquals(money(RETURN_PROFIT), scalar(connection,
                            "SELECT total_profit FROM total_sales_return_names_table WHERE id = ?",
                            fixture.refund()),
                    "the returns list is not showing the magnitude of document_profit's answer");
            assertEquals(money(RETURN_COST), scalar(connection,
                            "SELECT total_buy_price FROM total_sales_return_names_table WHERE id = ?",
                            fixture.refund()),
                    "the returns list is showing a negative cost. document_profit signs it "
                            + "negative along with the profit; this screen negates both back.");
        });
    }

    @Test
    @DisplayName("the profit and loss statement is what a person reaches with a pen")
    void theStatementIsRight() throws Exception {
        inTransaction(connection -> {
            fixture(connection);

            List<ProfitLossRow> rows = service().load(DAY, DAY);

            assertEquals(1, rows.size(), "the quiet day did not produce exactly one row");
            ProfitLossRow row = rows.get(0);
            assertEquals(DAY, row.date());
            assertEquals(money(NET_SALES), money(row.netSales()), "net sales");
            assertEquals(money(COST_OF_SALES), money(row.costOfSales()), "cost of sales");
            assertEquals(money(GROSS_PROFIT), money(row.grossProfit()), "gross profit");
            assertEquals(money(EXPENSE), money(row.expenses()), "expenses");
            assertEquals(money(NET_PROFIT), money(row.netProfit()), "net profit");
        });
    }

    /**
     * The reason the whole thing exists: an owner comparing two screens for one month
     * must not see two profits. This is the assertion the four definitions failed - the
     * yearly report used to answer {@code sales - purchases - expenses}, which is 150 for
     * this fixture against the statement's 22.
     */
    @Test
    @DisplayName("the yearly report and the statement report one profit, not two")
    void theTwoScreensAgree() throws Exception {
        inTransaction(connection -> {
            fixture(connection);

            BigDecimal fromStatement = service().load(DAY, DAY).get(0).netProfit();
            BigDecimal fromYearly = scalar(connection, """
                    SELECT estimated_net_profit FROM view_yearly_monthly_report
                    WHERE report_year = ? AND report_month = ?
                    """, DAY.getYear(), DAY.getMonthValue());

            assertEquals(money(NET_PROFIT), fromYearly, "the yearly report's net profit");
            assertEquals(money(fromStatement), fromYearly,
                    "the two screens report different profits for the same month - which is "
                            + "the defect document_profit exists to end");
        });
    }

    /**
     * The expenses column read {@code treasury_transfers} - money moved between two of
     * the business's own tills - while the real expenses were not in the report at all. A
     * transfer is in the fixture precisely so the old column has something to pick up.
     */
    @Test
    @DisplayName("the yearly report's expenses are expenses, not transfers between tills")
    void theYearlyExpensesAreReal() throws Exception {
        inTransaction(connection -> {
            fixture(connection);

            assertEquals(money(EXPENSE), scalar(connection, """
                            SELECT expenses FROM view_yearly_monthly_report
                            WHERE report_year = ? AND report_month = ?
                            """, DAY.getYear(), DAY.getMonthValue()),
                    "the expenses column is not reading expenses_details. A treasury transfer "
                            + "on the same day is in the fixture, and picking that up instead is "
                            + "exactly what the report used to do.");
        });
    }

    // ------------------------------------------------------------------
    // The period filter - the other half of the parent commit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("one bound filters on its own rather than being dropped")
    void aSingleBoundIsHonoured() throws Exception {
        inTransaction(connection -> {
            fixture(connection);
            ProfitLossService service = service();

            // "Everything from the day after" must not include the day itself. Dropped, this
            // returned the whole history and was read as the period's profit.
            assertFalse(containsTheDay(service.load(DAY.plusDays(1), null)),
                    "a from-bound on its own was ignored");
            assertFalse(containsTheDay(service.load(null, DAY.minusDays(1))),
                    "a to-bound on its own was ignored");
            assertTrue(containsTheDay(service.load(DAY, null)),
                    "a from-bound on its own excluded its own day");
            assertTrue(containsTheDay(service.load(null, DAY)),
                    "a to-bound on its own excluded its own day");
        });
    }

    private boolean containsTheDay(List<ProfitLossRow> rows) {
        return rows.stream().anyMatch(row -> DAY.equals(row.date()));
    }

    private ProfitLossService service() {
        return new ProfitLossService(new ProfitLossDao());
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    private record Fixture(long sale, long refund) {
    }

    private Fixture fixture(Connection connection) throws Exception {
        requireView(connection);
        requireQuietYear(connection);

        int customer = firstId(connection, "custom", "id");
        int employee = firstId(connection, "employees", "id");
        int treasury = firstId(connection, "treasury", "id");
        int stock = firstId(connection, "stocks", "stock_id");
        int unit = firstId(connection, "units", "unit_id");
        int item = anItem(connection, unit);

        long sale = insertSale(connection, customer, employee, treasury, stock);
        insertSaleLine(connection, sale, item, unit, 10, 10, 6, LINE_ONE_REVENUE, LINE_ONE_COST);
        insertSaleLine(connection, sale, item, unit, 5, 20, 12, LINE_TWO_REVENUE, LINE_TWO_COST);

        long refund = insertReturn(connection, customer, employee, treasury, stock);
        insertReturnLine(connection, refund, item, unit);

        execute(connection, """
                INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id)
                VALUES (?, ?, ?, 'profit-acceptance', 0, ?, 1)
                """, expenseType(connection), sqlDate(), EXPENSE, treasury);

        // Not an expense, and here to prove the report has stopped counting it as one.
        // It needs a second till to go to - treasury_transfers_not_same_chk refuses a
        // transfer to the treasury it left, which is the database saying the same thing
        // this test does: moving money to yourself is not a movement.
        execute(connection, """
                INSERT INTO treasury_transfers (treasury_from, treasury_to, amount, transfer_date,
                                                notes, user_id)
                VALUES (?, ?, ?, ?, 'profit-acceptance', 1)
                """, treasury, anotherTreasury(connection), 999, sqlDate());

        return new Fixture(sale, refund);
    }

    private long insertSale(Connection connection, int customer, int employee, int treasury,
                            int stock) throws Exception {
        DocumentTableSpec spec = DocumentTableSpec.of(DocumentType.SALES);
        long id = nextId(connection, spec.table(), spec.key());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(spec.key(), id);
        values.put(spec.party(), customer);
        values.put("invoice_type", 1);
        values.put("invoice_date", sqlDate());
        values.put("total", SALE_TOTAL);
        values.put("discount", SALE_DISCOUNT);
        values.put(spec.paid(), SALE_NET);
        values.put("stock_id", stock);
        values.put("delegate_id", employee);
        values.put("treasury_id", treasury);
        values.put("notes", "profit-acceptance");
        values.put("user_id", 1);

        insert(connection, spec.insertSql(), spec.insertColumns(), values, spec.table());
        return id;
    }

    private long insertReturn(Connection connection, int customer, int employee, int treasury,
                              int stock) throws Exception {
        DocumentTableSpec spec = DocumentTableSpec.of(DocumentType.SALES_RETURN);
        long id = nextId(connection, spec.table(), spec.key());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(spec.key(), id);
        values.put(spec.party(), customer);
        values.put("invoice_date", sqlDate());
        values.put("invoice_type", 1);
        values.put("total", RETURN_TOTAL);
        values.put("discount", RETURN_DISCOUNT);
        values.put(spec.paid(), RETURN_NET);
        values.put("stock_id", stock);
        values.put("delegate_id", employee);
        values.put("treasury_id", treasury);
        values.put("notes", "profit-acceptance");
        values.put("user_id", 1);

        insert(connection, spec.insertSql(), spec.insertColumns(), values, spec.table());
        return id;
    }

    /**
     * {@code total_profit} is written the way the invoice screen writes it - gross of the
     * invoice's discount - because that is what every stored row holds, and what this
     * test has to be able to tell apart from the reported answer.
     */
    private void insertSaleLine(Connection connection, long document, int item, int unit,
                                double quantity, double price, double buyPrice,
                                double revenue, double cost) throws Exception {
        execute(connection, """
                INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price,
                                   total_sel_price, total_buy_price, total_profit, discount,
                                   type_value, expiration_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, NULL)
                """, document, item, unit, quantity, price, buyPrice, revenue, cost, revenue - cost);
    }

    private void insertReturnLine(Connection connection, long document, int item, int unit)
            throws Exception {
        execute(connection, """
                INSERT INTO sales_re (invoice_number, item_id, type, quantity, price, buy_price,
                                      total_sel_price, total_buy_price, total_profit, discount,
                                      type_value, expiration_date)
                VALUES (?, ?, ?, 2, 10, 6, ?, ?, ?, 0, 1, NULL)
                """, document, item, unit, RETURN_TOTAL, RETURN_COST, RETURN_TOTAL - RETURN_COST);
    }

    /**
     * The view is built by {@code R__views.sql}, applied when the application runs.
     * Saying so plainly beats an unknown-table error from inside a mapper.
     */
    private void requireView(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW TABLES LIKE 'document_profit'")) {
            assertTrue(rows.next(),
                    "document_profit is missing: run the application once against this database "
                            + "so Flyway applies R__views.sql");
        }
    }

    /**
     * The statement groups by date and the yearly report by month, and neither can be
     * scoped to a fixture the way a treasury balance can be scoped to a treasury. So the
     * year has to be empty - and when it is not, the test names the table to look at
     * rather than quietly reporting somebody else's trade as this one's.
     */
    private void requireQuietYear(Connection connection) throws Exception {
        Map<String, String> dated = new LinkedHashMap<>();
        dated.put("total_sales", "invoice_date");
        dated.put("total_sales_re", "invoice_date");
        dated.put("total_buy", "invoice_date");
        dated.put("total_buy_re", "invoice_date");
        dated.put("expenses_details", "date");

        for (Map.Entry<String, String> table : dated.entrySet()) {
            BigDecimal count = scalar(connection, "SELECT COUNT(*) FROM " + table.getKey()
                    + " WHERE YEAR(" + table.getValue() + ") = ?", DAY.getYear());
            assertEquals(0, count.intValue(), () -> "there are already rows in " + table.getKey()
                    + " in " + DAY.getYear() + ". This test needs a year of its own, because the "
                    + "views it reads group the whole database by date. Move DAY, or clear that "
                    + "year.");
        }
    }

    /** Somewhere for the transfer to go. Its name is unique; the transaction is rolled back. */
    private int anotherTreasury(Connection connection) throws Exception {
        execute(connection, """
                INSERT INTO treasury (t_name, amount, treasury_type, is_active, sort_order,
                                      fee_percent, opening_date, user_id)
                VALUES (?, 0, 'CASH', 1, 0, 0, ?, 1)
                """, "profit-acceptance-" + System.nanoTime(), sqlDate());
        return scalar(connection, "SELECT LAST_INSERT_ID()").intValue();
    }

    /**
     * An item to hang the lines on, created rather than borrowed.
     * <p>
     * Everything else the fixture needs - a customer, an employee, a treasury, a
     * warehouse, a unit - is seeded by {@code V1__baseline.sql}, so a database that has
     * been migrated has one. {@code items} is not seeded: a shop enters its own. Taking
     * whichever item happens to be first would make the test require a populated
     * database, which is the difference between running against a scratch schema and
     * only ever running against a developer's working one.
     */
    private int anItem(Connection connection, int unit) throws Exception {
        BigDecimal existing = scalar(connection, "SELECT COALESCE(MIN(id), 0) FROM items");
        if (existing.intValue() > 0) {
            return existing.intValue();
        }
        execute(connection, """
                INSERT INTO items (barcode, nameItem, sub_num, buy_price, sel_price1, unit_id, user_id)
                VALUES (?, ?, ?, 6, 10, ?, 1)
                """, "PROFIT-ACCEPTANCE", "profit-acceptance", firstId(connection, "sub_group", "id"),
                unit);
        return scalar(connection, "SELECT LAST_INSERT_ID()").intValue();
    }

    private int expenseType(Connection connection) throws Exception {
        BigDecimal existing = scalar(connection, "SELECT COALESCE(MIN(id), 0) FROM expenses");
        if (existing.intValue() > 0) {
            return existing.intValue();
        }
        int id = 9_100_001;
        execute(connection, "INSERT INTO expenses (id, expenses_name) VALUES (?, ?)",
                id, "acceptance-" + id);
        return id;
    }

    private int firstId(Connection connection, String table, String key) throws Exception {
        BigDecimal id = scalar(connection, "SELECT COALESCE(MIN(" + key + "), 0) FROM " + table);
        assertTrue(id.intValue() > 0, "no row in " + table + " to hang a document on");
        return id.intValue();
    }

    private long nextId(Connection connection, String table, String key) throws Exception {
        return scalar(connection,
                "SELECT COALESCE(MAX(" + key + "), 0) + 3000 FROM " + table).longValue();
    }

    // ------------------------------------------------------------------
    // Reading back
    // ------------------------------------------------------------------

    private record Profit(BigDecimal netRevenue, BigDecimal costOfSales, BigDecimal profit) {
    }

    private Profit documentProfit(Connection connection, String kind, long id) throws Exception {
        String sql = """
                SELECT net_revenue, cost_of_sales, profit FROM document_profit
                WHERE document_kind = ? AND document_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kind);
            statement.setLong(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(),
                        "no " + kind + " row in document_profit for document " + id);
                Profit profit = new Profit(money(rows.getBigDecimal("net_revenue")),
                        money(rows.getBigDecimal("cost_of_sales")),
                        money(rows.getBigDecimal("profit")));
                assertFalse(rows.next(),
                        "document_profit returned more than one row for one document - a join in "
                                + "it is multiplying, and every screen reading it is wrong");
                return profit;
            }
        }
    }

    private BigDecimal scalar(Connection connection, String sql, Object... parameters)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "no row for: " + sql);
                return money(rows.getBigDecimal(1));
            }
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private void insert(Connection connection, String sql, List<String> columns,
                        Map<String, Object> values, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String column : columns) {
                Object value = values.get(column);
                assertNotNull(value, "No test value for column " + column + " of " + table);
                statement.setObject(index++, value);
            }
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    private java.sql.Date sqlDate() {
        return java.sql.Date.valueOf(DAY);
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
        assertNotNull(transaction,
                "no transaction was opened; another one is already running on this thread");
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
