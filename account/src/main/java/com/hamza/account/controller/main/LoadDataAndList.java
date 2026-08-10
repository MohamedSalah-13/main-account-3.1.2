package com.hamza.account.controller.main;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.events.UsersChanged;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Publisher;
import lombok.extern.log4j.Log4j2;

import java.util.stream.Stream;

@Log4j2
public class LoadDataAndList {

    public static void updateData(DataPublisher dataPublisher) {
        if (dataPublisher == null) {
            log.warn("DataPublisher is null, skipping update");
            return;
        }

        // The users and invoice families have moved to the bus; the rest are still
        // publishers, and this list shrinks as each family follows.
        var eventBus = ServiceRegistry.get(EventBus.class);
        if (eventBus != null) {
            eventBus.publish(new UsersChanged());
            eventBus.publish(new InvoiceSaved(InvoiceSide.PURCHASE));
            eventBus.publish(new InvoiceSaved(InvoiceSide.SALES));
        }

        Stream.of(
                        dataPublisher.getPublisherAddItem(),
                        dataPublisher.getPublisherAddArea(),
                        dataPublisher.getPublisherAddEmployee(),
                        dataPublisher.getPublisherAddAccountCustom(),
                        dataPublisher.getPublisherAddAccountSuppliers(),
                        dataPublisher.getPublisherAddNameCustomer(),
                        dataPublisher.getPublisherAddNameSuppliers(),
                        dataPublisher.getPublisherAddMainGroup(),
                        dataPublisher.getPublisherAddSubGroup()
                ).filter(java.util.Objects::nonNull)
                .forEach(Publisher::publish);
    }

}