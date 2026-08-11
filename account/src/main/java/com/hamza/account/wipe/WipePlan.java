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
     * Every DELETE first, in catalog order - children before parents - and only
     * then the seeds. Seeding a table as soon as it was emptied, which is what this
     * did first, put the row back while the wipe still had its parent to delete:
     * {@code sub_group} was re-seeded with {@code main_id = 1} and the DELETE on
     * {@code main_group} two statements later was refused by the foreign key.
     * <p>
     * The seeds run in the reverse of the delete order, for the same reason the
     * deletes run in theirs: a seeded row may point at another seeded row, and the
     * parent it points at is by definition emptied later. So {@code main_group}
     * gets its row back before {@code sub_group} asks for it.
     */
    public static WipePlan of(Collection<WipeTarget> selected) {
        List<WipeTarget> closure = WipeCatalog.closureOf(selected);
        List<WipeTable> tables = closure.stream()
                .flatMap(target -> target.tables().stream())
                .toList();

        List<String> statements = new ArrayList<>(tables.stream().map(WipeTable::deleteStatement).toList());
        for (int i = tables.size() - 1; i >= 0; i--) {
            statements.addAll(tables.get(i).seeds());
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
