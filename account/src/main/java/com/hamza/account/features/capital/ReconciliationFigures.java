package com.hamza.account.features.capital;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What {@link CapitalStatements#RECONCILIATION} reads: what the business holds and owes as recorded
 * today, and the two kinds of movement that change it without passing through the profit.
 *
 * @param customersOwe       what customers owe the business - an asset
 * @param customersInCredit  what the business owes customers who paid ahead or returned on account
 * @param suppliersOwed      what the business owes its suppliers
 * @param suppliersInAdvance what suppliers owe the business - paid ahead of their goods
 * @param stock              the stock on hand at each item's buy price
 * @param customersNonCash   the customers' account movements that moved no cash: debit and credit notes
 * @param suppliersNonCash   the same on the suppliers' side
 * @param ordinaryCash       deposits less withdrawals the treasury recorded outside the owner's capital,
 *                           which {@code ProfitLossDao} does not read
 */
public record ReconciliationFigures(BigDecimal treasuries, BigDecimal customersOwe, BigDecimal customersInCredit,
                                    BigDecimal suppliersOwed, BigDecimal suppliersInAdvance, BigDecimal stock,
                                    BigDecimal customersNonCash, BigDecimal suppliersNonCash,
                                    BigDecimal ordinaryCash) {

    public ReconciliationFigures {
        Objects.requireNonNull(treasuries, "treasuries");
        Objects.requireNonNull(customersOwe, "customersOwe");
        Objects.requireNonNull(customersInCredit, "customersInCredit");
        Objects.requireNonNull(suppliersOwed, "suppliersOwed");
        Objects.requireNonNull(suppliersInAdvance, "suppliersInAdvance");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(customersNonCash, "customersNonCash");
        Objects.requireNonNull(suppliersNonCash, "suppliersNonCash");
        Objects.requireNonNull(ordinaryCash, "ordinaryCash");
    }
}
