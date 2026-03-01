package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TotalsService;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.BuyApplication;
import com.hamza.account.view.PosView;
import com.hamza.account.view.TotalsApplication;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import static com.hamza.account.config.PropertiesName.getSettingShowInvoiceScreenSeparate;

@Log4j2
public class TotalsButton<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends TotalsService<T1, T2, T3, T4> {

    private final DataInterface<T1, T2, T3, T4> dataInterface;
    private final EmployeeService employeeService;

    public TotalsButton(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, EmployeeService employeeService) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.dataInterface = dataInterface;
        this.employeeService = employeeService;
    }

    public ButtonWithPerm totals() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return dataInterface.designInterface().show_totals();
            }

            @Override
            public void action() throws Exception {
                initializeTotalsApp();
            }

            @Override
            public Node imageMenu() {
                return dataInterface.designInterface().imageButtonTotals();
            }

            @NotNull
            @Override
            public String textName() {
                return dataInterface.designInterface().nameTextOfTotal();
            }

            @Override
            public Node imageNode() {
                return dataInterface.designInterface().imageButtonTotals();
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
            public UserPermissionType getPermissionType() {
                return dataInterface.designInterface().show();
            }

            @Override
            public void action() throws Exception {
                BuyApplication<T1, T2, T3, T4> buyApp = new BuyApplication<>(dataInterface, daoFactory, dataPublisher, 0);
                buyApp.start(new Stage());
            }

            @Override
            public Node imageMenu() {
                return dataInterface.designInterface().imageMenu();
            }

            @NotNull
            @Override
            public String textName() {
                return dataInterface.designInterface().nameTextOfInvoice();
            }

            @Override
            public Node imageNode() {
                return dataInterface.designInterface().imageButton();
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                BuyApplication<T1, T2, T3, T4> buyApp = new BuyApplication<>(dataInterface, daoFactory, dataPublisher, 0);

                var shoppingSales = new Image_Setting().shoppingPurchase;
                if (textName().equals("sales") || textName().equals("المبيعات"))
                    shoppingSales = new Image_Setting().shoppingSales;

                addTape(tabPane, buyApp.getPane(), textName(), shoppingSales);
            }

            @Override
            public boolean showOnTapPane() {
                return !getSettingShowInvoiceScreenSeparate();
            }

            @Override
            public boolean addMultiTabWithSameName() {
                return true;
            }
        };
    }

    public ButtonWithPerm addInvoicePos() {

        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return dataInterface.designInterface().show();
            }

            @Override
            public void action() throws Exception {
                new PosView(daoFactory, dataPublisher).start(new Stage());
            }

            @Override
            public Node imageMenu() {
                return dataInterface.designInterface().imageMenu();
            }

            @NotNull
            @Override
            public String textName() {
                return "نقاط البيع";
            }

            @Override
            public Node imageNode() {
                return dataInterface.designInterface().imageButton();
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
            }
        };
    }

    private TotalsApplication<T1, T2, T3, T4> initializeTotalsApp() throws Exception {
        return new TotalsApplication<>(dataInterface, daoFactory, dataPublisher, employeeService);
    }
}
