package com.hamza.account.features.itemreports;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every group, and under each one its sub groups and their items.
 * <p>
 * Three levels in one flat list, each row carrying its depth - see {@link ItemReportRow}
 * for why a flat list rather than a tree. A heading carries its own subtotals, so the
 * report answers "what is in this group" and "what is it worth" at the same time; a group
 * listing without the second question is a list somebody then has to add up by hand.
 * <p>
 * <b>An item whose group was deleted is listed, not dropped.</b> It goes under a heading of
 * its own at the end. The grouped view on the items screen silently skipped these - the
 * item was in the database, absent from the screen, and nothing said why - and a report is
 * the worst possible place to repeat that: the total at the bottom would be short by
 * exactly the items nobody can see.
 */
public final class GroupBreakdownReport implements ItemReport {

    private final CatalogFactRepository repository;

    public GroupBreakdownReport(CatalogFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public String id() {
        return "items.groups";
    }

    @Override
    public String titleKey() {
        return "itemreport.groups.title";
    }

    @Override
    public String descriptionKey() {
        return "itemreport.groups.description";
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        // The last-movement date is not printed here, so it is not paid for.
        return build(repository.facts(request.filter(), false));
    }

    static ItemReportResult build(List<CatalogFact> facts) {
        // Ungrouped items are keyed apart from every real group rather than under a null
        // that a map would collapse with anything else missing a name.
        Map<String, Map<String, List<CatalogFact>>> tree = new LinkedHashMap<>();
        for (CatalogFact fact : facts) {
            String main = fact.mainGroupName() == null ? UNGROUPED : fact.mainGroupName();
            String sub = fact.subGroupName() == null ? UNGROUPED : fact.subGroupName();
            tree.computeIfAbsent(main, key -> new LinkedHashMap<>())
                    .computeIfAbsent(sub, key -> new ArrayList<>())
                    .add(fact);
        }

        List<ItemReportRow> rows = new ArrayList<>();
        double grandCost = 0;
        int grandItems = 0;

        for (Map.Entry<String, Map<String, List<CatalogFact>>> main : sorted(tree)) {
            List<CatalogFact> everythingInMain = main.getValue().values().stream()
                    .flatMap(List::stream).toList();
            double mainCost = everythingInMain.stream().mapToDouble(CatalogFact::valueAtCost).sum();
            grandCost += mainCost;
            grandItems += everythingInMain.size();

            rows.add(groupRow(0, label(main.getKey()), everythingInMain.size(), mainCost));

            for (Map.Entry<String, List<CatalogFact>> sub : main.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String::compareTo)).toList()) {
                double subCost = sub.getValue().stream().mapToDouble(CatalogFact::valueAtCost).sum();
                rows.add(groupRow(1, label(sub.getKey()), sub.getValue().size(), subCost));

                sub.getValue().stream()
                        .sorted(Comparator.comparing(CatalogFact::name, Comparator.nullsLast(String::compareTo)))
                        .forEach(fact -> rows.add(ItemReportRow.item(2, fact.id(),
                                fact.name(),
                                String.valueOf(fact.id()),
                                fact.barcode(),
                                fact.unitName(),
                                null,
                                fact.sellPrice(),
                                fact.balance(),
                                fact.valueAtCost())));
            }
        }

        return ItemReportResult.of(COLUMNS, rows, List.of(
                new ItemReportResult.Total("itemreport.total.groups", String.valueOf(tree.size())),
                new ItemReportResult.Total("itemreport.total.items", String.valueOf(grandItems)),
                new ItemReportResult.Total("itemreport.total.cost.value", UnusedItemsReport.format(grandCost))));
    }

    /**
     * Real groups by name, and the ungrouped heading last however it sorts - it is not a
     * group, and putting it among them would suggest it is one somebody could edit.
     */
    private static List<Map.Entry<String, Map<String, List<CatalogFact>>>> sorted(
            Map<String, Map<String, List<CatalogFact>>> tree) {
        return tree.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Map<String, List<CatalogFact>>>, Boolean>comparing(
                                entry -> UNGROUPED.equals(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .toList();
    }

    /**
     * Keys the ungrouped items apart from every real group.
     * <p>
     * A leading space so it cannot collide with a group somebody actually named
     * "ungrouped", and it never reaches the screen: {@link #label} translates it on the way
     * out. Handing the renderer a message key for one row and a group name for every other
     * row would leave it unable to tell which of the two it was holding.
     */
    static final String UNGROUPED = " ungrouped";

    static String label(String key) {
        return UNGROUPED.equals(key)
                ? LanguageManager.getInstance().getString("itemreport.group.none")
                : key;
    }

    /**
     * A heading and an item fill different columns of the same table, which is what keeps
     * one renderer able to draw both: a group states how many items it holds and what they
     * are worth, and leaves the per-item columns empty. Summing the balance column into the
     * heading would be the tempting alternative and is meaningless - twelve pieces and three
     * cartons do not add up to fifteen of anything.
     */
    private static ItemReportRow groupRow(int depth, String label, int itemCount, double cost) {
        return ItemReportRow.group(depth, label, null, null, null,
                (double) itemCount, null, null, cost);
    }

    static final List<ItemReportColumn> COLUMNS = List.of(
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.code"),
            ItemReportColumn.text("itemreport.column.barcode"),
            ItemReportColumn.text("itemreport.column.unit"),
            ItemReportColumn.count("itemreport.column.items.count"),
            ItemReportColumn.number("itemreport.column.sell.price"),
            ItemReportColumn.number("itemreport.column.balance"),
            ItemReportColumn.number("itemreport.column.cost.value"));
}
