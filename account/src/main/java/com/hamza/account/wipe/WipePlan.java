package com.hamza.account.wipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The statements a wipe will run, in the order it will run them, worked out
 * before anything is executed.
 * <p>
 * Keeping the plan separate from running it is what makes the ordering checkable:
 * {@code WipeCatalogTest} builds plans and holds them against the foreign keys in
 * the migration files, without a database and without deleting anything.
 * <p>
 * Both fields are lists rather than sets. The order is the whole content of the
 * plan - a set would carry the same tables and lose the only thing that makes
 * them safe to run.
 */
public record WipePlan(List<WipeTarget> targets, List<String> statements) {

    public WipePlan {
        targets = List.copyOf(targets);
        statements = List.copyOf(statements);
    }

    /**
     * The plan for these targets and everything they require.
     * <p>
     * Each table contributes its DELETE, then its seeds. The seeds run with the
     * table already empty and its parents not yet touched, which is the only point
     * at which a row like the cash customer can be put back.
     */
    public static WipePlan of(Collection<WipeTarget> selected) {
        List<WipeTarget> closure = WipeCatalog.closureOf(selected);
        List<String> statements = new ArrayList<>();

        for (WipeTarget target : closure) {
            for (WipeTable table : target.tables()) {
                statements.add(table.deleteStatement());
                statements.addAll(table.seeds());
            }
        }
        return new WipePlan(closure, statements);
    }

    /**
     * The tables this plan empties, in the order it empties them - what the
     * ordering test reads.
     */
    public List<String> tablesInOrder() {
        return targets.stream()
                .flatMap(target -> target.tables().stream())
                .map(WipeTable::table)
                .toList();
    }

    public boolean isEmpty() {
        return statements.isEmpty();
    }
}
