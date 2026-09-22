package com.hamza.account.features.capital;

import com.hamza.account.features.profitloss.ProfitLossRow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquityStatementLineTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static EquityStatement statement() {
        return new EquityStatement(new CapitalFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                new BroughtForward(d("1000"), d("400"), d("150"), d("250")),
                new CapitalBefore(d("3000"), d("500")), d("1200"),
                List.of(new CapitalDay(LocalDate.of(2026, 1, 10), 1, "الخزينة", d("5000"), d("800"), 2)),
                List.of(new ProfitLossRow(LocalDate.of(2026, 2, 1), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, d("900"))));
    }

    /** Each total is the lines since the one before it - the details are explanation, not addends. */
    @Test
    void everyTotalIsTheLinesAboveIt() {
        List<EquityStatementLine> lines = EquityStatementLine.of(statement());

        BigDecimal running = BigDecimal.ZERO;
        int totals = 0;
        for (EquityStatementLine line : lines) {
            switch (line.kind()) {
                case LINE -> running = running.add(line.amount());
                case DETAIL -> { }
                case TOTAL -> {
                    assertEquals(0, running.compareTo(line.amount()), line.labelKey());
                    totals++;
                }
            }
        }
        assertEquals(2, totals);
        assertEquals("capital.equity.closing", lines.getLast().labelKey());
        assertEquals(d("10300"), lines.getLast().amount());
    }

    @Test
    void theDetailsAddUpToTheLineTheyExplain() {
        List<EquityStatementLine> lines = EquityStatementLine.of(statement());

        BigDecimal details = lines.stream().filter(line -> line.kind() == EquityStatementLine.Kind.DETAIL)
                .map(EquityStatementLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, details.compareTo(lines.getFirst().amount()), "treasuries + customers - suppliers + stock");
        assertEquals(d("-150"), lines.get(3).amount(), "what the business owed its suppliers is subtracted");
        assertEquals(d("-800"), lines.get(9).amount(), "drawings act on equity negatively");
    }

    /** The screen resolves each line's key through a variable, which the message-key scan cannot see. */
    @Test
    void everyLineHasALabelInAllThreeBundles() {
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = load(bundle);
            for (EquityStatementLine line : EquityStatementLine.of(statement())) {
                assertTrue(properties.containsKey(line.labelKey()), bundle + " has no " + line.labelKey());
            }
        }
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
