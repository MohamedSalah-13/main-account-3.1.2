package com.hamza.account.features.items;

import com.hamza.account.opening.OpeningBalanceGuard.Verdict;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which items of a bulk edit take a new opening balance - decided for the whole batch before
 * anything is written.
 * <p>
 * The rule is the item screen's, from {@code OpeningBalanceGuard}: an opening balance is the one
 * figure on the row with no date on it, so it may change only while nothing has moved the item.
 * The bulk editor used to offer the field, drop the value on the way to the database and report
 * the save as done; applied here, the value is written where the rule allows and refused out loud
 * where it does not.
 * <p>
 * <b>A refusal refuses the whole batch.</b> Saving the items that may take the balance and
 * quietly skipping the rest would leave a list that looks edited and is only partly so, with
 * nothing on screen to say which part. So the message names how many were refused and which,
 * and nothing is written until the operator has left them out.
 */
public final class BulkOpeningBalance {

    /** How many refused names the message spells out before it says "and more". */
    static final int NAMES_SHOWN = 5;

    /** One item of the batch and the balance it would be given. */
    public record Item(int id, String name, double incoming) {
    }

    /** The guard's verdict on one item - {@code OpeningBalanceGuard.verdict} in the application. */
    @FunctionalInterface
    public interface Verdicts {
        Verdict of(int itemId, double incoming) throws DaoException;
    }

    private BulkOpeningBalance() {
    }

    /**
     * @param correction what to do instead for an item that has moved - the rule's own sentence
     * @return the ids whose opening balance is written: every item nothing has moved. One that has
     *         moved but already holds the value has nothing to write and is not a refusal
     * @throws BusinessRuleException when any item has moved and its balance would change
     */
    public static Set<Integer> writableIds(List<Item> items, Verdicts verdicts, String correction)
            throws DaoException {
        Set<Integer> writable = new LinkedHashSet<>();
        List<String> refused = new ArrayList<>();
        for (Item item : items) {
            switch (verdicts.of(item.id(), item.incoming())) {
                case OPEN -> writable.add(item.id());
                case REFUSED -> refused.add(item.name());
                case UNCHANGED -> {
                }
            }
        }
        if (!refused.isEmpty()) {
            throw new BusinessRuleException(refusal(refused, correction));
        }
        return writable;
    }

    /** The names one per line - no separator to write in any one language. */
    static String refusal(List<String> refused, String correction) {
        String names = String.join("\n", refused.subList(0, Math.min(NAMES_SHOWN, refused.size())));
        if (refused.size() > NAMES_SHOWN) {
            names += "\n…";
        }
        return LanguageManager.getInstance().getString("item.bulk.opening.locked",
                refused.size(), names, correction);
    }
}
