package com.hamza.account.features.invoice;

import com.hamza.controlsfx.database.DaoException;

import java.util.Objects;

/** A business validation failure that also identifies the field the UI should focus. */
public final class InvoiceValidationException extends DaoException {

    private final InvoiceSaveValidator.Target target;

    public InvoiceValidationException(InvoiceSaveValidator.Target target, String message) {
        super(message);
        this.target = Objects.requireNonNull(target, "target");
    }

    public InvoiceSaveValidator.Target target() {
        return target;
    }
}
