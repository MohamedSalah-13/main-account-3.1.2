package com.hamza.controlsfx.language;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LanguageManagerTest {

    private final LanguageManager manager = LanguageManager.getInstance();
    private final Locale originalLocale = manager.getCurrentLocale();

    @AfterEach
    void restoreLocale() {
        manager.setLocale(originalLocale);
    }

    @Test
    void discoversBothShippedBundlesFromTheClasspath() {
        assertTrue(manager.supportedLocales().contains(LanguageManager.ARABIC));
        assertTrue(manager.supportedLocales().contains(LanguageManager.ENGLISH));
    }

    @Test
    void listsTheCurrentLocaleFirst() {
        manager.setLocale(LanguageManager.ENGLISH);
        assertEquals(LanguageManager.ENGLISH, manager.supportedLocales().get(0));

        manager.setLocale(LanguageManager.ARABIC);
        assertEquals(LanguageManager.ARABIC, manager.supportedLocales().get(0));
    }

    @Test
    void displayNameComesFromEachBundleItself() {
        assertEquals("English", manager.displayNameOf(LanguageManager.ENGLISH));
        assertEquals("العربية", manager.displayNameOf(LanguageManager.ARABIC));
    }

    @Test
    void arabicIsRtlAndEnglishIsLtr() {
        manager.setLocale(LanguageManager.ARABIC);
        assertTrue(manager.isRtl());

        manager.setLocale(LanguageManager.ENGLISH);
        assertFalse(manager.isRtl());
    }

    @Test
    void currentLocalePropertyTracksSetLocale() {
        manager.setLocale(LanguageManager.ENGLISH);
        assertEquals(LanguageManager.ENGLISH, manager.currentLocaleProperty().get());

        manager.setLocale(LanguageManager.ARABIC);
        assertEquals(LanguageManager.ARABIC, manager.currentLocaleProperty().get());
    }
}
