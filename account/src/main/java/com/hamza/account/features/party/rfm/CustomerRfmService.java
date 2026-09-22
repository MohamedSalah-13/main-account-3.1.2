package com.hamza.account.features.party.rfm;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * The one place the recency, frequency and value table comes from.
 *
 * <p>It opens from the customer balances screen, beside the ageing report, and asks what that report
 * asks: the accounts screen's own permission to read it, and {@code reports.show.customers} on top to
 * take it out of the building as a file. The table is customers only: a supplier is not somebody the
 * shop tries to bring back.</p>
 */
public final class CustomerRfmService {

    /** As elsewhere: an extract wide enough to print, narrow enough to hold in memory. */
    public static final int PRINT_LIMIT = 10_000;

    private final CustomerRfmRepository repository;

    public CustomerRfmService() {
        this(new JdbcCustomerRfmRepository());
    }

    public CustomerRfmService(CustomerRfmRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** One page, with the figures for the whole filtered set. */
    public CustomerRfmPage search(CustomerRfmFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        List<CustomerRfmRow> fetched = repository.search(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<CustomerRfmRow> rows = hasNext ? fetched.subList(0, filter.pageSize()) : fetched;
        return new CustomerRfmPage(rows, repository.summarize(filter), filter.page(), filter.page() > 0, hasNext);
    }

    /** Who the filter leaves out as the cash-sales customer, by name - empty when nobody is. */
    public Optional<String> excludedName(CustomerRfmFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        return repository.customerName(filter.excludedParty());
    }

    /**
     * The whole filtered set for a file, read again rather than taken from the table - the
     * {@code PartyAgeingService.forExport} rule, with its permission.
     */
    public CustomerRfmPage forExport(CustomerRfmFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_CUSTOMERS);
        CustomerRfmFilter whole = filter.withPage(0).withPageSize(PRINT_LIMIT);
        List<CustomerRfmRow> rows = repository.search(whole);
        boolean truncated = rows.size() > PRINT_LIMIT;
        return new CustomerRfmPage(truncated ? rows.subList(0, PRINT_LIMIT) : rows,
                repository.summarize(whole), 0, false, truncated);
    }
}
