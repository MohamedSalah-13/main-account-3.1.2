package com.hamza.account.features.wipe;

import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipeTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The four cards of the delete-data screen.
 * <p>
 * The stock counts had no card: they were added to the catalog after the list was written, so
 * the screen put them under "other" and logged a warning each time it opened, and on a window of
 * the shipped size their row sat below the card's edge. {@code WipeSectionsTest} now fails the
 * build when a target has no card, and {@link #of} still puts a stray one on the last card, so
 * nothing the catalog offers can go missing from the screen.
 */
public final class WipeSections {

    public static final String SALES = "sales";
    public static final String PURCHASES = "purchases";
    public static final String ITEMS = "items";
    public static final String OTHER = "other";

    private static final List<WipeSection> DECLARED = List.of(
            new WipeSection(SALES, "wipe.section.sales", List.of(
                    WipeCatalog.SALES_RETURNS, WipeCatalog.SALES,
                    WipeCatalog.CUSTOMER_ACCOUNTS, WipeCatalog.CUSTOMERS)),
            new WipeSection(PURCHASES, "wipe.section.purchases", List.of(
                    WipeCatalog.PURCHASE_RETURNS, WipeCatalog.PURCHASES,
                    WipeCatalog.SUPPLIER_ACCOUNTS, WipeCatalog.SUPPLIERS)),
            new WipeSection(ITEMS, "wipe.section.items", List.of(
                    WipeCatalog.STOCK_COUNTS, WipeCatalog.ITEMS,
                    WipeCatalog.SUB_GROUPS, WipeCatalog.MAIN_GROUPS)),
            new WipeSection(OTHER, "wipe.section.other", List.of(
                    WipeCatalog.EXPENSES, WipeCatalog.EMPLOYEES,
                    WipeCatalog.PROCESSES, WipeCatalog.USERS)));

    private WipeSections() {
    }

    /** The cards as declared, before anything the catalog has gained is added. */
    public static List<WipeSection> declared() {
        return DECLARED;
    }

    /** The cards for these targets: the declared ones, with any target they leave out on the last. */
    public static List<WipeSection> of(Collection<WipeTarget> catalog) {
        List<WipeTarget> strays = unplaced(catalog);
        if (strays.isEmpty()) {
            return DECLARED;
        }
        List<WipeSection> sections = new ArrayList<>(DECLARED);
        WipeSection last = sections.removeLast();
        List<WipeTarget> merged = new ArrayList<>(last.targets());
        merged.addAll(strays);
        sections.add(new WipeSection(last.id(), last.titleKey(), merged));
        return List.copyOf(sections);
    }

    /** The targets no declared card lists, in the catalog's order. */
    public static List<WipeTarget> unplaced(Collection<WipeTarget> catalog) {
        Set<WipeTarget> placed = new HashSet<>();
        DECLARED.forEach(section -> placed.addAll(section.targets()));
        return catalog.stream().filter(target -> !placed.contains(target)).toList();
    }
}
