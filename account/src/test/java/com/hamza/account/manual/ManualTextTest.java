package com.hamza.account.manual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every case here is a defect that was rendered to an image before it was written down. None of
 * them is visible in the text a PDF reader extracts, which is why they survived until a page was
 * looked at rather than parsed.
 */
class ManualTextTest {

    /** A line's width in "characters", so wrapping can be reasoned about without a PDF font. */
    private static double characters(List<ManualText.Span> runs) {
        return ManualText.plainLength(runs);
    }

    @Nested
    @DisplayName("wrapping")
    class Wrapping {

        @Test
        @DisplayName("breaks the logical text, so the lines come back in reading order")
        void keepsReadingOrder() {
            String text = "واحد اثنان ثلاثة أربعة خمسة ستة";
            List<List<ManualText.Span>> lines = ManualText.wrap(text, 14, ManualTextTest::characters);

            assertTrue(lines.size() > 1, "the sample is meant to need more than one line");
            assertTrue(ManualText.plain(lines.getFirst()).startsWith("واحد"),
                    "the first line must start the text - shaping first and letting the layout "
                            + "engine break the visual string lays the paragraph out upside down");
            assertTrue(ManualText.plain(lines.getLast()).endsWith("ستة"));
        }

        @Test
        @DisplayName("puts every word somewhere, once")
        void losesNothing() {
            String text = "بيع شراء مرتجع تحصيل جرد وردية نسخة";
            List<List<ManualText.Span>> lines = ManualText.wrap(text, 12, ManualTextTest::characters);

            String rejoined = lines.stream().map(ManualText::plain).reduce((a, b) -> a + " " + b).orElse("");
            assertEquals(text, rejoined.replaceAll("\\s+", " ").trim());
        }

        @Test
        @DisplayName("a word longer than the line still gets a line of its own rather than vanishing")
        void keepsAnOverlongWord() {
            List<List<ManualText.Span>> lines =
                    ManualText.wrap("قصير كلمةطويلةجدالاتتسعفيسطر", 8, ManualTextTest::characters);
            assertTrue(lines.stream().map(ManualText::plain).anyMatch(line -> line.contains("كلمةطويلة")));
        }
    }

    @Nested
    @DisplayName("code spans")
    class CodeSpans {

        @Test
        @DisplayName("Ctrl+1 is isolated, because bare it prints as 1+Ctrl")
        void isolatesAKeyCombination() {
            String prepared = ManualText.prepare("اضغط `Ctrl+1` للبيع");

            assertTrue(prepared.contains(ManualText.LEFT_TO_RIGHT_ISOLATE + "Ctrl+1"
                    + ManualText.POP_DIRECTIONAL_ISOLATE));
            assertFalse(prepared.contains("`"), "the backticks are markup, not text");
        }

        @Test
        @DisplayName("the shaped line reads Ctrl+1 the way it was written")
        void shapesAKeyCombinationInOrder() {
            String shaped = ManualText.shape("اضغط `Ctrl+1` للبيع");
            String latin = shaped.replaceAll("[^A-Za-z0-9+]", "");

            assertEquals("Ctrl+1", latin,
                    "a + between a letter and a digit defeats ArabicTextHelper's own guard");
        }
    }

    @Nested
    @DisplayName("bold")
    class Bold {

        @Test
        @DisplayName("splits the term out as a run of its own")
        void splitsTheTerm() {
            List<ManualText.Span> spans = ManualText.spans("**البيع**: فواتير");

            assertEquals(2, spans.size());
            assertEquals(new ManualText.Span("البيع", true), spans.getFirst());
            assertEquals(new ManualText.Span(": فواتير", false), spans.getLast());
        }

        @Test
        @DisplayName("does not invent a space the source did not have")
        void keepsTheSpacingAsWritten() {
            List<List<ManualText.Span>> lines =
                    ManualText.wrap("**البيع**: فواتير الشراء", 40, ManualTextTest::characters);

            assertEquals("البيع: فواتير الشراء", ManualText.plain(lines.getFirst()),
                    "splitting each run on its own whitespace put a space before the colon");
        }

        @Test
        @DisplayName("keeps the space that was there")
        void keepsARealSpace() {
            List<List<ManualText.Span>> lines =
                    ManualText.wrap("قبل **الوسط** بعد", 40, ManualTextTest::characters);

            assertEquals("قبل الوسط بعد", ManualText.plain(lines.getFirst()));
        }

        @Test
        @DisplayName("a word starting a line is flush, whatever separated it on the line before")
        void doesNotIndentAContinuationWithASpace() {
            List<List<ManualText.Span>> lines =
                    ManualText.wrap("واحد اثنان ثلاثة", 7, ManualTextTest::characters);

            lines.forEach(line -> assertFalse(ManualText.plain(line).startsWith(" "),
                    "a leading space would print as an indent nobody asked for"));
        }
    }
}
