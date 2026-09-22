package com.hamza.account.features.capital;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquityReconciliationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 22);

    /**
     * Treasuries 5,000, customers owing 2,000 and one in credit for 100, a supplier owed 1,500 and another
     * paid 50 ahead, stock 3,000 - net assets 8,450. Equity 8,000; a debit note of 200 on a customer, a
     * credit note of 30 on a supplier's account, and 250 of ordinary deposits explain 420 of the 450.
     */
    private static EquityReconciliation worked() {
        return new EquityReconciliation(DAY, new ReconciliationFigures(d("5000"), d("2000"), d("100"), d("1500"),
                d("50"), d("3000"), d("200"), d("30"), d("250")), d("8000"));
    }

    @Test
    void assetsLessLiabilitiesAgainstEquity() {
        EquityReconciliation reconciliation = worked();
        assertMoney("10050", reconciliation.assets());
        assertMoney("1600", reconciliation.liabilities());
        assertMoney("8450", reconciliation.netAssets());
        assertMoney("450", reconciliation.difference());
    }

    /** A customer's note raises an asset the profit never saw; a supplier's raises a liability. */
    @Test
    void theKnownCausesAreTakenOutOfTheDifferenceAndTheRestIsUnexplained() {
        EquityReconciliation reconciliation = worked();
        assertMoney("170", reconciliation.partyNonCash());
        assertMoney("420", reconciliation.explained());
        assertMoney("30", reconciliation.unexplained());
    }

    @Test
    void theLinesReadInOrderAndEachTotalIsItsFigure() {
        List<EquityStatementLine> lines = worked().lines();
        assertEquals(14, lines.size());
        assertEquals("capital.reconcile.treasuries", lines.getFirst().labelKey());
        assertEquals("capital.reconcile.unexplained", lines.getLast().labelKey());
        assertEquals(EquityStatementLine.Kind.TOTAL, lines.getLast().kind());
        assertMoney("10050", amount(lines, "capital.reconcile.assets"));
        assertMoney("8450", amount(lines, "capital.reconcile.net.assets"));
        assertMoney("8000", amount(lines, "capital.reconcile.equity"));
        assertMoney("30", amount(lines, "capital.reconcile.unexplained"));
    }

    /** The screen resolves these keys through a variable, which the message-key scan cannot see. */
    @Test
    void everyLineHasALabelInAllThreeBundles() throws Exception {
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            java.util.Properties properties = new java.util.Properties();
            try (var in = java.nio.file.Files.newInputStream(
                    java.nio.file.Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(in);
            }
            for (EquityStatementLine line : worked().lines()) {
                org.junit.jupiter.api.Assertions.assertTrue(properties.containsKey(line.labelKey()),
                        bundle + " has no " + line.labelKey());
            }
        }
    }

    private static BigDecimal amount(List<EquityStatementLine> lines, String key) {
        return lines.stream().filter(line -> line.labelKey().equals(key)).findFirst().orElseThrow().amount();
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " but was " + actual);
    }
}
