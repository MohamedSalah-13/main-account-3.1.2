package com.hamza.account.controller.main;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.observer.Publisher;
import lombok.Getter;

import java.util.HashMap;

@Getter
public class DataPublisher {

    // for database
    // publisherBuy and publisherSales are now InvoiceSaved on the EventBus, told
    // apart by InvoiceSide rather than by which of the two a caller reached for.
    // publisherAddItem is now ItemSaved (one item, always carried) and
    // ItemsChanged (a bulk reload) on the EventBus.
    // Areas used to borrow publisherAddStock, which was left over from the
    // warehouse screens. Those are gone; areas now have their own.
    private final Publisher<String> publisherAddArea = new Publisher<>();
    private final Publisher<String> publisherAddTreasury = new Publisher<>();
    private final Publisher<String> publisherAddAccountCustom = new Publisher<>();
    private final Publisher<String> publisherAddAccountSuppliers = new Publisher<>();
    private final Publisher<String> publisherAddNameCustomer = new Publisher<>();
    private final Publisher<String> publisherAddNameSuppliers = new Publisher<>();
    // The two user publishers that were here are now UserRenamed and UsersChanged
    // on the EventBus, in account.features.events.
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
