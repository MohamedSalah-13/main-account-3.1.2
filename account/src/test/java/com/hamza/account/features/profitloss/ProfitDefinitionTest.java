package com.hamza.account.features.profitloss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One definition of profit, and only one.
 * <p>
 * There were four, and they disagreed by exactly the discounts: the profit and loss
 * screen computed {@code (total - discount) - SUM(total_buy_price)}, the two invoice
 * lists summed the stored {@code total_profit} column - which is gross of the line's
 * discount and of the invoice's - and the yearly report computed
 * {@code sales - purchases - expenses}, a different concept altogether. Every one of
 * them was internally consistent, so nothing looked broken; an owner comparing two
 * screens saw two profits for the same month and no way to tell which was meant.
 * <p>
 * This is the same defect the treasury had with its three balances, and it gets the
 * same treatment: {@code document_profit} says it once and everything reads that.
 * <p>
 * The rule cannot be checked by looking at any single view - it is a property of all
 * of them together - so it is checked here, against the migration file itself, the way
 * {@code WipeCatalogTest} and {@code ItemReferenceRegistryTest} read the schema rather
 * than trusting a declaration about it.
 * <p>
 * Assertions about SQL run against the file with its {@code --} comments stripped;
 * assertions about a comment say so. A test that cannot tell the two apart forbids
 * documenting the very rule it is enforcing.
 */
class ProfitDefinitionTest {

    private static final Path VIEWS =
            Path.of("src", "main", "resources", "db", "migration", "R__views.sql");

    /**
     * The per-item views are exempt and it is not an oversight. They rank items, and an
     * invoice-level discount belongs to no single line, so attributing it needs an
     * allocation rule nobody has agreed on. Both carry a comment saying exactly that.
     */
    private static final List<String> PER_ITEM_VIEWS =
            List.of("view_item_sales_rank", "card_item_view_details");

    @Test
    @DisplayName("document_profit states the rule: net revenue less recorded cost")
    void theRuleIsStatedOnce() {
        String sql = sql();

        assertTrue(sql.contains("CREATE VIEW document_profit AS"),
                "document_profit is gone. It is the single definition of what a document "
                        + "earned; without it every screen computes its own again.");
        assertTrue(sql.contains("(ts.total - ts.discount) - COALESCE(c.cost_of_sales, 0)"),
                "document_profit no longer computes a sale's profit as its net revenue "
                        + "less the recorded cost of its lines. That is the rule; if it has "
                        + "genuinely changed, change it here and say why in the view.");
    }

    @Test
    @DisplayName("no view answers the profit question a second time")
    void nothingComputesProfitOnItsOwn() {
        String sql = sql();

        assertFalse(sql.contains("SUM(total_profit)"),
                "a view is summing the stored sales.total_profit column again. That column "
                        + "is quantity * price - cost, so it is gross of the line's discount "
                        + "and of the invoice's, and summing it produces a profit higher than "
                        + "document_profit by every discount ever given. Read document_profit.");
        assertFalse(sql.contains("SUM(snt.total_profit)"),
                "the same, through a line view's alias");
    }

    @Test
    @DisplayName("both invoice lists read document_profit rather than recomputing")
    void theInvoiceListsAgree() {
        assertTrue(viewBody("total_sales_names_table").contains("JOIN document_profit"),
                "the sales invoice list computes its own profit again");
        assertTrue(viewBody("total_sales_return_names_table").contains("JOIN document_profit"),
                "the sales return list computes its own profit again");
    }

    @Test
    @DisplayName("neither invoice list sums the lines' cost a second time")
    void theInvoiceListsDoNotReaggregateTheCost() {
        for (String view : List.of("total_sales_names_table", "total_sales_return_names_table")) {
            assertFalse(viewBody(view).contains("SUM(total_buy_price)"),
                    view + " is grouping the line table again to reach the cost of sales. "
                            + "document_profit has already taken that SUM - it is how it "
                            + "reaches the profit - and exposes it as cost_of_sales. A view "
                            + "with a UNION cannot be merged, so it is materialised in full "
                            + "on every read; a second aggregation over the same lines "
                            + "doubles that, on the most-opened list in the application.");
        }
    }

    @Test
    @DisplayName("the yearly report's profit is the documents' profit, not sales minus purchases")
    void theYearlyReportAgrees() {
        String yearly = viewBody("view_yearly_monthly_report");

        assertTrue(yearly.contains("ROUND(SUM(t.profit) - SUM(t.expenses), 2) AS estimated_net_profit"),
                "view_yearly_monthly_report is computing its own profit again. It read "
                        + "sales - purchases - expenses, which answers a different question "
                        + "than the profit and loss screen and disagrees with it every month "
                        + "stock levels move.");
        assertTrue(yearly.contains("JOIN document_profit"),
                "view_yearly_monthly_report has stopped reading document_profit");
        assertFalse(yearly.contains("treasury_transfers"),
                "view_yearly_monthly_report is reading treasury_transfers again. A transfer "
                        + "moves money between two of the business's own tills - it is not an "
                        + "expense, it nets to zero across them, and while it sat in the "
                        + "expenses column the real expenses were not in the report at all.");
        assertTrue(yearly.contains("FROM expenses_details"),
                "view_yearly_monthly_report no longer reads the expense table");
    }

    @Test
    @DisplayName("the per-item views are exempt on purpose, and say so")
    void thePerItemViewsAreDocumented() {
        String raw = read(VIEWS);

        for (String view : PER_ITEM_VIEWS) {
            assertTrue(raw.contains(view),
                    "the exemption list names " + view + ", which no longer exists - "
                            + "remove it from PER_ITEM_VIEWS rather than leaving a rule "
                            + "guarding nothing");
        }
        // Deliberately asserted against the comments: the exemption is only safe while
        // the reason for it is written next to the code it excuses.
        assertTrue(raw.contains("Deliberately a different question from document_profit"),
                "view_item_sales_rank computes a per-item profit that does not add up to "
                        + "the profit and loss screen. That is allowed, and the comment "
                        + "explaining why it is a different question rather than a bug is "
                        + "the only thing keeping the next reader from rewriting it.");
    }

    @Test
    @DisplayName("the profit and loss report reads the view - the rule is not decorative")
    void theReportReadsTheView() {
        Path dao = Path.of("src", "main", "java", "com", "hamza", "account", "features",
                "profitloss", "ProfitLossDao.java");
        assertTrue(read(dao).contains("FROM document_profit"),
                "ProfitLossDao has stopped reading document_profit. It is the screen the "
                        + "definition exists for; if it computes its own again, the view is "
                        + "a rule nothing follows.");
    }

    /** The file with every {@code --} comment removed, so prose cannot satisfy or trip a rule. */
    private static String sql() {
        return read(VIEWS).replaceAll("(?m)--.*$", "");
    }

    /** One view's text, from its {@code CREATE VIEW} to the start of the next statement. */
    private static String viewBody(String name) {
        String sql = sql();
        int start = sql.indexOf("CREATE VIEW " + name + " AS");
        assertTrue(start >= 0, "the view " + name + " is gone from R__views.sql");
        int end = sql.indexOf("DROP VIEW IF EXISTS", start);
        return end < 0 ? sql.substring(start) : sql.substring(start, end);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path.toAbsolutePath(), e);
        }
    }
}
