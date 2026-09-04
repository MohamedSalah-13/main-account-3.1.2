package com.hamza.account.features.items;

import java.util.Objects;

/**
 * Everything the items list is allowed to narrow itself by, in one value.
 * <p>
 * It exists so that the screen never assembles SQL and the DAO never learns what a
 * checkbox means. {@link ItemCatalogSql} turns one of these into a {@code WHERE}, and
 * both the page query and its count are built from that same object - which is the rule
 * the paging already lived by for the search and the group, extended to the rest.
 * <p>
 * <b>Every condition here is expressible in SQL against the catalog query's own tables.</b>
 * That is deliberate and is what keeps the screen fast: a filter applied in Java after the
 * fact would have to read the whole catalog in order to page it, and the count printed
 * beside the pages would be counting different rows than the ones on screen.
 * <p>
 * The balance conditions are stated against {@link ItemCatalogSql#BALANCE} - the same
 * arithmetic {@code ItemsDao.applyBalances} performs for the column the operator reads -
 * so a row can never be filtered out by a number different from the one shown in it.
 *
 * @param searchText   what was typed; blank means no text condition
 * @param searchScope  which columns {@code searchText} is matched against
 * @param matchMode    how {@code searchText} is compared to them
 * @param mainGroupId  a main group, expanded to every sub group under it
 * @param subGroupId   a sub group; narrower than {@code mainGroupId} and beats it
 * @param active       whether the item is on sale
 * @param hasBarcode   whether {@code items.barcode} carries anything
 * @param tracksExpiry whether the item is expiry-tracked
 * @param balance      how the item's stock compares to zero, or to its own minimum
 * @param minSellPrice inclusive lower bound on {@code sel_price1}; null for none
 * @param maxSellPrice inclusive upper bound on {@code sel_price1}; null for none
 * @param usage        whether the item has ever appeared on a document
 */
