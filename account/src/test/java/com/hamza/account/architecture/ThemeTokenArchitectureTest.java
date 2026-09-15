package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code -app-*} colour a stylesheet names is defined by both themes.
 * <p>
 * JavaFX does not fail on an unknown looked-up colour. It logs a
 * {@code ClassCastException ... String cannot be cast to Paint} to stderr - which
 * a packaged launcher has nowhere to show - and paints nothing: the database setup
 * tool's provisioning card lost its border that way, and the product setup tool its
 * highlighted background, both through {@code -app-primary-soft}, which neither theme
 * had ever declared. Nothing on the screen looks broken; a border is simply absent.
 */
class ThemeTokenArchitectureTest {

    private static final Path CSS = Path.of("src/main/resources/com/hamza/account/css");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile("-app-[a-z0-9-]*[a-z0-9]");
    private static final Pattern DEFINITION = Pattern.compile("(-app-[a-z0-9-]*[a-z0-9])\\s*:");

    @Test
    void everyThemeTokenAStylesheetUsesIsDefinedByBothThemes() throws IOException {
        Set<String> light = definedIn(CSS.resolve("theme-light.css"));
        Set<String> dark = definedIn(CSS.resolve("theme-dark.css"));

        Set<String> undefined = new TreeSet<>();
        try (Stream<Path> files = Files.list(CSS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".css")).toList()) {
                Matcher token = TOKEN.matcher(withoutComments(file));
                while (token.find()) {
                    String name = token.group();
                    if (!light.contains(name)) {
                        undefined.add(file.getFileName() + ": " + name + " (theme-light.css)");
                    }
                    if (!dark.contains(name)) {
                        undefined.add(file.getFileName() + ": " + name + " (theme-dark.css)");
                    }
                }
            }
        }

        assertTrue(undefined.isEmpty(), "Theme tokens used but not defined: " + undefined);
    }

    private static Set<String> definedIn(Path theme) throws IOException {
        Set<String> names = new TreeSet<>();
        Matcher definition = DEFINITION.matcher(withoutComments(theme));
        while (definition.find()) {
            names.add(definition.group(1));
        }
        return names;
    }

    private static String withoutComments(Path file) throws IOException {
        return COMMENT.matcher(Files.readString(file)).replaceAll("");
    }
}
