package com.hamza.controlsfx.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Breaks a message into lines no wider than a width, at the spaces between its words.
 * <p>
 * The shared alert breaks its message itself instead of leaving it to the label's wrapping,
 * because JavaFX on Linux wraps an Arabic sentence wrongly. Its Pango shaper caches each run's
 * text against the run object, and splitting a run at a line break shortens that object without
 * dropping the cache. The first line is then reshaped from the whole sentence: it is drawn wider
 * than the window and clipped at both ends, and the second line repeats the sentence's tail.
 * Latin text is not shaped and wraps correctly, and Windows shapes through DirectWrite, which
 * reads the run again. A line ended by {@code '\n'} is a run of its own that is never split, which
 * is what makes the lines built here right on every platform.
 * <p>
 * A word wider than the width is left whole on a line of its own: cutting inside an Arabic word
 * separates letters that are joined.
 */
final class MessageLines {

    private MessageLines() {
    }

    /**
     * The text with a {@code '\n'} wherever a line would be wider than {@code width}, as
     * {@code measure} reports it. A line break already in the text is kept.
     */
    static String wrap(String text, double width, ToDoubleFunction<String> measure) {
        if (text == null || text.isEmpty() || !(width > 0)) {
            return text;
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, width, measure, lines);
        }
        return String.join("\n", lines);
    }

    private static void wrapParagraph(String paragraph, double width, ToDoubleFunction<String> measure,
                                      List<String> lines) {
        if (measure.applyAsDouble(paragraph) <= width) {
            lines.add(paragraph);
            return;
        }
        String[] words = paragraph.split(" ", -1);
        StringBuilder line = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            String candidate = line + " " + words[i];
            if (line.isEmpty() || measure.applyAsDouble(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(words[i]);
            }
        }
        lines.add(line.toString());
    }
}
