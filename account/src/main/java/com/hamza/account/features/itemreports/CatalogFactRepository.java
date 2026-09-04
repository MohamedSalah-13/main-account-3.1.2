package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/**
 * The one read every report in this package is built on.
 * <p>
 * An interface rather than the JDBC class directly, so a report's test hands it a list of
 * facts instead of a database. There is exactly one method on purpose: a repository that
 * grew a method per report would put each report's SQL somewhere its own test cannot see.
 */
public interface CatalogFactRepository {

    /**
     * Every item the filter admits, with its group, its unit, its balance and the date it
     * last moved.
     *
     * @param withLastMovement whether to work out the last movement date, which costs a
     *                         scan of the four line tables. The reports that do not read it
     *                         say so, and skip it - a valuation over a large catalogue has
     *                         no business paying for a date it will not print.
     */
    List<CatalogFact> facts(ItemCatalogFilter filter, boolean withLastMovement) throws DaoException;
}
