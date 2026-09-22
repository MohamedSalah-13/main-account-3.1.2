package com.hamza.account.features.party.rfm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One customer's recency, frequency and value, and the fifth each falls in.
 *
 * <p>The constructor refuses a row whose value is not what was sold less what came back, and a score
 * outside one to five: a wrong row of this table looks exactly like a right one - three plausible
 * figures and three small numbers - so the arithmetic is checked where the row is made.</p>
 *
 * @param lastDay        the customer's last sale on or before the period's end
 * @param recencyDays    days from that sale to the period's end
 * @param documents      invoices in the period
 * @param sold           what they came to, less their own discounts
 * @param returned       what the period's returns came to, the same way
 * @param net            {@code sold - returned}: the value
 */
public record CustomerRfmRow(int partyId, String name, LocalDate lastDay, int recencyDays, int documents,
                             BigDecimal sold, BigDecimal returned, BigDecimal net,
                             int recencyScore, int frequencyScore, int valueScore) {

    public CustomerRfmRow {
        Objects.requireNonNull(lastDay, "a scored customer has bought at least once");
        sold = Objects.requireNonNull(sold, "sold");
        returned = Objects.requireNonNull(returned, "returned");
        net = Objects.requireNonNull(net, "net");
        if (sold.subtract(returned).compareTo(net) != 0) {
            throw new IllegalArgumentException("customer " + partyId + ": " + sold + " sold less " + returned
                    + " returned is not " + net);
        }
        if (recencyDays < 0 || documents < 0) {
            throw new IllegalArgumentException("customer " + partyId + ": a count below zero");
        }
        requireScore(partyId, recencyScore);
        requireScore(partyId, frequencyScore);
        requireScore(partyId, valueScore);
    }

    /** The three scores added up - what the default order lists by. */
    public int totalScore() {
        return recencyScore + frequencyScore + valueScore;
    }

    /** The three scores as the table writes them, recency first: {@code 5-3-4}. */
    public String scores() {
        return recencyScore + "-" + frequencyScore + "-" + valueScore;
    }

    private static void requireScore(int partyId, int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("customer " + partyId + ": a score of " + score);
        }
    }
}
