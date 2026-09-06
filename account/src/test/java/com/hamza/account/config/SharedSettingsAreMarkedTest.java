package com.hamza.account.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shared setting is marked as shared on the screen a user changes it from.
 *
 * <p>{@code SharedSettingKeysTest} says which keys are the shop's. This says the user can
 * tell - which is a different claim, and the one that was missing: a key can be added to
 * the list in one line, and nothing about the screen changes. The tick box goes on looking
 * exactly like the tick box beside it that sets a printer, so somebody turns off "sell
 * below zero" believing they are changing their own till and changes the shop's rule.
 *
 * <p>The check is textual - it reads the settings controllers looking for
 * {@code SettingScope.shared(control, SharedSettingKeys.X)} - so it proves the call is
 * written, not that the mark renders. Rendering needs a toolkit; whether the call exists
 * at all is what actually gets forgotten.
 */
class SharedSettingsAreMarkedTest {

    private static final Path SETTINGS =
            Path.of("src", "main", "java", "com", "hamza", "account", "controller", "setting");

    /**
     * Shared keys with no control of their own, and why. Each is a decision, not an
     * oversight, and the test fails if one of them gains a screen and stays on this list.
     */
    private static final Set<String> NOT_ON_ANY_SCREEN = Set.of(
            // Not a user setting at all: which machine owns the scheduled backup. It is
            // shop-wide state of the same shape, and the machines screen moves it with a
            // button rather than a tick box.
            "BACKUP_OWNER_MACHINE");

    @Test
    @DisplayName("every shared key is marked on the screen that changes it")
    void everySharedKeyIsMarked() {
        String sources = settingsSources();

        List<String> unmarked = new ArrayList<>();
        for (String constant : sharedConstantNames()) {
            if (NOT_ON_ANY_SCREEN.contains(constant)) {
                continue;
            }
            if (!sources.contains("SharedSettingKeys." + constant)) {
                unmarked.add(constant);
            }
        }

        assertTrue(unmarked.isEmpty(),
                "these settings are the shop's but the screen does not say so: " + unmarked
                        + ". Wrap the control in SettingScope.shared(control, SharedSettingKeys.KEY) "
                        + "where it is set up - or, if it genuinely has no control, add it to "
                        + "NOT_ON_ANY_SCREEN with the reason.");
    }

    @Test
    @DisplayName("the exception list does not outlive what it excuses")
    void theExceptionListStaysHonest() {
        String sources = settingsSources();
        List<String> stale = NOT_ON_ANY_SCREEN.stream()
                .filter(constant -> sources.contains("SharedSettingKeys." + constant))
                .toList();

        assertTrue(stale.isEmpty(),
                "these are excused as having no screen, but one marks them now: " + stale
                        + ". Remove them from NOT_ON_ANY_SCREEN.");
    }

    @Test
    @DisplayName("nothing is marked shared that is not")
    void nothingIsMarkedThatIsNotShared() {
        // SettingScope refuses a key that is not shared, so this checks the other half:
        // that every constant named at a call site is one SharedSettingKeys really holds.
        for (String constant : namesReferencedByScreens()) {
            assertTrue(sharedConstantNames().contains(constant),
                    "the settings screen marks SharedSettingKeys." + constant
                            + " as shared, but no such shared key exists");
        }
    }

    @Test
    @DisplayName("the sources really were read - the rest of this class would pass vacuously")
    void theSourcesWereRead() {
        assertFalse(sharedConstantNames().isEmpty(), "no shared key constants found");
        assertTrue(settingsSources().contains("SettingScope.shared("),
                "no SettingScope.shared call found in the settings controllers");
    }

    /** The public String constants of {@link SharedSettingKeys}, by name. */
    private static Set<String> sharedConstantNames() {
        return Arrays.stream(SharedSettingKeys.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())
                        && Modifier.isStatic(field.getModifiers())
                        && field.getType() == String.class)
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> namesReferencedByScreens() {
        var referenced = new TreeSet<String>();
        var matcher = java.util.regex.Pattern
                .compile("SettingScope\\.shared\\([^;]*?SharedSettingKeys\\.([A-Z_]+)")
                .matcher(settingsSources());
        while (matcher.find()) {
            referenced.add(matcher.group(1));
        }
        return referenced;
    }

    private static String settingsSources() {
        try (Stream<Path> files = Files.walk(SETTINGS)) {
            StringBuilder all = new StringBuilder();
            for (Path path : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                all.append(Files.readString(path)).append('\n');
            }
            return all.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
