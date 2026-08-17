package com.hamza.account.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code V15__return_cash_split.sql}, the data half of the return correction.
 * <p>
 * {@code R__views.sql} now reads the {@code paid_*} column of a return as what its name
 * says - money the treasury moved. The stored rows were entered under the opposite
 * convention for deferred returns, so the migration swaps the two halves. Getting its
 * scope wrong is the kind of mistake that is invisible until a client's supplier
 * balances have already moved: dropping the {@code invoice_type = 2} filter would zero
 * the cash on every cash return, and reversing the subtraction would double the error
 * rather than undo it.
 * <p>
 * No database - the migration is on the classpath, in the manner of {@code
 * WipeCatalogTest}, and the arithmetic it encodes is checked against
 * {@link DocumentLedgerEffect} directly.
 */
class ReturnCashSplitMigrationTest {

    private static final String MIGRATION_NAME = "V15__return_cash_split.sql";

    private static final String MIGRATION = read("db/migration/" + MIGRATION_NAME);

    /** The statements, lowercased and with runs of whitespace flattened. */
    private static final String NORMALISED =
            MIGRATION.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

    private static String read(String resource) {
        try (InputStream in = ReturnCashSplitMigrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Both return families are migrated, and only they")
    void bothReturnTablesAreSwapped() {
        assertTrue(NORMALISED.contains(
                "update total_sales_re set paid_from_treasury = total - discount - paid_from_treasury"),
                MIGRATION);
        assertTrue(NORMALISED.contains(
                "update total_buy_re set paid_to_treasury = total - discount - paid_to_treasury"),
                MIGRATION);
        // The invoice families settled nothing wrongly and must not be touched.
        assertFalse(NORMALISED.contains("update total_sales "), MIGRATION);
        assertFalse(NORMALISED.contains("update total_buy "), MIGRATION);
    }

    @Test
    @DisplayName("Scoped to deferred returns - a cash return already means what it says")
    void onlyDeferredRowsAreSwapped() {
        assertEquals(2, countOccurrences(NORMALISED, "where invoice_type = 2"), MIGRATION);
        assertEquals(2, countOccurrences(NORMALISED, "update "), MIGRATION);
    }

    /**
     * The swap has to be an involution on the pair (cash, on-account): applying it
     * moves the whole net from one side to the other and nothing is created or lost.
     * Checked through {@link DocumentLedgerEffect}, which is what will read the result.
     */
    @ParameterizedTest
    @CsvSource({
            // total, discount, storedPaid - the third is what the row holds today
            "1000, 0,   1000",   // the whole net was credited to the account
            "1000, 0,   0",      // the whole net was refunded in cash
            "1000, 0,   300",    // split
            "1000, 100, 900",    // with a discount, fully credited
            "1000, 100, 250",    // with a discount, split
    })
    void swappingMovesTheWholeNetBetweenTheTillAndTheAccount(
            double total, double discount, double storedPaid) {
        double net = total - discount;
        double swapped = net - storedPaid;

        DocumentLedgerEffect after =
                DocumentLedgerEffect.of(DocumentType.SALES_RETURN, total, discount, swapped);

        // What the old view credited to the account is what the new one credits, once
        // the row has been swapped - that is the whole point of the migration.
        assertEquals(-storedPaid, after.balanceChange().doubleValue(), 0.005);
        // And what the old view paid out of the till is what the new one pays.
        assertEquals(net - storedPaid, after.treasuryOut().doubleValue(), 0.005);
    }

    /**
     * The swap is its own inverse, which is why this is a versioned migration and not a
     * repeatable one: Flyway runs a {@code V} file once, and a second run would put every
     * row back exactly as it was. Nothing about the statements themselves would refuse it.
     */
    @Test
    @DisplayName("Swapping twice is the identity, so it must never be repeatable")
    void theSwapIsItsOwnInverse() {
        double net = 900;
        double stored = 250;
        assertEquals(stored, net - (net - stored), 0.005);
        assertTrue(MIGRATION_NAME.startsWith("V"), MIGRATION_NAME);
        assertFalse(MIGRATION_NAME.startsWith("R__"), MIGRATION_NAME);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
