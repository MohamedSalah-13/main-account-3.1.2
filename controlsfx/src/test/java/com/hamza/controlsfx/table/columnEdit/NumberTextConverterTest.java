package com.hamza.controlsfx.table.columnEdit;

import com.hamza.controlsfx.table.Columns;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An editable number cell writes what the column around it writes, and reads back what it wrote.
 * The second half is the one that matters: the text an editor opens with is the converter's own,
 * so a converter that writes {@code 1,050.00} and cannot read it turns every untouched edit into
 * a refusal.
 */
class NumberTextConverterTest {

    @Test
    void moneyIsWrittenWithTwoDecimalsAndASeparator() {
        NumberTextConverter money = NumberTextConverter.money();
        assertEquals("7.50", money.toString(7.5));
        assertEquals("1,050.00", money.toString(1050.0));
    }

    @Test
    void aQuantityCarriesOnlyTheDecimalsItHas() {
        NumberTextConverter quantity = NumberTextConverter.quantity();
        assertEquals("3", quantity.toString(3.0));
        assertEquals("2.5", quantity.toString(2.5));
        assertEquals("0.625", quantity.toString(0.625));
        assertEquals("1,200", quantity.toString(1200.0));
    }

    @Test
    void aQuantityIsRoundedHalfUpToTheGram() {
        assertEquals("0.626", Columns.quantity(new BigDecimal("0.6255")));
    }

    @Test
    void readsBackWhatItWrote() {
        NumberTextConverter money = NumberTextConverter.money();
        for (double value : new double[]{0.5, 7.5, 1050.0, 1234567.89}) {
            assertEquals(value, money.fromString(money.toString(value)));
        }
        NumberTextConverter quantity = NumberTextConverter.quantity();
        for (double value : new double[]{1.0, 2.5, 0.625, 1200.0}) {
            assertEquals(value, quantity.fromString(quantity.toString(value)));
        }
    }

    @Test
    void readsArabicDigitsAndSeparators() {
        assertEquals(1050.5, NumberTextConverter.money().fromString("١٬٠٥٠٫٥٠"));
        assertEquals(12.0, NumberTextConverter.quantity().fromString("۱۲"));
    }

    @Test
    void blankIsNothingAndNonsenseIsRefusedDownstream() {
        NumberTextConverter money = NumberTextConverter.money();
        assertNull(money.fromString(""));
        assertNull(money.fromString("   "));
        assertTrue(Double.isNaN(money.fromString("abc")));
        assertEquals("", money.toString(null));
        assertEquals("", money.toString(Double.NaN));
    }
}
