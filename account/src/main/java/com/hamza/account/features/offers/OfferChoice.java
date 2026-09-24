package com.hamza.account.features.offers;

/** Something an offer's form offers to choose - a group or a unit - by id and name. */
public record OfferChoice(int id, String name) {

    @Override
    public String toString() {
        return name;
    }
}
