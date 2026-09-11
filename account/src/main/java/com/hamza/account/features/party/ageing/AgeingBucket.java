package com.hamza.account.features.party.ageing;

/**
 * How overdue a debt is, in the bands every accountant asks for.
 *
 * <p><b>Overdue is measured from the day the invoice fell due, not from the day it was
 * written.</b> The two are the same only for a party on cash terms. For a customer on
 * thirty days, an invoice dated sixty days ago is thirty days overdue, and a report that
 * called it sixty would put it in the wrong band and overstate what is at risk. The agreed
 * term is {@code custom.payment_terms_days} / {@code suppliers.payment_terms_days}, added
 * by {@code V56} for this report and read by nothing until now.
 *
 * <p>{@link #CURRENT} is not a band of overdue debt at all - it is what is owed but not yet
 * due. It is here because an ageing report that omitted it would not add up to what the
 * party owes, and a column that does not add up is one nobody can check.
 *
 * <p>The bands are inclusive of their upper bound and the last one is open: 1-30, 31-60,
 * 61-90, then everything older. That is the convention the phrase "90 days overdue" comes
 * from, and getting the boundary wrong moves money between two columns that are read side
 * by side.
 */
public enum AgeingBucket {

    /** Owed, but not due yet: {@code daysOverdue <= 0}. */
    CURRENT("party.ageing.bucket.current", Integer.MIN_VALUE, 0),

    DAYS_1_30("party.ageing.bucket.1.30", 1, 30),

    DAYS_31_60("party.ageing.bucket.31.60", 31, 60),

    DAYS_61_90("party.ageing.bucket.61.90", 61, 90),

    /** Everything older, with no upper bound. */
    OVER_90("party.ageing.bucket.over.90", 91, Integer.MAX_VALUE);

    private final String messageKey;
    private final int from;
    private final int to;

    AgeingBucket(String messageKey, int from, int to) {
        this.messageKey = messageKey;
        this.from = from;
        this.to = to;
    }

    /** For display only - the SQL buckets by number, never by a translated label. */
    public String messageKey() {
        return messageKey;
    }

    /** First day of the band, or {@code Integer.MIN_VALUE} for {@link #CURRENT}. */
    public int from() {
        return from;
    }

    /** Last day of the band, or {@code Integer.MAX_VALUE} for {@link #OVER_90}. */
    public int to() {
        return to;
    }

    /** Whether this band is debt that has actually fallen due. */
    public boolean isOverdue() {
        return this != CURRENT;
    }

    /**
     * Which band a debt this many days past its due date belongs in.
     *
     * @param daysOverdue days between the due date and the day the report is as at; zero or
     *                    negative means it has not fallen due yet
     */
    public static AgeingBucket of(long daysOverdue) {
        if (daysOverdue <= 0) {
            return CURRENT;
        }
        for (AgeingBucket bucket : values()) {
            if (bucket != CURRENT && daysOverdue <= bucket.to()) {
                return bucket;
            }
        }
        return OVER_90;
    }

    /**
     * The bands in the order they are read, oldest debt last.
     * <p>
     * A method rather than {@code values()} at the call sites, so the order the report shows
     * is stated once - and so reordering the enum for any other reason cannot silently
     * reorder every column of the report and every column of its export.
     */
    public static AgeingBucket[] inReadingOrder() {
        return new AgeingBucket[]{CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, OVER_90};
    }
}
