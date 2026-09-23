package com.hamza.controlsfx.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lines the shared alert builds for its message. The defect they replace drew the whole
 * sentence on the first line and its tail again on the second, so the property that matters most
 * is that the lines, joined back, are the message - no word lost and none repeated.
 */
class MessageLinesTest {

    /** One point per character: enough to place the breaks, and independent of any font. */
    private static final ToDoubleFunction<String> CHARACTERS = String::length;

    private static final String REFUSAL =
            "تعذر الوصول إلى مصادر الأسعار تحقق من اتصال الجهاز بالإنترنت ثم أعد المحاولة أو اكتب السعر يدويا";

    @Test
    @DisplayName("a message that fits is returned as it is")
    void aMessageThatFitsIsUnchanged() {
        assertEquals("لا يوجد سعر لهذا اليوم", MessageLines.wrap("لا يوجد سعر لهذا اليوم", 40, CHARACTERS));
    }

    @Test
    @DisplayName("every line fits the width, and the lines joined back are the message")
    void everyLineFitsAndNothingIsLostOrRepeated() {
        String wrapped = MessageLines.wrap(REFUSAL, 30, CHARACTERS);

        List<String> lines = Arrays.asList(wrapped.split("\n"));
        assertTrue(lines.size() > 1, wrapped);
        lines.forEach(line -> assertTrue(line.length() <= 30, "too wide: " + line));
        assertEquals(REFUSAL, String.join(" ", lines));
    }

    @Test
    @DisplayName("a line takes as many words as fit, not fewer")
    void aLineIsFilledBeforeBreaking() {
        assertEquals("one two\nthree four\nfive", MessageLines.wrap("one two three four five", 10, CHARACTERS));
    }

    @Test
    @DisplayName("a word wider than the width stays whole on a line of its own")
    void aWordWiderThanTheWidthIsNotCut() {
        assertEquals("a\nبالإنترنتبالإنترنت\nb", MessageLines.wrap("a بالإنترنتبالإنترنت b", 5, CHARACTERS));
    }

    @Test
    @DisplayName("a line break already in the message is kept, and each part is wrapped on its own")
    void anExistingBreakIsKept() {
        assertEquals("one two\nthree\n\nfour five", MessageLines.wrap("one two three\n\nfour five", 9, CHARACTERS));
    }

    @Test
    @DisplayName("an empty message, no message and no width are returned as they are")
    void nothingToWrap() {
        assertNull(MessageLines.wrap(null, 30, CHARACTERS));
        assertEquals("", MessageLines.wrap("", 30, CHARACTERS));
        assertEquals(REFUSAL, MessageLines.wrap(REFUSAL, 0, CHARACTERS));
        assertEquals(REFUSAL, MessageLines.wrap(REFUSAL, Double.NaN, CHARACTERS));
    }
}
