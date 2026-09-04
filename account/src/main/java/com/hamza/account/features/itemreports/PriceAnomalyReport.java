package com.hamza.account.features.itemreports;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Items priced in a way that will cost the business money, and items that cannot be sold
 * at all.
 * <p>
 * Every one of these is a row that passes validation today. {@code ItemsService} refuses a
 * sale price at or below cost <em>on the way in</em>, so a catalogue that predates that
 * rule - or that was filled by the Excel import, or by an earlier version of this
 * application - carries the wrong prices with nothing to surface them. That is precisely
 * what a report is for: the rule stops new mistakes, and this finds the ones already on
 * file.
 * <p>
 * The missing barcode is here for a different reason and is not a pricing fault. It is
 * listed because it has the same consequence at the counter: the item cannot be scanned, so
 * it is either typed in by hand or quietly not sold. One report of "items that will cause
 * trouble at the till" is more useful than two lists nobody opens.
 */
public final class PriceAnomalyReport implements ItemReport {

    private final CatalogFactRepository repository;

    public PriceAnomalyReport(CatalogFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public String id() {
        return "items.price.anomalies";
    }

    @Override
    public String titleKey() {
        return "itemreport.anomalies.title";
    }

    @Override
    public String descriptionKey() {
        return "itemreport.anomalies.description";
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        return build(repository.facts(request.filter(), false));
    }

    /** What is wrong with an item, worst first. An item may have several; the worst is reported. */
    enum Fault {
        /** Sold below what it cost. Every sale of it loses money. */
        SELLING_AT_A_LOSS("itemreport.fault.selling.at.a.loss"),
        /** No sale price at all - it would ring up as free. */
        NO_SELL_PRICE("itemreport.fault.no.sell.price"),
        /** Sold at exactly cost. Not a loss, but not a business either. */
        NO_MARGIN("itemreport.fault.no.margin"),
        /** Priced to sell but with no recorded cost, so its profit is reported as the whole price. */
        NO_BUY_PRICE("itemreport.fault.no.buy.price"),
        /** Nothing to scan at the counter. */
        NO_BARCODE("itemreport.fault.no.barcode");

        private final String key;

        Fault(String key) {
            this.key = key;
        }

        String label() {
            return LanguageManager.getInstance().getString(key);
        }
    }

    static ItemReportResult build(List<CatalogFact> facts) {
        record Flagged(CatalogFact fact, Fault fault) {
        }

        List<Flagged> flagged = facts.stream()
                .map(fact -> {
                    Fault fault = faultOf(fact);
                    return fault == null ? null : new Flagged(fact, fault);
                })
                .filter(java.util.Objects::nonNull)
                // Worst fault first, and inside a fault the item holding the most stock -
                // a mispriced item with four hundred on the shelf costs four hundred times
                // what the same mistake costs on an item with one.
                .sorted(Comparator.comparing((Flagged flag) -> flag.fault().ordinal())
                        .thenComparing(Comparator.comparingDouble((Flagged flag) -> flag.fact().balance()).reversed()))
                .toList();

        List<ItemReportRow> rows = new ArrayList<>();
        for (Flagged flag : flagged) {
            CatalogFact fact = flag.fact();
            rows.add(ItemReportRow.item(0, fact.id(),
                    fact.id(),
                    fact.barcode(),
                    fact.name(),
                    UnusedItemsReport.groupLabel(fact),
                    fact.buyPrice(),
                    fact.sellPrice(),
                    fact.sellPrice() - fact.buyPrice(),
                    fact.marginPercent(),
                    fact.balance(),
                    flag.fault().label()));
        }

        return ItemReportResult.of(COLUMNS, rows, List.of(
                new ItemReportResult.Total("itemreport.total.items", String.valueOf(rows.size()))));
    }

    /**
     * The worst thing wrong with this item, or {@code null} if nothing is.
     * <p>
     * Ordered so that the more serious answer wins where several apply: an item with no
     * sale price also has no margin, and reporting it as "no margin" would understate a
     * row that would ring up as free. The missing barcode is tested last because it is the
     * only fault here that costs nothing as long as somebody types the code in.
     */
    static Fault faultOf(CatalogFact fact) {
        if (fact.sellPrice() == 0) return Fault.NO_SELL_PRICE;
        if (fact.sellPrice() < fact.buyPrice()) return Fault.SELLING_AT_A_LOSS;
        if (fact.sellPrice() == fact.buyPrice()) return Fault.NO_MARGIN;
        if (fact.buyPrice() == 0) return Fault.NO_BUY_PRICE;
        if (!fact.hasBarcode()) return Fault.NO_BARCODE;
        return null;
    }

    static final List<ItemReportColumn> COLUMNS = List.of(
            ItemReportColumn.count("itemreport.column.code"),
            ItemReportColumn.text("itemreport.column.barcode"),
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.group"),
            ItemReportColumn.number("itemreport.column.buy.price"),
            ItemReportColumn.number("itemreport.column.sell.price"),
            ItemReportColumn.number("itemreport.column.margin"),
            ItemReportColumn.number("itemreport.column.margin.percent"),
            ItemReportColumn.number("itemreport.column.balance"),
            ItemReportColumn.text("itemreport.column.fault"));
}
