package com.hamza.account.features.inventory;

/**
 * What the inventory screen is asking for: which items, and which slice of them.
 * <p>
 * It exists so the page and the summary are answered from <em>one</em> description
 * of the filter. The screen used to hold the search text in a text field and the
 * page in a {@code Pagination}, and the two drifted apart the moment anyone typed:
 * the rows came from a filtered query while the page count still described the
 * unfiltered table, so the first click on a page silently threw the search away.
 * <p>
 * Every field that narrows the set belongs here rather than in a method argument,
 * because {@link InventoryDao} builds the {@code WHERE} clause from this record
 * once and hands it to both queries. That is what keeps the rows, the totals in the
 * header and the number of pages describing the same set of items.
 *
 * @param mainGroupId    the group to show, or {@link #ALL_GROUPS} for every group
 * @param includeInactive whether items marked as stopped are counted. It defaults to
 *                        true: a stopped item that still has stock is still stock,
 *                        and leaving it out of a valuation would understate what the
 *                        shop is holding
 */
public record InventoryQuery(String search,
                             StockFilter level,
                             int mainGroupId,
                             boolean includeInactive,
                             int page,
                             int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;

    /** No group chosen - {@code main_group} ids start at 1. */
    public static final int ALL_GROUPS = 0;

    public InventoryQuery {
        search = search == null ? "" : search.trim();
        level = level == null ? StockFilter.ALL : level;
        mainGroupId = Math.max(mainGroupId, ALL_GROUPS);
        page = Math.max(page, 0);
        pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
    }

    /** The whole stock, first page. */
    public static InventoryQuery all() {
        return new InventoryQuery("", StockFilter.ALL, ALL_GROUPS, true, 0, DEFAULT_PAGE_SIZE);
    }

    /**
     * The same filter on another page. Changing the page must not change what is
     * being counted, which is why this copies everything else across.
     */
    public InventoryQuery withPage(int newPage) {
        return new InventoryQuery(search, level, mainGroupId, includeInactive, newPage, pageSize);
    }

    /**
     * A new search always returns to the first page: staying on page 7 of a result
     * that now has two pages shows an empty table and reads as "no matches". Every
     * narrowing below does the same, for the same reason.
     */
    public InventoryQuery withSearch(String newSearch) {
        return new InventoryQuery(newSearch, level, mainGroupId, includeInactive, 0, pageSize);
    }

    public InventoryQuery withLevel(StockFilter newLevel) {
        return new InventoryQuery(search, newLevel, mainGroupId, includeInactive, 0, pageSize);
    }

    public InventoryQuery withMainGroup(int newMainGroupId) {
        return new InventoryQuery(search, level, newMainGroupId, includeInactive, 0, pageSize);
    }

    public InventoryQuery withInactive(boolean newIncludeInactive) {
        return new InventoryQuery(search, level, mainGroupId, newIncludeInactive, 0, pageSize);
    }

    public boolean hasSearch() {
        return !search.isEmpty();
    }

    public boolean hasGroup() {
        return mainGroupId != ALL_GROUPS;
    }

    /** Whether anything at all is being left out - what the "clear" button acts on. */
    public boolean isNarrowed() {
        return hasSearch() || hasGroup() || level != StockFilter.ALL || !includeInactive;
    }

    public int offset() {
        return page * pageSize;
    }
}
