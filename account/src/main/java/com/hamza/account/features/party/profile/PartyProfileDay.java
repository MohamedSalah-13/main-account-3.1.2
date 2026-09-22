package com.hamza.account.features.party.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of a party's documents, read off the headers. Every figure is as the day stored it;
 * {@link #net()} is the day's documents less its returns.
 *
 * @param documentsNet the documents' {@code total - discount}
 * @param cash         what the documents' cash column moved
 * @param returnedNet  the returns' {@code total - discount}, positive
 * @param refunded     what the returns' cash column moved, positive
 */
public record PartyProfileDay(LocalDate day, int documents, BigDecimal documentsNet, BigDecimal cash,
                              BigDecimal headerDiscount, int returns, BigDecimal returnedNet,
                              BigDecimal refunded, BigDecimal returnsHeaderDiscount) {

    public BigDecimal net() {
        return documentsNet.subtract(returnedNet);
    }
}
