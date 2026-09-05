package com.hamza.account.features.shortcuts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static com.hamza.account.features.shortcuts.SidebarShortcutManager.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The migration that runs when a sidebar command is retired.
 * <p>
 * It is worth a test because its failure is silent in both directions: a preference stored
 * under a name the enum no longer has is never read, so a key the user set simply stops
 * working, and an over-eager version would overwrite a key they had already chosen for the
 * command that replaced it. Neither shows up as an error anywhere.
 * <p>
 * A scratch {@link Preferences} node is used rather than the manager's own, so running the
 * suite never touches the developer's real shortcuts.
 */
class SidebarShortcutManagerTest {

    private static final String[] RETIRED = {"UNITS", "MAIN_GROUP", "SUB_GROUP", "AREA"};
    private static final SidebarShortcut SUCCESSOR = SidebarShortcut.MASTER_DATA;
    private static final String SUCCESSOR_KEY = PREFIX + SUCCESSOR.name();

    private final Preferences preferences =
            Preferences.userRoot().node("com/hamza/account/test/sidebar-shortcuts");

    @AfterEach
    void clear() throws BackingStoreException {
        preferences.removeNode();
        preferences.flush();
    }

    @Test
    void aKeySavedUnderARetiredNameMovesToItsSuccessor() {
        preferences.put(PREFIX + "UNITS", "Ctrl+U");

        SidebarShortcutManager.adoptRetiredShortcuts(preferences, RETIRED, SUCCESSOR);

        assertEquals("Ctrl+U", preferences.get(SUCCESSOR_KEY, null));
        assertNull(preferences.get(PREFIX + "UNITS", null), "the retired key was left behind");
    }

    /** Four commands became one, so at most one of their keys can survive - the first, in order. */
    @Test
    void theFirstRetiredNameHoldingAKeyWins() {
        preferences.put(PREFIX + "MAIN_GROUP", "Ctrl+G");
        preferences.put(PREFIX + "AREA", "Ctrl+R");

        SidebarShortcutManager.adoptRetiredShortcuts(preferences, RETIRED, SUCCESSOR);

        assertEquals("Ctrl+G", preferences.get(SUCCESSOR_KEY, null));
        for (String retired : RETIRED) {
            assertNull(preferences.get(PREFIX + retired, null), retired + " was left behind");
        }
    }

    @Test
    void aChoiceAlreadyMadeForTheSuccessorIsNotOverwritten() {
        preferences.put(SUCCESSOR_KEY, "Ctrl+M");
        preferences.put(PREFIX + "UNITS", "Ctrl+U");

        SidebarShortcutManager.adoptRetiredShortcuts(preferences, RETIRED, SUCCESSOR);

        assertEquals("Ctrl+M", preferences.get(SUCCESSOR_KEY, null));
    }

    /**
     * An empty string is a stored decision - the user cleared that shortcut on purpose - and
     * handing them back a key they had removed is the same defect as losing one they set.
     */
    @Test
    void aDeliberatelyClearedSuccessorStaysCleared() {
        preferences.put(SUCCESSOR_KEY, "");
        preferences.put(PREFIX + "UNITS", "Ctrl+U");

        SidebarShortcutManager.adoptRetiredShortcuts(preferences, RETIRED, SUCCESSOR);

        assertEquals("", preferences.get(SUCCESSOR_KEY, null));
    }

    @Test
    void nothingToAdoptLeavesTheSuccessorOnItsDefault() {
        SidebarShortcutManager.adoptRetiredShortcuts(preferences, RETIRED, SUCCESSOR);

        assertNull(preferences.get(SUCCESSOR_KEY, null));
        assertEquals("", SUCCESSOR.defaultCombination());
    }

    /**
     * A blank value under a retired name is not a key, and adopting it would consume the
     * chance for a later one that is.
     */
    @Test
    void aBlankRetiredValueDoesNotConsumeTheAdoption() {
        preferences.put(PREFIX + "UNITS", "");
        preferences.put(PREFIX + "SUB_GROUP", "Ctrl+B");

        SidebarShortcutManager.adoptRetiredShortcuts(preferences, RETIRED, SUCCESSOR);

        assertEquals("Ctrl+B", preferences.get(SUCCESSOR_KEY, null));
    }
}
