package com.hamza.account.controller.invoice;

/** Chooses the amount of keyboard assistance offered by an invoice window. */
public enum InvoiceScreenMode {
    STANDARD,
    QUICK;

    public InvoiceScreenMode opposite() {
        return this == QUICK ? STANDARD : QUICK;
    }
}