public record ItemCatalogFilter(String searchText,
                                SearchScope searchScope,
                                MatchMode matchMode,
                                Integer mainGroupId,
                                Integer subGroupId,
                                Tristate active,
                                Tristate hasBarcode,
                                Tristate tracksExpiry,
                                BalanceRule balance,
                                Double minSellPrice,
                                Double maxSellPrice,
                                UsageRule usage) {

    /** Which columns a typed search is matched against. */
    public enum SearchScope {
        /** Name and all three kinds of code - what the search box has always done. */
        ANY,
        CODE,
        BARCODE,
        NAME
    }

    /**
     * How the typed text is compared.
     * <p>
     * {@link #AUTO} is the behaviour this screen has always had, and is the default: digits
     * alone are an id or a barcode and are matched exactly - never as a fragment of a name -
     * while anything else is matched anywhere in the text. That rule is what stops a search
     * for "6221" from returning every item with those four digits buried in a code, and it
     * has its own test.
     * <p>
     * The other four are the operator saying they meant something else, and they override
     * that rule for as long as they are chosen. An explicit "contains" on digits is a
     * deliberate request for exactly the fragment search AUTO refuses to guess at.
     */
    public enum MatchMode {
        AUTO, EXACT, CONTAINS, STARTS_WITH, ENDS_WITH
    }

    /** A yes/no column the operator may leave alone. */
    public enum Tristate {
        ANY, YES, NO
    }

    /** How an item's stock has to compare, for it to be listed. */
    public enum BalanceRule {
        ANY,
        /** At or below {@code items.mini_quantity}, which only counts when a minimum is set. */
        BELOW_MINIMUM,
        /** Exactly nothing on hand. */
        OUT_OF_STOCK,
        /** Less than nothing on hand, which is always an entry error. */
        NEGATIVE,
        /** Something on hand. */
        IN_STOCK
    }

    /** Whether the item has ever been bought or sold. */
    public enum UsageRule {
        ANY,
        /** Never on a sale, a purchase, or either return. Capital doing nothing. */
        NEVER_MOVED,
        /** Never on a sales invoice, though it may have been bought. */
        NEVER_SOLD
    }

    public static final ItemCatalogFilter EMPTY = new ItemCatalogFilter(
            "", SearchScope.ANY, MatchMode.AUTO, null, null,
            Tristate.ANY, Tristate.ANY, Tristate.ANY,
            BalanceRule.ANY, null, null, UsageRule.ANY);

    public ItemCatalogFilter {
        searchText = searchText == null ? "" : searchText.trim();
        searchScope = searchScope == null ? SearchScope.ANY : searchScope;
        matchMode = matchMode == null ? MatchMode.AUTO : matchMode;
        active = active == null ? Tristate.ANY : active;
        hasBarcode = hasBarcode == null ? Tristate.ANY : hasBarcode;
        tracksExpiry = tracksExpiry == null ? Tristate.ANY : tracksExpiry;
        balance = balance == null ? BalanceRule.ANY : balance;
        usage = usage == null ? UsageRule.ANY : usage;
    }

    public ItemCatalogFilter withSearch(String text) {
        return new ItemCatalogFilter(text, searchScope, matchMode, mainGroupId, subGroupId, active, hasBarcode,
                tracksExpiry, balance, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withMatchMode(MatchMode mode) {
        return new ItemCatalogFilter(searchText, searchScope, mode, mainGroupId, subGroupId, active,
                hasBarcode, tracksExpiry, balance, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withSearchScope(SearchScope scope) {
        return new ItemCatalogFilter(searchText, scope, matchMode, mainGroupId, subGroupId, active, hasBarcode,
                tracksExpiry, balance, minSellPrice, maxSellPrice, usage);
    }

    /** A sub group is the narrower of the two, so the pair is always set together. */
    public ItemCatalogFilter withGroup(Integer main, Integer sub) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, main, sub, active, hasBarcode,
                tracksExpiry, balance, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withActive(Tristate value) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, mainGroupId, subGroupId, value, hasBarcode,
                tracksExpiry, balance, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withHasBarcode(Tristate value) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, mainGroupId, subGroupId, active, value,
                tracksExpiry, balance, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withTracksExpiry(Tristate value) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, mainGroupId, subGroupId, active, hasBarcode,
                value, balance, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withBalance(BalanceRule rule) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, mainGroupId, subGroupId, active, hasBarcode,
                tracksExpiry, rule, minSellPrice, maxSellPrice, usage);
    }

    public ItemCatalogFilter withSellPriceBetween(Double min, Double max) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, mainGroupId, subGroupId, active, hasBarcode,
                tracksExpiry, balance, min, max, usage);
    }

    public ItemCatalogFilter withUsage(UsageRule rule) {
        return new ItemCatalogFilter(searchText, searchScope, matchMode, mainGroupId, subGroupId, active, hasBarcode,
                tracksExpiry, balance, minSellPrice, maxSellPrice, rule);
    }

    /** Whether anything at all narrows the catalog. Drives the "clear filters" affordance. */
    public boolean isEmpty() {
        return searchText.isEmpty()
                && searchScope == SearchScope.ANY
                && matchMode == MatchMode.AUTO
                && mainGroupId == null && subGroupId == null
                && active == Tristate.ANY && hasBarcode == Tristate.ANY && tracksExpiry == Tristate.ANY
                && balance == BalanceRule.ANY && usage == UsageRule.ANY
                && minSellPrice == null && maxSellPrice == null;
    }

    /**
     * How many conditions beyond the search box are active. The screen prints it on the
     * filter button, so a filter left on inside a collapsed panel cannot be invisible -
     * which is the one way a filter panel actively misleads: the operator sees an empty
     * table and concludes the items themselves are gone.
     */
    public int activeConditionCount() {
        int count = 0;
        if (mainGroupId != null || subGroupId != null) count++;
        if (active != Tristate.ANY) count++;
        if (hasBarcode != Tristate.ANY) count++;
        if (tracksExpiry != Tristate.ANY) count++;
        if (balance != BalanceRule.ANY) count++;
        if (usage != UsageRule.ANY) count++;
        if (minSellPrice != null || maxSellPrice != null) count++;
        if (searchScope != SearchScope.ANY) count++;
        if (matchMode != MatchMode.AUTO) count++;
        return count;
    }

    /**
     * Everything except the typed text, which is what a saved filter and a quick chip
     * both mean: the operator keeps typing, and the conditions stay where they were.
     */
    public boolean sameConditionsAs(ItemCatalogFilter other) {
        return other != null
                && searchScope == other.searchScope
                && matchMode == other.matchMode
                && Objects.equals(mainGroupId, other.mainGroupId)
                && Objects.equals(subGroupId, other.subGroupId)
                && active == other.active && hasBarcode == other.hasBarcode
                && tracksExpiry == other.tracksExpiry && balance == other.balance
                && usage == other.usage
                && Objects.equals(minSellPrice, other.minSellPrice)
                && Objects.equals(maxSellPrice, other.maxSellPrice);
    }
}
