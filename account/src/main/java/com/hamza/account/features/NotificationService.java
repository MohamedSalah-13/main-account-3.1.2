package com.hamza.account.features;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.Notifications;

/**
 * نظام إشعارات للمستخدم
 */
@Log4j2
public class NotificationService {

    /**
     * إشعار نجاح العملية
     */
    public static void showSuccess(String title, String message) {
        Platform.runLater(() -> {
            Notifications.create()
                    .title(title)
                    .text(message)
                    .hideAfter(Duration.seconds(5))
                    .position(Pos.TOP_RIGHT)
                    .showInformation();
        });
    }

    /**
     * إشعار خطأ
     */
    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Notifications.create()
                    .title(title)
                    .text(message)
                    .hideAfter(Duration.seconds(8))
                    .position(Pos.TOP_RIGHT)
                    .showError();
        });
    }

    /**
     * إشعار تحذير (مثل: وصول صنف للحد الأدنى)
     */
    public static void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Notifications.create()
                    .title(title)
                    .text(message)
                    .hideAfter(Duration.seconds(10))
                    .position(Pos.TOP_RIGHT)
                    .showWarning();
        });
    }
}
