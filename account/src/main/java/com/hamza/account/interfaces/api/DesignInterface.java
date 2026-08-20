package com.hamza.account.interfaces.api;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

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

    default PermissionKey show() {
        return documentType().showPermission();
    }

    default PermissionKey update() {
        return documentType().updatePermission();
    }

    default PermissionKey delete() {
        return documentType().deletePermission();
    }

    default PermissionKey show_totals() {
        return documentType().showTotalsPermission();
    }

    default PermissionKey show_totals_invoice() {
        return documentType().showTotalsInvoicePermission();
    }
}
