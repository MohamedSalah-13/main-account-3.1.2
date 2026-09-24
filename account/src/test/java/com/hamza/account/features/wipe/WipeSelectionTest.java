package com.hamza.account.features.wipe;

import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipeTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

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
}
