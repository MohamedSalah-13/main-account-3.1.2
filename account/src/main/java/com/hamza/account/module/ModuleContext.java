package com.hamza.account.module;

import com.hamza.account.controller.main.MainScreenController;
import com.hamza.account.model.dao.DaoFactory;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TabPane;

public record ModuleContext(MainScreenController mainScreenController, DaoFactory daoFactory, MenuBar menuBar,
                            TabPane tabPane) {

}
