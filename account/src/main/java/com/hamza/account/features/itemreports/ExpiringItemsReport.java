package com.hamza.account.features.itemreports;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stock that is about to go off, and stock that already has.
 * <p>
 * <b>It reports batches, not items.</b> The same product sits on the shelf in several
 * batches at once, bought on different days, and only one of them is near its date - so an
 * item's total balance is the wrong number against every batch but one. What is at risk is
 * the quantity in <em>that</em> batch, and that is what the report states and totals.
 * <p>
 * "About to expire" means what the business said it means for that product: each item's own
 * {@code alert_days_before_expire} is the window, so milk and tinned food are not warned
 * about on the same schedule. An item that never had a window set gets
 * {@link ExpiringBatch#DEFAULT_ALERT_DAYS}. Setting a date on the screen overrides all of
 * that with one horizon - "show me everything expiring by the end of the month" - which is
 * the question somebody planning a promotion actually asks.
 * <p>
 * <b>Already-expired batches are listed first and are not hidden.</b> They are the ones
 * that cost money, they are still counted in the stock balance, and a report that showed
 * only what is coming would let them sit there indefinitely.
 */
public final class ExpiringItemsReport implements ItemReport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CatalogFactRepository repository;
    /** Injected so the boundaries - today, tomorrow, the day it expires - are testable. */
    private final Clock clock;

    public ExpiringItemsReport(CatalogFactRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    ExpiringItemsReport(CatalogFactRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "items.expiring";
    }

    @Override
    public String titleKey() {
        return "itemreport.expiring.title";
    }

    @Override
    public String descriptionKey() {
        return "itemreport.expiring.description";
    }

    @Override
    public boolean usesDateRange() {
        return true;
    }

    @Override
    public String dateLabelKey() {
        return "itemreport.date.until";
    }

    @Override
    public ItemReportResult run(ItemReportRequest request) throws DaoException {
        return build(repository.expiringBatches(request.filter()), LocalDate.now(clock), request.from());
    }

    /**
     * @param horizon a date the operator asked about, or {@code null} to use each item's own
     *                warning window
     */
    static ItemReportResult build(List<ExpiringBatch> batches, LocalDate today, LocalDate horizon) {
        List<ExpiringBatch> flagged = batches.stream()
                .filter(batch -> isFlagged(batch, today, horizon))
                // Soonest first, and the expired ones therefore at the very top, since their
                // dates are the furthest in the past.
                .sorted(Comparator.comparing(ExpiringBatch::expiry)
                        .thenComparing(ExpiringBatch::name, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<ItemReportRow> rows = new ArrayList<>();
        int expired = 0;
        double expiredValue = 0;
        double atRiskValue = 0;

        for (ExpiringBatch batch : flagged) {
            boolean isExpired = batch.isExpired(today);
            if (isExpired) {
                expired++;
                expiredValue += batch.valueAtCost();
            } else {
                atRiskValue += batch.valueAtCost();
            }
            rows.add(ItemReportRow.item(0, batch.itemId(),
                    batch.itemId(),
                    batch.barcode(),
                    batch.name(),
                    batch.groupName(),
                    batch.unitName(),
                    batch.quantity(),
                    DATE.format(batch.expiry()),
                    // Negative on an expired batch, which reads as "eleven days ago" rather
                    // than hiding the fact behind a zero.
                    (double) batch.daysUntil(today),
                    batch.valueAtCost(),
                    stateLabel(isExpired)));
        }

        return ItemReportResult.of(COLUMNS, rows, List.of(
                new ItemReportResult.Total("itemreport.total.batches", String.valueOf(rows.size())),
                new ItemReportResult.Total("itemreport.total.expired", String.valueOf(expired)),
                new ItemReportResult.Total("itemreport.total.expired.value",
                        UnusedItemsReport.format(expiredValue)),
                new ItemReportResult.Total("itemreport.total.at.risk.value",
                        UnusedItemsReport.format(atRiskValue))));
    }

    /**
     * Whether this batch belongs in the report.
     * <p>
     * An expired batch always does, whatever window or horizon is in play - it is the money
     * already lost, and no setting should be able to hide it.
     */
    static boolean isFlagged(ExpiringBatch batch, LocalDate today, LocalDate horizon) {
        if (batch.isExpired(today)) return true;
        LocalDate limit = horizon != null
                ? horizon
                : today.plusDays(batch.effectiveAlertDays());
        return !batch.expiry().isAfter(limit);
    }

    private static String stateLabel(boolean expired) {
        return LanguageManager.getInstance().getString(
                expired ? "itemreport.state.expired" : "itemreport.state.near.expiry");
    }

    static final List<ItemReportColumn> COLUMNS = List.of(
            ItemReportColumn.count("itemreport.column.code"),
            ItemReportColumn.text("itemreport.column.barcode"),
            ItemReportColumn.name("itemreport.column.name"),
            ItemReportColumn.text("itemreport.column.group"),
            ItemReportColumn.text("itemreport.column.unit"),
            ItemReportColumn.number("itemreport.column.quantity"),
            ItemReportColumn.date("itemreport.column.expiry"),
            ItemReportColumn.count("itemreport.column.days.left"),
            ItemReportColumn.number("itemreport.column.cost.value"),
            ItemReportColumn.text("itemreport.column.state"));
}
