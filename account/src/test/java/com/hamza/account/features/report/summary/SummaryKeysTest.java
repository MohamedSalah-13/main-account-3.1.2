package com.hamza.account.features.report.summary;

import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.report.monthly.MonthFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The paper names each figure through a variable, where {@code MessageKeyArchitectureTest} cannot see the
 * keys, so every line a full summary prints is checked against the three bundles here.
 */
class SummaryKeysTest {

    @Test
    @DisplayName("every line of a full summary's paper is in the three bundles")
    void everyKeyIsTranslated() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 23);
        ProfitLossPeriod today = new ProfitLossPeriod(day, day);
        Summary everything = new Summary(today, today, day, EnumSet.allOf(SummaryCard.class),
                MonthFigures.ZERO, MonthFigures.ZERO, List.of(), MonthFigures.ZERO, MonthFigures.ZERO,
                CashFlow.NONE, CashFlow.NONE, new Receivables(0, BigDecimal.ZERO, List.of()), LowStock.NONE,
                List.of(), List.of());
        List<String> keys = SummaryPaper.lines(everything).stream().map(SummaryPaper.Line::captionKey).toList();
        assertEquals(13, keys.size(), "four for each side, three for the cash, two for the debts");

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
