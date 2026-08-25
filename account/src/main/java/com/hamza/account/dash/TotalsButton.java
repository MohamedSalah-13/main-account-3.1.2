package com.hamza.account.dash;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TotalsService;
import com.hamza.account.view.BuyApplication;
import com.hamza.account.view.TotalsApplication;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class TotalsButton<T3 extends BaseNames, T4 extends BaseAccount>
        extends TotalsService<T3, T4> {

    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    public TotalsButton(DataInterface<?, ?, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
    }

    public ButtonWithPerm totals() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return dataInterface.designInterface().show_totals();
            }

            @Override
            public void action() throws Exception {
                initializeTotalsApp();
            }

            @NotNull
            @Override
            public String textName() {
                return dataInterface.designInterface().nameTextOfTotal();
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                var pane = initializeTotalsApp().getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().totals);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm addInvoice() {

        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return dataInterface.designInterface().show();
            }

            @Override
            public void action() throws Exception {
                BuyApplication buyApp = new BuyApplication(dataInterface, 0);
                buyApp.start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return dataInterface.designInterface().nameTextOfInvoice();
            }

//            @Override
//            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
//                BuyApplication buyApp = new BuyApplication(dataInterface, dataPublisher, 0);
//
//                var shoppingSales = new Image_Setting().shoppingPurchase;
//                if (textName().equals("sales") || textName().equals("المبيعات"))
//                    shoppingSales = new Image_Setting().shoppingSales;
//
//                addTape(tabPane, buyApp.getPane(), textName(), shoppingSales);
//            }
//
//            @Override
//            public boolean showOnTapPane() {
//                return !getSettingShowInvoiceScreenSeparate();
//            }

            @Override
            public boolean addMultiTabWithSameName() {
                return true;
            }
        };
    }

    private TotalsApplication initializeTotalsApp() throws Exception {
        return new TotalsApplication(dataInterface, daoFactory, dataPublisher, employeeService);
    }
}
