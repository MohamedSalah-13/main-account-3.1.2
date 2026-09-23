package com.hamza.account.features.returns.reasons;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the returns reasons report reads. Asks no permission: its service does. */
public interface ReturnReasonsRepository {

    List<ReasonTotal> reasons(ReturnSide side, LocalDate from, LocalDate to) throws DaoException;

    List<ReturnedItem> items(ReturnSide side, LocalDate from, LocalDate to, int limit) throws DaoException;

    /** @param storedReason the reason as stored, or null for the returns that name none */
    List<ReturnDocument> documents(ReturnSide side, LocalDate from, LocalDate to, String storedReason)
            throws DaoException;

    BigDecimal documentsNet(ReturnSide side, LocalDate from, LocalDate to) throws DaoException;
}
