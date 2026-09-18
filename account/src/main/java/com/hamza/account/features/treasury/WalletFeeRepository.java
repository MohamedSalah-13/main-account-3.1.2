package com.hamza.account.features.treasury;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The rows a wallet fee lives in, as one seam - so {@link WalletFeeService} is tested against memory. */
public interface WalletFeeRepository {

    /** The fee row of a movement, as stored. */
    record StoredFee(int expenseId, LocalDate date, BigDecimal amount, int treasuryId, Integer shiftId) { }

    /** The fee paid for this movement, locked for the rest of the transaction, or {@code null}. */
    StoredFee lockFor(WalletFeeSource source) throws DaoException;

    /** @return the generated expense id */
    int insert(WalletFeeSource source, int headingId, LocalDate date, BigDecimal amount, String notes,
               int treasuryId, int userId, Integer shiftId) throws DaoException;

    int update(int expenseId, LocalDate date, BigDecimal amount, int treasuryId) throws DaoException;

    int delete(int expenseId) throws DaoException;
}
