package com.hamza.account.model.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two dashboards count a day's money the same way, and bind the parameters they have.
 * <p>
 * There are two of them: {@code daily_dashboard_report} answers "today" from a view, and
 * {@link DashboardPeriodDao} answers an arbitrary period from Java. They are the same
 * question asked twice, so they have to name the same sources - otherwise "today" and "a
 * period that contains today" disagree, and nothing says which is meant.
 * <p>
 * Both were short by the same two: {@code customers_accounts.paid} and
 * {@code suppliers_accounts.paid}, the collections and the payments made against party
 * accounts. {@code treasury_balance} counts both, so the dashboards differed from the
 * treasury statement for the same day by every collection and every settlement - which in
 * a business that sells on credit is most of the money that moved.
 * <p>
 * The parameter count is checked here rather than trusted because the DAO binds its dates
 * from a single {@code PLACEHOLDER_PAIRS} constant. Adding a subquery without touching
 * that line does not fail to compile: it throws at runtime, on a screen, with a parameter
 * index error - which is exactly how a number nobody counted twice reaches a user.
 */
class DashboardPeriodSummaryTest {

    private static final Path DAO =
            Path.of("src", "main", "java", "com", "hamza", "account", "model", "dao",
                    "DashboardPeriodDao.java");
    private static final Path VIEWS =
            Path.of("src", "main", "resources", "db", "migration", "R__views.sql");

    /**
     * What a till takes in and pays out in a day. Transfers between the business's own
     * tills are deliberately in neither: they net to zero across a company-wide figure,
     * which is what both dashboards are.
     */
    private static final List<String> CASH_SOURCES = List.of(
            "total_sales", "total_buy", "total_sales_re", "total_buy_re",
            "customers_accounts", "suppliers_accounts",
            "treasury_deposit_expenses", "expenses_details");

    @Test
    @DisplayName("The period dashboard binds exactly as many date pairs as its SQL has")
    void everyPlaceholderPairIsBound() throws IOException {
        String source = Files.readString(DAO);

        int pairsInSql = count(source, Pattern.compile("BETWEEN \\? AND \\?"));

        Matcher declared = Pattern.compile("PLACEHOLDER_PAIRS = (\\d+)").matcher(source);
        assertTrue(declared.find(), "PLACEHOLDER_PAIRS is gone from DashboardPeriodDao");
        int pairsBound = Integer.parseInt(declared.group(1));

        assertEquals(pairsInSql, pairsBound,
                "the SQL has " + pairsInSql + " date pairs and the DAO binds " + pairsBound
                        + ". A subquery was added or removed without moving PLACEHOLDER_PAIRS, "
                        + "which compiles and then throws a parameter index error on the "
                        + "dashboard the first time someone opens it.");
    }

    @Test
    @DisplayName("Both dashboards count the same sources of cash")
    void theTwoDashboardsAgreeOnWhatCounts() throws IOException {
        String periodSql = Files.readString(DAO);
        String dailySql = dailyDashboardView();

        for (String source : CASH_SOURCES) {
            assertTrue(periodSql.contains(source),
                    "DashboardPeriodDao no longer counts " + source + ", so an arbitrary "
                            + "period and today's figures answer differently");
            assertTrue(dailySql.contains(source),
                    "daily_dashboard_report no longer counts " + source + ", so today and a "
                            + "period containing today answer differently");
        }
    }

    /**
     * Asserted against the two tables the pair of them used to omit, by name, so that a
     * future edit dropping one has to argue with this rather than with nobody.
     */
    @Test
    @DisplayName("Party account movements are counted, on the side the treasury counts them")
    void partyAccountsAreCountedOnTheRightSide() throws IOException {
        String periodSql = Files.readString(DAO);
        String dailySql = dailyDashboardView();

        for (String sql : List.of(periodSql, dailySql)) {
            int receipts = sql.indexOf("customers_accounts");
            int payments = sql.indexOf("suppliers_accounts");
            assertTrue(receipts >= 0 && payments >= 0, "a party account table is not counted");
            assertTrue(receipts < payments,
                    "customers_accounts must be counted before suppliers_accounts - the first "
                            + "is money in and the second is money out, exactly as "
                            + "treasury_balance has them. Reversed, the dashboard reports a "
                            + "day's collections as payments.");
        }
    }

    /** The view's own text, from its CREATE to the statement terminator. */
    private static String dailyDashboardView() throws IOException {
        String sql = Files.readString(VIEWS);
        int start = sql.indexOf("CREATE VIEW daily_dashboard_report AS");
        assertTrue(start >= 0, "daily_dashboard_report is gone from R__views.sql");
        int end = sql.indexOf("DROP VIEW IF EXISTS", start);
        return end < 0 ? sql.substring(start) : sql.substring(start, end);
    }

    private static int count(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }
}
