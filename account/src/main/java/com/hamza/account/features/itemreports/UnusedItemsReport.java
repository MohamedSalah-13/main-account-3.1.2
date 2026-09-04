package com.hamza.account.features.itemreports;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Items nothing has been written against - the money standing still.
 * <p>
 * "Unused" means untouched by any of the four documents, not merely unsold. An item that
 * was bought last week and has not sold yet is not dead stock, it is new stock, and putting
 * it in this report is how an owner ends up writing off something that has only just
 * arrived. An item bought two years ago and never sold since is exactly what the report is
 * for, and the last-movement column is what tells the two apart at a glance.
 * <p>
 * With a start date set, the question widens from "never moved at all" to "has not moved
 * since" - which is the more useful one on a catalogue that has been running for years,
 * where almost nothing is literally untouched.
 */
public final class UnusedItemsReport implements ItemReport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CatalogFactRepository repository;

    public UnusedItemsReport(CatalogFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public String id() {
        return "items.unused";
    }

    @Override
    public String titleKey() {
        return "itemreport.unused.title";
    }

    @Override
    public String descriptionKey() {
        return "itemreport.unused.description";
    }

    @Override
    public boolean usesDateRange() {
        return true;
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        List<CatalogFact> facts = repository.facts(request.filter(), true);
        return build(facts, request.from());
    }

    /**
     * The report itself, over facts that are already read - which is what lets every rule
     * below be put through a test without a database behind it.
     *
     * @param since {@code null} for "never moved at all"; otherwise the date the item must
     *              not have moved on or after
     */
    static ItemReportResult build(List<CatalogFact> facts, LocalDate since) {
        List<CatalogFact> idle = facts.stream()
                .filter(fact -> isIdle(fact, since))
                // Most valuable first: the report is read to decide what to do about the
                // money, and the item worth 40,000 standing still matters more than the
                // twenty worth two pounds each, however many of those there are.
                .sorted(Comparator.comparingDouble(CatalogFact::valueAtCost).reversed()
                        .thenComparing(CatalogFact::name, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<ItemReportRow> rows = new ArrayList<>();
        double totalValue = 0;
        for (CatalogFact fact : idle) {
            totalValue += fact.valueAtCost();
            rows.add(ItemReportRow.item(0, fact.id(),
                    fact.id(),
                    fact.barcode(),
                    fact.name(),
                    groupLabel(fact),
                    fact.unitName(),
                    fact.balance(),
                    fact.buyPrice(),
                    fact.valueAtCost(),
                    fact.lastMovement() == null ? null : DATE.format(fact.lastMovement())));
        }

        return ItemReportResult.of(COLUMNS, rows, List.of(
                new ItemReportResult.Total("itemreport.total.items", String.valueOf(rows.size())),
                new ItemReportResult.Total("itemreport.total.cost.value", format(totalValue))));
    }

    /**
     * Whether this item counts as idle.
     * <p>
     * An item that has never moved is idle under either question - the {@code null} last
     * movement has to be admitted explicitly, because comparing it as a date would drop
     * exactly the items the report exists to find.
     */
    private static boolean isIdle(CatalogFact fact, LocalDate since) {
        if (fact.neverMoved()) return true;
        return since != null && fact.lastMovement().isBefore(since);
    }

    static String groupLabel(CatalogFact fact) {
        if (fact.subGroupName() != null) return fact.subGroupName();
        if (fact.mainGroupName() != null) return fact.mainGroupName();
        return "";
    }

    static String format(double value) {
        return String.format("%,.2f", value);
    }

    static final List<ItemReportColumn> COLUMNS = List.of(
            ItemReportColumn.count("itemreport.column.code"),
            ItemReportColumn.text("itemreport.column.barcode"),
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.group"),
            ItemReportColumn.text("itemreport.column.unit"),
            ItemReportColumn.number("itemreport.column.balance"),
            ItemReportColumn.number("itemreport.column.buy.price"),
            ItemReportColumn.number("itemreport.column.cost.value"),
            ItemReportColumn.date("itemreport.column.last.movement"));
}
