package com.hamza.account.features.wipe;

import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipeTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WipeSelectionTest {

    private final WipeSelection selection = new WipeSelection(WipeCatalog.TARGETS);

    @Test
    @DisplayName("ticking the customers ticks what points at them, and nothing else")
    void tickingTakesItsClosure() {
        selection.tick(WipeCatalog.CUSTOMERS);

        assertEquals(List.of(WipeCatalog.SALES_RETURNS, WipeCatalog.SALES,
                        WipeCatalog.CUSTOMER_ACCOUNTS, WipeCatalog.CUSTOMERS),
                selection.selected(), "in the catalog's order, the order the wipe runs in");
        assertFalse(selection.isSelected(WipeCatalog.SUPPLIERS));
    }

    @Test
    @DisplayName("unticking a target unticks whatever needed it, and leaves what did not")
    void untickingTakesItsDependents() {
        selection.tick(WipeCatalog.CUSTOMERS);
        selection.tick(WipeCatalog.EXPENSES);

        selection.untick(WipeCatalog.SALES);

        assertFalse(selection.isSelected(WipeCatalog.SALES));
        assertFalse(selection.isSelected(WipeCatalog.CUSTOMERS), "the customers need the sales gone");
        assertTrue(selection.isSelected(WipeCatalog.SALES_RETURNS), "the returns do not need the sales");
        assertTrue(selection.isSelected(WipeCatalog.CUSTOMER_ACCOUNTS));
        assertTrue(selection.isSelected(WipeCatalog.EXPENSES));
    }

    @Test
    @DisplayName("unticking reaches a target that needed it only through another")
    void untickingIsTransitive() {
        selection.tick(WipeCatalog.MAIN_GROUPS);
        assertTrue(selection.isSelected(WipeCatalog.SALES), "main groups -> sub groups -> items -> sales");

        selection.untick(WipeCatalog.SALES_RETURNS);

        assertFalse(selection.isSelected(WipeCatalog.MAIN_GROUPS));
        assertFalse(selection.isSelected(WipeCatalog.ITEMS));
        assertTrue(selection.isSelected(WipeCatalog.PURCHASES), "no path from the purchases to the sales returns");
    }

    @Test
    @DisplayName("whatever is ticked and unticked, what is ticked is closed under requires")
    void theSelectionIsAlwaysClosed() {
        Random random = new Random(20260924);
        for (int step = 0; step < 2_000; step++) {
            WipeTarget target = WipeCatalog.TARGETS.get(random.nextInt(WipeCatalog.TARGETS.size()));
            selection.set(target, random.nextBoolean());
            assertEquals(WipeCatalog.closureOf(selection.selected()), selection.selected(),
                    "after step " + step + " (" + target.id() + ")");
        }
    }

    @Test
    @DisplayName("select all is every target, and the toggle follows the boxes")
    void selectAllAndItsState() {
        assertTrue(selection.isEmpty());
        selection.selectAll();
        assertTrue(selection.isAll());
        assertEquals(WipeCatalog.TARGETS, selection.selected());

        selection.untick(WipeCatalog.PROCESSES);
        assertFalse(selection.isAll(), "one box off is no longer everything");

        selection.clear();
        assertTrue(selection.isEmpty());
        assertFalse(selection.isAll());
    }

    @Test
    @DisplayName("a card counts its own ticked targets")
    void aCardCountsItsTicks() {
        selection.tick(WipeCatalog.CUSTOMERS);
        WipeSection sales = WipeSections.declared().getFirst();

        assertEquals(4, selection.countIn(sales.targets()));
        assertEquals(0, selection.countIn(WipeSections.declared().get(1).targets()));
    }

    @Test
    @DisplayName("a box's tooltip lists what goes with it, and a target with nothing to take has none")
    void alsoErasedWith() {
        assertEquals(List.of(WipeCatalog.SALES_RETURNS), WipeSelection.alsoErasedWith(WipeCatalog.SALES));
        assertTrue(WipeSelection.alsoErasedWith(WipeCatalog.PROCESSES).isEmpty());
    }

    @Test
    @DisplayName("the plan is what is ticked, and nothing ticked is an empty plan")
    void thePlan() {
        assertTrue(selection.plan().isEmpty());

        selection.tick(WipeCatalog.SALES);

        assertEquals(List.of(WipeCatalog.SALES_RETURNS, WipeCatalog.SALES), selection.plan().targets());
    }

    @Test
    @DisplayName("a locked target cannot be ticked, on its own or through select all")
    void aLockedTargetCannotBeTicked() {
        WipeSelection locked = new WipeSelection(WipeCatalog.TARGETS, Set.of(WipeCatalog.USERS));

        locked.tick(WipeCatalog.USERS);
        assertFalse(locked.isSelected(WipeCatalog.USERS));

        locked.set(WipeCatalog.USERS, true);
        assertFalse(locked.isSelected(WipeCatalog.USERS));

        locked.selectAll();
        assertFalse(locked.isSelected(WipeCatalog.USERS), "select all skips what it cannot tick");
        assertTrue(locked.isSelected(WipeCatalog.SALES), "everything else is still reached");
    }

    @Test
    @DisplayName("a locked target ticks nothing that requires it either")
    void nothingThatRequiresALockedTargetCanBeTicked() {
        WipeSelection locked = new WipeSelection(WipeCatalog.TARGETS, Set.of(WipeCatalog.SALES));

        // customers requires sales, which is locked here - not the real catalog, but the same shape.
        locked.tick(WipeCatalog.CUSTOMERS);

        assertFalse(locked.isSelected(WipeCatalog.CUSTOMERS));
        assertFalse(locked.isSelected(WipeCatalog.SALES));
    }

    @Test
    @DisplayName("select all with a lock is still all, once the locked box is left out")
    void selectAllIsAllWithoutTheLockedBox() {
        WipeSelection locked = new WipeSelection(WipeCatalog.TARGETS, Set.of(WipeCatalog.USERS));

        assertFalse(locked.isAll());
        locked.selectAll();

        assertTrue(locked.isAll(), "everything selectable is ticked");
        assertFalse(locked.isSelected(WipeCatalog.USERS));
    }

    @Test
    @DisplayName("isLocked names the target itself, isSelectable follows its closure")
    void isLockedAndIsSelectable() {
        WipeSelection locked = new WipeSelection(WipeCatalog.TARGETS, Set.of(WipeCatalog.SALES));

        assertTrue(locked.isLocked(WipeCatalog.SALES));
        assertFalse(locked.isLocked(WipeCatalog.CUSTOMERS), "customers is not itself locked");

        assertFalse(locked.isSelectable(WipeCatalog.SALES));
        assertFalse(locked.isSelectable(WipeCatalog.CUSTOMERS), "but ticking it would reach sales");
        assertTrue(locked.isSelectable(WipeCatalog.PURCHASES), "unrelated to sales");
    }

    @Test
    @DisplayName("an empty lock set behaves exactly as before - the single-argument constructor's promise")
    void noLockIsTheDefault() {
        WipeSelection unlocked = new WipeSelection(WipeCatalog.TARGETS, Set.of());

        assertTrue(unlocked.isSelectable(WipeCatalog.USERS));
        unlocked.tick(WipeCatalog.USERS);
        assertTrue(unlocked.isSelected(WipeCatalog.USERS));
    }
}
