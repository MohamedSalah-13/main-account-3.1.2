package com.hamza.account.interfaces.api;

import com.hamza.account.type.UserPermissionType;
import javafx.scene.Node;

public interface DesignInterface {

    /**
     * Returns the stylesheet for the user interface design.
     *
     * @return a string representing the path or identifier of the stylesheet for the user interface.
     */
    String styleSheet();

    /**
     * Retrieves the name text of the data.
     *
     * @return a {@link String} representing the name text of the data.
     */
    String nameTextOfData();

    /**
     * Retrieves the text representation of the account's name.
     *
     * @return a string representing the account's name text
     */
    String nameTextOfAccount();

    /**
     * Provides the name text for the total section.
     *
     * @return the text representing the name of the total section.
     */
    String nameTextOfTotal();

    /**
     * Retrieves the display name for an invoice.
     *
     * @return the name text of the invoice
     */
    String nameTextOfInvoice();

    /**
     * Provides the text name for the report.
     *
     * @return the name text of the report
     */
    String nameTextOfReport();

    /**
     * Returns a Node representation for an image button.
     *
     * @return a Node associated with an image button, or null if not implemented
     */
    default Node imageButton() {
        return null;
    }

    /**
     * Provides a default image node for menus.
     *
     * @return a Node representing the image for the menu item, or null if not provided.
     */
    default Node imageMenu() {
        return null;
    }

    /**
     * Provides a graphical node representing the totals image button.
     * This method can be overridden by implementing classes to return
     * a specific Node for displaying totals in the user interface.
     *
     * @return a Node representing the totals image button, or null if not provided
     */
    default Node imageButtonTotals() {
        return null;
    }

    default boolean showDataForCustomer() {
        return false;
    }

    default boolean showScreenPaidInInvoice() {
        return false;
    }

    UserPermissionType show();

    UserPermissionType update();

    UserPermissionType delete();

    UserPermissionType show_totals();

    UserPermissionType show_totals_invoice();
}
