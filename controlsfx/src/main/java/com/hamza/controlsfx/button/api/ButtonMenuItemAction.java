package com.hamza.controlsfx.button.api;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;

public interface ButtonMenuItemAction extends ActionInterface, BasicsSettingInterface, MenuItemInterface {

    default void actionAddPaneToTabPane(TabPane tabPane) throws Exception {

    }

    /** Opens a tab with a live Ikonli (or other JavaFX) graphic. */
    default void addTape(TabPane tabPane, Parent node, String title, Node graphic) throws Exception {
        for (int i = 0; i < tabPane.getTabs().size(); i++) {
            if (!addMultiTabWithSameName()) {
                if (tabPane.getTabs().get(i).getText().equals(title)) {
                    tabPane.getSelectionModel().select(i);
                    return;
                }
            } else if (tabPane.getTabs().get(i).getText().equals(title)) {
                title = title + i;
            }
        }

        Tab tab = new Tab(title);
        if (node instanceof Region region) {
            region.setMinWidth(100);
        }
        tab.setContent(node);
        tab.setGraphic(graphic);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    default boolean showOnTapPane() {
        return false;
    }

    default boolean addMultiTabWithSameName() {
        return false;
    }
}
