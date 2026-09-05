package com.hamza.account.features.items;

import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.ErrorCategory;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The units tab's decisions, checked without a JavaFX toolkit - which is the point of
 * {@link UnitEntryRules} existing. Every one of these used to need a {@code TableView},
 * a selection model or a bound text property to reach, so none of them were covered.
 * <p>
 * The messages are deliberately not asserted: the current language is a machine-wide
 * preference, so pinning Arabic text here would pin the developer's settings too. What
 * is asserted is the <b>category</b> - a refusal the user is meant to read has to be
 * {@code VALIDATION}, because that is the only thing standing between the operator and a
 * reference code. That is the defect this class was extracted alongside.
 */
class UnitEntryRulesTest {

    private static ItemsUnitsModel row(String unitName) {
        var row = new ItemsUnitsModel();
        if (unitName != null) {
            var unit = new UnitsModel();
            unit.setUnit_name(unitName);
            row.setUnitsModel(unit);
        }
        return row;
    }

    @Nested
    @DisplayName("The base-unit row")
    class BaseRow {

        @Test
        void rowZeroIsTheBaseUnit() {
            assertTrue(UnitEntryRules.isBaseRow(0));
            assertFalse(UnitEntryRules.isBaseRow(1));
        }

        @Test
        void theBaseRowCannotBeDeletedOrEdited() {
            assertFalse(UnitEntryRules.mayDeleteRow(0));
            assertFalse(UnitEntryRules.mayEditRow(0));
        }

        @Test
        void anyOtherRowCanBe() {
            assertTrue(UnitEntryRules.mayDeleteRow(1));
            assertTrue(UnitEntryRules.mayEditRow(3));
        }

        @Test
        @DisplayName("nothing selected is -1, and is not the base row being deleted")
        void nothingSelectedIsRefusedToo() {
            assertFalse(UnitEntryRules.mayDeleteRow(-1));
            assertFalse(UnitEntryRules.mayEditRow(-1));
        }
    }

    @Nested
    @DisplayName("The factor")
    class Factor {

        @Test
        void aPositiveNumberIsTheFactor() throws Exception {
            assertEquals(12, UnitEntryRules.factor("12"));
            assertEquals(0.5, UnitEntryRules.factor(" 0.5 "));
        }

        @Test
        @DisplayName("zero is refused - it is a line that moves no stock")
        void zeroIsRefused() {
            assertThrows(UserValidationException.class, () -> UnitEntryRules.factor("0"));
        }

        @Test
        void negativeIsRefused() {
            assertThrows(UserValidationException.class, () -> UnitEntryRules.factor("-3"));
        }

        @Test
        void blankAndNullAndNonsenseAreRefused() {
            assertThrows(UserValidationException.class, () -> UnitEntryRules.factor(null));
            assertThrows(UserValidationException.class, () -> UnitEntryRules.factor("   "));
            assertThrows(UserValidationException.class, () -> UnitEntryRules.factor("كرتونة"));
        }

        @Test
        @DisplayName("the refusal is a validation failure, not a technical one")
        void theRefusalIsReadableByTheUser() {
            var e = assertThrows(UserValidationException.class, () -> UnitEntryRules.factor("0"));
            assertEquals(ErrorCategory.VALIDATION, e.category());
            assertFalse(e.userMessage().isBlank());
        }
    }

    @Nested
    @DisplayName("A unit's own price")
    class Price {

        @Test
        @DisplayName("blank means priced from the item, which is zero")
        void blankIsZero() throws Exception {
            assertEquals(0, UnitEntryRules.price(null, "شراء"));
            assertEquals(0, UnitEntryRules.price("", "شراء"));
            assertEquals(0, UnitEntryRules.price("   ", "شراء"));
        }

        @Test
        void aNumberIsThePrice() throws Exception {
            assertEquals(85.5, UnitEntryRules.price(" 85.5 ", "بيع"));
        }

        @Test
        @DisplayName("zero typed explicitly is still 'no price of its own'")
        void zeroIsAccepted() {
            assertDoesNotThrow(() -> UnitEntryRules.price("0", "بيع"));
        }

        @Test
        void negativeIsRefused() {
            var e = assertThrows(UserValidationException.class, () -> UnitEntryRules.price("-1", "بيع"));
            assertEquals(ErrorCategory.VALIDATION, e.category());
        }

        @Test
        void nonsenseIsRefused() {
            assertThrows(UserValidationException.class, () -> UnitEntryRules.price("abc", "بيع"));
        }

        @Test
        @DisplayName("the field's name reaches the message, so the operator knows which one")
        void theMessageNamesTheField() {
            var e = assertThrows(UserValidationException.class, () -> UnitEntryRules.price("-1", "سعر البيع 2"));
            assertTrue(e.userMessage().contains("سعر البيع 2"), e.userMessage());
        }
    }

    @Nested
    @DisplayName("Which units are already on the item")
    class AlreadyAdded {

        private final List<ItemsUnitsModel> rows = new ArrayList<>(
                List.of(row("قطعة"), row("كرتونة"), row("دستة")));

        @Test
        void holdsUnitSeesEveryRowIncludingTheBaseOne() {
            assertTrue(UnitEntryRules.holdsUnit(rows, "قطعة"));
            assertTrue(UnitEntryRules.holdsUnit(rows, "دستة"));
            assertFalse(UnitEntryRules.holdsUnit(rows, "لفة"));
        }

        @Test
        @DisplayName("besides-base skips row 0, so the item's own unit does not clash with itself")
        void holdsUnitBesidesBaseSkipsTheBaseRow() {
            assertFalse(UnitEntryRules.holdsUnitBesidesBase(rows, "قطعة"));
            assertTrue(UnitEntryRules.holdsUnitBesidesBase(rows, "كرتونة"));
        }

        @Test
        @DisplayName("a row whose unit did not resolve counts as no unit, it does not throw")
        void anUnresolvedRowIsSkipped() {
            var withNull = new ArrayList<>(List.of(row("قطعة")));
            withNull.add(row(null));
            assertDoesNotThrow(() -> UnitEntryRules.holdsUnit(withNull, "كرتونة"));
            assertFalse(UnitEntryRules.holdsUnit(withNull, "كرتونة"));
        }

        @Test
        void nullsAreNotAMatch() {
            assertFalse(UnitEntryRules.holdsUnit(rows, null));
            assertFalse(UnitEntryRules.holdsUnit(null, "قطعة"));
        }

        @Test
        @DisplayName("an empty table holds nothing, base row included")
        void anEmptyListHoldsNothing() {
            assertFalse(UnitEntryRules.holdsUnit(List.of(), "قطعة"));
            assertFalse(UnitEntryRules.holdsUnitBesidesBase(List.of(), "قطعة"));
        }
    }
}
