package com.hamza.account.table;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.AppIcon;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One button that acts on the row it sits in.
 *
 * <p><b>Why a row button rather than a toolbar button.</b> A toolbar button that acts on
 * "the selected row" is two gestures - select, then travel to the toolbar - and it has a
 * failure mode of its own: with nothing selected it has to answer "choose a row first",
 * which is a message that exists only because the control was put in the wrong place. A
 * button in the row cannot be pressed without naming its row.
 *
 * <p>The {@code permission} is a <b>hint</b>, exactly as {@code AuthorizationGuard.isGranted}
 * is: an action the user may not perform is left out of the column rather than shown and
 * refused. It is not enforcement - the service the action reaches still calls
 * {@code require}, and must, because the same operation is reachable from elsewhere. See
 * the authorization section of {@code CLAUDE.md}.
 *
 * @param <S>        the row type
 * @param titleKey   message key for the tooltip and the accessible text. A row button has no
 *                   caption - a column of three captioned buttons is wider than the data -
 *                   so this is the only place its name appears, and it is a key like any
 *                   other so the architecture test can see it
 * @param icon       the Ikonli icon, never an {@code InputStream} field
 * @param styleClass the button's class in the theme, so two screens cannot colour one action
 *                   two ways
 * @param permission the key that decides whether this button is offered at all, or null for
 *                   an action that needs none - opening a read-only view of what the row
 *                   already shows
 * @param enabled    whether the action applies to a given row; a row it cannot act on gets a
 *                   disabled button rather than a missing one, so the column does not change
 *                   shape as the user scrolls
 * @param action     what the button does with its row
 */
public record RowAction<S>(String titleKey, AppIcon icon, String styleClass,
                           PermissionKey permission, Predicate<S> enabled, Consumer<S> action) {

    public RowAction {
        if (titleKey == null || titleKey.isBlank()) {
            throw new IllegalArgumentException("A row action needs a message key for its tooltip");
        }
        if (action == null) {
            throw new IllegalArgumentException("A row action needs something to do");
        }
        if (enabled == null) {
            enabled = row -> true;
        }
    }

    /** The ordinary case: always applicable, guarded by one permission. */
    public static <S> RowAction<S> of(String titleKey, AppIcon icon, String styleClass,
                                      PermissionKey permission, Consumer<S> action) {
        return new RowAction<>(titleKey, icon, styleClass, permission, row -> true, action);
    }

    /**
     * The actions this user may be offered, in the order given.
     * <p>
     * Kept here rather than inside the cell factory so it can be exercised without a JavaFX
     * toolkit - the rule is "drop what the permission refuses", and a rule living inside a
     * cell is a rule nothing can check.
     *
     * @param granted answers whether a permission is held; the application passes
     *                {@code AuthorizationGuard::isGranted}
     */
    public static <S> List<RowAction<S>> permitted(List<RowAction<S>> actions,
                                                   Predicate<PermissionKey> granted) {
        return actions.stream()
                .filter(candidate -> candidate.permission() == null
                        || granted.test(candidate.permission()))
                .toList();
    }

    /** As {@link #permitted(List, Predicate)}, asking the real guard. */
    public static <S> List<RowAction<S>> permitted(List<RowAction<S>> actions) {
        return permitted(actions, AuthorizationGuard::isGranted);
    }
}
