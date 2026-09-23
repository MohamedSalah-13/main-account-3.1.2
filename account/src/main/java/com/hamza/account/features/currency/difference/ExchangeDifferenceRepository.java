package com.hamza.account.features.currency.difference;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** What the exchange differences are read from: the foreign accounts, and their movements. */
public interface ExchangeDifferenceRepository {

    /** Every treasury, customer and supplier in a currency other than the base, stopped ones included. */
    List<ExchangeAccount> accounts() throws DaoException;

    /** Every movement of those accounts dated up to {@code through}, each account's in its statement's order. */
    List<ExchangeMovement> movements(LocalDate through) throws DaoException;
}
