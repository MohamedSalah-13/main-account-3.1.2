package com.hamza.account.features.stocktransfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The screen read this with {@code Double.parseDouble}, so every one of these was zero and the
 * line was refused as an invalid quantity - on a keyboard that produces ٠-٩ by default.
 */
class TransferQuantityInputTest {

    @Test void readsArabicIndicDigits() {
        assertEquals(7, TransferQuantityInput.parse("٧"));
        assertEquals(12.5, TransferQuantityInput.parse("١٢٫٥"));
    }

    @Test void readsPersianDigits() {
        assertEquals(7, TransferQuantityInput.parse("۷"));
    }

    @Test void dropsTheSeparatorItsOwnFormatterWrites() {
        assertEquals(1000, TransferQuantityInput.parse("1٬000"));
    }

    @Test void readsWhatWasAlwaysRead() {
        assertEquals(3, TransferQuantityInput.parse("3"));
        assertEquals(2.25, TransferQuantityInput.parse("2.25"));
    }

    @Test void blankAndRubbishAreNothing() {
        assertEquals(0, TransferQuantityInput.parse(null));
        assertEquals(0, TransferQuantityInput.parse("   "));
        assertEquals(0, TransferQuantityInput.parse("كرتونة"));
    }

    /** A line refuses a quantity of zero or less anyway, and one message about it is enough. */
    @Test void negativeIsNothing() {
        assertEquals(0, TransferQuantityInput.parse("-5"));
    }
}
