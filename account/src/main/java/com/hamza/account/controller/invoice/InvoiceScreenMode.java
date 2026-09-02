package com.hamza.account.controller.invoice;

import com.hamza.account.config.PropertiesName;

/** Chooses the amount of keyboard assistance offered by an invoice window. */
public enum InvoiceScreenMode {
    STANDARD,
    QUICK;

    public InvoiceScreenMode opposite() {
        return this == QUICK ? STANDARD : QUICK;
    }

    /**
     * The screen a <b>new</b> invoice opens in - the one last chosen with F6.
     *
     * <p>A till is worked in one of the two screens all day, and the only way into the
     * quick one is to open the standard one and switch, which closes that window and
     * opens another. Without this the operator paid that twice per invoice, all day.
     *
     * <p>Anything unreadable - no choice made yet, or a name written by a later
     * version - is {@link #STANDARD}, since a preference must never be the reason a
     * screen fails to open.
     */
    public static InvoiceScreenMode remembered() {
        try {
            return valueOf(PropertiesName.getInvoiceScreenMode());
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return STANDARD;
        }
    }

    /** Makes this the screen the next new invoice opens in. */
    public void remember() {
        PropertiesName.setInvoiceScreenMode(name());
    }
}
