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
    // Areas are now AreasChanged on the EventBus.
    // Treasuries are now TreasuriesChanged on the EventBus.
    // The four name and account publishers are now NameChanged and AccountChanged
    // on the EventBus, carrying a PartyKind instead of being one publisher per
    // combination.
    // The two user publishers that were here are now UserRenamed and UsersChanged
    // on the EventBus, in account.features.events.
    // The two group publishers are now GroupsChanged, carrying a GroupLevel.
    // Employees and expenses are now EmployeesChanged and ExpensesChanged.
    private final Publisher<String> publisherUpdateCompany = new Publisher<>();
    // publisherAddUnits is now UnitsChanged on the EventBus. publisherAddItemUnit
    // went with it: nothing published it and nothing listened.

    private final Publisher<Boolean> closeStageFromLogout = new Publisher<>();
    private final Publisher<Boolean> showLoginScreen = new Publisher<>();
    private final Publisher<Boolean> showMainTotalsScreen = new Publisher<>();
    private final Publisher<String> changeMainScreenImage = new Publisher<>();
    // afterAddTarget went with the Target and TargetsDetails classes.
    private final Publisher<HashMap<Integer, String>> publisherSelPriceUnits = new Publisher<>();

    private final Publisher<Boolean> publisherShiftChanged = new Publisher<>();

}
