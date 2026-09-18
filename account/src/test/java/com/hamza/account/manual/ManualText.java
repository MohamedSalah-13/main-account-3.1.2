package com.hamza.account.manual;

import com.hamza.account.features.export.ArabicTextHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The four things a flowing Arabic page needs that a report cell never did. Each was found by
 * rendering a page to an image, not by reasoning about it; {@code ManualTextTest} pins them.
 * <p>
 * <b>A paragraph must be wrapped in logical order and shaped one line at a time.</b>
 * {@link ArabicTextHelper#shape(String)} answers the <em>visual</em> string, and every other caller
 * in this application hands that to a single short cell. Give a whole paragraph to iText and it
 * breaks the visual string at its spaces, so the paragraph is laid out from its last line upwards:
 * rendered, the opening sentence of a four-line paragraph sat on the bottom line and the closing
 * sentence on the top. {@link #wrap} therefore breaks the logical text itself.
 * <p>
 * <b>A key combination is one left-to-right word, and the bidi pass does not know that.</b>
 * {@code ArabicTextHelper} deliberately leaves a digit touching a Latin letter alone - that is the
 * fix that stopped {@code NC7013} printing as {@code 7013NC} - but a {@code +} between them defeats
 * the guard: the digit is isolated by itself, becomes a run of its own, and {@code Ctrl+1} printed
 * as {@code 1+Ctrl}. A manual is mostly key combinations, so every {@code `code span`} is isolated
 * whole before shaping.
 * <p>
 * <b>Bold inside a line is a separate run, shaped on its own, and the runs go in reversed.</b>
 * Shaping produces visual order, so a paragraph built from several shaped runs must receive them
 * right to left - the first logical run last. Added in logical order instead, the bold term landed
 * in the middle of the line and the colon after it moved to the far edge.
 * <p>
 * <b>A line is measured run by run, in the face each run is drawn with.</b> Measuring the whole
 * line in the regular face under-reads every line holding a bold term, so iText found the line too
 * wide and wrapped it itself - and because the runs are handed to it reversed, what came out was
 * not a line broken in two but a word from the <em>start</em> of the line printed two lines below
 * where it belonged. A line this class returns must fit; nothing downstream may re-break it.
 */
public final class ManualText {

    /** Written as escapes: both are invisible, and a literal one is lost to the next edit. */
    static final String LEFT_TO_RIGHT_ISOLATE = "⁦";
    static final String POP_DIRECTIONAL_ISOLATE = "⁩";

    /**
     * Room left at the end of every line. A width is measured from the font's own metrics and drawn
     * by a layout engine that rounds differently; a line measured at exactly the limit is the one
     * that gets re-broken, and a re-broken line is not merely ugly here, it is out of order.
     */
    private static final float SAFETY = 4f;

    /** A `code span`: a key, a button caption, a file name - anything that reads left to right. */
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    /** A **bold term**, the lead-in of a bullet or the name of a thing being defined. */
    private static final Pattern BOLD_SPAN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    /**
     * A piece of a line drawn with one face. Code spans are <b>not</b> split out: they stay inside
     * the text and are isolated by {@link #prepare}, which is the path already proven to put
     * {@code Ctrl+1} the right way round. Splitting a sentence at every code span would put the
     * punctuation either side of it into runs of its own for no gain.
     */
    public record Span(String text, boolean bold) {
    }

    /** One word and whether a space separated it from the word before it on the same line. */
    private record Word(String text, boolean bold, boolean spaceBefore) {
    }

    private ManualText() {
    }

    /** The logical text with every code span isolated and its backticks removed, ready for shaping. */
    public static String prepare(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return CODE_SPAN.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                LEFT_TO_RIGHT_ISOLATE + match.group(1) + POP_DIRECTIONAL_ISOLATE));
    }

    /** {@link #prepare} then {@link ArabicTextHelper#shape}: what actually goes on the page. */
    public static String shape(String text) {
        return ArabicTextHelper.shape(prepare(text));
    }

    /** Splits {@code **bold**} out of a line, leaving everything else as it was written. */
    public static List<Span> spans(String text) {
        List<Span> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        Matcher bold = BOLD_SPAN.matcher(text);
        int at = 0;
        while (bold.find()) {
            if (bold.start() > at) {
                spans.add(new Span(text.substring(at, bold.start()), false));
            }
            spans.add(new Span(bold.group(1), true));
            at = bold.end();
        }
        if (at < text.length()) {
            spans.add(new Span(text.substring(at), false));
        }
        return spans;
    }

    /** The text of a line as the reader sees it, with the markers gone. */
    public static String plain(List<Span> runs) {
        StringBuilder text = new StringBuilder();
        for (Span run : runs) {
            text.append(run.text());
        }
        return text.toString();
    }

    /** How many characters a line holds - a stand-in for a font's measurement, for tests. */
    public static double plainLength(List<Span> runs) {
        return plain(runs).length();
    }

    /**
     * Greedy wrap over the <b>logical</b> text, keeping each word's face and the spacing it was
     * written with - {@code **term**:} has no space before its colon and must not gain one.
     *
     * @param measure the width of a line once shaped, measured run by run in each run's own face
     * @return the lines, each as the runs it is drawn from, in reading order
     */
    public static List<List<Span>> wrap(String text, float maxWidth,
                                        ToDoubleFunction<List<Span>> measure) {
        List<List<Span>> lines = new ArrayList<>();
        List<Word> words = words(spans(text));
        List<Word> line = new ArrayList<>();
        for (Word word : words) {
            List<Word> candidate = new ArrayList<>(line);
            candidate.add(word);
            if (!line.isEmpty() && measure.applyAsDouble(merge(candidate)) > maxWidth - SAFETY) {
                lines.add(merge(line));
                // A word starting a line is flush against the margin, whatever preceded it.
                line = new ArrayList<>(List.of(new Word(word.text(), word.bold(), false)));
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) {
            lines.add(merge(line));
        }
        return lines;
    }

    /**
     * One word per entry, so a line can break between any two of them, each remembering whether a
     * space stood before it. A bold span's boundary is not a word boundary: in {@code **term**:}
     * the colon opens the next span with no space, and splitting each span on its own whitespace
     * would insert one.
     */
    private static List<Word> words(List<Span> spans) {
        List<Word> words = new ArrayList<>();
        boolean spacePending = false;
        for (Span span : spans) {
            String text = span.text();
            if (text.isEmpty()) {
                continue;
            }
            spacePending |= Character.isWhitespace(text.charAt(0));
            boolean first = true;
            for (String word : text.trim().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                words.add(new Word(word, span.bold(), !words.isEmpty() && (spacePending || !first)));
                first = false;
            }
            spacePending = Character.isWhitespace(text.charAt(text.length() - 1));
        }
        return words;
    }

    /** Adjacent words of one face become one run, so a line is drawn in as few pieces as it can be. */
    private static List<Span> merge(List<Word> words) {
        List<Span> runs = new ArrayList<>();
        for (Word word : words) {
            String piece = (word.spaceBefore() ? " " : "") + word.text();
            if (!runs.isEmpty() && runs.getLast().bold() == word.bold()) {
                Span previous = runs.removeLast();
                runs.add(new Span(previous.text() + piece, word.bold()));
            } else if (word.spaceBefore() && !runs.isEmpty()) {
                // The separating space is drawn with the earlier run's face. A space has no shape,
                // so which face draws it does not matter - that it is drawn once does.
                Span previous = runs.removeLast();
                runs.add(new Span(previous.text() + " ", previous.bold()));
                runs.add(new Span(word.text(), word.bold()));
            } else {
                runs.add(new Span(word.text(), word.bold()));
            }
        }
        return runs;
    }
}
