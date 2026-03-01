package com.hamza.account.controller.main;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;

@Log4j2
public record MenuButtonSetting(TabPane tabPane) {

    private static final boolean FOCUS_TRAVERSABLE = false;

    /**
     * Configures the provided button with the specified action. This includes setting
     * the button's graphic and text, disabling the button based on the action's
     * disable condition, setting the focus traversable property, and binding the
     * provided action to the button's action event.
     *
     * @param button The button that will be configured.
     * @param action The action to bind to the button, containing the necessary
     *               configuration details such as graphic, text, disable condition,
     *               and the event handler.
     */
    public void configureButton(Button button, ButtonWithPerm action) {
        setGraphicAndText(button, action);
        disableButton(button::setDisable, action);
        button.focusTraversableProperty().setValue(FOCUS_TRAVERSABLE);
        setActionEvent(button, action);
    }

    public void configureButton(Button button, InputStream stream, ButtonWithPerm action) {
        button.setGraphic(new ImageDesign(stream, 36));
        button.setTooltip(new Tooltip(action.textName()));
        button.setText(action.textName());
        disableButton(button::setDisable, action);
        button.focusTraversableProperty().setValue(FOCUS_TRAVERSABLE);
        setActionEvent(button, action);
    }


    public void initializeMenuItem(MenuItem menuItem, ButtonWithPerm action) {
        setActionEvent(menuItem, action);
        menuItem.setText(action.textName());
        disableButton(menuItem::setDisable, action);

        if (action.acceleratorKey() != null)
            menuItem.setAccelerator(action.acceleratorKey());
        if (action.imageMenu() != null) {
            menuItem.setGraphic(action.imageMenu());
        }
    }

    /**
     * Sets an action event for the given control, which can be either a Button or a MenuItem,
     * based on the provided ButtonMenuItemAction.
     *
     * @param control The control to which the action event will be set. Must be an instance of Button or MenuItem.
     * @param action  The action to be executed when the event is triggered. Includes logic for showing on a tap pane
     *                or performing a custom action.
     */
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
        e.printStackTrace();
    }

    private void disableButton(DisableButtons.Disableable uiElement, ButtonWithPerm action) {
        new DisableButtons.PermissionDisableService().applyPermissionBasedDisable(uiElement, action.getPermissionType());
    }
}
