package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;

import java.time.LocalDate;

/**
 * What was asked of a report.
 * <p>
 * It carries an {@link ItemCatalogFilter} rather than a group id and a checkbox, and that
 * reuse is the point: every report in this package can be narrowed by exactly the same
 * conditions the items screen offers, in exactly the same SQL, without a line of it being
 * written twice. "Unused items, in the drinks group, that are still active" is the
 * combination of one report and one filter - not a fifth report.
 *
 * @param filter what to narrow the catalogue to; {@link ItemCatalogFilter#EMPTY} for all of it
 * @param from   start of the period, inclusive. Only read by reports that say they use it.
 * @param to     end of the period, inclusive
 */
public record ItemReportRequest(ItemCatalogFilter filter, LocalDate from, LocalDate to) {

    public ItemReportRequest {
        filter = filter == null ? ItemCatalogFilter.EMPTY : filter;
    }

    public static ItemReportRequest of(ItemCatalogFilter filter) {
        return new ItemReportRequest(filter, null, null);
    }
}
