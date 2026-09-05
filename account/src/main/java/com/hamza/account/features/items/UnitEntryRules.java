package com.hamza.account.features.items;

import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.Objects;

/**
 * What the units tab of the item screen may and may not accept, as plain functions.
 * <p>
 * These were spread over {@code TableUnitsSetting} and {@code UnitsTabController}, where
 * each one needed a {@code TableView}, a selection model or a bound property to reach -
 * so nothing could check them without a running JavaFX toolkit, and nothing did. That is
 * the same move {@code QuickEntryRules} made for the quick invoice screen, for the same
 * reason: every one of these was a defect at some point.
 * <p>
 * <b>Row 0 is the item's own unit</b> - {@code items.unit_id} shown in the table so the
 * factors have something visible to be counted against. It is not one of the item's unit
 * rows, {@code ItemsDao.saveUnits} drops it, and that is the whole reason for
 * {@link #mayDeleteRow} and {@link #mayEditRow}.
 * <p>
 * Every refusal is a {@link UserValidationException} carrying a message key's text.
 * The type is not decoration: {@code AllAlerts.handleError} shows a
 * {@code UserFacingException} as its own sentence and everything else as a generic
 * "unexpected error" and a reference code, so a {@code RuntimeException} here would take
 * the operator's answer away from them. See {@code UnitEntryRulesTest}.
 */
public final class UnitEntryRules {

    private UnitEntryRules() {
    }

    /** Whether {@code index} is the base-unit row. */
    public static boolean isBaseRow(int index) {
        return index == 0;
    }

    /**
     * Whether the row at {@code index} may be removed. The base unit is changed with the
     * item's own unit combo, not deleted here, and a negative index is "nothing selected"
     * - a double-click below the last row, or a delete pressed on an empty table.
     */
    public static boolean mayDeleteRow(int index) {
        return index > 0;
    }

    /** Whether an edited cell in the row at {@code index} may be applied. */
    public static boolean mayEditRow(int index) {
        return index > 0;
    }

    /**
     * How many base units this one holds - the number an invoice line multiplies by.
     * <p>
     * Zero or less is refused rather than stored: {@code quantity_items_table} computes a
     * balance as {@code quantity * type_value}, so a factor of zero is a line that moves
     * no stock at all and a negative one moves it the wrong way.
     */
    public static double factor(String text) throws UserValidationException {
        double quantity;
        try {
            quantity = text == null ? 0 : Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.units.quantity.invalid"));
        }
        if (quantity <= 0) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.units.quantity.positive"));
        }
        return quantity;
    }

    /**
     * A price this unit carries of its own, or zero for "priced from the item".
     * <p>
     * Blank is the zero: leaving the field empty is how a unit is said to have no price
     * of its own, and {@code ItemUnits.sellPrice} then falls back to the item's price
     * times the factor. A negative number would read as a price and be charged.
     */
    public static double price(String text, String fieldName) throws UserValidationException {
        if (text == null || text.isBlank()) {
            return 0;
        }
        double price;
        try {
            price = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.price.invalid", fieldName));
        }
        if (price < 0) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.price.negative", fieldName));
        }
        return price;
    }

    /**
     * Whether any row already carries {@code unitName} - the check the add button makes.
     * A row whose unit did not resolve counts as no unit rather than throwing.
     */
    public static boolean holdsUnit(List<ItemsUnitsModel> rows, String unitName) {
        return holdsUnit(rows, unitName, 0);
    }

    /**
     * Whether a row <em>other than the base one</em> carries {@code unitName} - the check
     * behind refusing to make the item's own unit one it is also sold by. Two rows for
     * one unit with different factors is a contradiction, not a duplicate.
     */
    public static boolean holdsUnitBesidesBase(List<ItemsUnitsModel> rows, String unitName) {
        return holdsUnit(rows, unitName, 1);
    }

    private static boolean holdsUnit(List<ItemsUnitsModel> rows, String unitName, int skip) {
        if (rows == null || unitName == null) {
            return false;
        }
        return rows.stream()
                .skip(skip)
                .map(ItemsUnitsModel::getUnitsModel)
                .filter(Objects::nonNull)
                .anyMatch(unit -> unitName.equals(unit.getUnit_name()));
    }

    // The keys are written out at each getString call rather than through a helper:
    // MessageKeyArchitectureTest reads the argument of every getString in the tree and
    // checks it against the three bundles, and a key handed through a variable is
    // invisible to it. See its javadoc - a key it cannot see is a screen that reads
    // "item.error.price.negative" with nothing in the build noticing.
}
