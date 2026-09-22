package com.hamza.account.features.report;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hub's openers live in {@code ReportsButtons}, which needs a database and a toolkit to build - so
 * this reads the source, the way {@code InvoiceEditRestoresTreasuryTest} does. Crude, and the only
 * check possible without either: an entry with no opener would be a card that answers "no opener" to
 * whoever presses it.
 */
class ReportsHubWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "hamza", "account");

    @Test
    void everyEntryHasExactlyOneOpener() throws IOException {
        String buttons = Files.readString(MAIN.resolve(Path.of("dash", "ReportsButtons.java")));
        for (ReportEntry entry : ReportEntry.values()) {
            String opener = "openers.put(ReportEntry." + entry.name() + ",";
            assertEquals(1, count(buttons, opener), entry + " needs exactly one opener in ReportsButtons.openers");
        }
    }

    @Test
    void theSidebarOpensTheHubAndOffersItAShortcut() throws IOException {
        String screen = Files.readString(MAIN.resolve(Path.of("controller", "main", "MainScreenController.java")));
        assertTrue(screen.contains("menuButtonSetting.configureButton(btnReportHub, getReportsButtons().reportsHub());"));
        assertTrue(screen.contains("Map.entry(SidebarShortcut.REPORT_HUB, btnReportHub)"));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }
}
