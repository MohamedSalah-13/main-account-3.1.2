package com.hamza.controlsfx.button.api;

import com.hamza.controlsfx.button.ImageDesign;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.io.InputStream;

public interface ButtonMenuItemAction extends ActionInterface, BasicsSettingInterface, MenuItemInterface {

    default void actionAddPaneToTabPane(TabPane tabPane) throws Exception {

    }

    default void addTape(TabPane tabPane, Parent node, String title, InputStream stream) throws Exception {
        for (int i = 0; i < tabPane.getTabs().size(); i++) {
            if (!addMultiTabWithSameName()) {
                if (tabPane.getTabs().get(i).getText().equals(title)) {
                    tabPane.getSelectionModel().select(i);
                    return;
                }
            } else {
                if (tabPane.getTabs().get(i).getText().equals(title)) {
                    title = title + i;
                }
            }
        }

        Tab tab = new Tab(title);
        node.minWidth(100);
        tab.setContent(node);
        if (stream != null) {
            tab.setGraphic(new ImageDesign(stream,20));
        }
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
