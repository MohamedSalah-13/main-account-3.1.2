package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;

import java.util.Locale;
import java.util.Objects;

/**
 * A header choice an operator may keep as the local default for new invoices.
 *
 * <p>The key deliberately includes the document type: the treasury or warehouse used
 * for a purchase is often not the one a sales till uses. These are workstation
 * preferences, not business data, so they stay in {@code Preferences} rather than on
 * an invoice or a master-data row.
 */
public enum InvoicePinField {

    PARTY,
    TREASURY,
    STOCK,
    DELEGATE;

    /** Purchases have no delegate column and therefore no delegate pin. */
    public boolean appliesTo(DocumentType documentType) {
        return this != DELEGATE || documentType.hasDelegate();
    }

    /** A stable, local preference key for this field on one document type. */
    public String preferenceKey(DocumentType documentType) {
        Objects.requireNonNull(documentType, "documentType");
        return "invoice.pin." + documentType.name().toLowerCase(Locale.ROOT)
                + "." + name().toLowerCase(Locale.ROOT);
    }
}
