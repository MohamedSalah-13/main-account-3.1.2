package com.hamza.account.features.invoice;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.document.DocumentType;

import java.util.function.Predicate;

/**
 * Who may use the quick invoice screen, and which screen a new invoice opens in.
 *
 * <p>Two questions that used to have one answer each, and neither was the right one. The quick
 * screen was open to anyone who could open the invoice at all - there was no key for it - and to
 * every document family, returns included. And the screen last chosen with F6 was one preference
 * for the whole program, so a till that sold on the quick screen found its purchases and its
 * returns opening there too.
 *
 * <p>Now the screen exists for a new sale and a new purchase only
 * ({@link DocumentType#quickEntryPermission()}), and only to whoever holds that document's key; the
 * choice is remembered per document. A choice written before it was per document is still read,
 * as the fallback for a document that has none of its own, so nobody's till changes on upgrade.
 */
public final class QuickInvoiceAccess {

    public static final String QUICK = "QUICK";
    public static final String STANDARD = "STANDARD";

    private QuickInvoiceAccess() {
    }

    /** Whether this user may open the quick screen for this document. */
    public static boolean allowed(DocumentType documentType, Predicate<PermissionKey> granted) {
        return documentType != null
                && documentType.quickEntryPermission().map(granted::test).orElse(false);
    }

    /**
     * Whether a <b>new</b> document opens on the quick screen.
     *
     * @param documentChoice the choice remembered for this document, blank when none
     * @param olderChoice    the one choice remembered for every document before it was per
     *                       document, blank when none
     * @param allowed        {@link #allowed} for this user and document
     */
    public static boolean opensQuick(String documentChoice, String olderChoice, boolean allowed) {
        if (!allowed) {
            return false;
        }
        String choice = isBlank(documentChoice) ? olderChoice : documentChoice;
        return QUICK.equals(choice == null ? null : choice.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
