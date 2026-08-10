package com.hamza.account.wipe;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One table a wipe empties, and what it puts back afterwards.
 *
 * @param table the table to empty
 * @param where an optional condition, so the wipe can spare a row it must not
 *              remove - {@code users} keeps id 1, which every {@code user_id}
 *              column in the schema still points at
 * @param seeds statements run after the table is emptied, restoring the rows the
 *              application cannot start without: the cash customer, the default
 *              unit, the main treasury
 */
public record WipeTable(String table, String where, List<String> seeds) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public WipeTable {
        if (!IDENTIFIER.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a table name: " + table);
        }
        seeds = List.copyOf(seeds);
    }

    public static WipeTable of(String table) {
        return new WipeTable(table, null, List.of());
    }

    public static WipeTable of(String table, String... seeds) {
        return new WipeTable(table, null, List.of(seeds));
    }

    /**
     * A table emptied except for the rows {@code where} matches.
     * <p>
     * The condition is written here, not taken from anywhere: it is part of the
     * declaration, like the table name.
     */
    public static WipeTable keeping(String table, String where, String... seeds) {
        return new WipeTable(table, where, List.of(seeds));
    }

    /** The DELETE this table contributes to the plan. */
    public String deleteStatement() {
        return where == null ? "DELETE FROM " + table : "DELETE FROM " + table + " WHERE " + where;
    }
}
