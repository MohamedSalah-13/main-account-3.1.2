package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.InvoiceSide;

import java.util.Objects;

/** Document-type capabilities and semantic styling for the saved-invoice screen. */
public record InvoiceDetailsPresentation(
        boolean showProfit,
        boolean showDelegate,
        boolean showReturnDetails,
        String styleClass) {

    public static InvoiceDetailsPresentation of(DocumentType type, boolean maySeeProfit) {
        Objects.requireNonNull(type, "type");
        String styleClass = switch (type) {
            case SALES -> "invoice-details-sales";
            case SALES_RETURN -> "invoice-details-sales-return";
            case PURCHASE -> "invoice-details-purchase";
            case PURCHASE_RETURN -> "invoice-details-purchase-return";
        };
        return new InvoiceDetailsPresentation(
                type.side() == InvoiceSide.SALES && maySeeProfit,
                type.hasDelegate(), type.isReturn(), styleClass);
    }
}
