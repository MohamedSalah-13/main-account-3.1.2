package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseTotals;

import java.time.LocalDateTime;

/**
 * One saved document's header, with everything the per-family
 * {@link TotalsDataInterface} had to be asked for already resolved.
 *
 * <p>It exists because a screen that no longer names the concrete totals type cannot
 * ask two questions of {@link DataInterface} and correlate the answers: each call on a
 * wildcard-returning method captures an independent type the compiler will not relate
 * to the other's, even though both describe the same class at runtime. Reading the
 * header and resolving it happens in one place - {@link DataInterface#loadInvoiceHeader}
 * - where the type is still concrete, and the screen reads plain fields off the result.
 */
public record InvoiceHeaderView(
        BaseTotals totals,
        String partyName,
        String delegateName,
        int partyId,
        int sourceInvoiceNumber,
        String returnReason,
        LocalDateTime dateInsert) {
}
