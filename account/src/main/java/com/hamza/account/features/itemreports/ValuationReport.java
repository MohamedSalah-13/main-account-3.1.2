package com.hamza.account.features.itemreports;

import com.hamza.controlsfx.database.DaoException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the stock on the shelves is worth, per group and altogether.
 * <p>
 * Three figures per group and they are three different questions, which is why all three
 * are printed rather than one: what the stock <em>cost</em> is the money already spent and
 * is the figure that belongs on a balance sheet; what it would <em>fetch</em> is a
 * best-case sale of everything at the first price tier; and the difference between them is
 * profit that has not happened yet. Printing only the second is how a catalogue comes to be
 * described as an asset worth far more than the business paid for it.
 * <p>
 * <b>This is a valuation of stock, not a profit and loss.</b> Nothing here is revenue and
 * nothing here is an expense - {@code ProfitLossDao} answers that question, from documents,
 * and the two must never be added together.
 */
public final class ValuationReport implements ItemReport {

    private final CatalogFactRepository repository;

    public ValuationReport(CatalogFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public String id() {
        return "items.valuation";
    }

    @Override
    public String titleKey() {
        return "itemreport.valuation.title";
    }

    @Override
    public String descriptionKey() {
        return "itemreport.valuation.description";
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        return build(repository.facts(request.filter(), false));
    }

    static ItemReportResult build(List<CatalogFact> facts) {
        Map<String, List<CatalogFact>> byGroup = new LinkedHashMap<>();
        for (CatalogFact fact : facts) {
            byGroup.computeIfAbsent(
                    fact.mainGroupName() == null ? GroupBreakdownReport.UNGROUPED : fact.mainGroupName(),
                    key -> new ArrayList<>()).add(fact);
        }

        List<ItemReportRow> rows = new ArrayList<>();
        double totalCost = 0;
        double totalSale = 0;
        int totalItems = 0;

        List<Map.Entry<String, List<CatalogFact>>> ordered = byGroup.entrySet().stream()
                // By value, largest first: a valuation is read to find where the money is,
                // and alphabetical order buries the answer in the middle of the page.
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<String, List<CatalogFact>> entry) ->
                                entry.getValue().stream().mapToDouble(CatalogFact::valueAtCost).sum()).reversed())
                .toList();

        for (Map.Entry<String, List<CatalogFact>> group : ordered) {
            double cost = group.getValue().stream().mapToDouble(CatalogFact::valueAtCost).sum();
            double sale = group.getValue().stream().mapToDouble(CatalogFact::valueAtSale).sum();
            totalCost += cost;
            totalSale += sale;
            totalItems += group.getValue().size();
            rows.add(ItemReportRow.group(0,
                    GroupBreakdownReport.label(group.getKey()),
                    (double) group.getValue().size(),
                    cost,
                    sale,
                    sale - cost,
                    marginPercent(cost, sale)));
        }

        rows.add(ItemReportRow.total(0, null, (double) totalItems, totalCost, totalSale,
                totalSale - totalCost, marginPercent(totalCost, totalSale)));

        return ItemReportResult.of(COLUMNS, rows, List.of(
                new ItemReportResult.Total("itemreport.total.cost.value", UnusedItemsReport.format(totalCost)),
                new ItemReportResult.Total("itemreport.total.sale.value", UnusedItemsReport.format(totalSale)),
                new ItemReportResult.Total("itemreport.total.potential.profit",
                        UnusedItemsReport.format(totalSale - totalCost))));
    }

    /**
     * The margin as a share of the sale value, or zero where there is nothing to take a
     * share of. A group holding no stock at all values at zero on both sides, and a
     * percentage of zero is not "no margin" - it is a division that must not be attempted.
     */
    static double marginPercent(double cost, double sale) {
        return sale == 0 ? 0 : ((sale - cost) / sale) * 100;
    }

    static final List<ItemReportColumn> COLUMNS = List.of(
            ItemReportColumn.name("itemreport.column.group"),
            ItemReportColumn.count("itemreport.column.items.count"),
            ItemReportColumn.number("itemreport.column.cost.value"),
            ItemReportColumn.number("itemreport.column.sale.value"),
            ItemReportColumn.number("itemreport.column.potential.profit"),
            ItemReportColumn.number("itemreport.column.margin.percent"));
}
