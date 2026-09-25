package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * What an offer has given so far: invoices and lines naming it, the discount given on them, their net, the
 * first and last day it was used, the part of its discount the returns naming it gave back, and the units it
 * covered less the ones the returns brought back - what its global limit counts (V86). The last day is the
 * floor the offer's end may be brought back to (ق-ع٧).
 */
public record OfferUsage(int invoices, int lines, BigDecimal given, BigDecimal net, LocalDate firstUsed,
                         LocalDate lastUsed, BigDecimal returned, BigDecimal units) {

    public static final OfferUsage NONE = new OfferUsage(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
            BigDecimal.ZERO, BigDecimal.ZERO);

    public OfferUsage {
        units = units == null ? BigDecimal.ZERO : units;
    }

    public OfferUsage(int invoices, int lines, BigDecimal given, BigDecimal net, LocalDate firstUsed,
                      LocalDate lastUsed, BigDecimal returned) {
        this(invoices, lines, given, net, firstUsed, lastUsed, returned, BigDecimal.ZERO);
    }

    public boolean used() {
        return lines > 0;
    }

    /** The times the offer has been given: its units over its group, to three places. */
    public BigDecimal times(Offer offer) {
        return units.divide(offer.groupSize(), 3, RoundingMode.HALF_UP);
    }
}
