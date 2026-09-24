package com.hamza.account.features.offers;

import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;

/**
 * What the offers list is narrowed by: a text in the name, a status, a kind, and the days an offer runs on -
 * an offer is kept when its dates overlap them. A blank text and an absent bound mean "no condition".
 */
public record OfferFilter(String text, OfferStatus status, OfferKind kind, LocalDate runningFrom,
                          LocalDate runningTo) {

    public OfferFilter {
        text = text == null || text.isBlank() ? null : text.strip();
    }

    public static OfferFilter everything() {
        return new OfferFilter(null, null, null, null, null);
    }

    /** The filter, refused when its days run backwards. */
    public static OfferFilter of(String text, OfferStatus status, OfferKind kind, LocalDate runningFrom,
                                 LocalDate runningTo) throws UserValidationException {
        if (runningFrom != null && runningTo != null && runningTo.isBefore(runningFrom)) {
            throw new UserValidationException("offer.filter.error.period");
        }
        return new OfferFilter(text, status, kind, runningFrom, runningTo);
    }

    /**
     * How many conditions the closed filters panel holds: the kind and the days. The text and the status sit
     * in the bar itself, where they can be seen.
     */
    public int panelConditionCount() {
        int count = 0;
        if (kind != null) {
            count++;
        }
        if (runningFrom != null || runningTo != null) {
            count++;
        }
        return count;
    }
}
