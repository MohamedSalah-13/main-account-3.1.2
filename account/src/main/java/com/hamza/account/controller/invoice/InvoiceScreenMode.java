package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.QuickInvoiceAccess;

/**
 * The two invoice screens: {@link BuyController2} with its entry form, and
 * {@link QuickInvoiceController}, where the lines table is the entry surface. The rules about
 * which one a user gets are {@link QuickInvoiceAccess}'s; this reads the session and the
 * computer's preferences for it.
 */
public enum InvoiceScreenMode {
    STANDARD,
    QUICK;

    public InvoiceScreenMode opposite() {
        return this == QUICK ? STANDARD : QUICK;
    }

    /**
     * Whether this screen can be opened for this document by whoever is signed in. The standard
     * screen always can; the quick one exists for a new sale and a new purchase, to the holder of
     * that document's key.
     */
    public boolean availableFor(DocumentType documentType) {
        return this == STANDARD || QuickInvoiceAccess.allowed(documentType, AuthorizationGuard::isGranted);
    }

    /**
     * The screen a <b>new</b> document of this kind opens in - the one last chosen with F6 for it.
     *
     * <p>A till is worked in one screen all day, and the only way into the quick one used to be
     * to open the standard one and switch, which closes that window and opens another. Without
     * this the operator paid that twice per invoice, all day. Anything unreadable is
     * {@link #STANDARD}: a preference must never be the reason a screen fails to open.
     */
    public static InvoiceScreenMode rememberedFor(DocumentType documentType) {
        boolean quick = QuickInvoiceAccess.opensQuick(
                PropertiesName.getInvoiceScreenMode(documentType),
                PropertiesName.getInvoiceScreenMode(),
                QUICK.availableFor(documentType));
        return quick ? QUICK : STANDARD;
    }

    /** Makes this the screen the next new document of this kind opens in. */
    public void rememberFor(DocumentType documentType) {
        PropertiesName.setInvoiceScreenMode(documentType, name());
    }
}
