package com.hamza.account.features.party.ageing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Objects;

/**
 * The one place an ageing report comes from.
 *
 * <p>It is the first thing built on the allocation the payment work added: without
 * {@code numberInv} being written, "ninety days overdue" could only be guessed from the age of
 * a balance, and a balance has no age - it is a single number with the oldest and the newest
 * debt folded together. See {@code OpenInvoiceQuery}.
 *
 * <p><b>Two permissions, and which one is asked depends on why.</b> Reading what every party
 * owes is the accounts screen's own permission, the same one {@code PartyBalanceService}
 * requires. Exporting it is {@code reports.show.customers} / its supplier twin - three keys
 * that were defined and granted and then reached by no screen at all (§1.2 of the plan). This
 * is what finally connects one of them, and it is deliberately the narrower gate: a list on a
 * screen is looked at, a file walks out of the building.
 */
public final class PartyAgeingService {

    /** As elsewhere: an extract wide enough to print, narrow enough to hold in memory. */
    public static final int PRINT_LIMIT = 10_000;

    private final PartyAgeingRepository repository;

    public PartyAgeingService() {
        this(new JdbcPartyAgeingRepository());
    }

    public PartyAgeingService(PartyAgeingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * One page, with the figures for the whole filtered set.
     * <p>
     * One row more than the page holds is fetched, and that row is what answers "is there another
     * page" - so there is no second count to fall out of step with the page's own conditions.
     */
    public PartyAgeingPage search(PartyAgeingFilter filter) throws DaoException {
        requireShow(filter.kind());
        List<PartyAgeingRow> fetched = repository.search(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<PartyAgeingRow> rows = hasNext
                ? List.copyOf(fetched.subList(0, filter.pageSize()))
                : List.copyOf(fetched);
        return new PartyAgeingPage(rows, repository.summarize(filter), filter.page(),
                filter.page() > 0, hasNext);
    }

    /**
     * The whole filtered set, for printing and for export.
     * <p>
     * Read again rather than taken from the table, so the file and the page cannot describe
     * different sets - the {@code PartyStatementService.forPrint} rule. The report permission is
     * required on top of the screen's.
     */
    public PartyAgeingPage forExport(PartyAgeingFilter filter) throws DaoException {
        requireShow(filter.kind());
        AuthorizationGuard.require(reportPermission(filter.kind()));
        PartyAgeingFilter whole = filter.withPage(0).withPageSize(PRINT_LIMIT);
        List<PartyAgeingRow> rows = repository.search(whole);
        boolean truncated = rows.size() > PRINT_LIMIT;
        return new PartyAgeingPage(truncated ? List.copyOf(rows.subList(0, PRINT_LIMIT)) : rows,
                repository.summarize(whole), 0, false, truncated);
    }

    private static void requireShow(PartyKind kind) throws DaoException {
        AuthorizationGuard.require(PermAccountAndNameInt.forParty(kind).showAccounts());
    }

    /**
     * The report key for this side - one of the three that {@code AppPermissions} defines,
     * {@code V13} grants, and nothing has ever asked for.
     */
    static PermissionKey reportPermission(PartyKind kind) {
        return kind == PartyKind.CUSTOMER
                ? AppPermissions.REPORTS_SHOW_CUSTOMERS
                : AppPermissions.REPORTS_SHOW_SUPPLIERS;
    }
}
