package com.hamza.account.features.totals;

import com.hamza.account.document.DocumentTableSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every translation key a report page is built from, checked against all three bundles.
 *
 * <p>{@code MessageKeyArchitectureTest} cannot see these. It scans the argument list of
 * each {@code getString} call, and the report's keys are returned by a switch in
 * {@link TotalsReportLayout} and {@link TotalsFilterDescription} and handed to a
 * translating function - one indirection is enough to be invisible to it. That is not a
 * hypothetical: the item report shipped with a column headed {@code item}, a key no bundle
 * has ever held, and it printed as a row of empty boxes on the page while the build stayed
 * green.</p>
 *
 * <p>So the keys are collected here the only way that cannot drift - by asking the code
 * itself for them, over every report and every document family.</p>
 */
class TotalsReportKeysTest {

    /** Read from the source tree, as MessageKeyArchitectureTest does: the bundles live in
     *  the other module and are not resources of this one. */
    private static final Path BUNDLE_DIR =
            Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
    private static final List<String> BUNDLES = List.of(
            "messages.properties", "messages_ar.properties", "messages_en.properties");

    @Test
    void everyKeyAReportPrintsExistsInAllThreeBundles() throws IOException {
        Set<String> keys = keysUsedByEveryReport();
        assertTrue(keys.size() > 10, "the collector stopped seeing the layout's keys");

        List<String> missing = new ArrayList<>();
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (String key : keys) {
                if (!properties.containsKey(key)) missing.add(bundle + " -> " + key);
            }
        }
        if (!missing.isEmpty()) fail("Untranslated report keys:\n  " + String.join("\n  ", missing));
    }

    /**
     * Asks the layout for its headers with a function that records what it was asked for,
     * rather than listing the keys again here - a second list would be the thing that
     * drifts.
     */
    private static Set<String> keysUsedByEveryReport() {
        Set<String> keys = new LinkedHashSet<>();
        for (DocumentTableSpec.Report report : DocumentTableSpec.Report.values()) {
            for (boolean hasProfit : new boolean[]{true, false}) {
                boolean perItem = report == DocumentTableSpec.Report.BY_ITEM;
                var row = new TotalsReportRow("x", 1, java.math.BigDecimal.ONE,
                        java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
                var built = new TotalsReportService.TotalsReport(
                        report, List.of(row), row, hasProfit, perItem, false);
                TotalsReportLayout.of(built, report, key -> {
                    keys.add(key);
                    return key;
                });
            }
        }
        // The filter line is printed on every one of those pages.
        var criteria = new com.hamza.account.document.TotalsSearchCriteria(
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 2, 1), 1,
                "a", "b", com.hamza.account.type.InvoiceType.CASH, "c",
                java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "d");
        TotalsFilterDescription.describe(criteria, (key, args) -> {
            keys.add(key);
            return key;
        });
        // and the three shapes only an unbounded or half-bounded search reaches
        for (var open : List.of(
                withDates(null, null), withDates(java.time.LocalDate.now(), null),
                withDates(null, java.time.LocalDate.now()))) {
            TotalsFilterDescription.describe(open, (key, args) -> {
                keys.add(key);
                return key;
            });
        }
        return keys;
    }

    private static com.hamza.account.document.TotalsSearchCriteria withDates(
            java.time.LocalDate from, java.time.LocalDate to) {
        return new com.hamza.account.document.TotalsSearchCriteria(
                from, to, null, null, null, null, null,
                java.math.BigDecimal.ONE, null, null);
    }

    private static Properties load(String bundle) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(BUNDLE_DIR.resolve(bundle))) {
            properties.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
