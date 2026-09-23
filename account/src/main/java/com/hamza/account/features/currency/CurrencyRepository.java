package com.hamza.account.features.currency;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The currencies and their rates, as one seam - so {@link CurrencyService} can be tested without a database. */
public interface CurrencyRepository {

    /** Every currency, stopped ones included, the base first. */
    List<Currency> all() throws DaoException;

    /** One currency, or {@code null}. */
    Currency find(int id) throws DaoException;

    /** The base currency, or {@code null} on a database V80 has not reached. */
    Currency base() throws DaoException;

    /** The generated id. */
    int insert(CurrencyDraft draft, int userId) throws DaoException;

    int update(CurrencyDraft draft) throws DaoException;

    int delete(int id) throws DaoException;

    /** Locks every currency row until the transaction ends - see {@link CurrencyQuery#LOCK_ALL_SQL}. */
    void lockAll() throws DaoException;

    /** Takes the base flag off whichever currency has it. */
    void clearBase() throws DaoException;

    /** Puts it on this one, if it is active; the rows changed. */
    int markBase(int id) throws DaoException;

    /** How many rates exist, for every currency together. */
    int rateCount() throws DaoException;

    /** How many treasuries are in a currency other than the base (V81). */
    int foreignTreasuryCount() throws DaoException;

    /** How many active treasuries are in this currency (V81). */
    int activeTreasuryCount(int currencyId) throws DaoException;

    /** The currency, read under a shared lock for a rate about to be written - or {@code null}. */
    Currency lockForRate(int id) throws DaoException;

    /** One currency's rates, newest day first. */
    List<ExchangeRate> rates(int currencyId) throws DaoException;

    /** One rate, or {@code null}. */
    ExchangeRate findRate(int rateId) throws DaoException;

    Optional<RateInForce> rateOn(int currencyId, LocalDate day) throws DaoException;

    /** Per currency id; a currency with no rate on or before the day is absent. */
    Map<Integer, RateInForce> ratesInForce(LocalDate day) throws DaoException;

    boolean rateDayTaken(int currencyId, LocalDate day, int exceptRateId) throws DaoException;

    /** The generated id. */
    int insertRate(ExchangeRateDraft draft, int userId) throws DaoException;

    int updateRate(ExchangeRateDraft draft) throws DaoException;

    int deleteRate(int rateId) throws DaoException;
}
