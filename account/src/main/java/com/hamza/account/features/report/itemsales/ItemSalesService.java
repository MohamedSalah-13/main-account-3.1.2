package com.hamza.account.features.report.itemsales;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The one place the item sales report comes from.
 *
 * <p>It asks {@code reports.show.items} before anything is read - the two screens it replaced asked
 * nothing, and were guarded only by a hidden sidebar button - and <b>it reads the cost only for a reader
 * holding {@code reports.show.profit}</b>, the key the Pareto report by margin asks on top of the items'.
 * The old ranking printed a profit column to anybody who could open it.</p>
 */
public final class ItemSalesService {

    private final ItemSalesRepository repository;
    private final Predicate<PermissionKey> granted;

    public ItemSalesService() {
        this(new JdbcItemSalesReportRepository(), AuthorizationGuard::isGranted);
    }

    /** @param granted whether the reader holds a key - a hint, asked only to decide what is read */
    public ItemSalesService(ItemSalesRepository repository, Predicate<PermissionKey> granted) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.granted = Objects.requireNonNull(granted, "granted");
    }

    public ItemSalesReport report(ItemSalesFilter filter) throws DaoException {
        Objects.requireNonNull(filter, "filter");
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_ITEMS);
        boolean marginVisible = marginVisible();
        List<ItemSalesRow> rows = repository.rows(filter, marginVisible);
        Optional<BigDecimal> discounts = filter.narrows() ? Optional.empty()
                : Optional.of(repository.headerDiscounts(filter.from(), filter.to()));
        return new ItemSalesReport(filter, rows, discounts, marginVisible);
    }

    /** One item's lines over the report's period, by unit and price - the row's drawer. */
    public List<ItemSalesLine> lines(ItemSalesFilter filter, int itemId) throws DaoException {
        Objects.requireNonNull(filter, "filter");
        AuthorizationGuard.require(AppPermissions.REPORTS_SHOW_ITEMS);
        return repository.lines(itemId, filter.from(), filter.to());
    }

    /** Whether the margin is read and shown: a profit is the profit key's, whichever screen shows it. */
    public boolean marginVisible() {
        return granted.test(AppPermissions.REPORTS_SHOW_PROFIT);
    }
}
