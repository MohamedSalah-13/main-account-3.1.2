package com.hamza.account.interfaces.api;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

/**
 * What one of the four invoice/name/account/total/report screens is called and guarded
 * by. Every default below reads it off {@link #documentType()} - the five captions, the
 * five permissions, and whether the screen is a customer's or a supplier's used to be
 * written out per implementation, which is how a permission ended up being the only
 * field that told a sale from a sales return apart, and how two of the four returned
 * {@code null} for a permission nothing had gotten around to answering. This interface
 * now has exactly one thing left to say.
 */
@FunctionalInterface
public interface DesignInterface {

    /** Which of the four documents this screen is showing. */
    DocumentType documentType();

    /** What the names screen is called: customers or suppliers. */
    default String nameTextOfData() {
        return documentType().partyKind().nameText();
    }

    /** What the account screen is called: a customer's account or a supplier's. */
    default String nameTextOfAccount() {
        return documentType().partyKind().accountText();
    }

    /** What the totals list is called for this document. */
    default String nameTextOfTotal() {
        return documentType().totalText();
    }

    /** What a single document of this type is called. */
    default String nameTextOfInvoice() {
        return documentType().invoiceText();
    }

    /** What the report screen is called for this document. */
    default String nameTextOfReport() {
        return documentType().reportText();
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
        return documentType().paidInInvoice();
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
