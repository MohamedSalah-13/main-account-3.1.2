package com.hamza.account.features.invoice;

import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;

import java.math.BigDecimal;

/**
 * What a quantity typed into the "return from this invoice" picker means.
 * <p>
 * Out of the dialog because a dialog cannot be tested, and each of these was wrong in it:
 * it read the text with {@code Double.parseDouble}, which does not know the ٠-٩ an Arabic
 * keyboard produces, so a quantity typed the ordinary way on the machines this ships to was
 * silently a zero and the line was silently left off the return; and it accepted any
 * quantity at all, leaving the refusal to the save, after the whole document was written.
 */
public final class ReturnQuantityInput {

    private ReturnQuantityInput() {
    }

    /**
     * The quantity to return, never negative and never above {@code remaining}. Blank, and
     * text that is no number, are zero - which is "this line is not being returned".
     */
    public static double within(String text, double remaining) {
        double typed = parse(text);
        return Math.max(0, Math.min(typed, Math.max(remaining, 0)));
    }

    private static double parse(String text) {
        try {
            BigDecimal value = NumberTextConverter.parse(text);
            return value == null ? 0 : value.doubleValue();
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
