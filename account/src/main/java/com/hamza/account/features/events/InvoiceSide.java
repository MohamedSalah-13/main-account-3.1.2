package com.hamza.account.features.events;

/**
 * Which half of the ledger an invoice belongs to.
 * <p>
 * It is deliberately two-valued rather than four: returns share a side with the
 * invoices they reverse, exactly as they shared a publisher before, so a screen
 * showing purchases still reloads when a purchase return is saved.
 */
public enum InvoiceSide {
    PURCHASE,
    SALES
}
