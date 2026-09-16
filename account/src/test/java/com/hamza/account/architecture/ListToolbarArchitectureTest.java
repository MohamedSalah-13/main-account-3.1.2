package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A list screen's bar is placed by {@code ListToolbar}, and nothing else opens or closes a
 * filters panel.
 * <p>
 * The order - search, filters, clear all, then refresh, print, export, view, then anything else -
 * was agreed once for every screen. Before the class existed each screen wrote its own row, and
 * no two agreed: the totals screen had the view menu before refresh and print after the bulk
 * delete, the employees screen put its search after two rows of fields. None of that was
 * decided; it is what a hand-written row turns into. A rule that lives only in a note drifts the
 * same way, so this is where it is kept.
 * <p>
 * What makes a screen a "list screen" here is that it uses the list kit - the view menu
 * ({@code TableColumnViews}) or the page box ({@code PageJumpBox}). That is checkable from the
 * source, where "has a toolbar" is not.
 */
class ListToolbarArchitectureTest {

    private static final Pattern LIST_KIT = Pattern.compile("\\b(TableColumnViews|PageJumpBox)\\b");

    /**
     * Screens that use the list kit and deliberately have no bar of their own to order, each with
     * why. The list fails in both directions: a new screen missing the toolbar fails, and so does
     * an entry here that has since moved onto it - so the list cannot quietly become fiction.
     */
    private static final Map<String, String> WITHOUT_A_TOOLBAR = Map.of(
            "com/hamza/account/controller/name_account/PartyNamesTable.java",
            "a DataTable hosted by TableController, whose bar is already ListToolbar's",
            "com/hamza/account/controller/items/StockCountController.java",
            "a count being entered and posted, not a list being searched: its bar is save and post");

    private static final Pattern PANEL_VISIBILITY = Pattern.compile(
            "\\b\\w*[Ff]ilters?(Pane|Panel)\\s*\\.\\s*set(Managed|Visible)\\s*\\(");

    private static final String TOOLBAR = "com/hamza/account/table/ListToolbar.java";

    @Test
    void everyListScreenPlacesItsBarThroughListToolbar() {
        var missing = new TreeSet<String>();
        var stale = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("controller"))) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            if (!LIST_KIT.matcher(source).find()) {
                continue;
            }
            boolean usesToolbar = source.contains("ListToolbar");
            boolean exempt = WITHOUT_A_TOOLBAR.containsKey(file);
            if (!usesToolbar && !exempt) {
                missing.add(file);
            }
            if (usesToolbar && exempt) {
                stale.add(file);
            }
        }
        assertTrue(missing.isEmpty(),
                "A list screen's bar is ordered by ListToolbar (search, filters, clear all | refresh, "
                        + "print, export, view | the rest). These build their own: " + missing);
        assertTrue(stale.isEmpty(),
                "These use ListToolbar now - remove them from WITHOUT_A_TOOLBAR: " + stale);
    }

    @Test
    void onlyListToolbarOpensAndClosesAFiltersPanel() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage())) {
            if (file.equals(TOOLBAR)) {
                continue;
            }
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            if (PANEL_VISIBILITY.matcher(source).find()) {
                offenders.add(file);
            }
        }
        assertTrue(offenders.isEmpty(),
                "A filters panel is shown and hidden by ListToolbar.filters/setFiltersVisible, which "
                        + "also keeps the toggle's count - a panel toggled by hand closes with its "
                        + "conditions still narrowing the list and nothing saying so. Found in: " + offenders);
    }
}
