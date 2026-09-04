package com.hamza.account.features.itemreports;

import com.hamza.account.features.notification.StockLevel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What has run out, gone below its minimum, or gone negative.
 * <p>
 * The three states are one report rather than three because they are read together: a
 * buyer works down the list, and splitting it would mean opening three screens to
 * reconstruct one order. They are ordered by severity, so what needs an answer today is at
 * the top.
 * <p>
 * <b>A negative balance is not a low balance, it is an entry error</b> - stock cannot go
 * below nothing, so the row is saying that something was sold that was never recorded as
 * bought, or that an opening balance is wrong. It ranks above everything else for that
 * reason: buying more of it would not fix it.
 * <p>
 * The boundary itself is {@link StockLevel#of}, which the sale-time alert already uses, so
 * an item this report calls low and the alert that fires when it is sold cannot disagree -
 * including on the rule that a minimum of zero means no minimum is set rather than that
 * everything is low.
 */
public final class StockLevelReport implements ItemReport {

    private final CatalogFactRepository repository;

    public StockLevelReport(CatalogFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public String id() {
        return "items.stock.levels";
    }

    @Override
    public String titleKey() {
        return "itemreport.levels.title";
    }

    @Override
    public String descriptionKey() {
        return "itemreport.levels.description";
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        return build(repository.facts(request.filter(), false));
    }

    static ItemReportResult build(List<CatalogFact> facts) {
        List<ItemReportRow> rows = new ArrayList<>();
        int negative = 0;
        int out = 0;
        int low = 0;

        List<CatalogFact> flagged = facts.stream()
                .filter(fact -> severity(fact) > 0)
                .sorted(Comparator.comparingInt(StockLevelReport::severity).reversed()
                        .thenComparing(CatalogFact::name, Comparator.nullsLast(String::compareTo)))
                .toList();

        for (CatalogFact fact : flagged) {
            int severity = severity(fact);
            switch (severity) {
                case 3 -> negative++;
                case 2 -> out++;
                default -> low++;
            }
            rows.add(ItemReportRow.item(0, fact.id(),
                    fact.id(),
                    fact.barcode(),
                    fact.name(),
                    UnusedItemsReport.groupLabel(fact),
                    fact.unitName(),
                    fact.balance(),
                    fact.minimum(),
                    // How much would have to be bought to reach the minimum again. The
                    // number the buyer is actually after, and the one that is easiest to
                    // get wrong by hand on a negative balance.
                    shortfall(fact),
                    stateLabel(severity)));
        }

        return ItemReportResult.of(COLUMNS, rows, List.of(
                new ItemReportResult.Total("itemreport.total.negative", String.valueOf(negative)),
                new ItemReportResult.Total("itemreport.total.out.of.stock", String.valueOf(out)),
                new ItemReportResult.Total("itemreport.total.below.minimum", String.valueOf(low))));
    }

    /**
     * 3 negative, 2 out of stock, 1 below its minimum, 0 nothing to report.
     * <p>
     * Read straight off {@link StockLevel}, which already draws every boundary and draws
     * them once - restating "balance below zero" here would be a second opinion on the same
     * question, and the two would drift the first time either was corrected.
     */
    static int severity(CatalogFact fact) {
        return switch (StockLevel.of(fact.balance(), fact.minimum())) {
            case NEGATIVE -> 3;
            case OUT_OF_STOCK -> 2;
            case AT_MINIMUM -> 1;
            case OK -> 0;
        };
    }

    /** What has to be bought to reach the minimum, or zero where no minimum is set. */
    static double shortfall(CatalogFact fact) {
        if (fact.minimum() <= 0) return 0;
        return Math.max(0, fact.minimum() - fact.balance());
    }

    private static String stateLabel(int severity) {
        LanguageManager language = LanguageManager.getInstance();
        return switch (severity) {
            case 3 -> language.getString("itemreport.state.negative");
            case 2 -> language.getString("itemreport.state.out.of.stock");
            default -> language.getString("itemreport.state.below.minimum");
        };
    }

    static final List<ItemReportColumn> COLUMNS = List.of(
            ItemReportColumn.count("itemreport.column.code"),
            ItemReportColumn.text("itemreport.column.barcode"),
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.group"),
            ItemReportColumn.text("itemreport.column.unit"),
            ItemReportColumn.number("itemreport.column.balance"),
            ItemReportColumn.number("itemreport.column.minimum"),
            ItemReportColumn.number("itemreport.column.shortfall"),
            ItemReportColumn.text("itemreport.column.state"));
}
