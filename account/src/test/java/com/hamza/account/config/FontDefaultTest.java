package com.hamza.account.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The font an install starts with is said in two places: {@link FontManager#DEFAULT_FAMILY}, which
 * the screens are stamped with, and the first family app-theme.css names, which a dialog reads -
 * a dialog is not stamped. With the two apart, the screens and their dialogs draw the same sentence
 * in two fonts, and a fix made in one place reaches half of them.
 */
class FontDefaultTest {

    private static final Pattern FIRST_FAMILY = Pattern.compile("-fx-font-family:\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("the stylesheet's first family is the default the screens are stamped with")
    void theStylesheetAndTheDefaultAgree() throws IOException {
        String css = read("com/hamza/account/css/app-theme.css");
        Matcher first = FIRST_FAMILY.matcher(css);

        assertTrue(first.find(), "app-theme.css names no font family");
        assertEquals(FontManager.DEFAULT_FAMILY, first.group(1));
    }

    /**
     * JavaFX draws Cairo without the space before «في», on every screen and in every dialog - the
     * delete dialog read "مستخدمفي". Seen on screen and in a render of the font alone; the same text
     * in El Messiri, Tahoma and Segoe keeps its space.
     */
    @Test
    @DisplayName("the default is not Cairo, which swallows the space before «في»")
    void notCairo() {
        assertNotEquals("Cairo", FontManager.DEFAULT_FAMILY);
    }

    @Test
    @DisplayName("the default family's file is bundled")
    void theDefaultIsBundled() throws IOException {
        try (InputStream font = com.hamza.controlsfx.FontResources.open("El_Messiri/static/ElMessiri-Regular.ttf")) {
            assertNotNull(font, "El Messiri is the default and its file is not in the jar");
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = FontDefaultTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " was not found");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
