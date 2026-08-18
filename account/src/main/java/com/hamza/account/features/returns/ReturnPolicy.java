package com.hamza.account.features.returns;

/**
 * What a business allows when returning against a source invoice.
 * <p>
 * Its own type rather than bare {@code boolean} parameters threaded through
 * {@link ReturnGuard} and {@link ReturnEligibility} - the shape a per-customer or
 * per-item policy grows into without either of those two changing a signature.
 *
 * @param allowExceedingSource whether a return may take back more of an item than its
 *                             source invoice actually sold
 * @param requireSourceInvoice whether a return must name a source invoice at all. Off
 *                             by default: {@code source_invoice_number} is nullable
 *                             precisely so a return entered without one keeps working
 *                             (every return written before {@code V16__return_source.sql}
 *                             has none, and a customer who lost the receipt is a real
 *                             case). On, it closes the gap that lets an item be
 *                             returned that no document says was ever sold - which is
 *                             also the only way stock can be created out of nothing
 *                             through this screen.
 * @param freeReturnLimit      the most a return with no source invoice may be worth,
 *                             or {@code 0} for no ceiling. The middle setting between
 *                             the two extremes {@link #requireSourceInvoice} offers:
 *                             a shop that has to serve the customer who lost the
 *                             receipt still does not want that door open for a
 *                             lorry-load. Judged on the goods being handed back -
 *                             quantity times price, less the line discounts - before
 *                             any document-level discount, since that is what is
 *                             actually leaving the shelf.
 */
public record ReturnPolicy(boolean allowExceedingSource, boolean requireSourceInvoice,
                           double freeReturnLimit) {

    /** No return may exceed what its source sold; a return without a source is allowed, uncapped. */
    public static final ReturnPolicy DEFAULT = new ReturnPolicy(false, false, 0);

    /** The default with {@link #requireSourceInvoice} turned on. */
    public static ReturnPolicy requiringSource() {
        return new ReturnPolicy(false, true, 0);
    }

    /** The default with a ceiling on what a return with no source may be worth. */
    public static ReturnPolicy cappingFreeReturns(double limit) {
        return new ReturnPolicy(false, false, Math.max(limit, 0));
    }

    /** Whether {@link #freeReturnLimit} is set at all - zero means no ceiling. */
    public boolean capsFreeReturns() {
        return freeReturnLimit > 0;
    }
}
