package com.hamza.account.features.party.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which column a hand-entered movement is written to, and what follows from it.
 * <p>
 * The rule being pinned is a fact about the schema rather than a preference:
 * {@code treasury_balance} unions {@code customers_accounts.paid} as money into the till and
 * {@code suppliers_accounts.paid} as money out of it, and neither view reads {@code purchase} at
 * all. So putting a note's amount in {@code paid} would move a treasury balance that nothing
 * moved, and every one of those is a shortfall somebody has to explain at the end of a shift.
 */
class PartyEntryKindTest {

    private static final double AMOUNT = 250;

    @Test
    @DisplayName("a collection goes to paid and nowhere near purchase")
    void aCollectionIsCash() {
        PartyEntryKind kind = PartyEntryKind.COLLECTION;
        assertTrue(kind.movesCash());
        assertEquals(AMOUNT, kind.paidColumn(AMOUNT));
        assertEquals(0, kind.purchaseColumn(AMOUNT));
        assertTrue(kind.allowsAllocation());
    }

    /**
     * A debit note raises what the party owes and touches no till.
     * <p>
     * This is the movement that makes {@code opening.correction.customers} true: the message the
     * opening-balance guard shows says to record a movement on the account, and until this existed
     * the only movement available was a collection — so an opening balance entered too low could
     * never be corrected by anybody.
     */
    @Test
    @DisplayName("a debit note goes to purchase, positive, and moves no cash")
    void aDebitNoteRaisesTheBalanceWithoutCash() {
        PartyEntryKind kind = PartyEntryKind.DEBIT_NOTE;
        assertFalse(kind.movesCash());
        assertEquals(0, kind.paidColumn(AMOUNT));
        assertEquals(AMOUNT, kind.purchaseColumn(AMOUNT));
    }

    /**
     * And a credit note is a <b>negative purchase</b>, not a positive paid.
     * <p>
     * Both would reduce what the party owes by the same amount, and exactly one of them leaves the
     * treasury alone. Getting this backwards would balance the customer's account and put money in
     * a drawer that never received any.
     */
    @Test
    @DisplayName("a credit note is a negative purchase, never a positive paid")
    void aCreditNoteLowersTheBalanceWithoutCash() {
        PartyEntryKind kind = PartyEntryKind.CREDIT_NOTE;
        assertFalse(kind.movesCash());
        assertEquals(0, kind.paidColumn(AMOUNT),
                "a credit note must not write the cash column: treasury_balance reads it");
        assertEquals(-AMOUNT, kind.purchaseColumn(AMOUNT));
    }

    /** Only a collection may be put against an invoice: a note is not a settlement of one. */
    @ParameterizedTest
    @EnumSource(PartyEntryKind.class)
    void onlyACollectionAllocates(PartyEntryKind kind) {
        assertEquals(kind == PartyEntryKind.COLLECTION, kind.allowsAllocation());
    }

    /** Every kind writes to exactly one of the two columns, whichever it is. */
    @ParameterizedTest
    @EnumSource(PartyEntryKind.class)
    void exactlyOneColumnIsWritten(PartyEntryKind kind) {
        boolean cash = kind.paidColumn(AMOUNT) != 0;
        boolean ledger = kind.purchaseColumn(AMOUNT) != 0;
        assertTrue(cash ^ ledger, kind + " must write one column, not both and not neither");
    }

    /**
     * The labels are translated in all three bundles.
     * <p>
     * As with {@code PartyMovementKind}: the screen resolves these through a variable, so
     * {@code MessageKeyArchitectureTest} cannot see them, and a missing one would render as
     * {@code party.payment.kind.debit.note} inside the combo a user picks from.
     */
    @ParameterizedTest
    @EnumSource(PartyEntryKind.class)
    void everyKindIsTranslatedInEveryBundle(PartyEntryKind kind) {
        Path bundleDir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
        for (String bundleName : new String[]{
                "messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties bundle = read(bundleDir.resolve(bundleName));
            String value = bundle.getProperty(kind.messageKey());
            assertNotNull(value, kind.messageKey() + " is missing from " + bundleName);
            assertFalse(value.isBlank(), kind.messageKey() + " is blank in " + bundleName);
        }
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return properties;
    }
}
