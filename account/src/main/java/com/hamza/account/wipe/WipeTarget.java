package com.hamza.account.wipe;

import java.util.List;
import java.util.Set;

/**
 * One thing the "delete data" screen offers to erase - the sales invoices, the
 * items, the users - as a declaration rather than a branch inside a stored
 * procedure.
 *
 * @param id       what the screen and the other targets call this one
 * @param label    the Arabic name shown to the user
 * @param tables   the tables it empties, <b>children before parents</b>: they are
 *                 deleted in this order, and a parent listed before its child
 *                 fails on the foreign key
 * @param requires the targets that must be erased along with this one, because
 *                 their tables point at its tables. Not a matter of taste - it is
 *                 what the foreign keys say, and {@code WipeCatalogTest} checks
 *                 the declarations against the schema
 */
public record WipeTarget(String id, String label, List<WipeTable> tables, Set<String> requires) {

    public WipeTarget {
        tables = List.copyOf(tables);
        requires = Set.copyOf(requires);
    }

    public static WipeTarget of(String id, String label, List<WipeTable> tables, String... requires) {
        return new WipeTarget(id, label, tables, Set.of(requires));
    }
}
