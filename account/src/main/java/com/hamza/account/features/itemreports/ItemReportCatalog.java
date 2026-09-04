package com.hamza.account.features.itemreports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every report the items area offers, in the order they are listed.
 * <p>
 * This is the extension point, and it is deliberately the only one: a new report is a class
 * implementing {@link ItemReport} and a line in {@link #reports}. The screen reads this
 * list, so the report appears in it, gets a table, totals, printing, an Excel export and
 * the whole filter panel with nothing else written - and no controller learns its name.
 * <p>
 * It is a plain class rather than a service in {@code ServiceRegistry} because it holds no
 * state and depends on nothing but the repository handed to it. That also means a test can
 * build one over a fake repository and exercise every report in the list.
 */
public final class ItemReportCatalog {

    private final Map<String, ItemReport> byId = new LinkedHashMap<>();

    public ItemReportCatalog(CatalogFactRepository repository) {
        for (ItemReport report : reports(repository)) {
            byId.put(report.id(), report);
        }
    }

    /**
     * The reports, in the order an owner would work through them: what is going off first,
     * because that one has a date attached and the others do not; then what is running out,
     * what the catalogue is worth, what is priced wrongly, what has been sitting still, and
     * finally the whole catalogue by group.
     */
    private static List<ItemReport> reports(CatalogFactRepository repository) {
        return List.of(
                new ExpiringItemsReport(repository),
                new StockLevelReport(repository),
                new ValuationReport(repository),
                new PriceAnomalyReport(repository),
                new UnusedItemsReport(repository),
                new GroupBreakdownReport(repository));
    }

    public List<ItemReport> all() {
        return List.copyOf(byId.values());
    }

    /** The report with this id, or {@code null} - a remembered id may name a report since removed. */
    public ItemReport byId(String id) {
        return byId.get(id);
    }

    public ItemReport first() {
        return byId.values().iterator().next();
    }
}
