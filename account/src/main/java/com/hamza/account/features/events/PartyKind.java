package com.hamza.account.features.events;

/**
 * Which side of the ledger a name belongs to: someone the business sells to, or
 * someone it buys from.
 * <p>
 * It is the axis the four name and account publishers were split along, one per
 * combination, and now selects between two events instead.
 */
public enum PartyKind {
    CUSTOMER,
    SUPPLIER
}
