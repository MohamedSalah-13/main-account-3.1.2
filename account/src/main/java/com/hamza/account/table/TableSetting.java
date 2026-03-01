package com.hamza.account.table;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.prefs.Preferences;

public class TableSetting {


    public static <S> void tableMenuSetting(@NotNull Class<?> clazz, TableView<S> tableView) {
        Preferences preferences = Preferences.userNodeForPackage(clazz);
        tableView.tableMenuButtonVisibleProperty().setValue(true);
        // Restore column visibility from preferences
        for (TableColumn<S, ?> column : tableView.getColumns()) {
            String key = "column_" + column.getText() + "_visible";
            boolean visible = preferences.getBoolean(key, column.isVisible());
            column.setVisible(visible);
        }

        // Save column visibility and width when changed
        for (TableColumn<S, ?> column : tableView.getColumns()) {
            var text = column.getText();
            column.visibleProperty().addListener((observable, oldValue, newValue) -> {
                String key = "column_" + text + "_visible";
                preferences.putBoolean(key, newValue);
            });

            // Restore column width from preferences
            String widthKey = "column_" + text + "_width";
            double width = preferences.getDouble(widthKey, column.getPrefWidth());
            column.setPrefWidth(width);

            // Save column width when changed
            column.widthProperty().addListener((observable, oldValue, newValue) -> {
                String key = "column_" + text + "_width";
                preferences.putDouble(key, newValue.doubleValue());
            });
        }
    }

    public static <S> void tableMenuSetting(@NotNull Class<?> clazz, TreeTableView<S> treeTableView) {
        Preferences preferences = Preferences.userNodeForPackage(clazz);
        treeTableView.tableMenuButtonVisibleProperty().setValue(true);
        // Restore column visibility from preferences
        for (TreeTableColumn<S, ?> column : treeTableView.getColumns()) {
            String key = "column_" + column.getText() + "_visible";
            boolean visible = preferences.getBoolean(key, column.isVisible());
            column.setVisible(visible);
        }

        // Save column visibility and width when changed
        for (TreeTableColumn<S, ?> column : treeTableView.getColumns()) {
            var text = column.getText();
            column.visibleProperty().addListener((observable, oldValue, newValue) -> {
                String key = "column_" + text + "_visible";
                preferences.putBoolean(key, newValue);
            });

            // Restore column width from preferences
            String widthKey = "column_" + text + "_width";
            double width = preferences.getDouble(widthKey, column.getPrefWidth());
            column.setPrefWidth(width);

            // Save column width when changed
            column.widthProperty().addListener((observable, oldValue, newValue) -> {
                String key = "column_" + text + "_width";
                preferences.putDouble(key, newValue.doubleValue());
            });
        }
    }


    private static int calculateTableHash(TableView<?> table) {
        return Objects.hash(table.getId(), table.getClass().getName());
    }

    private static int calculateTableHash(TreeTableView<?> table) {
        return Objects.hash(table.getId(), table.getClass().getName());
    }
}
