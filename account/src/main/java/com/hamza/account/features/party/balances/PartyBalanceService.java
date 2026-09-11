package com.hamza.account.features.party.balances;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Objects;

/**
 * The one place a balances list comes from.
 * <p>
 * What it replaces: {@code AccountCustomerService.accountTotalList(null, null)}, called from the one
 * screen that wanted it, which read {@code account_customer_totals} entire on every refresh and let
 * the screen filter, sort and total it in memory. On a shop with five thousand customers that is five
 * thousand rows crossing the wire to show fifty, and the period filter the specification already had
 * could not be reached at all.
 * <p>
 * The permission is the accounts screen's own, and is required on the reads: what every customer owes
 * is not public inside a shop.
 */
public final class PartyBalanceService {

    /** As elsewhere: an extract wide enough to print, narrow enough to hold. */
    public static final int PRINT_LIMIT = 10_000;

    private final PartyBalanceRepository repository;

    public PartyBalanceService() {
        this(new JdbcPartyBalanceRepository());
    }

    public PartyBalanceService(PartyBalanceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** The areas parties of this kind are actually filed under. */
    public List<PartyAreaOption> areas(PartyKind kind) throws DaoException {
        requireShow(kind);
        return repository.areas(kind);
    }

    /**
     * One page, with the figures for the whole filtered set.
     * <p>
     * One row more than the page holds is fetched, and that row is what answers "is there another
     * page" - so there is no second count to fall out of step with the page's own conditions.
     */
    public PartyBalancePage search(PartyBalanceFilter filter) throws DaoException {
        requireShow(filter.partyKind());
        List<PartyBalanceRow> fetched = repository.search(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<PartyBalanceRow> rows = hasNext
                ? List.copyOf(fetched.subList(0, filter.pageSize()))
                : List.copyOf(fetched);
        return new PartyBalancePage(rows, repository.summarize(filter), filter.page(),
                filter.page() > 0, hasNext);
    }

    /**
     * Every matching party, for printing and for export.
     * <p>
     * A query of its own with the same filter, so a printed or exported list is the list on screen -
     * all of it. The screen this replaces exported the <em>ticked</em> rows while asking the table
     * whether it was empty, so ticking nothing wrote an empty file and reported that it had saved.
     */
    public PartyBalancePage forPrint(PartyBalanceFilter filter) throws DaoException {
        requireShow(filter.partyKind());
        PartyBalanceFilter printable = filter.firstPageWithSize(PRINT_LIMIT);
        List<PartyBalanceRow> fetched = repository.search(printable);
        boolean truncated = fetched.size() > PRINT_LIMIT;
        List<PartyBalanceRow> rows = truncated
                ? List.copyOf(fetched.subList(0, PRINT_LIMIT))
                : List.copyOf(fetched);
        return new PartyBalancePage(rows, repository.summarize(printable), 0, false, truncated);
    }

    private static void requireShow(PartyKind kind) throws DaoException {
        AuthorizationGuard.require(PermAccountAndNameInt.forParty(kind).showAccounts());
    }
}
