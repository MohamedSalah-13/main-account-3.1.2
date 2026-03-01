package com.hamza.account.interfaces;

import com.hamza.account.openFxml.MainData;
import com.hamza.controlsfx.table.Column;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;

import java.util.List;

/**
 * Defines the interface for creating and manipulating a report tree structure.
 *
 * @param <T1> the type parameter representing the main model for the report items.
 * @param <T3> the type parameter representing the names or entities associated with the report.
 */
public interface ReportTreeInterface<T1, T3> extends MainData {


    List<Column<?>> getColumnDefinitions();

    void addColumns(TreeTableView<T1> tableView);

    T1 loadTreeRoot();

    TreeItem<T1> treeItemMain(List<T1> list);

    void addItemInTree(TreeItem<T1> treeItem, List<T1> list);

    List<T1> listTree(String dateForm, String dateTo) throws Exception;

    boolean filterListByName(T1 t1, String name);

    boolean filterListByTableName(T1 t1, String name);

    String nameTitle();

    void print() throws Exception;


    List<T3> listNames() throws Exception;

    default void print_totals() throws Exception {
    }

    default boolean showData() {
        return false;
    }

    boolean colorRow(T1 t1);

}
