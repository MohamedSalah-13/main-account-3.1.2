package com.hamza.account.features.stockcount;

/**
 * Where a stock count is in its life.
 * <p>
 * The distinction is the reason a count is a document and not an edit: a shop counts
 * over an afternoon and corrects entries as it goes, and none of that may move a
 * balance while people are still counting. Only {@link #POSTED} reaches
 * {@code quantity_items_table} - see the {@code adjustment_agg} in
 * {@code R__views.sql}, which filters on exactly this.
 */
public enum StockCountStatus {

    /** Being entered. Changes freely, moves nothing. */
    DRAFT("مسودة"),

    /**
     * Posted. Its differences are part of every balance from that moment, and it is
     * read-only: correcting a posted count means posting another one, the way a wrong
     * invoice is answered with a return rather than by editing it.
     */
    POSTED("مرحّل");

    private final String title;

    StockCountStatus(String title) {
        this.title = title;
    }

    public static StockCountStatus of(String name) {
        // An unrecognised value is treated as a draft on purpose: a row that cannot be
        // read must not be one that silently moves stock.
        for (StockCountStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        return DRAFT;
    }

    public String title() {
        return title;
    }

    public boolean isPosted() {
        return this == POSTED;
    }
}
