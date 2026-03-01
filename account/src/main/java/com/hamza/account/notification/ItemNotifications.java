package com.hamza.account.notification;

import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.controlsfx.notifications.NotificationAdd;
import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class ItemNotifications {

    public ItemNotifications(List<ItemsMiniQuantity> itemsMiniQuantityList) {
        // check if show alert
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(10000);
                Platform.runLater(() -> new NotificationAdd(new NotifyItemAlert(itemsMiniQuantityList)));
            } catch (InterruptedException e) {
                log.error(e);
            }
        });
        thread.start();
    }
}
