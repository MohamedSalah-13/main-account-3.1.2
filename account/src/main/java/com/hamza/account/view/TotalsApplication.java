package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.invoice.TotalsController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.EmployeeService;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.layout.Pane;
import lombok.Getter;

@Getter
public class TotalsApplication {

    private final Pane pane;
    private final TotalsController<?, ?> controller;

    public TotalsApplication(DataInterface<?, ?, ?, ?> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, EmployeeService employeeService) throws Exception {

        CssToColorHelper helper = new CssToColorHelper();
        controller = new TotalsController<>(dataInterface, daoFactory, dataPublisher, employeeService, helper);
        pane = new OpenFxmlApplication(controller).getPane();
        pane.getStylesheets().add(ThemeManager.getStylesheet());
        pane.getChildren().add(helper);
    }

}
