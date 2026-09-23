package com.hamza.account.features.treasury;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.JdbcCurrencyRepository;
import com.hamza.account.features.currency.RateInForce;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The currency side of moving cash: which currency a treasury is in, and the rate in force for it on
 * a day (docs/currency-plan.md §11). A seam, so the treasury services can be tested without a database.
 * <p>
 * Both reads are unguarded, as {@code CurrencyService}'s are: they are what a movement is valued with,
 * and a cashier depositing dollars is not administering currencies.
 */
public interface TreasuryCurrencies {

    /** The currency a treasury names; {@code null} for {@code null}, which is the base. */
    Currency find(Integer currencyId) throws DaoException;

    /** The rate in force on {@code day} - the latest dated on it or before it - or {@code null}. */
    BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException;

    /** The rate in force on {@code day}, and a refusal when there is none - never a zero, never a one (ق-٣). */
    default BigDecimal requireRate(int currencyId, LocalDate day) throws DaoException {
        BigDecimal rate = rateOn(currencyId, day);
        if (rate == null || rate.signum() <= 0) {
            throw new UserValidationException("currency.error.no.rate");
        }
        return rate;
    }

    static TreasuryCurrencies jdbc() {
        JdbcCurrencyRepository repository = new JdbcCurrencyRepository();
        return new TreasuryCurrencies() {
            @Override
            public Currency find(Integer currencyId) throws DaoException {
                return currencyId == null ? null : repository.find(currencyId);
            }

            @Override
            public BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException {
                return repository.rateOn(currencyId, day).map(RateInForce::rate).orElse(null);
            }
        };
    }
}
