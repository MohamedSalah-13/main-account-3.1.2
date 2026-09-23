package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screen resolves these through a variable - a line's key, a choice's key - where
 * {@code MessageKeyArchitectureTest} cannot see them, so they are checked against the three bundles here.
 */
class ProfitLossKeysTest {

    @Test
    @DisplayName("every key a statement line, a choice or a movement can carry is in the three bundles")
    void everyKeyIsTranslated() throws Exception {
        List<String> keys = new ArrayList<>();
        ProfitLossStatement.Side empty = new ProfitLossStatement.Side(ProfitLossFigures.ZERO, SalesBreakdown.ZERO,
                List.of(), OutsideProfitFigures.ZERO);
        ProfitLossStatement.Side off = new ProfitLossStatement.Side(new ProfitLossFigures(java.math.BigDecimal.ONE,
                null, null, null, null), SalesBreakdown.ZERO, List.of(), OutsideProfitFigures.ZERO);
        for (StatementLine line : ProfitLossStatement.lines(off, empty)) {
            if (line.messageKey() != null) {
                keys.add(line.messageKey());
            }
        }
        assertTrue(keys.contains("profitloss.line.unexplained"), "the difference line is among them");
        for (ComparisonBasis basis : ComparisonBasis.values()) {
            keys.add(basis.messageKey());
        }
        for (ProfitLossGrouping grouping : ProfitLossGrouping.values()) {
            keys.add(grouping.messageKey());
        }
        for (ProfitLossMovement.Kind kind : ProfitLossMovement.Kind.values()) {
            keys.add(kind.messageKey());
        }
        keys.add("profitloss.error.period.required");
        keys.add("profitloss.error.period.reversed");
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newBufferedReader(Path.of("..", "controlsfx", "src", "main", "resources", "i18n",
                    bundle))) {
                properties.load(in);
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
        }
    }
}
