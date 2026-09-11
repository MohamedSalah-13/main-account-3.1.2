package com.hamza.account.config;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableView;

import java.util.prefs.Preferences;

/** Persisted, application-wide presentation choices for data tables. */
public final class TableAppearance {
    private static final Preferences PREFS = Preferences.userRoot().node("com.hamza.account.tableAppearance");
    private static final String SHOW_COLUMN_DIVIDERS = "showColumnDividers";
    private static final String FILL_AVAILABLE_WIDTH = "fillAvailableWidth";
    private static final String FILL_WIDTH_OVERRIDE =
            TableAppearance.class.getName() + ".fillWidthOverride";

    private TableAppearance() { }

    public static boolean showColumnDividers() { return PREFS.getBoolean(SHOW_COLUMN_DIVIDERS, false); }
    public static void setShowColumnDividers(boolean value) { PREFS.putBoolean(SHOW_COLUMN_DIVIDERS, value); }
    public static boolean fillAvailableWidth() { return PREFS.getBoolean(FILL_AVAILABLE_WIDTH, true); }
    public static void setFillAvailableWidth(boolean value) { PREFS.putBoolean(FILL_AVAILABLE_WIDTH, value); }

    /**
     * Lets a screen keep intentional, content-sized columns even when the user has enabled
     * the general "fill available width" preference. The marker lives on the control, so a
     * later theme refresh honours it rather than putting the unused width back into a column.
     */
    public static void setFillAvailableWidthOverride(TableView<?> tableView, boolean fillWidth) {
        tableView.getProperties().put(FILL_WIDTH_OVERRIDE, fillWidth);
    }

    /** Applies the saved choices to a root and every table currently under it. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void apply(Parent root) {
        if (root == null) return;
        root.getStyleClass().remove("table-column-dividers");
        if (showColumnDividers()) root.getStyleClass().add("table-column-dividers");

        boolean fillWidth = fillAvailableWidth();
        applyTo(root, fillWidth);
        for (Node node : root.lookupAll(".table-view")) applyTo(node, fillWidth);
    }

    private static void applyTo(Node node, boolean fillWidth) {
        if (node instanceof TableView tableView) {
            tableView.setColumnResizePolicy(effectiveFillWidth(tableView, fillWidth)
                    ? TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN
                    : TableView.UNCONSTRAINED_RESIZE_POLICY);
        } else if (node instanceof TreeTableView treeTableView) {
            treeTableView.setColumnResizePolicy(fillWidth
                    ? TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN
                    : TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
        }
    }

    private static boolean effectiveFillWidth(TableView<?> tableView, boolean defaultValue) {
        Object override = tableView.getProperties().get(FILL_WIDTH_OVERRIDE);
        return override instanceof Boolean value ? value : defaultValue;
    }
}
