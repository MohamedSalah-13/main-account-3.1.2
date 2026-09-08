package com.hamza.account.features.treasury.statement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryStatementScreenArchitectureTest {
    private static final Path ROOT = Path.of("src", "main");

    @Test
    void theStatementDoesNotRegainItsLegacyInMemoryFilters() throws IOException {
        String controller = Files.readString(ROOT.resolve(Path.of("java", "com", "hamza", "account",
                "controller", "convert_treasury", "TreasureDetailsController.java")));

        assertFalse(controller.contains("Image_Setting"));
        assertFalse(controller.contains("FilteredList"));
        assertFalse(controller.contains("filterByTime"));
        assertFalse(controller.contains("CurrentUser.get().getId() != 1"));
        assertTrue(controller.contains("TreasuryMovementKind"));
        assertTrue(controller.contains("subscribe(TreasuryBalancesChanged.class"));
    }

    @Test
    void theTableOwnsTheCenterAndTheObsoleteShiftSummaryIsGone() throws IOException {
        String fxml = Files.readString(ROOT.resolve(Path.of("resources", "com", "hamza", "account",
                "view", "treasury", "treasury-details.fxml")));

        assertFalse(fxml.contains("ScrollPane"));
        assertFalse(fxml.contains("checkTime"));
        assertFalse(fxml.contains("btnPrintSummary"));
        assertTrue(fxml.contains("onAction=\"#search\""));
        assertTrue(fxml.contains("fx:id=\"btnRefresh\" onAction=\"#refresh\""));
        assertTrue(fxml.contains("fx:id=\"tableView\""));
    }
}
