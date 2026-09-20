package com.hamza.account.features.stocktransfer;

import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;

import java.math.BigDecimal;

/**
 * What a quantity typed into the transfer screen means.
 * <p>
 * Out of the controller because a controller cannot be tested, and because what was there
 * was {@code Double.parseDouble}: it does not know the ٠-٩ an Arabic keyboard produces, nor
 * the thousands separator {@code NumberTextConverter} writes back into the very same field.
 * So a quantity typed the ordinary way on the machines this ships to came back as zero and
 * the line was refused as an invalid quantity - the screen telling the user that what they
 * had just typed correctly was not a number. The same omission that cost
 * {@code ReturnQuantityInput} its existence.
 */
public final class TransferQuantityInput {

    private TransferQuantityInput() {
    }

    /**
     * The quantity typed, or zero - which the screen reads as "there is nothing to add here".
     * Blank, text that is no number, and a negative are all zero; a transfer line refuses a
     * quantity of zero or less anyway, and one message about it is enough.
     */
    public static double parse(String text) {
        try {
            BigDecimal value = NumberTextConverter.parse(text);
            return value == null ? 0 : Math.max(0, value.doubleValue());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
