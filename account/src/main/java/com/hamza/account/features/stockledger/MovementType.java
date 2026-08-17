package com.hamza.account.features.stockledger;

/**
 * Mirrors {@code stock_movements_type_chk} in {@code V1__baseline.sql} exactly - the
 * database is the source of truth for what a movement may say it is, and this enum's
 * job is only to keep Java from writing a string the check constraint would reject.
 * <p>
 * {@link #TRANSFER_IN}, {@link #TRANSFER_OUT} and {@link #OPENING} have no producer yet:
 * transfers have no live write path (the multi-warehouse screens were removed in
 * {@code 0853cf4}) and the opening balance still lives on {@code items.first_balance}.
 * They are declared so the constraint and this enum stay in lockstep regardless.
 */
public enum MovementType {
    OPENING,
    PURCHASE,
    PURCHASE_RETURN,
    SALE,
    SALE_RETURN,
    TRANSFER_IN,
    TRANSFER_OUT,
    INVENTORY_ADJUST_IN,
    INVENTORY_ADJUST_OUT
}
