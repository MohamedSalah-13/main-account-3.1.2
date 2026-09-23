package com.hamza.account.features.party.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * The figures above the payments list, added up from the very rows it shows - so they describe the table
 * beside them by construction.
 *
 * @param received what came in on this side's own direction: collected from customers, or paid to
 *                 suppliers
 * @param returned what went the other way, as a positive amount: handed back to a customer, or refunded
 *                 by a supplier
 * @param total    the cash net of anything handed back, as the till saw it
 */
public record PartyPaymentsSummary(int movements, BigDecimal received, BigDecimal returned, BigDecimal total) {

    public static PartyPaymentsSummary of(List<PartyPaymentRow> rows) {
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        for (PartyPaymentRow row : rows) {
            if (row.paid().signum() >= 0) {
                received = received.add(row.paid());
            } else {
                returned = returned.add(row.paid().negate());
            }
        }
        return new PartyPaymentsSummary(rows.size(), received, returned, received.subtract(returned));
    }
}
