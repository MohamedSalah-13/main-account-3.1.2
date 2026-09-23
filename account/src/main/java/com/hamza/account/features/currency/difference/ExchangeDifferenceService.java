package com.hamza.account.features.currency.difference;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.RateInForce;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The exchange differences of every foreign account over a period (docs/currency-plan.md §16).
 *
 * <p><b>It writes nothing.</b> Every figure comes from columns the earlier phases already write - each
 * movement's base figure and its figure in the account's currency - walked at the average rate
 * ({@link ExchangeDifferenceCalculator}). So no balance and no profit moves anywhere; the profit and loss
 * statement shows the result under its net profit and does not count it (ق-هـ١).</p>
 *
 * <p><b>It asks {@code reports.show.profit} before it reads</b> (ق-هـ٧): it is a line of the profit and loss
 * explained, and a row of that statement opens with the same key. A shop with no foreign account reads three
 * short lists and nothing else.</p>
 */
public final class ExchangeDifferenceService {

    /** The currencies and the rates in force on a day - {@link CurrencyService}'s, unguarded there. */
    public interface Rates {
        List<Currency> currencies() throws DaoException;

        Map<Integer, RateInForce> inForce(LocalDate day) throws DaoException;

        static Rates of(CurrencyService service) {
            return new Rates() {
                @Override
                public List<Currency> currencies() throws DaoException {
                    return service.all();
                }

                @Override
                public Map<Integer, RateInForce> inForce(LocalDate day) throws DaoException {
                    return service.ratesInForce(day);
                }
            };
        }
    }

    private final ExchangeDifferenceRepository repository;
    private final Rates rates;

    public ExchangeDifferenceService() {
        this(new JdbcExchangeDifferenceRepository(), Rates.of(new CurrencyService()));
    }

    public ExchangeDifferenceService(ExchangeDifferenceRepository repository, Rates rates) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rates = Objects.requireNonNull(rates, "rates");
    }

    public ExchangeDifferenceReport report(LocalDate from, LocalDate to) throws DaoException {
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_PROFIT);
        if (from == null || to == null) {
            throw new UserValidationException(text("currency.difference.error.period.missing"));
        }
        if (from.isAfter(to)) {
            throw new UserValidationException(text("currency.difference.error.period.order"));
        }
        List<ExchangeAccount> accounts = repository.accounts();
        if (accounts.isEmpty()) {
            return new ExchangeDifferenceReport(from, to, List.of(), ExchangeDifferenceSummary.of(List.of()));
        }
        Map<Integer, Currency> currencies = rates.currencies().stream()
                .collect(Collectors.toMap(Currency::id, Function.identity()));
        return ExchangeDifferenceCalculator.report(from, to, accounts, repository.movements(to), currencies,
                rates.inForce(from.minusDays(1)), rates.inForce(to));
    }

    /**
     * What the profit and loss statement shows for a period: {@link ExchangeFigures#NONE} for a shop with no
     * foreign account, so it draws no line.
     */
    public ExchangeFigures figures(LocalDate from, LocalDate to) throws DaoException {
        return report(from, to).summary().figures();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
