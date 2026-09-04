package com.hamza.account.features.scalebarcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaleBarcodeFormatTest {

    /** What every install has been running: 27 | 5 item digits | 5 value digits | check digit. */
    private static ScaleBarcodeFormat defaults() {
        return ScaleBarcodeFormat.deriveValueDigits(27, 2, 5, 13, true);
    }

    @Nested
    @DisplayName("the value takes what the other parts leave")
    class Derivation {

        @Test
        @DisplayName("the shipped defaults still mean what they always meant")
        void defaultsAreUnchanged() {
            ScaleBarcodeFormat format = defaults();

            assertEquals(5, format.valueDigits());
            assertEquals(13, format.totalLength());
            assertEquals("27", format.prefixText());
            assertNull(format.problemKey());
        }

        @Test
        @DisplayName("a scale with no check digit gives that position back to the value")
        void noCheckDigitLengthensTheValue() {
            ScaleBarcodeFormat format = ScaleBarcodeFormat.deriveValueDigits(27, 2, 5, 13, false);

            assertEquals(6, format.valueDigits());
            assertEquals(13, format.totalLength());
        }

        @Test
        @DisplayName("the prefix is padded to its width, so 7 in two digits is 07")
        void prefixIsPadded() {
            assertEquals("07", ScaleBarcodeFormat.deriveValueDigits(7, 2, 5, 13, true).prefixText());
            assertEquals("00027", ScaleBarcodeFormat.deriveValueDigits(27, 5, 5, 13, true).prefixText());
        }
    }

    @Nested
    @DisplayName("a layout that cannot read anything says so")
    class Problems {

        @Test
        @DisplayName("parts that overrun the barcode leave no digits for the value")
        void partsMustFit() {
            ScaleBarcodeFormat format = ScaleBarcodeFormat.deriveValueDigits(27, 2, 10, 13, true);

            assertEquals(ScaleBarcodeFormat.VALUE_DIGITS_REQUIRED, format.problemKey());
            assertFalse(format.isUsable());
        }

        @Test
        @DisplayName("a prefix wider than the digits allotted to it is refused, not truncated")
        void prefixMustFitItsWidth() {
            ScaleBarcodeFormat format = ScaleBarcodeFormat.deriveValueDigits(1234, 2, 5, 13, true);

            assertEquals(ScaleBarcodeFormat.PREFIX_TOO_LONG, format.problemKey());
        }

        @Test
        void everyPartNeedsAtLeastOneDigit() {
            assertEquals(ScaleBarcodeFormat.PREFIX_DIGITS_REQUIRED,
                    ScaleBarcodeFormat.deriveValueDigits(27, 0, 5, 13, true).problemKey());
            assertEquals(ScaleBarcodeFormat.ITEM_DIGITS_REQUIRED,
                    ScaleBarcodeFormat.deriveValueDigits(27, 2, 0, 13, true).problemKey());
        }

        @Test
        @DisplayName("the shortfall is reported as it is, so the number to change is visible")
        void aNegativeRemainderIsNotClamped() {
            assertTrue(ScaleBarcodeFormat.deriveValueDigits(27, 2, 20, 13, true).valueDigits() < 0);
        }
    }
}
