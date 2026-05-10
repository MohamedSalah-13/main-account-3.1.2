package com.hamza.account.controller.main;

import com.hamza.account.dash.*;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.interfaces.impl_dataInterface.CustomDataReturn;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersData;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersDataReturn;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import lombok.Getter;

@Getter
public class MainItems extends DataPublisher {

    private final SettingButtons settingButtons;
    private final EmployeesButtons addEmployee;
    private final NameButtons<Purchase, Total_buy, Suppliers, SupplierAccount> nameSup;
    private final NameButtons<Sales, Total_Sales, Customers, CustomerAccount> nameCustomer;
    private final TotalsButton totalSalesReturn;
    private final TotalsButton totalPurchaseReturn;
    private final TotalsButton totalPurchase;
    private final TotalsButton totalSales;
    private final AccountButtons<Purchase, Total_buy, Suppliers, SupplierAccount> accountButtonsSup;
    private final AccountButtons<Sales, Total_Sales, Customers, CustomerAccount> accountButtonsCustom;
    private final ItemsButtons itemsButtons;
    private final ForAllButtons forAllButtons;
    private final UsersButtons usersAll;
    private final SuppliersData suppliersData;
    private final CustomData customData;
    private final ReportsButtons ReportsButtons;
    private final TreasuryButtons treasuryButtons;
    private final ShiftButtons shiftButtons;
    protected DaoFactory daoFactory;
    protected DataInterface<Purchase, Total_buy, Suppliers, SupplierAccount> dataInterfacePurchase;
    protected DataInterface<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> dataInterfacePurchaseReturn;
    protected DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterfaceSales;
    protected DataInterface<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> dataInterfaceSalesReturn;

    public MainItems(DaoFactory daoFactory, LoadDataAndList loadDataAndList) throws Exception {
        this.daoFactory = daoFactory;
        this.dataInterfacePurchase = new SuppliersData(daoFactory, this);
        this.dataInterfaceSales = new CustomData(daoFactory, this);
        this.dataInterfacePurchaseReturn = new SuppliersDataReturn(daoFactory, this);
        this.dataInterfaceSalesReturn = new CustomDataReturn(daoFactory, this);
        this.suppliersData = (SuppliersData) dataInterfacePurchase;
        this.customData = (CustomData) dataInterfaceSales;
        this.usersAll = new UsersButtons(daoFactory, this);
        this.settingButtons = new SettingButtons(daoFactory, this);
        this.addEmployee = new EmployeesButtons(daoFactory, this);
        this.itemsButtons = new ItemsButtons(daoFactory, this);
        this.ReportsButtons = new ReportsButtons(daoFactory, this, this);
        this.forAllButtons = new ForAllButtons(daoFactory, this, loadDataAndList);
        this.treasuryButtons = new TreasuryButtons(daoFactory, this);
        this.shiftButtons = new ShiftButtons(daoFactory, this);
        this.nameSup = new NameButtons<>(daoFactory, this, suppliersData);
        this.nameCustomer = new NameButtons<>(daoFactory, this, customData);
        this.totalPurchase = new TotalsButton(suppliersData, daoFactory, this);
        this.totalSales = new TotalsButton(customData, daoFactory, this);
        this.totalPurchaseReturn = new TotalsButton(getDataInterfacePurchaseReturn(), daoFactory, this);
        this.totalSalesReturn = new TotalsButton(getDataInterfaceSalesReturn(), daoFactory, this);
        this.accountButtonsSup = new AccountButtons<>(daoFactory, this, suppliersData);
        this.accountButtonsCustom = new AccountButtons<>(daoFactory, this, customData);
    }
}
