package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What an offer has given so far: invoices and lines naming it, the discount given on them, their net, the
 * first and last day it was used, and the part of its discount the returns naming it gave back. The last day
 * is the floor the offer's end may be brought back to (ق-ع٧).
 */
public record OfferUsage(int invoices, int lines, BigDecimal given, BigDecimal net, LocalDate firstUsed,
                         LocalDate lastUsed, BigDecimal returned) {

    public static final OfferUsage NONE = new OfferUsage(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
            BigDecimal.ZERO);

    public boolean used() {
        return lines > 0;
    }
}
