package com.hamza.account.interfaces.api;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.type.UserPermissionType;
import javafx.scene.Node;

public interface DesignInterface {

    /**
     * Which of the four documents this screen is showing.
     * <p>
     * Everything below it that is not a caption or an icon follows from this: the five
     * permissions, and whether the screen is a customer's or a supplier's. They used to
     * be written out in each implementation, which is how a permission ended up being
     * the only way to tell a sale from a sales return.
     */
    DocumentType documentType();

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

    /**
     * True on the two customer screens - the sale and its return - and false on the
     * two supplier ones. It follows from the document type and is no longer worth
     * overriding.
     */
    default boolean showDataForCustomer() {
        return documentType().partyKind() == PartyKind.CUSTOMER;
    }

    default boolean showScreenPaidInInvoice() {
        return false;
    }

    default UserPermissionType show() {
        return documentType().showPermission();
    }

    default UserPermissionType update() {
        return documentType().updatePermission();
    }

    default UserPermissionType delete() {
        return documentType().deletePermission();
    }

    default UserPermissionType show_totals() {
        return documentType().showTotalsPermission();
    }

    default UserPermissionType show_totals_invoice() {
        return documentType().showTotalsInvoicePermission();
    }
}
