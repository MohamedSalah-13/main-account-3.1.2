package com.hamza.account.wipe;

import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.Set;

/**
 * One thing the "delete data" screen offers to erase - the sales invoices, the
 * items, the users - as a declaration rather than a branch inside a stored
 * procedure.
 *
 * @param id       what the screen and the other targets call this one
 * @param labelKey the i18n bundle key for the name shown to the user
 * @param tables   the tables it empties, <b>children before parents</b>: they are
 *                 deleted in this order, and a parent listed before its child
 *                 fails on the foreign key
 * @param requires the targets that must be erased along with this one, because
 *                 their tables point at its tables. Not a matter of taste - it is
 *                 what the foreign keys say, and {@code WipeCatalogTest} checks
 *                 the declarations against the schema
 */
public record WipeTarget(String id, String labelKey, List<WipeTable> tables, Set<String> requires) {

    public WipeTarget {
        tables = List.copyOf(tables);
        requires = Set.copyOf(requires);
    }

    public static WipeTarget of(String id, String labelKey, List<WipeTable> tables, String... requires) {
        return new WipeTarget(id, labelKey, tables, Set.of(requires));
    }

    /** The name shown to the user, resolved through the current language. */
    public String label() {
        return LanguageManager.getInstance().getString(labelKey);
    }
}
