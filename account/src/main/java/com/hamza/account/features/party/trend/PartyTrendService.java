package com.hamza.account.features.party.trend;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Objects;

/**
 * The one place a trend chart comes from.
 * <p>
 * It asks the accounts screen's own permission - the chart is what that screen's period columns
 * say, drawn over time - and asks it before anything is read. The year before is read only when
 * the filter asks for a comparison, so the ordinary chart costs one query.
 */
public final class PartyTrendService {

    private final PartyTrendRepository repository;

    public PartyTrendService() {
        this(new JdbcPartyTrendRepository());
    }

    public PartyTrendService(PartyTrendRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public PartyTrend trend(PartyTrendFilter filter) throws DaoException {
        AuthorizationGuard.require(PermAccountAndNameInt.forParty(filter.kind()).showAccounts());
        List<PartyTrendDay> current = repository.daily(filter.kind(), filter.from(), filter.to(),
                filter.partyId());
        List<PartyTrendDay> previous = filter.compareWithPreviousYear()
                ? repository.daily(filter.kind(), filter.previousFrom(), filter.previousTo(),
                filter.partyId())
                : List.of();
        return PartyTrend.build(filter, current, previous);
    }
}
