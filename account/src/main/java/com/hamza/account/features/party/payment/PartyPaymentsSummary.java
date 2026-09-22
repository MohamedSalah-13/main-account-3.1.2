package com.hamza.account.features.party.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * The two figures above the payments list, added up from the very rows it shows - so the count and
 * the total describe the table beside them by construction.
 *
 * @param total the cash net of anything handed back, as the till saw it
 */
public record PartyPaymentsSummary(int movements, BigDecimal total) {

    public static PartyPaymentsSummary of(List<PartyPaymentRow> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (PartyPaymentRow row : rows) {
            total = total.add(row.paid());
        }
        return new PartyPaymentsSummary(rows.size(), total);
    }
}
