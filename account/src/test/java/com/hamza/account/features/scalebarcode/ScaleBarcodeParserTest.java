package com.hamza.account.features.scalebarcode;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ScaleBarcodeParserTest {

    private static final ScaleBarcodeFormat DEFAULTS =
            ScaleBarcodeFormat.deriveValueDigits(27, 2, 5, 13, true);

    /** 27 | 00001 | 00050 | 1 - item 00001, 50 grams. */
    private static final String SAMPLE = "2700001000501";

    @Nested
    @DisplayName("splitting")
    class Splitting {

        @Test
        void readsTheItemCodeAndTheValue() throws Exception {
            ScaleBarcodeParts parts = ScaleBarcodeParser.parse(SAMPLE, DEFAULTS, false);

            assertEquals("00001", parts.itemCode());
            assertEquals(50, parts.rawValue());
        }

        @Test
        @DisplayName("the item code keeps its leading zeros - it is matched as text")
        void itemCodeKeepsLeadingZeros() throws Exception {
            assertEquals("00001", ScaleBarcodeParser.parse(SAMPLE, DEFAULTS, false).itemCode());
        }

        @Test
        @DisplayName("without a check digit the last position belongs to the value")
        void noCheckDigitMeansOneMoreValueDigit() throws Exception {
            ScaleBarcodeFormat format = ScaleBarcodeFormat.deriveValueDigits(27, 2, 5, 13, false);

            ScaleBarcodeParts parts = ScaleBarcodeParser.parse(SAMPLE, format, false);

            // The same digits, read by a scale that prints no check digit: the last
            // position joins the value, so 00050|1 is read as 000501.
            assertEquals(501, parts.rawValue());
            assertEquals(6, format.valueDigits());
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        void aLayoutThatCannotWorkIsRefusedBeforeTheBarcodeIsTouched() {
            ScaleBarcodeFormat broken = ScaleBarcodeFormat.deriveValueDigits(27, 2, 10, 13, true);

            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse(SAMPLE, broken, false));
        }

        @Test
        @DisplayName("a mis-set item width refuses, rather than throwing out of a substring")
        void anOverrunningLayoutDoesNotCrash() {
            ScaleBarcodeFormat broken = ScaleBarcodeFormat.deriveValueDigits(27, 2, 30, 13, true);

            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse(SAMPLE, broken, false));
        }

        @Test
        void nonNumeric() {
            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse("27000A1000501", DEFAULTS, false));
        }

        @Test
        void wrongLength() {
            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse("270000100050", DEFAULTS, false));
        }

        @Test
        void wrongScalePrefix() {
            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse("2500001000501", DEFAULTS, false));
        }

        @Test
        void anItemCodeOfAllZeros() {
            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse("2700000000501", DEFAULTS, false));
        }

        @Test
        void aValueOfZero() {
            assertThrows(UserValidationException.class, () -> ScaleBarcodeParser.parse("2700001000001", DEFAULTS, false));
        }
    }

    @Nested
    @DisplayName("the check digit")
    class CheckDigit {

        @Test
        @DisplayName("carrying one and verifying it are different questions")
        void presenceIsNotValidation() {
            // SAMPLE's last digit is whatever the fixture chose; with validation off it is
            // still consumed as a position, and the parse succeeds either way.
            assertDoesNotThrow(() -> ScaleBarcodeParser.parse(SAMPLE, DEFAULTS, false));
        }

        @Test
        void aCorrectOnePasses() throws Exception {
            String data = SAMPLE.substring(0, 12);
            String valid = data + ScaleBarcodeCheckDigit.of(data);

            assertEquals("00001", ScaleBarcodeParser.parse(valid, DEFAULTS, true).itemCode());
        }

        @Test
        void aWrongOneIsRefused() {
            String data = SAMPLE.substring(0, 12);
            char correct = ScaleBarcodeCheckDigit.of(data);
            char wrong = correct == '0' ? '1' : '0';

            assertThrows(UserValidationException.class,
                    () -> ScaleBarcodeParser.parse(data + wrong, DEFAULTS, true));
        }

        @Test
        @DisplayName("EAN-13's own digit is what the algorithm produces")
        void matchesEan13() {
            // 590123412345 -> 7 is the worked example in the GS1 specification.
            assertEquals('7', ScaleBarcodeCheckDigit.of("590123412345"));
        }

        @Test
        @DisplayName("and EAN-8's, which weighting from the left got wrong")
        void matchesEan8() {
            // Seven data digits, where the two anchorings genuinely disagree: right-anchored
            // gives 0, and weighting from the left - what this code used to do - gives 8.
            assertEquals('0', ScaleBarcodeCheckDigit.of("1234567"));
        }
    }
}
