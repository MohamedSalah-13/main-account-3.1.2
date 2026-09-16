package com.hamza.account.table;

import com.hamza.account.config.TableAppearance;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import org.jetbrains.annotations.NotNull;

import java.util.prefs.Preferences;

/**
 * Remembers which columns a table shows, and how wide they are.
 *
 * <p><b>A width the layout computed is not a preference.</b> {@link TableAppearance} defaults
 * to "fill the available width", which is a <em>constrained</em> resize policy: JavaFX then
 * derives every column's width from the table's own, and the width property changes whenever
 * the window does. Persisting that on change stores the window size in disguise, under a key
 * that claims to hold something the user chose.
 *
 * <p>It is not harmless. The merge screen's operations table saved widths summing to 1613
 * points while it was stretched across the window; moved into a 690-point panel it seeded the
 * policy with those widths, showed two of its seven columns and put the rest behind a
 * horizontal scroll bar. Any table moved into a narrower place inherits the same, which is
 * why this is fixed here rather than at that one screen.
 *
 * <p>So a width is read and written only while the policy is unconstrained - the state in
 * which a drag is the only thing that can change one. Under a filling policy the columns are
 * a function of the window, and the right thing to remember about them is nothing.
 * Visibility is unaffected: that is always the user's.
 */
public class TableSetting {

    /**
     * Whether a width change is worth storing. Separated from the control so the direction -
     * the easy thing to get backwards - can be pinned without a JavaFX toolkit.
     */
    static boolean widthIsAPreference(boolean fillsAvailableWidth) {
        return !fillsAvailableWidth;
    }

    /**
     * The width to apply on opening: what was stored, unless the policy is going to compute
     * one anyway, in which case the column's own declared width is the better starting point.
     */
    static double widthToApply(double stored, double declared, boolean fillsAvailableWidth) {
        return widthIsAPreference(fillsAvailableWidth) ? stored : declared;
    }

    private static boolean fillsAvailableWidth(@NotNull TableView<?> tableView) {
        return tableView.getColumnResizePolicy() != TableView.UNCONSTRAINED_RESIZE_POLICY;
    }

    private static boolean fillsAvailableWidth(@NotNull TreeTableView<?> treeTableView) {
        return treeTableView.getColumnResizePolicy() != TreeTableView.UNCONSTRAINED_RESIZE_POLICY;
    }

    public static <S> void tableMenuSetting(@NotNull Class<?> clazz, @NotNull TableView<S> tableView) {
        Preferences preferences = Preferences.userNodeForPackage(clazz);
        tableView.tableMenuButtonVisibleProperty().setValue(true);
        TableAppearance.apply(tableView);

        // استخدام معرف الجدول كجزء من المفتاح لتجنب التداخل بين الجداول في نفس الكلاس
        String tablePrefix = (tableView.getId() != null && !tableView.getId().isEmpty())
                ? tableView.getId() + "_" : "table_";

        int index = 0;
        for (TableColumn<S, ?> column : tableView.getColumns()) {

            // تحديد المعرف: الأولوية لـ ID العمود، وإذا لم يوجد نستخدم الـ Index
            String colIdentifier = (column.getId() != null && !column.getId().isEmpty())
                    ? column.getId()
                    : String.valueOf(index);

            String visibleKey = tablePrefix + "col_" + colIdentifier + "_visible";
            String widthKey = tablePrefix + "col_" + colIdentifier + "_width";

            // استرجاع الإعدادات المحفوظة
            boolean visible = preferences.getBoolean(visibleKey, column.isVisible());
            column.setVisible(visible);

            double stored = preferences.getDouble(widthKey, column.getPrefWidth());
            column.setPrefWidth(widthToApply(stored, column.getPrefWidth(), fillsAvailableWidth(tableView)));

            // حفظ التغييرات عند حدوثها
            column.visibleProperty().addListener((observable, oldValue, newValue) -> {
                preferences.putBoolean(visibleKey, newValue);
            });

            // The policy is read again here rather than captured: a theme refresh can switch
            // it after these listeners are registered.
            column.widthProperty().addListener((observable, oldValue, newValue) -> {
                if (widthIsAPreference(fillsAvailableWidth(tableView))) {
                    preferences.putDouble(widthKey, newValue.doubleValue());
                }
            });

            index++;
        }
    }

    public static <S> void tableMenuSetting(@NotNull Class<?> clazz, @NotNull TreeTableView<S> treeTableView) {
        Preferences preferences = Preferences.userNodeForPackage(clazz);
        treeTableView.tableMenuButtonVisibleProperty().setValue(true);
        TableAppearance.apply(treeTableView);

        String tablePrefix = (treeTableView.getId() != null && !treeTableView.getId().isEmpty())
                ? treeTableView.getId() + "_" : "treeTable_";

        int index = 0;
        for (TreeTableColumn<S, ?> column : treeTableView.getColumns()) {

            String colIdentifier = (column.getId() != null && !column.getId().isEmpty())
                    ? column.getId()
                    : String.valueOf(index);

            String visibleKey = tablePrefix + "col_" + colIdentifier + "_visible";
            String widthKey = tablePrefix + "col_" + colIdentifier + "_width";

            boolean visible = preferences.getBoolean(visibleKey, column.isVisible());
            column.setVisible(visible);

            double stored = preferences.getDouble(widthKey, column.getPrefWidth());
            column.setPrefWidth(widthToApply(stored, column.getPrefWidth(), fillsAvailableWidth(treeTableView)));

            column.visibleProperty().addListener((observable, oldValue, newValue) -> {
                preferences.putBoolean(visibleKey, newValue);
            });

            column.widthProperty().addListener((observable, oldValue, newValue) -> {
                if (widthIsAPreference(fillsAvailableWidth(treeTableView))) {
                    preferences.putDouble(widthKey, newValue.doubleValue());
                }
            });

            index++;
        }
    }
}