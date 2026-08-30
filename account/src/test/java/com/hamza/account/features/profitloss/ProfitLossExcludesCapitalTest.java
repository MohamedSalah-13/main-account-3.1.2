package com.hamza.account.features.profitloss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner's money must not reach the profit.
 * <p>
 * Capital paid in is not income and drawings are not an expense. Counted as either,
 * the treasury still balances and the profit - the one number the owner actually
 * reads - is wrong by the whole amount, silently and forever.
 * <p>
 * Today that holds for a structural reason rather than a rule: {@code ProfitLossDao}
 * reads expenses from {@code expenses_details} and never looks at
 * {@code treasury_deposit_expenses}, where capital lives. That is a property nobody
 * chose and nothing was protecting - a single {@code UNION ALL} added to the report
 * "so deposits show up" would end it - so it is written down here as a rule with a
 * reason, and the build fails if the report ever reaches into that table.
 * <p>
 * The other half of the same guarantee - that a real capital row against a real
 * MySQL leaves the profit untouched - is
 * {@code TreasuryBalanceViewAcceptanceTest.capitalDoesNotReachTheProfit}.
 */
class ProfitLossExcludesCapitalTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "hamza",
            "account", "features", "profitloss", "ProfitLossDao.java");

    @Test
    @DisplayName("the profit and loss report does not read the treasury deposit table")
    void theReportCannotSeeCapital() {
        String source = read(SOURCE);

        assertFalse(source.contains("treasury_deposit_expenses"),
                "ProfitLossDao now reads treasury_deposit_expenses, which is where capital "
                        + "paid in and the owner's drawings live. Neither is income or expense: "
                        + "including them makes every profit figure wrong. See "
                        + "docs/treasury-plan.md §4.");
        assertFalse(source.contains("category"),
                "ProfitLossDao has grown a notion of a cash category; the report is not the "
                        + "place to decide what counts as the owner's money");
    }

    @Test
    @DisplayName("it still reads the expense table it is supposed to - the test is not vacuous")
    void theReportWasActuallyRead() {
        String source = read(SOURCE);

        assertTrue(source.contains("expenses_details"),
                "ProfitLossDao no longer reads expenses_details either; this test would pass "
                        + "for the wrong reason");
        assertTrue(source.contains("document_profit"),
                "ProfitLossDao no longer reads document_profit, which is the single "
                        + "definition of what a sale earned. Whatever it reads instead is a "
                        + "second answer to the same question - see ProfitDefinitionTest.");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path.toAbsolutePath(), e);
        }
    }
}
