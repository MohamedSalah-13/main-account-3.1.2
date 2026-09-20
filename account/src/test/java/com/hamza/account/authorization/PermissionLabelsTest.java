package com.hamza.account.authorization;

import com.hamza.controlsfx.language.LanguageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fallback order is the whole of this class, and each step exists because the step after it had
 * been reached in production: the bundle was not asked at all, so the stored description answered, and
 * the stored description is the key itself for 108 of the 162 rows.
 * <p>
 * <b>The locale is put back.</b> {@code LanguageManager.setLocale} writes the choice into Java
 * {@code Preferences}, so a test that switched language and walked away would leave the developer's
 * program in whichever language it happened to finish in.
 */
class PermissionLabelsTest {

    private static final Locale ORIGINAL = LanguageManager.getInstance().getCurrentLocale();

    @AfterAll
    static void restoreTheLanguageTheDeveloperChose() {
        LanguageManager.getInstance().setLocale(ORIGINAL);
    }

    @Nested
    @DisplayName("the bundle answers first")
    class TheBundle {

        @Test
        void aDeclaredKeyIsNamedFromTheBundleAndNotFromTheDatabase() {
            String label = PermissionLabels.describe("treasury.capital", "treasury.capital");
            assertEquals(LanguageManager.getInstance().getString("permission.treasury.capital"), label);
            assertFalse(label.contains("."), "the name still reads as a key: " + label);
        }

        /**
         * The stored description loses even when it is a real sentence. A database that has one keeps
         * showing it only where the bundle is silent - otherwise two installs of the same build would
         * name the same permission differently.
         */
        @Test
        void theBundleWinsOverAStoredSentence() {
            assertEquals(LanguageManager.getInstance().getString("permission.sales.create"),
                    PermissionLabels.describe("sales.create", "شيء آخر تمامًا"));
        }

        /**
         * Both languages, because the screen this feeds is opened in both and a bundle can be edited
         * one file at a time. The stored description passed in is the key itself, which is what
         * {@code COALESCE(description, permission_key)} actually hands the screen for most rows.
         */
        @Test
        void everyDeclaredKeyResolvesToSomethingThatIsNotItsKeyInEitherLanguage() {
            for (Locale locale : List.of(LanguageManager.ARABIC, LanguageManager.ENGLISH)) {
                LanguageManager.getInstance().setLocale(locale);
                for (PermissionDefinition definition : AppPermissions.definitions()) {
                    String key = definition.key().value();
                    String label = PermissionLabels.describe(key, key);
                    assertFalse(label.equals(key), "no name for " + key + " in " + locale);
                    assertFalse(label.startsWith(PermissionLabels.BUNDLE_PREFIX),
                            "the bundle prefix reached the screen for " + key + " in " + locale);
                    assertTrue(label.length() > 3,
                            "name too short for " + key + " in " + locale + ": " + label);
                }
            }
        }
    }

    @Nested
    @DisplayName("and where it is silent")
    class TheFallbacks {

        /** A key the bundle does not carry, with a description worth showing. */
        @Test
        void aStoredDescriptionIsUsedWhenItSaysMoreThanTheKey() {
            assertEquals("وصف مكتوب بيد",
                    PermissionLabels.describe("not.a.declared.key", "وصف مكتوب بيد"));
        }

        /**
         * The last resort, and the reason the bundle is pinned by an architecture test: it renders
         * {@code Total · Sales · Re · Show}, which is what English showed for every key.
         */
        @Test
        void theKeysOwnWordsAreTheLastResort() {
            assertEquals("Not · A · Declared · Key",
                    PermissionLabels.describe("not.a.declared.key", null));
            // A description that only repeats the key is no description, which is the case
            // COALESCE(description, permission_key) produces for most rows.
            assertEquals("Not · A · Declared · Key",
                    PermissionLabels.describe("not.a.declared.key", "not.a.declared.key"));
            assertEquals("Total · Sales · Re · Show · Nowhere",
                    PermissionLabels.describe("total.sales.re.show.nowhere", null));
        }

        @Test
        void nothingIsAnEmptyStringRatherThanAThrow() {
            assertEquals("", PermissionLabels.describe((PermissionKey) null));
            assertEquals("", PermissionLabels.describe(null, null));
            assertEquals("kept", PermissionLabels.describe("  ", "kept"));
        }
    }

    @Test
    void theBundleKeyIsBuiltInOnePlace() {
        assertEquals("permission.sales.create", PermissionLabels.bundleKey("sales.create"));
    }
}
