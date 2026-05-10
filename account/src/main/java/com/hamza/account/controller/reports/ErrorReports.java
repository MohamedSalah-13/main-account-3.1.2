package com.hamza.account.controller.reports;

import javafx.scene.control.Alert;

public class ErrorReports {

    public static void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).show();
    }

    public static void showWarning(String msg) {
        new Alert(Alert.AlertType.WARNING, msg).show();
    }

    public static void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).show();
    }
}
