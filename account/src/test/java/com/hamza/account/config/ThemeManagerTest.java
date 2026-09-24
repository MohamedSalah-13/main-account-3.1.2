package com.hamza.account.config;

import com.hamza.account.config.ThemeManager.Theme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a computer's saved theme is read as. The glass theme is gone, and a computer that still has
 * {@code GLASS} saved - it could only be put there by hand, since no screen ever offered it - has to
 * open in a theme that exists rather than fail to find a stylesheet.
 */
class ThemeManagerTest {

    @Test
    @DisplayName("a theme this build has is read back as itself")
    void aThemeThisBuildHasIsReadBack() {
        assertEquals(Theme.LIGHT, Theme.fromStored("LIGHT"));
        assertEquals(Theme.DARK, Theme.fromStored("DARK"));
    }

    @Test
    @DisplayName("the removed glass theme, or anything else this build does not have, opens light")
    void aThemeThisBuildDoesNotHaveOpensLight() {
        assertEquals(Theme.LIGHT, Theme.fromStored("GLASS"));
        assertEquals(Theme.LIGHT, Theme.fromStored("dark"), "the name is stored as the constant's, as before");
        assertEquals(Theme.LIGHT, Theme.fromStored(""));
        assertEquals(Theme.LIGHT, Theme.fromStored(null));
    }
}
