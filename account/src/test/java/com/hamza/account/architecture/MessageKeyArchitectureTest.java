package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java-side half of the guard {@link FxmlArchitectureTest} already applies to FXML:
 * a translation key named in code must exist in all three bundles.
 * <p>
 * Nothing checked this before, and ten keys shipped in the master-data work without a
 * translation anywhere - two of them <b>table column headings</b>, and the whole of
 * {@code EmptyGroupsSource}'s notification. {@code LanguageManager.getString} answers a
 * missing key with the key itself and a {@code warn} in the log, so there is no exception
 * and no failing test: the user simply reads {@code masterdata.count.items} in a heading.
 * That is a silent defect the build can catch, which makes it one the build should catch.
 * <p>
 * <b>What is checked.</b> Every string literal that appears inside the argument list of one
 * of the {@link #ENTRY_POINTS} calls, and that has the shape of a key (dotted, starting
 * lowercase). Literals are collected from the whole call region rather than the first
 * argument alone, because a screen picks its heading with a ternary as often as not
 * ({@code text(main ? "a.b" : "c.d")}).
 * <p>
 * <b>What is not, and the one rule that follows.</b> A key assembled at run time
 * ({@code "masterdata.notify." + suffix + ".title"}) is invisible here - the halves do not
 * have the shape of a key, and nothing static can know what they add up to. So
 * <b>write a key as one whole literal</b>; a ternary between two whole keys is fine, string
 * concatenation is not. Keys without a dot ({@code "name"}, {@code "code"}) are also out of
 * scope: they are indistinguishable from the column names passed to
 * {@code ResultSet.getString}, which shares the method name.
 */
class MessageKeyArchitectureTest {

    /**
     * Read from the sibling module rather than the classpath, for the reason
     * {@link FxmlArchitectureTest} records: the tests run on the module path, where
     * {@code controlsfx} does not open its resources to {@code com.hamza.account}.
     */
    private static final Path CONTROLSFX = Path.of("..", "controlsfx");
    private static final Path BUNDLE_DIR = CONTROLSFX.resolve(Path.of("src", "main", "resources", "i18n"));

    private static final String[] BUNDLES = {
            "messages.properties",
            "messages_ar.properties",
            "messages_en.properties"};

    /**
     * Method names whose arguments are message keys. {@code getString} is
     * {@code LanguageManager}'s; {@code text} and {@code tip} are the per-screen helpers
     * that delegate to it; {@code number}/{@code date}/{@code column} are
     * {@code Columns}', whose first argument is a heading key.
     */
    private static final Pattern ENTRY_POINTS =
            Pattern.compile("(?<![A-Za-z0-9_$])(getString|text|tip|number|date|column)\\s*\\(");

    private static final Pattern KEY_SHAPE =
            Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9_]+)+");

    /**
     * {@code PropertiesName} is the {@code java.util.prefs} façade, and its own
     * {@code getString(key, fallback)} takes preference keys - {@code backup.database.save.folder}
     * and the like - which have exactly the shape of a message key and belong in no bundle.
     */
    private static final Set<String> NOT_LOCALIZATION = Set.of(
            "com/hamza/account/config/PropertiesName.java");

    @Test
    void everyKeyNamedInJavaExistsInEveryBundle() {
        Map<String, Properties> bundles = loadBundles();
        var missing = new TreeMap<String, Set<String>>();
        for (Map.Entry<String, List<String>> used : keysByFile().entrySet()) {
            for (String key : used.getValue()) {
                for (var bundle : bundles.entrySet()) {
                    if (bundle.getValue().getProperty(key) == null) {
                        missing.computeIfAbsent(key, k -> new TreeSet<>())
                                .add(used.getKey() + " -> " + bundle.getKey());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "LanguageManager answers a missing key with the key itself, so these reach the screen "
                        + "as raw text. Add each to all three bundles under controlsfx/src/main/resources/i18n: "
                        + missing);
    }

    /**
     * A scanner that stops matching is a guard that passes for the wrong reason: rename an
     * entry point, or break the paren walk, and the check above would go green over nothing.
     */
    @Test
    void theScanStillReachesTheScreens() {
        var keys = new TreeSet<String>();
        keysByFile().values().forEach(keys::addAll);
        assertTrue(keys.size() >= KEYS_SEEN_BASELINE,
                "The scan found only " + keys.size() + " distinct keys, below the " + KEYS_SEEN_BASELINE
                        + " it saw when written - ENTRY_POINTS or the argument walk has stopped matching, "
                        + "and everyKeyNamedInJavaExistsInEveryBundle is now checking almost nothing.");
    }

    /**
     * Distinct keys the scan reached when the rule was written. Lower it only when a screen
     * was deleted and its keys went with it - never to make a broken scanner go quiet.
     */
    private static final int KEYS_SEEN_BASELINE = 966;

    /** Keys named in each source file, so a failure says which screen to open. */
    private static Map<String, List<String>> keysByFile() {
        var result = new LinkedHashMap<String, List<String>>();
        for (Path root : List.of(SourceTree.MAIN_JAVA, CONTROLSFX.resolve(SourceTree.MAIN_JAVA))) {
            for (Path file : javaFiles(root)) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (NOT_LOCALIZATION.contains(relative)) {
                    continue;
                }
                List<String> keys = keysIn(SourceTree.withoutComments(SourceTree.read(file)));
                if (!keys.isEmpty()) {
                    result.put(relative, keys);
                }
            }
        }
        return result;
    }

    /** Key-shaped literals inside the argument list of every entry-point call. */
    private static List<String> keysIn(String source) {
        var keys = new ArrayList<String>();
        Matcher call = ENTRY_POINTS.matcher(source);
        while (call.find()) {
            for (String literal : literalsInArguments(source, call.end() - 1)) {
                if (KEY_SHAPE.matcher(literal).matches()) {
                    keys.add(literal);
                }
            }
        }
        return keys;
    }

    /**
     * Every string literal between {@code openParen} and the parenthesis closing it.
     * Depth is tracked so a nested call's literals are read too, and quotes are honoured
     * so a bracket inside a literal does not end the scan early.
     */
    private static List<String> literalsInArguments(String source, int openParen) {
        var literals = new ArrayList<String>();
        int depth = 0;
        for (int index = openParen; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                if (--depth == 0) {
                    return literals;
                }
            } else if (current == '\'') {
                index = endOfQuoted(source, index, '\'');
            } else if (current == '"') {
                int end = endOfQuoted(source, index, '"');
                literals.add(source.substring(index + 1, Math.min(end, source.length())));
                index = end;
            }
        }
        return literals;
    }

    /** Index of the closing quote, skipping escaped ones. */
    private static int endOfQuoted(String source, int openQuote, char quote) {
        for (int index = openQuote + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '\\') {
                index++;
            } else if (current == quote || current == '\n') {
                return index;
            }
        }
        return source.length();
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
    }

    private static Map<String, Properties> loadBundles() {
        var loaded = new LinkedHashMap<String, Properties>();
        for (String bundle : BUNDLES) {
            Properties properties = new Properties();
            try (InputStream stream = Files.newInputStream(BUNDLE_DIR.resolve(bundle))) {
                properties.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + bundle, e);
            }
            loaded.put(bundle, properties);
        }
        return loaded;
    }
}
