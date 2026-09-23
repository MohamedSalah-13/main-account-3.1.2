package com.hamza.account.features.currency;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one place currencies and exchange rates are read and written (docs/currency-plan.md, phase A).
 * <p>
 * <b>The catalogue and the rate in force are unguarded, and on purpose.</b> They are what a later phase's
 * invoice and payment screens convert with, and a cashier converting a dollar at the till is
 * not asking to administer currencies - the same line the expense heading pickers draw. A rate is not a
 * figure about a person. What is guarded is the history screen's list ({@link #rates}) and every write.
 */
public final class CurrencyService {

    private final CurrencyRepository repository;
    private final CurrencyTransactions transactions;

    public CurrencyService() {
        this(new JdbcCurrencyRepository(), CurrencyTransactions.jdbc());
    }

    public CurrencyService(CurrencyRepository repository, CurrencyTransactions transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    // ---- the catalogue ---------------------------------------------------------------------

    /** Every currency, stopped ones included - the currencies screen, which is where one is restarted. */
    public List<Currency> all() throws DaoException {
        return repository.all();
    }

    /** What a picker offers: the active currencies, the base first. */
    public List<Currency> active() throws DaoException {
        return repository.all().stream().filter(Currency::active).toList();
    }

    public Currency find(int id) throws DaoException {
        return repository.find(id);
    }

    /**
     * The currency the books are in. V80 makes one and the unique key keeps it to one, so its absence is a
     * database this build has not migrated rather than a state to carry on from.
     */
    public Currency base() throws DaoException {
        Currency base = repository.base();
        if (base == null) {
            throw new BusinessRuleException("currency.error.base.missing");
        }
        return base;
    }

    /**
     * Whether {@link #setBase} would still take a new base: true while no exchange rate is recorded
     * (docs/currency-plan.md ق-٦) and no treasury holds another currency (ق-ب٨). A hint for the settings
     * tab, read outside any lock - {@code setBase} asks the same question again, under one.
     */
    public boolean baseMayChange() throws DaoException {
        return repository.rateCount() == 0 && repository.foreignTreasuryCount() == 0;
    }

    // ---- rates -----------------------------------------------------------------------------

    /** One currency's recorded rates, newest day first. The history is the screen's, so it asks the screen's key. */
    public List<ExchangeRate> rates(int currencyId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_SHOW);
        return repository.rates(currencyId);
    }

    /** Every currency's rate on a day; a currency with none on or before it is absent. */
    public Map<Integer, RateInForce> ratesInForce(LocalDate day) throws DaoException {
        return repository.ratesInForce(Objects.requireNonNull(day, "day"));
    }

    /** One currency's rate on a day, or empty - for the base as well, whose rate is not a row. */
    public Optional<RateInForce> rateOn(int currencyId, LocalDate day) throws DaoException {
        return repository.rateOn(currencyId, Objects.requireNonNull(day, "day"));
    }

    /**
     * The rate a conversion on {@code day} uses: one for the base, the rate in force for anything else, and
     * a refusal when there is none - never a guess (docs/currency-plan.md ق-٣).
     */
    public BigDecimal requireRate(Currency currency, LocalDate day) throws DaoException {
        Objects.requireNonNull(currency, "currency");
        if (currency.base()) {
            return BigDecimal.ONE;
        }
        return repository.rateOn(currency.id(), Objects.requireNonNull(day, "day"))
                .map(RateInForce::rate)
                .orElseThrow(() -> new UserValidationException("currency.error.no.rate"));
    }

    /** {@code amount} of {@code currency} on {@code day}, in the base currency. */
    public BigDecimal toBase(BigDecimal amount, Currency currency, LocalDate day) throws DaoException {
        return CurrencyConverter.toBase(amount, requireRate(currency, day), base());
    }

    /**
     * {@code amount} of one currency written in every other active one at the day's rates - the converter
     * on the currencies screen. A currency with no rate on the day is listed without an amount.
     */
    public List<CurrencyConversion> convert(BigDecimal amount, int fromCurrencyId, LocalDate day)
            throws DaoException {
        Objects.requireNonNull(amount, "amount");
        Currency from = repository.find(fromCurrencyId);
        if (from == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        return CurrencyConversion.table(amount, from, active(), ratesInForce(day));
    }

    // ---- writing currencies ----------------------------------------------------------------

    /**
     * Adds or edits a currency.
     *
     * @return the currency's id - generated for a new one
     */
    public int save(CurrencyDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_UPDATE);
        CurrencyRules.requireValid(draft, repository.all());
        if (!draft.isNew() && !draft.active()) {
            CurrencyRules.requireCanStop(repository.find(draft.id()), draft,
                    repository.activeTreasuryCount(draft.id()));
        }
        try {
            if (draft.isNew()) {
                return repository.insert(draft, currentUserId());
            }
            repository.update(draft);
            return draft.id();
        } catch (DaoException failure) {
            // The rules asked first as a courtesy; two people saving one code both pass that, and the
            // unique key refuses the second. Said as the same sentence rather than a reference code.
            String duplicate = duplicateKey(failure);
            if (duplicate != null) {
                throw new UserValidationException(duplicate.contains("currency_name_uk")
                        ? "currency.error.name.taken" : "currency.error.code.taken", failure);
            }
            throw failure;
        }
    }

