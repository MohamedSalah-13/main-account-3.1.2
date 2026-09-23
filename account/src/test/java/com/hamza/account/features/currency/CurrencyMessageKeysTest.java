package com.hamza.account.features.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code currency.*} key the currency code names is in all three bundles.
 * <p>
 * {@code MessageKeyArchitectureTest} reads the arguments of {@code getString}, {@code text} and the
 * {@code Columns} builders; a key handed to an exception's constructor is none of those, and every refusal
 * in {@code features/currency} is one. {@code ErrorReporter} turns such a key into its sentence at the
 * screen - and when the key is missing, the person reads {@code currency.error.base.locked}. So the keys
 * are read out of the source here, whole literals only, the way that test reads its own.
 */
class CurrencyMessageKeysTest {

    private static final Path BUNDLES = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
    private static final Path SOURCES = Path.of("src", "main", "java", "com", "hamza", "account");
    private static final Pattern KEY = Pattern.compile("\"(currency\\.[a-z.]+[a-z])\"");

    private static Set<String> keysNamedInTheSource() throws IOException {
        Set<String> keys = new TreeSet<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES.resolve(Path.of("features", "currency")))) {
            files = new java.util.ArrayList<>(walk.filter(path -> path.toString().endsWith(".java")).toList());
        }
        files.add(SOURCES.resolve(Path.of("controller", "convert_treasury", "CurrenciesController.java")));
        for (Path file : files) {
            Matcher matcher = KEY.matcher(Files.readString(file));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private static Properties bundle(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(BUNDLES.resolve(name))) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    @DisplayName("every currency key in the service, the rules and the screen is in all three bundles")
    void everyKeyIsTranslated() throws IOException {
        Set<String> keys = keysNamedInTheSource();
        assertTrue(keys.size() > 40, "the keys were read - the rest of this test would pass on nothing: " + keys.size());
        for (String name : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties bundle = bundle(name);
            Set<String> missing = new TreeSet<>(keys);
            missing.removeIf(bundle::containsKey);
            assertEquals(Set.of(), missing, name + " lacks these keys");
        }
    }

    @Test
    @DisplayName("a refusal's key is a sentence, never the key itself, in the language a user reads")
    void refusalsAreSentences() throws IOException {
        Properties arabic = bundle("messages.properties");
        for (String key : keysNamedInTheSource()) {
            if (key.contains(".error.")) {
                String sentence = arabic.getProperty(key, "");
                assertTrue(sentence.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF),
                        key + " reads as no Arabic sentence: " + sentence);
            }
        }
    }
}
