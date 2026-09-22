package com.hamza.account.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opening a saved document puts it back on the treasury it was saved on.
 * <p>
 * It did not, and nothing could see it: {@code BuyController2.selectData} restored the date, the
 * party, the delegate and the warehouse and left the treasury on the screen's default. Opening an
 * invoice paid on a wallet and saving it again moved its cash to the main drawer without a word -
 * found on 2026-09-18 only by opening the screen on a copy of a real database, after a green build
 * of 2,615 tests. A controller cannot be unit tested without a toolkit, so this reads the source:
 * a crude check, and the only one that fails the build if the line is lost in a merge.
 */
class InvoiceEditRestoresTreasuryTest {

    private static final Path SCREEN = Path.of("src", "main", "java", "com", "hamza", "account",
            "controller", "invoice", "InvoiceScreenController.java");

    @Test
    @DisplayName("selectData restores the stored treasury, and adds one that has since been closed")
    void theStoredTreasuryIsRestored() throws IOException {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        int start = source.indexOf("private void selectData()");
        assertTrue(start >= 0, "InvoiceScreenController.selectData was renamed - point this test at its successor");
        String body = source.substring(start, source.indexOf("\n    }\n", start) < 0
                ? source.length() : source.indexOf("    private ", start + 10));

        assertTrue(body.contains("selectStoredTreasury(dataById.getTreasuryModel())"),
                "an edited document opens on the default treasury again, and saving it moves its cash there");
        assertTrue(source.contains("comboTreasury.getItems().add(stored.getName())"),
                "a document on a treasury since closed would be re-filed under another one");
    }
}
