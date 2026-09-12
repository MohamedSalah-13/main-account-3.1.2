package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.employee.EmployeesScreenController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.layout.Pane;
import lombok.Getter;

/** The employees screen as a pane, so it opens as a tab like the parties' balances screen. */
@Getter
public class EmployeesApplication {

    private final Pane pane;

    public EmployeesApplication(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        CssToColorHelper helper = new CssToColorHelper();
        pane = new OpenFxmlApplication(new EmployeesScreenController(daoFactory, dataPublisher)).getPane();
        pane.getChildren().add(helper);
        pane.getStylesheets().add(ThemeManager.getStylesheet());
    }
}
