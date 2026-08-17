package com.hamza.controlsfx.others;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code DefaultStringConverter.fromString} used to answer zero for a blank or
 * partial field ("", "-", "."), and {@code TextFormatter} resyncs its text from
 * that parsed value on every accepted change - so clearing a field that held
 * anything other than zero wrote "0.0" straight back over the empty text the
 * user had just typed. The field the user meant to erase read as refusing to.
 */
class TextFormatTest {

    @Test
    @DisplayName("an empty field parses to null, not zero")
    void emptyStringIsNull() {
        assertNull(TextFormat.doubleStringConverter.fromString(""));
    }

    @Test
    @DisplayName("a bare sign or decimal point - mid-typing states - also parses to null")
    void partialEditingStatesAreNull() {
        assertNull(TextFormat.doubleStringConverter.fromString("-"));
        assertNull(TextFormat.doubleStringConverter.fromString("."));
        assertNull(TextFormat.doubleStringConverter.fromString("-."));
    }

    @Test
    @DisplayName("a real number still parses as itself")
    void realNumberParsesNormally() {
        assertEquals(12.5, TextFormat.doubleStringConverter.fromString("12.5"));
        assertEquals(0.0, TextFormat.doubleStringConverter.fromString("0"));
    }

    @Test
    @DisplayName("null round-trips back to an empty string, not \"0.0\" - what actually fixes the resync")
    void nullRoundTripsToEmptyString() {
        assertEquals("", TextFormat.doubleStringConverter.toString(null));
    }

    @Test
    @DisplayName("the integer converter behaves the same way for its own type")
    void integerConverterAlsoAnswersNull() {
        assertNull(TextFormat.integerStringConverter.fromString(""));
        assertEquals(7, TextFormat.integerStringConverter.fromString("7"));
        assertEquals("", TextFormat.integerStringConverter.toString(null));
    }
}
