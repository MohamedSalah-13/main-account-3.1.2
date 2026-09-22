package com.hamza.account.features.itemreports;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which few items make most of the period's sales, or of its margin - {@code docs/reports-plan.md}
 * §13, with {@link ParetoRanking} as the arithmetic.
 *
 * <p><b>Before the invoices' own discounts, and the discount is its own line.</b> A discount on a
 * whole invoice belongs to no item, and sharing it out would be an invented rule (§9). So under the
 * table, when the report is not narrowed, the items' total less the invoices' discounts is shown as
 * the invoices' net - or, for the margin, as the profit the profit and loss reports for the same
 * dates. Narrowed to a group, those two lines are left out: a discount on a whole invoice is no more
 * a group's than an item's.</p>
 *
 * <p>By the net it asks what the item sales ranking asks, {@code reports.show.items}; by the margin it
 * asks {@code reports.show.profit} as well, because a margin is a profit.</p>
 */
public final class ParetoReport implements ItemReport {

    public enum Basis {
        NET("items.pareto.net", "itemreport.pareto.net.title", "itemreport.pareto.net.description"),
        MARGIN("items.pareto.margin", "itemreport.pareto.margin.title", "itemreport.pareto.margin.description");

        private final String id;
        private final String titleKey;
        private final String descriptionKey;

        Basis(String id, String titleKey, String descriptionKey) {
            this.id = id;
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
        }
    }

    private final ItemSalesRepository repository;
    private final Basis basis;

    public ParetoReport(ItemSalesRepository repository, Basis basis) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.basis = Objects.requireNonNull(basis, "basis");
    }

    @Override
    public String id() {
        return basis.id;
    }

    @Override
    public String titleKey() {
        return basis.titleKey;
    }

    @Override
    public String descriptionKey() {
        return basis.descriptionKey;
    }

    @Override
    public boolean usesDateRange() {
        return true;
    }

    @Override
    public boolean usesPeriod() {
        return true;
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_ITEMS);
        if (basis == Basis.MARGIN) {
            AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_PROFIT);
        }
        LocalDate from = request.from();
        LocalDate to = request.to();
        if (from == null || to == null) {
            throw new UserValidationException(text("itemreport.error.period"));
        }
        if (from.isAfter(to)) {
            throw new UserValidationException(text("itemreport.error.period.reversed"));
        }
        List<ItemSalesFact> facts = repository.sales(request.filter(), from, to);
        boolean whole = ItemCatalogFilter.EMPTY.equals(request.filter());
        Double headerDiscounts = whole ? repository.headerDiscounts(from, to) : null;
        return build(facts, basis, headerDiscounts);
    }

    /**
     * The rows and the strip under them from the facts alone - no database, so each rule is tested.
     *
     * @param headerDiscounts the invoices' own discounts, or {@code null} when the report is narrowed
     */
    static ItemReportResult build(List<ItemSalesFact> facts, Basis basis, Double headerDiscounts) {
        List<ParetoRanking.Ranked> ranked = ParetoRanking.rank(facts,
                basis == Basis.MARGIN ? ItemSalesFact::margin : ItemSalesFact::net);
        List<ItemReportRow> rows = new ArrayList<>(ranked.size());
        double net = 0;
        double margin = 0;
        for (ParetoRanking.Ranked row : ranked) {
            ItemSalesFact fact = row.fact();
            net += fact.net();
            margin += fact.margin();
            String group = fact.groupName() == null
                    ? GroupBreakdownReport.label(GroupBreakdownReport.UNGROUPED) : fact.groupName();
            String paretoClass = text(row.paretoClass().labelKey());
            rows.add(basis == Basis.MARGIN
                    ? ItemReportRow.item(0, fact.itemId(), (long) row.rank(), fact.name(), group,
                    fact.net(), fact.margin(), row.share(), row.cumulative(), paretoClass)
                    : ItemReportRow.item(0, fact.itemId(), (long) row.rank(), fact.name(), group, fact.quantity(),
                    fact.net(), row.share(), row.cumulative(), paretoClass));
        }

        ParetoRanking.Summary summary = ParetoRanking.summary(ranked);
        List<ItemReportResult.Total> totals = new ArrayList<>();
        totals.add(new ItemReportResult.Total("itemreport.pareto.total.class.a", LanguageManager.getInstance()
                .getString("itemreport.pareto.total.class.a.value", summary.classA(), summary.ranked(),
                        percent(summary.classAItemsPercent()), percent(summary.classAShare()))));
        double items = basis == Basis.MARGIN ? margin : net;
        totals.add(new ItemReportResult.Total(basis == Basis.MARGIN
                ? "itemreport.pareto.total.items.margin" : "itemreport.pareto.total.items.net",
                UnusedItemsReport.format(items)));
        if (headerDiscounts != null) {
            totals.add(new ItemReportResult.Total("itemreport.pareto.total.header.discount",
                    UnusedItemsReport.format(headerDiscounts)));
            totals.add(new ItemReportResult.Total(basis == Basis.MARGIN
                    ? "itemreport.pareto.total.profit" : "itemreport.pareto.total.invoices.net",
                    UnusedItemsReport.format(items - headerDiscounts)));
        }
        return ItemReportResult.of(basis == Basis.MARGIN ? MARGIN_COLUMNS : NET_COLUMNS, rows, totals);
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    /**
     * The class last, read after the cumulative share it comes from, and one letter wide. Both
     * reports come to 18 width units, which is what the table holds at 1366x768: drawn the first
     * time, the class was a text column and the margin report nine columns, and the class - the
     * report's answer - sat behind the horizontal scroll bar. The margin report leaves the
     * quantity to the net one for the same reason.
     */
    static final List<ItemReportColumn> NET_COLUMNS = List.of(
            ItemReportColumn.count("itemreport.column.rank"),
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.group"),
            ItemReportColumn.number("itemreport.pareto.column.quantity"),
            ItemReportColumn.number("itemreport.pareto.column.net"),
            ItemReportColumn.number("itemreport.pareto.column.share"),
            ItemReportColumn.number("itemreport.pareto.column.cumulative"),
            ItemReportColumn.mark("itemreport.pareto.column.class"));

    static final List<ItemReportColumn> MARGIN_COLUMNS = List.of(
            ItemReportColumn.count("itemreport.column.rank"),
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.group"),
            ItemReportColumn.number("itemreport.pareto.column.net"),
            ItemReportColumn.number("itemreport.pareto.column.margin"),
            ItemReportColumn.number("itemreport.pareto.column.share"),
            ItemReportColumn.number("itemreport.pareto.column.cumulative"),
            ItemReportColumn.mark("itemreport.pareto.column.class"));

    /** The width, in {@link ItemReportColumn#weight} units, the table has at 1366x768. */
    static final int FITS_THE_SMALLEST_SCREEN = 18;
}
