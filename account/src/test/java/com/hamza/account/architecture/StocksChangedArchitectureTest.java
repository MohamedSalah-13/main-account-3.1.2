package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bug reported 2026-08-30: a warehouse created after login was invisible
 * on every already-open screen - its balance could not be selected on the inventory
 * sheet, and an invoice already saved against it could not re-select it on edit.
 * <p>
 * The cause was structural, not a one-off typo: {@code ItemsButtons} constructs each
 * of these controllers once per session, so a {@code ComboBox<Stock>} built from
 * {@code stockService.getStocks()} at that moment stays stale for the rest of the
 * session unless something tells it to reread the list. {@link
 * com.hamza.account.features.events.StocksChanged} is that something - published by
 * {@code StocksController} after a save or a delete, and subscribed to by every
 * screen offering a warehouse to pick from.
 * <p>
 * A source-text scan is what fits here: the fix has no return value and no branch a
 * unit test could assert on without a JavaFX toolkit, and this repository does not
 * carry one (see {@code CLAUDE.md} - "the controllers, the FXML screens ... What
 * still has none [test coverage]"). What can be pinned without one is the wiring
 * itself, the same way {@code DefaultStockUsageArchitectureTest} pins a different
 * warehouse invariant by scanning source rather than running it.
 */
class StocksChangedArchitectureTest {

    private static final String SUBSCRIBE_CALL = "subscribe(StocksChanged.class";
    private static final String PUBLISH_CALL = "eventBus.publish(new StocksChanged())";

    /**
     * Unconditional, unlike {@code DefaultStockUsageArchitectureTest}'s allowlist:
     * there is no legitimate reason for a screen that offers a warehouse to pick
     * from to skip listening for a new one. Any file declaring
     * {@code ComboBox<Stock>} must also subscribe.
     */
    @Test
    void everyWarehouseComboSubscribesToStocksChanged() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("controller"))) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            if (source.contains("ComboBox<Stock>") && !source.contains(SUBSCRIBE_CALL)) {
                offenders.add(file);
            }
        }
        assertTrue(offenders.isEmpty(),
                "A screen offering a warehouse combo must refresh it when a warehouse is added, "
                        + "renamed or removed - see StocksChanged. A warehouse created after this "
                        + "controller was built is otherwise invisible to it for the rest of the "
                        + "session. Missing the subscription: " + offenders);
    }

    /**
     * The other half: the screen that actually creates and deletes warehouses has to
     * be the one announcing it. Two call sites - after {@code save()} and after
     * {@code delete()} - are both required; losing either one leaves half of the bug
     * back.
     */
    @Test
    void stocksControllerAnnouncesBothSaveAndDelete() {
        String source = SourceTree.withoutComments(
                SourceTree.readJava("com/hamza/account/controller/items/StocksController.java"));
        assertTrue(occurrences(source, PUBLISH_CALL) >= 2,
                "StocksController must publish StocksChanged after both a successful save and a "
                        + "successful delete, so every subscribed screen's warehouse combo stays "
                        + "current. Found " + occurrences(source, PUBLISH_CALL) + " call(s) to \""
                        + PUBLISH_CALL + "\".");
    }

    private static int occurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
