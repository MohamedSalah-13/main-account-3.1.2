package com.hamza.account.features.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.account.features.productprofile.ProductFeatures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCatalogTest {

    private static final Predicate<PermissionKey> EVERY_KEY = key -> true;
    private static final Predicate<FeatureKey> EVERY_FEATURE = feature -> true;
    private static final Function<String, String> KEY_AS_TEXT = key -> key;

    @Test
    void aReaderHoldingEverythingSeesEveryReportUnderItsSectionInOrder() {
        Map<ReportSection, List<ReportEntry>> visible = ReportCatalog.visible(EVERY_KEY, EVERY_FEATURE, "", KEY_AS_TEXT);

        assertEquals(List.of(ReportSection.values()), List.copyOf(visible.keySet()),
                "every section has a report, in the order the enum declares");
        assertEquals(ReportEntry.values().length, visible.values().stream().mapToInt(List::size).sum());
        visible.forEach((section, entries) -> entries.forEach(entry -> assertEquals(section, entry.section())));
    }

    @Test
    void aReportIsListedOnlyWhenEveryKeyItsRoadAsksIsGranted() {
        // The expense reports sit behind the list's key and their own: holding only the second is
        // not a road to them today, so the hub must not become one.
        Predicate<PermissionKey> reportsOnly = key -> key.equals(AppPermissions.EXPENSES_REPORTS);
        assertFalse(listed(reportsOnly, EVERY_FEATURE).contains(ReportEntry.EXPENSE_REPORTS));

        Predicate<PermissionKey> both = key -> key.equals(AppPermissions.EXPENSES_REPORTS)
                || key.equals(AppPermissions.EXPENSES_SHOW);
        assertTrue(listed(both, EVERY_FEATURE).contains(ReportEntry.EXPENSE_REPORTS));
    }

    @Test
    void aReportTheEditionLeavesOutIsNotListedWhateverThePermissions() {
        Predicate<FeatureKey> noCapital = feature -> !feature.equals(ProductFeatures.TREASURY_CAPITAL);

        Set<ReportEntry> listed = listed(EVERY_KEY, noCapital);

        assertFalse(listed.contains(ReportEntry.CAPITAL));
        assertTrue(listed.contains(ReportEntry.WALLET_FEES), "only the feature left out goes");
    }

    @Test
    void aSectionWithNothingVisibleIsLeftOutRatherThanShownEmpty() {
        Predicate<PermissionKey> sharesOnly = key -> key.equals(AppPermissions.REPORTS_SHOW_SALES);

        Map<ReportSection, List<ReportEntry>> visible = ReportCatalog.visible(sharesOnly, EVERY_FEATURE, "", KEY_AS_TEXT);

        assertEquals(Set.of(ReportSection.SALES, ReportSection.PARTIES, ReportSection.SHIFTS), visible.keySet(),
                "the sales-by-year and payments reports, and the shift screen the sidebar shows everybody");
    }

    @Test
    void theShiftScreenIsListedForEverybodyBecauseTheSidebarShowsItToEverybody() {
        assertTrue(listed(key -> false, EVERY_FEATURE).contains(ReportEntry.SHIFT_REPORTS));
        assertEquals(1, listed(key -> false, EVERY_FEATURE).size());
    }

    @Test
    void theSearchMatchesTheTitleOrTheDescriptionIgnoringAlefFormsDiacriticsAndCase() {
        Function<String, String> arabic = key -> switch (key) {
            case "report.profit.loss.title" -> "الأرباح والخسائر";
            case "report.hub.describe.capital" -> "رأس المال والمسحوبات";
            case "report.hub.describe.wallet.fees" -> "Wallet FEES by treasury";
            default -> "شيء آخر";
        };

        assertEquals(Set.of(ReportEntry.PROFIT_LOSS), listed("الارباح", arabic), "typed without the hamza");
        assertEquals(Set.of(ReportEntry.CAPITAL), listed("راس المال", arabic), "a match in the description");
        assertEquals(Set.of(ReportEntry.CAPITAL), listed("  المسحوبات  ", arabic), "surrounding blanks do not count");
        assertEquals(Set.of(ReportEntry.WALLET_FEES), listed("wallet fees", arabic));
        assertTrue(listed("لا يوجد تقرير بهذا", arabic).isEmpty());
    }

    @Test
    void aBlankOrMissingSearchListsEverything() {
        assertEquals(ReportEntry.values().length, listed("   ", KEY_AS_TEXT).size());
        assertEquals(ReportEntry.values().length, listed(null, KEY_AS_TEXT).size());
    }

    @Test
    void normalisingFoldsWhatTwoSpellingsOfOneWordDoNotShare() {
        assertEquals("الارباح", ReportCatalog.normalise("الأرباح"));
        assertEquals("اسم", ReportCatalog.normalise("إسم"));
        assertEquals("مستوي", ReportCatalog.normalise("مستوى"));
        assertEquals("خزينه", ReportCatalog.normalise("خزينة"));
        assertEquals("محمد", ReportCatalog.normalise("مُحَمَّد"));
        assertEquals("profit", ReportCatalog.normalise(" PROFIT "));
    }

    @Test
    void everyEntryNamesAKeyAndATitleOfItsOwn() {
        Set<String> titles = new HashSet<>();
        Set<String> descriptions = new HashSet<>();
        for (ReportEntry entry : ReportEntry.values()) {
            assertFalse(entry.permissions().isEmpty(), entry + " names no permission");
            assertTrue(titles.add(entry.titleKey()), entry + " repeats a title - one report, one card");
            assertTrue(descriptions.add(entry.descriptionKey()), entry + " repeats a description");
        }
    }

    /** The screen resolves these keys through a variable, which the message-key scan cannot see. */
    @Test
    void everyTitleDescriptionAndSectionHasTextInAllThreeBundles() {
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = load(bundle);
            for (ReportEntry entry : ReportEntry.values()) {
                assertTrue(properties.containsKey(entry.titleKey()), bundle + " has no " + entry.titleKey());
                assertTrue(properties.containsKey(entry.descriptionKey()), bundle + " has no " + entry.descriptionKey());
            }
            for (ReportSection section : ReportSection.values()) {
                assertTrue(properties.containsKey(section.titleKey()), bundle + " has no " + section.titleKey());
            }
        }
    }

    private static Set<ReportEntry> listed(Predicate<PermissionKey> granted, Predicate<FeatureKey> enabled) {
        return flatten(ReportCatalog.visible(granted, enabled, "", KEY_AS_TEXT));
    }

    private static Set<ReportEntry> listed(String search, Function<String, String> translate) {
        return flatten(ReportCatalog.visible(EVERY_KEY, EVERY_FEATURE, search, translate));
    }

    private static Set<ReportEntry> flatten(Map<ReportSection, List<ReportEntry>> visible) {
        Set<ReportEntry> entries = new HashSet<>();
        visible.values().forEach(entries::addAll);
        return entries;
    }

    private static Properties load(String name) {
        Path path = Path.of("..", "controlsfx", "src", "main", "resources", "i18n", name);
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
