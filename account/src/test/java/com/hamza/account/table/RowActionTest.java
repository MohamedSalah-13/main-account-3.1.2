package com.hamza.account.table;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.AppIcon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decisions a row of buttons makes, without a JavaFX toolkit.
 * <p>
 * The cell itself needs a running table to exercise, which is exactly why the two rules worth
 * checking were lifted out of it: which buttons a user is offered, and whether a button applies
 * to the row it is in. A rule written inside a cell factory is a rule nothing can check - the
 * reason {@code QuickEntryRules} exists for the same kind of screen.
 */
class RowActionTest {

    private static final Consumer<String> NOTHING = row -> { };

    private static RowAction<String> action(String key, PermissionKey permission) {
        return RowAction.of(key, AppIcon.EDIT, "app-neutral-button", permission, NOTHING);
    }

    @Nested
    @DisplayName("Which buttons the user is offered")
    class Permissions {

        private final RowAction<String> show = action("row.action.show", AppPermissions.CUSTOMER_SHOW);
        private final RowAction<String> edit = action("row.action.edit", AppPermissions.CUSTOMER_UPDATE);
        private final RowAction<String> delete = action("row.action.delete", AppPermissions.CUSTOMER_DELETE);

        @Test
        @DisplayName("an action the user may not perform is left out, not shown and refused")
        void refusedActionsAreNotOffered() {
            Set<PermissionKey> held = Set.of(AppPermissions.CUSTOMER_SHOW);

            List<RowAction<String>> offered =
                    RowAction.permitted(List.of(show, edit, delete), held::contains);

            assertEquals(List.of(show), offered);
        }

        @Test
        @DisplayName("the order given is the order kept")
        void orderSurvives() {
            List<RowAction<String>> offered =
                    RowAction.permitted(List.of(show, edit, delete), key -> true);

            assertEquals(List.of(show, edit, delete), offered);
        }

        /**
         * A read-only action that opens what the row already shows needs no key of its own.
         * Requiring one would mean inventing a permission for every button.
         */
        @Test
        void anActionWithNoPermissionIsAlwaysOffered() {
            RowAction<String> unguarded = action("row.action.show", null);

            assertEquals(List.of(unguarded),
                    RowAction.permitted(List.of(unguarded), key -> false));
        }

        @Test
        @DisplayName("holding nothing leaves a column with no buttons, not a missing column")
        void everythingCanBeRefused() {
            assertTrue(RowAction.permitted(List.of(show, edit, delete), key -> false).isEmpty());
        }
    }

    @Nested
    @DisplayName("Whether the button applies to its row")
    class Applicability {

        @Test
        @DisplayName("the default is that it does")
        void enabledByDefault() {
            assertTrue(action("row.action.edit", null).enabled().test("anything"));
        }

        @Test
        void aRowTheActionCannotActOnIsDisabled() {
            RowAction<String> onlyLongNames = new RowAction<>("row.action.edit", AppIcon.EDIT,
                    "app-neutral-button", null, row -> row.length() > 3, NOTHING);

            assertTrue(onlyLongNames.enabled().test("محمد1"));
            assertFalse(onlyLongNames.enabled().test("مح"));
        }

        /**
         * A null predicate is normalised rather than stored, so no caller has to guard it and
         * the cell can call {@code enabled().test(row)} unconditionally.
         */
        @Test
        void aMissingPredicateBecomesAlwaysTrue() {
            RowAction<String> action = new RowAction<>("row.action.edit", AppIcon.EDIT,
                    "app-neutral-button", null, null, NOTHING);

            assertTrue(action.enabled().test("anything"));
        }
    }

    @Nested
    @DisplayName("What a row action refuses to be")
    class Refusals {

        @Test
        @DisplayName("a button with no name is invisible to a screen reader")
        void aTooltipKeyIsRequired() {
            for (String missing : new String[]{null, "", "   "}) {
                assertThrows(IllegalArgumentException.class,
                        () -> RowAction.of(missing, AppIcon.EDIT, "app-neutral-button", null, NOTHING));
            }
        }

        @Test
        void anActionMustDoSomething() {
            assertThrows(IllegalArgumentException.class,
                    () -> RowAction.of("row.action.edit", AppIcon.EDIT, "app-neutral-button", null, null));
        }
    }

    @Nested
    @DisplayName("The action receives the row it was pressed in")
    class TheRow {

        @Test
        void itIsHandedItsOwnRow() {
            List<String> acted = new ArrayList<>();
            RowAction<String> action = RowAction.of("row.action.edit", AppIcon.EDIT,
                    "app-neutral-button", null, acted::add);

            action.action().accept("العميل 164");

            assertEquals(List.of("العميل 164"), acted);
        }

        /** The same instance is reused across every cell, so it must hold no row of its own. */
        @Test
        void oneActionServesEveryRow() {
            List<String> acted = new ArrayList<>();
            RowAction<String> action = RowAction.of("row.action.edit", AppIcon.EDIT,
                    "app-neutral-button", null, acted::add);

            action.action().accept("first");
            action.action().accept("second");

            assertEquals(List.of("first", "second"), acted);
            assertSame(action.action(), action.action());
        }
    }
}
