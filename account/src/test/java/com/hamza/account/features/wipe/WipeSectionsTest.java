package com.hamza.account.features.wipe;

import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipeTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WipeSectionsTest {

    @Test
    @DisplayName("every target the catalog offers is on exactly one card")
    void everyTargetHasACard() {
        assertEquals(List.of(), WipeSections.unplaced(WipeCatalog.TARGETS),
                "a target with no card lands on the last one and logs nothing any more - give it a card");

        List<WipeTarget> listed = new ArrayList<>();
        WipeSections.declared().forEach(section -> listed.addAll(section.targets()));
        assertEquals(listed.size(), new HashSet<>(listed).size(), "a target on two cards: " + listed);
    }

    @Test
    @DisplayName("the stock counts are with the items, where the user looks for them")
    void theStockCountsAreWithTheItems() {
        WipeSection items = WipeSections.declared().stream()
                .filter(section -> section.id().equals(WipeSections.ITEMS)).findFirst().orElseThrow();
        assertTrue(items.targets().contains(WipeCatalog.STOCK_COUNTS));
    }

    @Test
    @DisplayName("a target the cards do not list is put on the last card rather than lost")
    void aStrayTargetGoesOnTheLastCard() {
        WipeTarget stray = WipeTarget.of("stray", "wipe.target.users", List.of());
        List<WipeTarget> catalog = new ArrayList<>(WipeCatalog.TARGETS);
        catalog.add(stray);

        List<WipeSection> sections = WipeSections.of(catalog);

        assertEquals(WipeSections.declared().size(), sections.size());
        assertEquals(stray, sections.getLast().targets().getLast());
        assertEquals(WipeSections.declared(), WipeSections.of(WipeCatalog.TARGETS), "nothing stray, nothing added");
    }

    /**
     * The screen resolves a card's title through a variable, which {@code MessageKeyArchitectureTest}
     * cannot see, so the keys are checked where they are declared - the way {@code PartyStatementTest}
     * checks its movement kinds.
     */
    @Test
    @DisplayName("every card's title is translated in every bundle")
    void everyTitleIsTranslated() {
        Path bundleDir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
        for (String bundleName : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties bundle = read(bundleDir.resolve(bundleName));
            for (WipeSection section : WipeSections.declared()) {
                String value = bundle.getProperty(section.titleKey());
                assertNotNull(value, section.titleKey() + " is missing from " + bundleName);
                assertFalse(value.isBlank(), section.titleKey() + " is blank in " + bundleName);
            }
        }
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return properties;
    }
}
