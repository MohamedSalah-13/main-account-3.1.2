package com.hamza.account.view;

import com.hamza.account.controller.invoice.TotalsController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.EmployeeService;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.layout.Pane;
import lombok.Getter;

@Getter
public class TotalsApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    private final Pane pane;
    private final TotalsController<T1, T2, T3, T4> controller;

    public TotalsApplication(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, EmployeeService employeeService) throws Exception {

        CssToColorHelper helper = new CssToColorHelper();
        controller = new TotalsController<>(dataInterface, daoFactory, dataPublisher, employeeService, helper);
        pane = new OpenFxmlApplication(controller).getPane();
        pane.getStylesheets().add(dataInterface.designInterface().styleSheet());
        pane.getChildren().add(helper);
    }

}
