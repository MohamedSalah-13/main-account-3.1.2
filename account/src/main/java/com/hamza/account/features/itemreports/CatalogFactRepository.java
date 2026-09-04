package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/**
 * The one read every report in this package is built on.
 * <p>
 * An interface rather than the JDBC class directly, so a report's test hands it a list of
 * facts instead of a database.
 * <p>
 * Two methods, and the reason there are not more is worth stating: they are two <em>kinds
 * of fact</em>, not two reports. An item has one row; an expiry batch is an (item, date)
 * pair and an item has several of them at once, so no shape of {@link CatalogFact} can
 * carry one. A repository that grew a method per report would put each report's SQL
 * somewhere its own test cannot see, and that is still the rule.
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

    /**
     * Every expiry batch of every item the filter admits that still has something left.
     * <p>
     * Across all warehouses. Which shelf a box is on does not change the day it goes off,
     * and an owner reading this wants the whole exposure in one list.
     */
    List<ExpiringBatch> expiringBatches(ItemCatalogFilter filter) throws DaoException;
}
