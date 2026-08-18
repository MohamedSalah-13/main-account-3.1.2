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
 */
public record ReturnPolicy(boolean allowExceedingSource, boolean requireSourceInvoice) {

    /** No return may exceed what its source sold; a return without a source is allowed. */
    public static final ReturnPolicy DEFAULT = new ReturnPolicy(false, false);

    /** The default with {@link #requireSourceInvoice} turned on. */
    public static ReturnPolicy requiringSource() {
        return new ReturnPolicy(false, true);
    }
}
