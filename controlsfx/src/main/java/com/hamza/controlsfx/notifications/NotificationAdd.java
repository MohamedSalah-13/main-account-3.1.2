package com.hamza.controlsfx.notifications;

import javafx.geometry.Pos;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.Notifications;

import java.awt.*;

@Log4j2
public class NotificationAdd {


    public NotificationAdd(NotificationAction notificationAction) {
        Notifications notifications = Notifications.create()
                .title(notificationAction.titleName())
                .text(notificationAction.text())
                .graphic(notificationAction.graphic_design())
                .position(Pos.BOTTOM_LEFT)
                .onAction(actionEvent -> {
                    try {
                        notificationAction.action();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e.getCause());
                    }
                });
        Toolkit.getDefaultToolkit().beep();
        notifications.show();
    }
}
