package com.hamza.account.features.capital;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.profitloss.ProfitLossDao;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.profitloss.ProfitLossService;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The one place the owner's equity comes from.
 *
 * <p><b>Two permissions, because two different things are shown.</b> The owner's movements are
 * {@code treasury.capital}'s - "a statement of the owner's equity, not of the till", as
 * {@code TreasuryCashService.capitalMovements} says. The profit on the statement is the profit and
 * loss screen's, read through {@link ProfitLossService}, which requires {@code reports.show.profit}
 * itself: a reader allowed the capital and not the profit gets the movements and a refusal for the
 * statement, rather than the profit through a side door.</p>
 */
public final class EquityStatementService {

    /** The profit and loss screen's daily rows. {@link ProfitLossService} in production. */
    @FunctionalInterface
    public interface ProfitReader {
        List<ProfitLossRow> load(LocalDate from, LocalDate to) throws DaoException;
    }

    private final CapitalRepository repository;
    private final ProfitReader profit;

    public EquityStatementService() {
        this(new JdbcCapitalRepository(), new ProfitLossService(new ProfitLossDao())::load);
    }

    public EquityStatementService(CapitalRepository repository, ProfitReader profit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.profit = Objects.requireNonNull(profit, "profit");
    }

    /** The owner's movements alone, a row per day and treasury - the movement tab. */
    public List<CapitalDay> movements(CapitalFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_CAPITAL);
        return repository.days(filter.from(), filter.to());
    }

    /**
     * The whole statement. The profit before the period is the profit and loss asked with no start
     * - "everything up to the day before" - which {@code ProfitLossDao.load} answers rather than
     * ignoring a single bound.
     */
    public EquityStatement statement(CapitalFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_CAPITAL);
        List<ProfitLossRow> profitDays = profit.load(filter.from(), filter.to());
        BigDecimal profitBefore = profit.load(null, filter.from().minusDays(1)).stream()
                .map(ProfitLossRow::netProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EquityStatement(filter, repository.broughtForward(), repository.before(filter.from()),
                profitBefore, repository.days(filter.from(), filter.to()), profitDays);
    }
}
