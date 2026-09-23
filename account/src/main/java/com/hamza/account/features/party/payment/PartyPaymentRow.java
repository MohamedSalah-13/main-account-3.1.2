package com.hamza.account.features.party.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One cash movement on a party's account, as the payments report lists it.
 *
 * @param paid          the cash, signed as stored: a collection from a customer or a payment to a
 *                      supplier is positive, money handed back the other way is negative
 * @param invoiceNumber the invoice the movement was allocated to, or {@code 0} for "on account"
 * @param userName      who entered it
 */
public record PartyPaymentRow(long movementId, LocalDate date, int partyId, String partyName,
                              BigDecimal paid, String treasuryName, int invoiceNumber, String notes,
                              String userName) {
}
