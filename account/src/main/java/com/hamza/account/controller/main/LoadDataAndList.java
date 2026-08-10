package com.hamza.account.controller.main;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.AreasChanged;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.GroupLevel;
import com.hamza.account.features.events.GroupsChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.events.UsersChanged;
import com.hamza.controlsfx.observer.EventBus;
import lombok.extern.log4j.Log4j2;

/**
 * Tells every screen that the data underneath it has been replaced wholesale -
 * after a restore from backup, or after the delete-everything screen.
 */
@Log4j2
public class LoadDataAndList {

    public static void updateData() {
        var eventBus = ServiceRegistry.get(EventBus.class);
        if (eventBus == null) {
            log.warn("The event bus is not registered, so no screen was told to reload");
            return;
        }

        eventBus.publish(new UsersChanged());
        eventBus.publish(new ItemsChanged());
        eventBus.publish(new EmployeesChanged());
        eventBus.publish(new ExpensesChanged());
        eventBus.publish(new AreasChanged());
        for (InvoiceSide side : InvoiceSide.values()) eventBus.publish(new InvoiceSaved(side));
        for (PartyKind kind : PartyKind.values()) {
            eventBus.publish(new NameChanged(kind));
            eventBus.publish(new AccountChanged(kind));
        }
        for (GroupLevel level : GroupLevel.values()) eventBus.publish(new GroupsChanged(level));
    }

}
