package com.hamza.account.controller.main;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class MenuButtonSetting {

    private static final boolean FOCUS_TRAVERSABLE = false;
    private static final String ACTIVE_STYLE_CLASS = "sidebar-nav-active";

    private final TabPane tabPane;
    // Every nav button configured through this instance, so clicking one can
    // clear the highlight off whichever other one currently carries it.
    private final List<Button> navButtons = new ArrayList<>();

    public MenuButtonSetting(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    public TabPane tabPane() {
        return tabPane;
    }

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
        trackNavButton(button);
    }

    /**
     * Registers a nav button for active-state tracking and highlights it on click,
     * clearing the highlight off every other button configured through this instance.
     * <p>
     * Idempotent on purpose: a language switch re-runs configureButton on every
     * sidebar button to refresh its text, and without this check each re-run would
     * add another ACTION handler to the same button - markActive firing once per
     * past language switch on every click.
     */
    private void trackNavButton(Button button) {
        if (navButtons.contains(button)) return;
        navButtons.add(button);
        button.addEventHandler(ActionEvent.ACTION, event -> markActive(button));
    }

    private void markActive(Button activeButton) {
        navButtons.forEach(button -> button.getStyleClass().remove(ACTIVE_STYLE_CLASS));
        if (!activeButton.getStyleClass().contains(ACTIVE_STYLE_CLASS)) {
            activeButton.getStyleClass().add(ACTIVE_STYLE_CLASS);
        }
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
        AllAlerts.handleError("فتح شاشة من القائمة", e);
    }

    private void disableButton(DisableButtons.Disableable uiElement, ButtonWithPerm action) {
        new DisableButtons.PermissionDisableService().applyPermissionBasedDisable(uiElement, action.getPermissionType());
    }
}
