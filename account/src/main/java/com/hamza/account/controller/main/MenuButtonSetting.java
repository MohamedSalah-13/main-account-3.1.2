package com.hamza.account.controller.main;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
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
    private final ProductFeatureAccess productFeatures;
    // Every nav button configured through this instance, so clicking one can
    // clear the highlight off whichever other one currently carries it.
    private final List<Button> navButtons = new ArrayList<>();

    public MenuButtonSetting(TabPane tabPane, ProductFeatureAccess productFeatures) {
        this.tabPane = tabPane;
        this.productFeatures = productFeatures;
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
        configureButton(button, action, null);
    }

    /**
     * Hides a button whose feature is absent from the edition - and only ever hides.
     * <p>
     * The edition is the last word on whether a command exists at all, but it is not the
     * only rule that hides a sidebar button, and it must not overrule the others. Writing
     * {@code setVisible(available)} unconditionally did: shifts are {@code DISABLED} by
     * default, so {@code refreshShiftButtonVisibility} hides "My shift" during
     * {@code setupRightPane}, and {@code configureAllButtons} - which runs straight after
     * it, and again on every language change - put it back, because the edition happened to
     * carry the feature. A shop with shifts switched off got the shift screen in its sidebar
     * and in its shortcut map.
     * <p>
     * So an absent feature hides; a present one leaves the button exactly as it found it.
     * The button is visible in the FXML, which is what a present feature means anyway.
     */
    public void configureButton(Button button, ButtonWithPerm action, FeatureKey feature) {
        setGraphicAndText(button, action);
        disableButton(button::setDisable, action);
        button.focusTraversableProperty().setValue(FOCUS_TRAVERSABLE);
        setActionEvent(button, action, feature);
        if (feature != null && (productFeatures == null || !productFeatures.isEnabled(feature))) {
            button.setVisible(false);
            button.setManaged(false);
        }
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
        setActionEvent(menuItem, action, null);
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
    private void setActionEvent(Object control, ButtonWithPerm action, FeatureKey feature) {
        EventHandler<ActionEvent> eventHandler = (actionEvent) -> {
            try {
                if (feature != null) productFeatures.require(feature);
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
        AllAlerts.handleError(LanguageManager.getInstance().getString("nav.error.open.screen"), e);
    }

    private void disableButton(DisableButtons.Disableable uiElement, ButtonWithPerm action) {
        new DisableButtons.PermissionDisableService().applyPermissionBasedDisable(uiElement, action.getPermissionType());
    }
}
