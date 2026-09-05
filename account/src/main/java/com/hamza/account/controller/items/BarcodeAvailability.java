package com.hamza.account.controller.items;

import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Answers, for one code at a time, whether some other item already holds it -
 * as its own barcode, one of its extra codes, or the code on one of its units.
 * <p>
 * The item screen refuses a duplicate at the moment it is entered rather than
 * only at save: the three barcode tables each have their own unique index and
 * none can see the others, so a code typed into the extra-barcodes list could
 * sit there through a whole form being filled in and be refused at the end,
 * with nothing saying which of the codes on the screen was the problem. The
 * save-time check in {@code AddItemController.checkBarcodesAreFree} stays - it
 * is the rule, applied where the row is written; this is the hint, applied
 * where the mistake is made.
 * <p>
 * Holds no reference to any JavaFX type: the lookup arrives as a function and
 * the item being edited as an {@link IntSupplier}, so it can be exercised
 * without a toolkit - see {@code BarcodeAvailabilityTest}.
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

    /**
     * The same question asked about a whole list at once -
     * {@code ItemsService::takenBarcodesAmong} in the application.
     * <p>
     * {@link #firstFreeFrom} needs it. Walking upwards one candidate at a time is a
     * query per number tried, and the numbers it starts from - {@code max(item id) + 1}
     * upwards - are exactly the short numeric codes a shop that types its own is most
     * likely to have used, so the walk is not the rare case. Asked in batches, the
     * ordinary open costs one query and the pathological one costs four.
     */
    @FunctionalInterface
    public interface TakenCodes {
        Set<String> takenAmong(List<String> codes, int itemId) throws Exception;
    }

    /**
     * The longest code the item screen accepts - see
     * {@code item.error.barcode.too.long}. A generated code that grew past it
     * would be refused by the same screen that produced it.
     */
    public static final int MAX_LENGTH = 14;

    /**
     * How far {@link #firstFreeFrom} will walk before giving up. A thousand
     * consecutive taken codes means the numbering is not what this generator
     * assumes, and asking the database a million times is not the answer.
     */
    private static final int MAX_ATTEMPTS = 1000;

    /**
     * How many candidates {@link #firstFreeFrom} asks about in one go. Large enough that
     * the ordinary case is a single query, small enough that the {@code IN} list stays a
     * reasonable statement.
     */
    private static final int BATCH_SIZE = 250;

    private final Owner owner;
    private final TakenCodes takenCodes;
    private final IntSupplier editedItemId;

    /**
     * @param editedItemId the item being edited, read on each call rather than
     *                     once, because the screen saves and then goes on being
     *                     used - a duplicate save re-opens it on a new id.
     */
    public BarcodeAvailability(Owner owner, IntSupplier editedItemId) {
        this(owner, null, editedItemId);
    }

    /**
     * @param takenCodes the batch lookup {@link #firstFreeFrom} uses. Null falls back to
     *                   asking {@code owner} one candidate at a time, which is what the
     *                   generator did before there was a batch query - correct, and a
     *                   round trip per number tried.
     */
    public BarcodeAvailability(Owner owner, TakenCodes takenCodes, IntSupplier editedItemId) {
        this.owner = owner;
        this.takenCodes = takenCodes;
        this.editedItemId = editedItemId;
    }

    /**
     * The name of the other item holding {@code code}, or {@code null} when the
     * code is free, blank, or belongs to the item being edited.
     */
    public String takenBy(String code) throws Exception {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return null;
        return owner.itemNameHoldingBarcode(trimmed, editedItemId.getAsInt());
    }

    /**
     * The first whole number from {@code start} upwards that no item answers to,
     * as a code - or {@code null} if there is none within {@link #MAX_ATTEMPTS}
     * or the numbering has grown past {@link #MAX_LENGTH} digits.
     * <p>
     * The item screen offers a code of its own when a new item is opened, and it
     * used to offer {@code max(item id) + 1} without asking whether anything held
     * it. It routinely does: a barcode is a printed code, not a row number, so an
     * item whose real code happens to be a small number - or an item deleted and
     * its id reused - collides with it, and the collision only surfaced at save,
     * on a field the user never typed in.
     * <p>
     * Walks upwards rather than jumping to {@code max(code) + 1}: one item
     * carrying a 13-digit EAN would otherwise push every generated code past the
     * length the same screen accepts.
     * <p>
     * The walk is done a batch at a time - see {@link TakenCodes}. It used to be one
     * query per number tried, on the JavaFX thread, both when the dialog opens and again
     * after every save, so a shop whose first few hundred codes are taken paid hundreds
     * of round trips before the window drew.
     */
    public String firstFreeFrom(long start) throws Exception {
        long candidate = Math.max(start, 1);
        int attempts = 0;

        while (attempts < MAX_ATTEMPTS) {
            List<String> batch = new ArrayList<>();
            while (batch.size() < BATCH_SIZE && attempts + batch.size() < MAX_ATTEMPTS) {
                String code = String.valueOf(candidate + batch.size());
                // Past the length the screen accepts, and every later candidate is longer
                // still - there is nothing left to offer, batch or no batch.
                if (code.length() > MAX_LENGTH) break;
                batch.add(code);
            }
            if (batch.isEmpty()) return null;

            String free = firstFreeIn(batch);
            if (free != null) return free;

            attempts += batch.size();
            candidate += batch.size();
        }
        return null;
    }

    /** The first of {@code batch} nobody holds, or null if they are all taken. */
    private String firstFreeIn(List<String> batch) throws Exception {
        if (takenCodes == null) {
            for (String code : batch) {
                if (takenBy(code) == null) return code;
            }
            return null;
        }

        Set<String> taken = takenCodes.takenAmong(batch, editedItemId.getAsInt());
        for (String code : batch) {
            if (!taken.contains(code)) return code;
        }
        return null;
    }

    /**
     * Refuses {@code code} if another item holds it, naming that item - "used by
     * another item" on its own leaves the user hunting for which one.
     */
    public void requireFree(String code) throws Exception {
        String holder = takenBy(code);
        if (holder != null) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("item.error.barcode.used.by.other.named", code.trim(), holder));
        }
    }
}
