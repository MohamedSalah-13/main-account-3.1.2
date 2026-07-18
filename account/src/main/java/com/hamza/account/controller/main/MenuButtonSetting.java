package com.hamza.account.controller.main;

import com.hamza.controlsfx.alert.AllAlerts;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import lombok.extern.log4j.Log4j2;

@Log4j2
public record MenuButtonSetting(TabPane tabPane) {

    private static final boolean FOCUS_TRAVERSABLE = false;

    public void configureButton(Button button, ButtonWithPerm action) {
        setGraphicAndText(button, action);
        button.focusTraversableProperty().setValue(FOCUS_TRAVERSABLE);
        setActionEvent(button, action);
    }


    public void initializeMenuItem(MenuItem menuItem, ButtonWithPerm action) {
        setActionEvent(menuItem, action);
        menuItem.setText(action.textName());

        if (action.acceleratorKey() != null)
            menuItem.setAccelerator(action.acceleratorKey());
        if (action.imageMenu() != null) {
            menuItem.setGraphic(action.imageMenu());
        }
    }


    private void setActionEvent(Object control, ButtonWithPerm action) {
        EventHandler<ActionEvent> eventHandler = (actionEvent) -> {
            try {
                if (action.showOnTapPane()) {
                    action.actionAddPaneToTabPane(tabPane);
                } else {
                    action.action();
                }
            } catch (Exception e) {
                logException(e);
            }
        };

        if (control instanceof Button) {
            ((Button) control).setOnAction(eventHandler);
        } else if (control instanceof MenuItem) {
            ((MenuItem) control).setOnAction(eventHandler);
        }
    }

    /**
     * Sets the graphic and text of the specified button based on the provided action.
     *
     * @param button the button whose graphic and text will be set
     * @param action the action containing the graphic and text information
     */
    private void setGraphicAndText(Button button, ButtonWithPerm action) {
        button.setGraphic(action.imageMenu());
        button.setText(action.textName());
    }

    /**
     * Logs the provided exception and displays an alert dialog with its details.
     *
     * @param e the exception to log and display
     */
    private void logException(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.showExceptionDialog(e);
    }
}
