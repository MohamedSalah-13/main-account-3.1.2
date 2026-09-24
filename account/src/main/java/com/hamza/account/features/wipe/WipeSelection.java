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
 * <p>
 * A target may also be <b>locked</b> - not offered by the screen at all, whatever it depends on.
 * Wiping {@link WipeCatalog#USERS} keeps only {@code id = 1}, so a signed-in user who is not that
 * row would erase their own account by ticking it; the screen locks it out for anyone else, rather
 * than let them tick a box that deletes the session reading it.
 */
public final class WipeSelection {

    private final List<WipeTarget> catalog;
    private final Set<WipeTarget> locked;
    private final Set<WipeTarget> selected = new LinkedHashSet<>();

    public WipeSelection(List<WipeTarget> catalog) {
        this(catalog, Set.of());
    }

    public WipeSelection(List<WipeTarget> catalog, Set<WipeTarget> locked) {
        this.catalog = List.copyOf(catalog);
        this.locked = Set.copyOf(locked);
    }

    /** Whether this target itself is locked - what a box's own explanation reads. */
    public boolean isLocked(WipeTarget target) {
        return locked.contains(target);
    }

    /** Whether ticking this target is possible at all - itself and everything it would take with it. */
    public boolean isSelectable(WipeTarget target) {
        return WipeCatalog.closureOf(List.of(target)).stream().noneMatch(locked::contains);
    }

    /** Ticks the target and everything erased with it. A locked target, or one that would take a locked
     * target with it, is left alone. */
    public void tick(WipeTarget target) {
        if (!isSelectable(target)) {
            return;
        }
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

    /** Ticks everything selectable. A locked target is left off, silently - the box is disabled too. */
    public void selectAll() {
        catalog.forEach(this::tick);
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

    /** Whether every selectable target is ticked - what the "select all" toggle shows. A locked
     * target is not counted against it, since nothing can ever tick it. */
    public boolean isAll() {
        return catalog.stream().filter(this::isSelectable).allMatch(selected::contains);
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
