package com.hamza.account.features.currency;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Currencies, rates and a repository that remembers what it was asked - no database. */
final class CurrencyFixtures {

    /** Not user 1: user 1 bypasses every permission, and a test signed in as it tests nothing. */
    static final int OPERATOR = 7;

    static final Currency EGP = new Currency(1, "EGP", "جنيه مصري", "ج.م", "L.E.", 2, true, true, 1);
    static final Currency SAR = new Currency(2, "SAR", "ريال سعودي", "ر.س", "SAR", 2, false, true, 2);
    static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "", 2, false, true, 3);
    static final Currency KWD = new Currency(4, "KWD", "دينار كويتي", "د.ك", "", 3, false, true, 4);
    static final Currency JPY = new Currency(5, "JPY", "ين ياباني", "¥", "", 0, false, true, 5);
    static final Currency OLD_LIRA = new Currency(6, "TRY", "ليرة تركية", "₺", "", 2, false, false, 6);

    static final LocalDate DAY = LocalDate.of(2026, 9, 23);

    private CurrencyFixtures() {
    }

    static void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    static ExchangeRate rate(int id, Currency currency, LocalDate day, String rate) {
        return new ExchangeRate(id, currency.id(), day, new BigDecimal(rate), null, "operator", null);
    }

    /** Every write it was asked to make, and what it answers reads with. */
    static final class Repository implements CurrencyRepository {

        final List<Currency> currencies = new ArrayList<>();
        final List<ExchangeRate> rates = new ArrayList<>();
        final List<CurrencyDraft> inserted = new ArrayList<>();
        final List<CurrencyDraft> updated = new ArrayList<>();
        final List<Integer> deleted = new ArrayList<>();
        final List<ExchangeRateDraft> insertedRates = new ArrayList<>();
        final List<ExchangeRateDraft> updatedRates = new ArrayList<>();
        final List<Integer> deletedRates = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        /** Makes the next insert fail the way MySQL does on a unique key. */
        String duplicateKey;

        Repository(Currency... currencies) {
            this.currencies.addAll(List.of(currencies));
        }

        @Override
        public List<Currency> all() {
            return List.copyOf(currencies);
        }

        @Override
        public Currency find(int id) {
            return currencies.stream().filter(currency -> currency.id() == id).findFirst().orElse(null);
        }

        @Override
        public Currency base() {
            return currencies.stream().filter(Currency::base).findFirst().orElse(null);
        }

        @Override
        public int insert(CurrencyDraft draft, int userId) throws DaoException {
            failIfDuplicate();
            inserted.add(draft);
            return 40;
        }

        @Override
        public int update(CurrencyDraft draft) throws DaoException {
            failIfDuplicate();
            updated.add(draft);
            return 1;
        }

        @Override
        public int delete(int id) {
            deleted.add(id);
            return 1;
        }

        @Override
        public void lockAll() {
            calls.add("lockAll");
        }

        @Override
        public void clearBase() {
            calls.add("clearBase");
            currencies.replaceAll(currency -> currency.base()
                    ? new Currency(currency.id(), currency.code(), currency.name(), currency.symbol(),
                    currency.latinSymbol(), currency.decimalPlaces(), false, currency.active(), currency.sortOrder())
                    : currency);
        }

        @Override
        public int markBase(int id) {
            calls.add("markBase:" + id);
            Currency target = find(id);
            if (target == null || !target.active()) {
                return 0;
            }
            currencies.replaceAll(currency -> currency.id() == id
                    ? new Currency(currency.id(), currency.code(), currency.name(), currency.symbol(),
                    currency.latinSymbol(), currency.decimalPlaces(), true, currency.active(), currency.sortOrder())
                    : currency);
            return 1;
        }

        @Override
        public int rateCount() {
            return rates.size();
        }

        /** Treasuries per currency id, and whether each is active - V81's two counts. */
        final List<int[]> treasuries = new ArrayList<>();

        @Override
        public int foreignTreasuryCount() {
            return treasuries.size();
        }

        @Override
        public int activeTreasuryCount(int currencyId) {
            return (int) treasuries.stream().filter(t -> t[0] == currencyId && t[1] == 1).count();
        }

        @Override
        public Currency lockForRate(int id) {
            calls.add("lockForRate:" + id);
            return find(id);
        }

        @Override
        public List<ExchangeRate> rates(int currencyId) {
            return rates.stream().filter(rate -> rate.currencyId() == currencyId)
                    .sorted(Comparator.comparing(ExchangeRate::effectiveDate).reversed())
                    .toList();
        }

        @Override
        public ExchangeRate findRate(int rateId) {
            return rates.stream().filter(rate -> rate.id() == rateId).findFirst().orElse(null);
        }

        @Override
        public Optional<RateInForce> rateOn(int currencyId, LocalDate day) {
            List<ExchangeRate> upToDay = rates(currencyId).stream()
                    .filter(rate -> !rate.effectiveDate().isAfter(day))
                    .toList();
            if (upToDay.isEmpty()) {
                return Optional.empty();
            }
            ExchangeRate current = upToDay.get(0);
            BigDecimal previous = upToDay.size() > 1 ? upToDay.get(1).rate() : null;
            return Optional.of(new RateInForce(currencyId, current.effectiveDate(), current.rate(), previous));
        }

        @Override
        public Map<Integer, RateInForce> ratesInForce(LocalDate day) {
            Map<Integer, RateInForce> inForce = new HashMap<>();
            for (Currency currency : currencies) {
                rateOn(currency.id(), day).ifPresent(rate -> inForce.put(currency.id(), rate));
            }
            return inForce;
        }

        @Override
        public boolean rateDayTaken(int currencyId, LocalDate day, int exceptRateId) {
            return rates.stream().anyMatch(rate -> rate.currencyId() == currencyId
                    && rate.effectiveDate().equals(day) && rate.id() != exceptRateId);
        }

        @Override
        public int insertRate(ExchangeRateDraft draft, int userId) throws DaoException {
            failIfDuplicate();
            insertedRates.add(draft);
            return 90;
        }

        @Override
        public int updateRate(ExchangeRateDraft draft) {
            updatedRates.add(draft);
            return 1;
        }

        @Override
        public int deleteRate(int rateId) {
            if (findRate(rateId) == null) {
                return 0;
            }
            deletedRates.add(rateId);
            return 1;
        }

        private void failIfDuplicate() throws DaoException {
            if (duplicateKey != null) {
                String key = duplicateKey;
                duplicateKey = null;
                throw new DaoException("insert failed", new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'USD' for key 'currency." + key + "'"));
            }
        }
    }
}