    /**
     * Makes this currency the one the books are in - allowed only while no exchange rate is recorded
     * (docs/currency-plan.md ق-٦) and no treasury holds another currency (ق-ب٨), and only for an active
     * currency. A treasury put in a currency takes a shared lock on its row through the foreign key, as
     * a rate does, so the same lock keeps both counts true until the flag has moved.
     * <p>
     * Every currency row is locked first. A rate's insert needs a shared lock on its currency's row for
     * the foreign key, so from that moment no rate can be written until this commits, and the count read
     * next stays true until the flag has moved. The flag comes off the old base before it goes on the new
     * one, inside one transaction: the unique key would refuse the other order.
     */
    public void setBase(int currencyId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_UPDATE);
        transactions.execute(() -> {
            repository.lockAll();
            Currency currency = repository.find(currencyId);
            if (currency != null && currency.base()) {
                return null;
            }
            CurrencyRules.requireCanBecomeBase(currency, repository.rateCount(),
                    repository.foreignTreasuryCount());
            repository.clearBase();
            if (repository.markBase(currencyId) != 1) {
                throw new UserValidationException("currency.error.base.inactive");
            }
            return null;
        });
    }

    /**
     * Deletes a currency nothing holds. One with rates is refused by {@code DeletionService} through
     * {@code DeleteRegistry.CURRENCIES} with the count in the message; a currency merely out of use is
     * stopped instead.
     */
    public int delete(int currencyId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_UPDATE);
        CurrencyRules.requireDeletable(repository.find(currencyId));
        return DeletionService.shared()
                .delete(DeleteRegistry.CURRENCIES, currencyId, repository::delete)
                .rowsOrThrow();
    }

    // ---- writing rates ---------------------------------------------------------------------

    /**
     * Records a rate or corrects one.
     * <p>
     * The currency is read under a shared lock inside the transaction, so the base cannot move between
     * "this currency is not the base" and the row being written (see {@link #setBase}).
     *
     * @return the rate's id - generated for a new one
     */
    public int saveRate(ExchangeRateDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_RATE_UPDATE);
        return transactions.execute(() -> {
            Currency currency = repository.lockForRate(draft.currencyId());
            if (!draft.isNew()) {
                ExchangeRate stored = repository.findRate(draft.id());
                if (stored == null) {
                    throw new UserValidationException("currency.rate.error.not.found");
                }
                if (stored.currencyId() != draft.currencyId()) {
                    throw new UserValidationException("currency.rate.error.currency");
                }
            }
            ExchangeRateRules.requireValid(draft, currency);
            if (repository.rateDayTaken(draft.currencyId(), draft.effectiveDate(), Math.max(draft.id(), 0))) {
                throw new UserValidationException("currency.rate.error.day.taken");
            }
            try {
                if (draft.isNew()) {
                    return repository.insertRate(draft, currentUserId());
                }
                repository.updateRate(draft);
                return draft.id();
            } catch (DaoException failure) {
                if (duplicateKey(failure) != null) {
                    throw new UserValidationException("currency.rate.error.day.taken", failure);
                }
                throw failure;
            }
        });
    }

    /**
     * Records rates taken from the internet (docs/currency-plan.md ق-٩, §12), <b>on days that have none</b>.
     * <p>
     * A day that already has a rate keeps it, whoever typed it: the preview was read before its dialog
     * opened, and another till may have recorded one since. Such a day is kept rather than refused, so the
     * rest of the batch still goes in - and it is kept under the currency's row lock, the lock
     * {@link #saveRate} takes, so the check and the insert cannot be separated by another writer's rate. A
     * rate recorded at the same instant elsewhere meets the unique key and is kept the same way. Every rate
     * passes the rules a typed one does, and the whole batch is one transaction.
     */
    public RecordedRates recordFetched(List<ExchangeRateDraft> drafts) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_RATE_UPDATE);
        Objects.requireNonNull(drafts, "drafts");
        for (ExchangeRateDraft draft : drafts) {
            if (!draft.isNew()) {
                throw new IllegalArgumentException("A fetched rate is a new row, never an edit: " + draft.id());
            }
        }
        return transactions.execute(() -> {
            List<Integer> recorded = new ArrayList<>();
            List<Integer> kept = new ArrayList<>();
            for (ExchangeRateDraft draft : drafts) {
                Currency currency = repository.lockForRate(draft.currencyId());
                ExchangeRateRules.requireValid(draft, currency);
                if (repository.rateDayTaken(draft.currencyId(), draft.effectiveDate(), 0)) {
                    kept.add(draft.currencyId());
                    continue;
                }
                try {
                    repository.insertRate(draft, currentUserId());
                    recorded.add(draft.currencyId());
                } catch (DaoException failure) {
                    if (duplicateKey(failure) == null) {
                        throw failure;
                    }
                    kept.add(draft.currencyId());
                }
            }
            return new RecordedRates(recorded, kept);
        });
    }

    /** Deletes one recorded rate. */
    public int deleteRate(int rateId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_RATE_UPDATE);
        int deleted = repository.deleteRate(rateId);
        if (deleted == 0) {
            throw new UserValidationException("currency.rate.error.not.found");
        }
        return deleted;
    }

    /** The duplicate-key message when this failure is one, else {@code null}. */
    private static String duplicateKey(Throwable failure) {
        for (Throwable link = failure; link != null; link = link.getCause()) {
            if (link instanceof SQLIntegrityConstraintViolationException
                    && link.getMessage() != null && link.getMessage().contains("Duplicate entry")) {
                return link.getMessage();
            }
        }
        return null;
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
