package com.hamza.account.features.treasury.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** Application boundary for the read-only statement. */
public final class TreasuryStatementService {
    public static final int PRINT_LIMIT = 10_000;

    private final TreasuryStatementRepository repository;

    public TreasuryStatementService() {
        this(new JdbcTreasuryStatementRepository());
    }

    public TreasuryStatementService(TreasuryStatementRepository repository) {
        this.repository = repository;
    }

    public TreasuryStatementOptions options() throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_SHOW);
        return repository.options();
    }

    public TreasuryStatementPage search(TreasuryStatementFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_SHOW);
        List<TreasuryStatementRow> fetched = repository.search(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<TreasuryStatementRow> pageRows = hasNext
                ? List.copyOf(fetched.subList(0, filter.pageSize())) : List.copyOf(fetched);
        return new TreasuryStatementPage(pageRows, repository.summarize(filter), filter.page(),
                filter.page() > 0, hasNext);
    }

    public TreasuryStatementPrintData forPrint(TreasuryStatementFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_SHOW);
        TreasuryStatementFilter printable = filter.firstPageWithSize(PRINT_LIMIT);
        List<TreasuryStatementRow> fetched = repository.search(printable);
        boolean truncated = fetched.size() > PRINT_LIMIT;
        List<TreasuryStatementRow> rows = truncated
                ? List.copyOf(fetched.subList(0, PRINT_LIMIT)) : List.copyOf(fetched);
        return new TreasuryStatementPrintData(rows, repository.summarize(printable), truncated);
    }
}
