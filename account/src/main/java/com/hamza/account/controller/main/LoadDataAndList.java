package com.hamza.account.controller.main;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.AreasChanged;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.GroupLevel;
import com.hamza.account.features.events.GroupsChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.events.RemoteChangeTopics;
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

        announceToTheOtherMachines();
    }

    /**
     * The other tills are reading a database that has just been replaced under them.
     *
     * <p>Every other announcement in the program is written by the service that made the
     * change, inside its transaction. A restore has no such service and no single write to
     * hang an announcement on - it is the whole database at once - so this is the one place
     * that announces {@link RemoteChangeTopics.Announcer#SERVICE} topics on its own. Without
     * it those topics would never be announced from here at all: the relay deliberately does
     * not listen for them, since the services that normally raise them have already written
     * the row.
     *
     * <p>Announced whatever the local publishing above does, and for every such topic rather
     * than the subset this method publishes: a restore moves the stock and treasury balances
     * as surely as it moves the item list.
     *
     * <p>A failure here is logged and not raised. There is no transaction to roll back at
     * this point - the restore is already done - and refusing to finish it because the other
     * tills could not be told would be the worse outcome.
     */
    private static void announceToTheOtherMachines() {
        try {
            ChangeAnnouncer announcer = ChangeAnnouncer.jdbc();
            for (String topic : RemoteChangeTopics.serviceAnnouncedTopics()) {
                announcer.announce(RemoteChangeTopics.eventOf(topic));
            }
        } catch (Exception e) {
            log.warn("The other machines were not told that the database was replaced", e);
        }
    }

}
