package com.hamza.account.features.offers;

import java.math.BigDecimal;

/**
 * A row of the offers list: the offer without its targets, the name of the unit its amount or price is for,
 * how many targets it has, and what the sale lines naming it were given - {@code usedLines} above zero is
 * what fixes its terms (ق-ع٧).
 */
public record OfferRow(Offer offer, String unitName, int targetCount, int usedLines, BigDecimal given) {

    public boolean used() {
        return usedLines > 0;
    }
}
