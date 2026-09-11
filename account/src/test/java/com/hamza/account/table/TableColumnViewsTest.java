package com.hamza.account.table;

import com.hamza.account.table.TableColumnViews.Preset;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableColumnViewsTest {

    private static final Set<String> COMPACT = Set.of("code", "name");

    @Test
    void theFullViewShowsEveryColumn() {
        assertTrue(TableColumnViews.visibleUnder(Preset.FULL, "code", COMPACT));
        assertTrue(TableColumnViews.visibleUnder(Preset.FULL, "notes", COMPACT));
        assertTrue(TableColumnViews.visibleUnder(Preset.FULL, null, COMPACT));
    }

    @Test
    void theCompactViewShowsOnlyTheColumnsItNames() {
        assertTrue(TableColumnViews.visibleUnder(Preset.COMPACT, "name", COMPACT));
        assertFalse(TableColumnViews.visibleUnder(Preset.COMPACT, "notes", COMPACT));
        // A column nobody gave an id cannot be named by the compact set, and Set.of refuses
        // contains(null) with an exception rather than an answer.
        assertFalse(TableColumnViews.visibleUnder(Preset.COMPACT, null, COMPACT));
    }

    @Test
    void aStoredViewThatIsMissingOrUnknownFallsBackToTheDefault() {
        assertEquals(Preset.FULL, Preset.fromStored("FULL", Preset.COMPACT));
        assertEquals(Preset.CUSTOM, Preset.fromStored("CUSTOM", Preset.COMPACT));
        assertEquals(Preset.COMPACT, Preset.fromStored(null, Preset.COMPACT));
        assertEquals(Preset.FULL, Preset.fromStored("wide", Preset.FULL));
    }

    @Test
    void aTableCannotOpenOnACustomView() {
        Preferences unused = Preferences.userRoot().node("com.hamza.account.test.columnViews");
        assertThrows(IllegalArgumentException.class, () -> new TableColumnViews<>(
                unused, "view.mode", Preset.CUSTOM, COMPACT, Set.of()));
    }
}
