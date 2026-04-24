package com.hamza.account.controller.main;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.observer.Publisher;
import lombok.Getter;

import java.util.HashMap;

@Getter
public class DataPublisher {

    // for database
    private final Publisher<String> publisherBuy = new Publisher<>();
    private final Publisher<String> publisherSales = new Publisher<>();
    private final Publisher<ItemsModel> publisherAddItem = new Publisher<>();
    private final Publisher<String> publisherAddStock = new Publisher<>();
    private final Publisher<String> publisherAddTreasury = new Publisher<>();
    private final Publisher<String> publisherAddAccountCustom = new Publisher<>();
    private final Publisher<String> publisherAddAccountSuppliers = new Publisher<>();
    private final Publisher<String> publisherAddNameCustomer = new Publisher<>();
    private final Publisher<String> publisherAddNameSuppliers = new Publisher<>();
    private final Publisher<String> publisherAddUser = new Publisher<>();
    private final Publisher<String> publisherAddMainGroup = new Publisher<>();
    private final Publisher<String> publisherAddSubGroup = new Publisher<>();
    private final Publisher<String> publisherAddEmployee = new Publisher<>();
    private final Publisher<String> publisherAddExpenses = new Publisher<>();
    private final Publisher<String> publisherUpdateCompany = new Publisher<>();
    private final Publisher<String> publisherAddUnits = new Publisher<>();
    private final Publisher<String> publisherAddItemUnit = new Publisher<>();

    private final Publisher<Boolean> closeStageFromLogout = new Publisher<>();
    private final Publisher<Boolean> showLoginScreen = new Publisher<>();
    private final Publisher<Boolean> showMainTotalsScreen = new Publisher<>();
    private final Publisher<String> changeMainScreenImage = new Publisher<>();
    private final Publisher<String> afterAddTarget = new Publisher<>();
    private final Publisher<HashMap<Integer, String>> publisherSelPriceUnits = new Publisher<>();

    private final Publisher<Boolean> publisherShiftChanged = new Publisher<>();

}
