package com.hamza.account.features.returns;

/**
 * The one thing a business gets to decide about returning against a source invoice:
 * whether it may ever return more of an item than that invoice actually sold.
 * <p>
 * A single boolean today, and deliberately its own type rather than a bare
 * {@code boolean} parameter threaded through {@link ReturnGuard} and
 * {@link ReturnEligibility} - the shape a per-customer or per-item policy grows into
 * without either of those two changing a signature. {@code allowExceedingSource} is
 * the field a future policy adds a reason code or a manager-approval flag beside, not
 * one it replaces.
 */
public record ReturnPolicy(boolean allowExceedingSource) {

    /** No return may exceed what its source invoice sold - the rule until a business asks otherwise. */
    public static final ReturnPolicy DEFAULT = new ReturnPolicy(false);
}
