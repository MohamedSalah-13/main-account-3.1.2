package com.hamza.controlsfx.menu;

import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class ContextMenuTree extends ContextMenuTable {

    private final MenuItem expandedAll;
    private final MenuItem collapseAll;
    private final MenuItem expanded;
    private final MenuItem collapse;

    public ContextMenuTree(TreeView<?> treeView, TreeItem<?> treeItem, boolean menuItem_Expanded_Collapse
            , ActionTable actionTable) {
        super(actionTable);
        expandedAll = new MenuItem("توسيع الكل");
        collapseAll = new MenuItem("طى الكل");
        expanded = new MenuItem("توسيع");
        collapse = new MenuItem("طى");

        getItems().addAll(expanded, expandedAll, new SeparatorMenuItem(), collapse, collapseAll);

        if (treeView != null) {
            menuItem_Expanded_Collapse(treeView);
        } else if (treeItem != null) menuItem_Expanded_Collapse(treeItem);

        if (!menuItem_Expanded_Collapse)
            setEnableAll(expanded, expandedAll, collapse, collapseAll);
    }

    private void setEnableAll(MenuItem... menuItems) {
        for (MenuItem s : menuItems) {
            s.setDisable(true);
        }
    }

    public void menuItem_Expanded_Collapse(TreeView<?> treeView) {
        expandedAll.setOnAction(event -> setExpandedAll(treeView, true));
        collapseAll.setOnAction(event -> setExpandedAll(treeView, false));
        expanded.setOnAction(event -> treeView.getRoot().setExpanded(true));
        collapse.setOnAction(event -> treeView.getRoot().setExpanded(false));
    }

    public void menuItem_Expanded_Collapse(TreeItem<?> treeItem) {
        expandedAll.setOnAction(event -> setExpandedAll(treeItem, true));
        collapseAll.setOnAction(event -> setExpandedAll(treeItem, false));
        expanded.setOnAction(event -> treeItem.setExpanded(true));
        collapse.setOnAction(event -> treeItem.setExpanded(false));
    }

    private void setExpandedAll(TreeItem<?> treeItem, boolean b) {
        for (int i = 0; i < treeItem.getChildren().size(); i++) {
            TreeItem<?> item = treeItem.getChildren().get(i);
            item.setExpanded(b);
        }
        treeItem.setExpanded(b);
    }

    private void setExpandedAll(TreeView<?> treeItem, boolean b) {
        for (int i = 0; i < treeItem.getRoot().getChildren().size(); i++) {
            TreeItem<?> item = treeItem.getRoot().getChildren().get(i);
            item.setExpanded(b);
        }
        treeItem.getRoot().setExpanded(b);
    }

}
