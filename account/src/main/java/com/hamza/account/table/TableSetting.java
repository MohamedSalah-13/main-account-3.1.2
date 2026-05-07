package com.hamza.account.table;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import org.jetbrains.annotations.NotNull;

import java.util.prefs.Preferences;

public class TableSetting {

    public static <S> void tableMenuSetting(@NotNull Class<?> clazz, @NotNull TableView<S> tableView) {
        Preferences preferences = Preferences.userNodeForPackage(clazz);
        tableView.tableMenuButtonVisibleProperty().setValue(true);

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

            double width = preferences.getDouble(widthKey, column.getPrefWidth());
            column.setPrefWidth(width);

            // حفظ التغييرات عند حدوثها
            column.visibleProperty().addListener((observable, oldValue, newValue) -> {
                preferences.putBoolean(visibleKey, newValue);
            });

            column.widthProperty().addListener((observable, oldValue, newValue) -> {
                preferences.putDouble(widthKey, newValue.doubleValue());
            });

            index++;
        }
    }

    public static <S> void tableMenuSetting(@NotNull Class<?> clazz, @NotNull TreeTableView<S> treeTableView) {
        Preferences preferences = Preferences.userNodeForPackage(clazz);
        treeTableView.tableMenuButtonVisibleProperty().setValue(true);

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

            double width = preferences.getDouble(widthKey, column.getPrefWidth());
            column.setPrefWidth(width);

            column.visibleProperty().addListener((observable, oldValue, newValue) -> {
                preferences.putBoolean(visibleKey, newValue);
            });

            column.widthProperty().addListener((observable, oldValue, newValue) -> {
                preferences.putDouble(widthKey, newValue.doubleValue());
            });

            index++;
        }
    }
}