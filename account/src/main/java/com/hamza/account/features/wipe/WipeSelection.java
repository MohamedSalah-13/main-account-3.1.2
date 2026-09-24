package com.hamza.account.features.wipe;

import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipePlan;
import com.hamza.account.wipe.WipeTarget;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the delete-data screen has ticked, and the rules for ticking.
 * <p>
 * <b>What is ticked is always closed under {@code requires}</b>: ticking a target ticks everything
 * it takes with it, and unticking one unticks everything that needed it. So the plan built from a
 * selection never lacks a table it depends on, and what the screen shows ticked is exactly what
 * the wipe will erase. The rules lived in the screen, behind a flag that kept one box's listener
 * from re-entering another's, where nothing could test them; the boxes now only mirror this.
 * <p>
 * The catalog is an argument so a test can hand it a small one; the screen hands it
 * {@link WipeCatalog#TARGETS}.
 */
public final class WipeSelection {

    private final List<WipeTarget> catalog;
    private final Set<WipeTarget> selected = new LinkedHashSet<>();

    public WipeSelection(List<WipeTarget> catalog) {
        this.catalog = List.copyOf(catalog);
    }

    /** Ticks the target and everything erased with it. */
    public void tick(WipeTarget target) {
        selected.addAll(WipeCatalog.closureOf(List.of(target)));
    }

    /**
     * Unticks the target and everything that needed it. What is left is still closed: a target
     * whose closure reached the one unticked, directly or through another, is itself unticked.
     */
    public void untick(WipeTarget target) {
        selected.removeIf(other -> WipeCatalog.closureOf(List.of(other)).contains(target));
    }

    public void set(WipeTarget target, boolean ticked) {
        if (ticked) {
            tick(target);
        } else {
            untick(target);
        }
    }

    public void selectAll() {
        selected.addAll(catalog);
    }

    public void clear() {
        selected.clear();
    }

    public boolean isSelected(WipeTarget target) {
        return selected.contains(target);
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    /** Whether every target is ticked - what the "select all" toggle shows. */
    public boolean isAll() {
        return selected.containsAll(catalog);
    }

    /** What is ticked, in the catalog's order - which is the order the wipe runs in. */
    public List<WipeTarget> selected() {
        return catalog.stream().filter(selected::contains).toList();
    }

    /** How many of these targets are ticked - a card's count. */
    public long countIn(Collection<WipeTarget> targets) {
        return targets.stream().filter(selected::contains).count();
    }

    /** What ticking this target erases besides it, in execution order - its box's tooltip. */
    public static List<WipeTarget> alsoErasedWith(WipeTarget target) {
        return WipeCatalog.closureOf(List.of(target)).stream()
                .filter(other -> !other.equals(target))
                .toList();
    }

    /** The plan for what is ticked, built now rather than on the thread that will run it. */
    public WipePlan plan() {
        return WipePlan.of(selected());
    }
}
