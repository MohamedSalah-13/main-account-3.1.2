package com.hamza.account.controller.main;

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

        Stream.of(
                        dataPublisher.getPublisherAddItem(),
                        dataPublisher.getPublisherAddStock(),
                        dataPublisher.getPublisherAddUser(),
                        dataPublisher.getPublisherAddEmployee(),
                        dataPublisher.getPublisherBuy(),
                        dataPublisher.getPublisherSales(),
                        dataPublisher.getPublisherAddAccountCustom(),
                        dataPublisher.getPublisherAddAccountSuppliers(),
                        dataPublisher.getPublisherAddNameCustomer(),
                        dataPublisher.getPublisherAddNameSuppliers(),
                        dataPublisher.getPublisherAddMainGroup(),
                        dataPublisher.getPublisherAddSubGroup()
                ).filter(java.util.Objects::nonNull)
                .forEach(Publisher::notifyObservers);
    }

}