package com.hamza.account.manual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one manual page out of its {@code .md} file.
 * <p>
 * Deliberately a small, whole-line grammar rather than a Markdown library: the manual is written by
 * whoever knows the screens, the renderer has to draw every construct it accepts, and a format that
 * accepts more than the renderer draws is a page that silently loses a paragraph. Anything the
 * grammar does not recognise is prose, so a typo reads as a sentence rather than disappearing.
 */
public final class ManualParser {

    private static final Pattern FIGURE = Pattern.compile("^!\\[(.*)]\\(([A-Za-z0-9._-]+)\\)\\s*$");
    private static final Pattern STEP = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");
    private static final Pattern FACT = Pattern.compile("^\\|([^|]+)\\|([^|]*)\\|\\s*$");
    private static final Map<String, ManualBlock.Callout.Kind> CALLOUTS = Map.of(
            "ملاحظة", ManualBlock.Callout.Kind.NOTE,
            "تحذير", ManualBlock.Callout.Kind.WARNING,
            "نصيحة", ManualBlock.Callout.Kind.TIP);

    private ManualParser() {
    }

    public static ManualPage parse(String id, String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.replace("\r\n", "\n").split("\n", -1)));
        Map<String, String> header = readHeader(id, lines);
        return new ManualPage(
                id,
                required(header, "title", id),
                required(header, "chapter", id),
                Optional.ofNullable(header.get("shortcut")).filter(value -> !value.isBlank()),
                Optional.ofNullable(header.get("screenshot")).filter(value -> !value.isBlank()),
                splitList(header.get("sources")),
                readBlocks(lines));
    }

    private static Map<String, String> readHeader(String id, List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            throw new IllegalArgumentException("Manual page " + id + " does not start with a --- header");
        }
        lines.remove(0);
        Map<String, String> header = new LinkedHashMap<>();
        while (!lines.isEmpty()) {
            String line = lines.remove(0);
            if (line.trim().equals("---")) {
                return header;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Manual page " + id + " has a header line that is not key: value - " + line);
            }
            header.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        throw new IllegalArgumentException("Manual page " + id + " has a header that is never closed with ---");
    }

    private static List<ManualBlock> readBlocks(List<String> lines) {
        List<ManualBlock> blocks = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        List<String> bullets = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        List<String> quote = new ArrayList<>();
        List<ManualBlock.Facts.Fact> facts = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.strip();
            // A run of one kind of line ends at anything that is not that kind, blank included,
            // so the flushes come first and every branch below starts from a clean slate.
            boolean continuesBullets = line.startsWith("- ");
            boolean continuesSteps = STEP.matcher(line).matches();
            boolean continuesFacts = FACT.matcher(line).matches();
            boolean continuesQuote = line.startsWith(">");
            boolean continuesParagraph = !line.isEmpty() && !continuesBullets && !continuesSteps
                    && !continuesFacts && !line.startsWith("#") && !continuesQuote
                    && !FIGURE.matcher(line).matches();

            // A bullet or a step written over two source lines is still one item. Without this the
            // second line ended the list, and the steps after it were numbered from 1 again - a
            // four-step procedure printed as 1, 2, 1, 2.
            if (continuesParagraph && !bullets.isEmpty()) {
                bullets.set(bullets.size() - 1, bullets.getLast() + " " + line);
                continue;
            }
            if (continuesParagraph && !steps.isEmpty()) {
                steps.set(steps.size() - 1, steps.getLast() + " " + line);
                continue;
            }

            if (!continuesBullets) flushBullets(blocks, bullets);
            if (!continuesSteps) flushSteps(blocks, steps);
            if (!continuesFacts) flushFacts(blocks, facts);
            if (!continuesQuote) flushQuote(blocks, quote);
            if (!continuesParagraph) flushParagraph(blocks, paragraph);

            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("###")) {
                blocks.add(new ManualBlock.Heading(3, line.replaceFirst("^#+\\s*", "")));
            } else if (line.startsWith("##")) {
                blocks.add(new ManualBlock.Heading(2, line.replaceFirst("^#+\\s*", "")));
            } else if (continuesQuote) {
                // A callout wraps over several source lines. Each was its own box once, so a
                // three-line warning printed as three boxes, two of them labelled "ملاحظة"
                // because only the first line carried the word "تحذير".
                quote.add(line.substring(1).strip());
            } else if (continuesBullets) {
                bullets.add(line.substring(2).strip());
            } else if (continuesSteps) {
                steps.add(STEP.matcher(line).replaceFirst("$1").strip());
            } else if (continuesFacts) {
                Matcher fact = FACT.matcher(line);
                if (fact.matches()) {
                    facts.add(new ManualBlock.Facts.Fact(fact.group(1).strip(), fact.group(2).strip()));
                }
            } else {
                Matcher figure = FIGURE.matcher(line);
                if (figure.matches()) {
                    blocks.add(new ManualBlock.Figure(figure.group(2), figure.group(1).strip()));
                } else {
                    paragraph.add(line);
                }
            }
        }
        flushBullets(blocks, bullets);
        flushSteps(blocks, steps);
        flushFacts(blocks, facts);
        flushQuote(blocks, quote);
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    private static ManualBlock.Callout callout(String body) {
        int colon = body.indexOf(':');
        if (colon > 0) {
            ManualBlock.Callout.Kind kind = CALLOUTS.get(body.substring(0, colon).strip());
            if (kind != null) {
                return new ManualBlock.Callout(kind, body.substring(colon + 1).strip());
            }
        }
        return new ManualBlock.Callout(ManualBlock.Callout.Kind.NOTE, body);
    }

    /** The whole quoted run is one callout, and its kind is what its <em>first</em> line says. */
    private static void flushQuote(List<ManualBlock> blocks, List<String> quote) {
        if (!quote.isEmpty()) {
            blocks.add(callout(String.join(" ", quote)));
            quote.clear();
        }
    }

    private static void flushParagraph(List<ManualBlock> blocks, List<String> paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new ManualBlock.Para(String.join(" ", paragraph)));
            paragraph.clear();
        }
    }

    private static void flushBullets(List<ManualBlock> blocks, List<String> bullets) {
        if (!bullets.isEmpty()) {
            blocks.add(new ManualBlock.Bullets(List.copyOf(bullets)));
            bullets.clear();
        }
    }

    private static void flushSteps(List<ManualBlock> blocks, List<String> steps) {
        if (!steps.isEmpty()) {
            blocks.add(new ManualBlock.Steps(List.copyOf(steps)));
            steps.clear();
        }
    }

    private static void flushFacts(List<ManualBlock> blocks, List<ManualBlock.Facts.Fact> facts) {
        if (!facts.isEmpty()) {
            blocks.add(new ManualBlock.Facts(List.copyOf(facts)));
            facts.clear();
        }
    }

    private static String required(Map<String, String> header, String key, String id) {
        String value = header.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Manual page " + id + " is missing the header key " + key);
        }
        return value;
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }
}
