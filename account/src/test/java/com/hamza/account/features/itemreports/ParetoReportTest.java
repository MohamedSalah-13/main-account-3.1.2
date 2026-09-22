package com.hamza.account.features.itemreports;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParetoReportTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);
    private static final List<ItemSalesFact> FACTS = List.of(
            new ItemSalesFact(1, "rice", "food", "kg", 10, 600, 450),
            new ItemSalesFact(2, "juice", null, "piece", 24, 400, 200));

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /** User 2, never user 1: user 1 bypasses every permission and would prove nothing. */
    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "tester", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    /** Not narrowed: the items' net less the invoices' own discounts is the invoices' net. */
    @Test
    void theWholeCatalogueCarriesTheInvoiceDiscountLine() {
        ItemReportResult result = ParetoReport.build(FACTS, ParetoReport.Basis.NET, 25.0);

        assertEquals(ParetoReport.NET_COLUMNS, result.columns());
        assertEquals(List.of("itemreport.pareto.total.class.a", "itemreport.pareto.total.items.net",
                "itemreport.pareto.total.header.discount", "itemreport.pareto.total.invoices.net"),
                result.totals().stream().map(ItemReportResult.Total::labelKey).toList());
        assertEquals(UnusedItemsReport.format(1000), result.totals().get(1).value());
        assertEquals(UnusedItemsReport.format(975), result.totals().get(3).value());
    }

    /** By the margin the ranking turns over: rice sold more, juice earned more. */
    @Test
    void theMarginRanksByTheMarginAndEndsOnTheProfit() {
        ItemReportResult result = ParetoReport.build(FACTS, ParetoReport.Basis.MARGIN, 25.0);

        assertEquals(ParetoReport.MARGIN_COLUMNS, result.columns());
        assertEquals(2, result.rows().getFirst().itemId(), "juice: 200 against rice's 150");
        assertEquals("itemreport.pareto.total.profit", result.totals().getLast().labelKey());
        assertEquals(UnusedItemsReport.format(325), result.totals().getLast().value(), "350 less 25");
    }

    /**
     * Both fit the table at 1366x768, and every row carries one value per column. Drawn the first
     * time, the margin report was three units wider and its class column was behind the scroll bar.
     */
    @Test
    void bothReportsFitTheSmallestScreen() {
        for (ParetoReport.Basis basis : ParetoReport.Basis.values()) {
            ItemReportResult result = ParetoReport.build(FACTS, basis, 25.0);
            int width = result.columns().stream().mapToInt(ItemReportColumn::weight).sum();
            assertTrue(width <= ParetoReport.FITS_THE_SMALLEST_SCREEN, basis + " is " + width + " units wide");
            assertEquals(1, result.columns().getLast().weight(), "the class is one letter");
            for (ItemReportRow row : result.rows()) {
                assertEquals(result.columns().size(), row.values().size(), basis + " row " + row.itemId());
            }
        }
    }

    /** Narrowed to a group, a discount on a whole invoice belongs to it no more than to an item. */
    @Test
    void aNarrowedReportLeavesTheInvoiceDiscountOut() {
        ItemReportResult result = ParetoReport.build(FACTS, ParetoReport.Basis.NET, null);
        assertEquals(2, result.totals().size());
    }

    @Test
    void theNetAsksTheItemsReportKeyAndTheMarginTheProfitsToo() throws Exception {
        Recording repository = new Recording();
        ItemReportRequest request = new ItemReportRequest(ItemCatalogFilter.EMPTY, FROM, TO);

        signIn();
        assertThrows(BusinessRuleException.class, () -> new ParetoReport(repository, ParetoReport.Basis.NET).run(request));

        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        new ParetoReport(repository, ParetoReport.Basis.NET).run(request);
        assertThrows(BusinessRuleException.class, () -> new ParetoReport(repository, ParetoReport.Basis.MARGIN).run(request));

        signIn(AppPermissions.REPORTS_SHOW_ITEMS, AppPermissions.REPORTS_SHOW_PROFIT);
        new ParetoReport(repository, ParetoReport.Basis.MARGIN).run(request);
        assertEquals(List.of("sales", "discounts", "sales", "discounts"), repository.calls,
                "nothing read by the two refused runs");
    }

    @Test
    void aPeriodMustBeWholeAndTheRightWayRound() {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        ParetoReport report = new ParetoReport(new Recording(), ParetoReport.Basis.NET);
        assertThrows(UserValidationException.class,
                () -> report.run(new ItemReportRequest(ItemCatalogFilter.EMPTY, FROM, null)));
        assertThrows(UserValidationException.class,
                () -> report.run(new ItemReportRequest(ItemCatalogFilter.EMPTY, TO, FROM)));
    }

    @Test
    void aNarrowedRunDoesNotAskForTheInvoiceDiscounts() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        Recording repository = new Recording();
        ItemCatalogFilter group = ItemCatalogFilter.EMPTY.withGroup(4, null);

        new ParetoReport(repository, ParetoReport.Basis.NET).run(new ItemReportRequest(group, FROM, TO));

        assertEquals(List.of("sales"), repository.calls);
    }

    @Test
    void bothReportsReadAPeriod() {
        for (ParetoReport.Basis basis : ParetoReport.Basis.values()) {
            ParetoReport report = new ParetoReport(new Recording(), basis);
            assertTrue(report.usesDateRange() && report.usesPeriod(), basis.name());
        }
    }

    /** The report resolves its titles, columns, classes and totals through variables. */
    @Test
    void everyKeyItUsesIsInAllThreeBundles() throws Exception {
        List<String> keys = new ArrayList<>(List.of("itemreport.pareto.net.title", "itemreport.pareto.net.description",
                "itemreport.pareto.margin.title", "itemreport.pareto.margin.description",
                "itemreport.pareto.total.class.a", "itemreport.pareto.total.class.a.value",
                "itemreport.pareto.total.items.net", "itemreport.pareto.total.items.margin",
                "itemreport.pareto.total.header.discount", "itemreport.pareto.total.invoices.net",
                "itemreport.pareto.total.profit", "itemreport.error.period", "itemreport.error.period.reversed"));
        ParetoReport.MARGIN_COLUMNS.forEach(column -> keys.add(column.titleKey()));
        for (ParetoRanking.ParetoClass paretoClass : ParetoRanking.ParetoClass.values()) {
            keys.add(paretoClass.labelKey());
        }
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(in);
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
        }
    }

    private static final class Recording implements ItemSalesRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<ItemSalesFact> sales(ItemCatalogFilter filter, LocalDate from, LocalDate to) {
            calls.add("sales");
            return FACTS;
        }

        @Override
        public double headerDiscounts(LocalDate from, LocalDate to) {
            calls.add("discounts");
            return 25;
        }
    }
}
