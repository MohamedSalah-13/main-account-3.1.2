package com.hamza.account.controller.items;

import java.util.function.IntSupplier;

/**
 * Answers, for one code at a time, whether some other item already holds it - as its own
 * barcode, one of its extra codes, or the code on one of its units.
 *
 * <p>The item screen refuses a duplicate at the moment it is entered rather than only at
 * save. The three barcode tables each have their own unique index and none can see the
 * others, so a code typed into the extra-barcodes list could sit there through a whole
 * form being filled in and be refused at the end, with nothing saying which of the codes
 * on the screen was the problem - and with the item's own barcode field long since left
 * behind. {@code AddItemController.checkBarcodesAreFree} stays: it is the rule, applied
 * where the row is written. This is the hint, applied where the mistake is made.
 *
 * <p>Holds no reference to any JavaFX type: the lookup arrives as a function and the item
 * being edited as an {@link IntSupplier}, so it can be exercised without a toolkit - see
 * {@code BarcodeAvailabilityTest}.
 */
public final class BarcodeAvailability {

    /**
     * The lookup itself - {@code ItemsService::itemNameHoldingBarcode} in the
     * application, a stub in the tests.
     */
    @FunctionalInterface
    public interface Owner {
        String itemNameHoldingBarcode(String code, int itemId) throws Exception;
    }

    private final Owner owner;
    private final IntSupplier editedItemId;

    /**
     * @param editedItemId the item being edited, read on each call rather than once,
     *                     because the screen saves and then goes on being used - a
     *                     duplicate save re-opens it on a new id.
     */
    public BarcodeAvailability(Owner owner, IntSupplier editedItemId) {
        this.owner = owner;
        this.editedItemId = editedItemId;
    }

    /**
     * The name of the other item holding {@code code}, or {@code null} when the code is
     * free, blank, or belongs to the item being edited.
     */
    public String takenBy(String code) throws Exception {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return null;
        return owner.itemNameHoldingBarcode(trimmed, editedItemId.getAsInt());
    }

    /**
     * Refuses {@code code} if another item holds it, naming that item - "used by another
     * item" on its own leaves the user hunting for which one.
     */
    public void requireFree(String code) throws Exception {
        String holder = takenBy(code);
        if (holder != null) {
            throw new Exception("الباركود " + code.trim() + " مستخدم بالفعل للصنف: " + holder);
        }
    }
}
