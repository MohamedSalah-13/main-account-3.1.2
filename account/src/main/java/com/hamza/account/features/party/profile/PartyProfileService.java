package com.hamza.account.features.party.profile;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The one place a party's profile comes from.
 *
 * <p><b>Viewing asks what opening the party asks, and a file asks the report key on top.</b> The
 * profile replaced the "what did this customer buy" window, which any reader who could open a party
 * reached from its row - so asking a narrower key to look would take an ability away on upgrade. A
 * file leaves the building, which is why printing and exporting require
 * {@code reports.show.customers}/{@code reports.show.suppliers} as well: the rule
 * {@code PartyAgeingService.forExport} set.</p>
 *
 * <p>The balance is the statement's own figure ({@link PartyStatementService#currentBalance}), read
 * only for a reader who may see accounts; for anybody else it is absent, not zero.</p>
 */
public final class PartyProfileService {

    /** What the party owes today. The statement service in production, a stub in a test. */
    @FunctionalInterface
    public interface BalanceReader {
        BigDecimal balance(PartyKind kind, int partyId) throws DaoException;
    }

    private final PartyProfileRepository repository;
    private final BalanceReader balances;

    public PartyProfileService() {
        this(new JdbcPartyProfileRepository(), new PartyStatementService()::currentBalance);
    }

    public PartyProfileService(PartyProfileRepository repository, BalanceReader balances) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.balances = Objects.requireNonNull(balances, "balances");
    }

    public static PermissionKey viewPermission(PartyKind kind) {
        return PermAccountAndNameInt.forParty(kind).showNames();
    }

    public static PermissionKey exportPermission(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? AppPermissions.REPORTS_SHOW_CUSTOMERS : AppPermissions.REPORTS_SHOW_SUPPLIERS;
    }

    public PartyProfile profile(PartyProfileFilter filter) throws DaoException {
        AuthorizationGuard.require(viewPermission(filter.kind()));
        return read(filter);
    }

    /** The same profile read again for a file - never the rows a screen is holding. */
    public PartyProfile forExport(PartyProfileFilter filter) throws DaoException {
        AuthorizationGuard.require(viewPermission(filter.kind()));
        AuthorizationGuard.require(exportPermission(filter.kind()));
        return read(filter);
    }

    private PartyProfile read(PartyProfileFilter filter) throws DaoException {
        PartyKind kind = filter.kind();
        int party = filter.partyId();
        List<PartyItemRow> items = repository.items(kind, party, filter.from(), filter.to());
        List<PartyItemRow> previous = repository.items(kind, party, filter.previousFrom(), filter.previousTo());
        List<PartyProfileDay> days = repository.days(kind, party, filter.from(), filter.to());
        Optional<LocalDate> last = repository.lastDocument(kind, party);
        Optional<BigDecimal> balance = AuthorizationGuard.isGranted(PermAccountAndNameInt.forParty(kind).showAccounts())
                ? Optional.ofNullable(balances.balance(kind, party))
                : Optional.empty();
        return PartyProfile.build(filter, items, previous, days, last, balance);
    }
}
