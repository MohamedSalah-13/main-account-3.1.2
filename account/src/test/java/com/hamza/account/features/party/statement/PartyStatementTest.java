package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic and the rules of a party statement, without a database and without a
 * JavaFX toolkit.
 */
class PartyStatementTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private static PartyStatementRow row(PartyMovementKind kind, boolean cash,
                                         String purchase, String discount, String paid) {
        return new PartyStatementRow(1, DAY, LocalDateTime.of(2026, 9, 1, 10, 0), kind, cash, 1,
                new BigDecimal(purchase), new BigDecimal(discount), new BigDecimal(paid),
                BigDecimal.ZERO, 1, "الدرج", 1, "admin", "");
    }

    @Nested
    @DisplayName("a row's two columns always subtract to its effect on the balance")
    class DebitAndCredit {

        /**
         * The invariant the whole presentation rests on. Whatever lands in the debtor and
         * creditor columns, their difference is what the party's balance actually moved by -
         * the figure {@code DocumentLedgerEffect.balanceChange()} states once for the system.
         */
        private void assertColumnsExplain(PartyStatementRow row) {
            assertEquals(0, row.debit().subtract(row.credit()).compareTo(row.balanceChange()),
                    "debit - credit must equal balanceChange for " + row.kind()
                            + " (debit " + row.debit() + ", credit " + row.credit()
                            + ", change " + row.balanceChange() + ")");
        }

        @Test
        void aDeferredInvoiceIsAllDebt() {
            PartyStatementRow invoice = row(PartyMovementKind.INVOICE, false, "1000", "50", "0");
            assertEquals(new BigDecimal("950.00"), invoice.debit());
            assertEquals(new BigDecimal("0.00"), invoice.credit());
            assertEquals(new BigDecimal("950.00"), invoice.balanceChange());
            assertColumnsExplain(invoice);
        }

        /**
         * A cash invoice moves the balance by nothing, and still has to be visible.
         * <p>
         * This is why the columns are not {@code max(balanceChange, 0)} and
         * {@code max(-balanceChange, 0)}: that would show two zeros and the invoice would
         * vanish from the statement of a shop that sells for cash.
         */
        @Test
        void aCashInvoiceShowsItsValueAndItsPaymentAndMovesNothing() {
            PartyStatementRow invoice = row(PartyMovementKind.INVOICE, true, "1000", "0", "1000");
            assertEquals(new BigDecimal("1000.00"), invoice.debit());
            assertEquals(new BigDecimal("1000.00"), invoice.credit());
            assertEquals(new BigDecimal("0.00"), invoice.balanceChange());
            assertColumnsExplain(invoice);
        }

        /**
         * <b>The defect this package was written for.</b> A deferred sales return is stored
         * with a negative total and a zero cash column since {@code V15__return_cash_split.sql},
         * so it credits the customer by its whole value.
         * <p>
         * What it replaced read {@code if (invoiceType == CASH) total = totalAfterDiscount;}
         * and therefore put zero in both columns: the row was invisible, and a customer who
         * had returned a thousand pounds of goods on account still appeared to owe it — while
         * the totals screen, reading the view, knew they did not.
         */
        @Test
        void aDeferredReturnCreditsItsWholeValue() {
            PartyStatementRow ret = row(PartyMovementKind.RETURN, false, "-1000", "0", "0");
            assertEquals(new BigDecimal("0.00"), ret.debit());
            assertEquals(new BigDecimal("1000.00"), ret.credit());
            assertEquals(new BigDecimal("-1000.00"), ret.balanceChange());
            assertColumnsExplain(ret);
        }

        /** A cash return credits the goods and debits the money handed back. Net nothing. */
        @Test
        void aCashReturnShowsBothSidesAndMovesNothing() {
            PartyStatementRow ret = row(PartyMovementKind.RETURN, true, "-1000", "0", "-1000");
            assertEquals(new BigDecimal("1000.00"), ret.debit());
            assertEquals(new BigDecimal("1000.00"), ret.credit());
            assertEquals(new BigDecimal("0.00"), ret.balanceChange());
            assertColumnsExplain(ret);
        }

        @Test
        void aPaymentIsAllCredit() {
            PartyStatementRow payment = row(PartyMovementKind.PAYMENT, true, "0", "0", "400");
            assertEquals(new BigDecimal("0.00"), payment.debit());
            assertEquals(new BigDecimal("400.00"), payment.credit());
            assertColumnsExplain(payment);
        }

        @Test
        void anOpeningBalanceIsDebtAndANegativeOneIsCredit() {
            assertEquals(new BigDecimal("700.00"),
                    row(PartyMovementKind.OPENING, false, "700", "0", "0").debit());
            assertEquals(new BigDecimal("700.00"),
                    row(PartyMovementKind.OPENING, false, "-700", "0", "0").credit());
        }

        /** A partial refund on a return, which is where writing the branch by hand went wrong. */
        @Test
        void aPartlyRefundedReturnSplitsBetweenTheTwoColumns() {
            PartyStatementRow ret = row(PartyMovementKind.RETURN, false, "-1000", "0", "-400");
            assertEquals(new BigDecimal("400.00"), ret.debit());
            assertEquals(new BigDecimal("1000.00"), ret.credit());
            assertEquals(new BigDecimal("-600.00"), ret.balanceChange());
            assertColumnsExplain(ret);
        }
    }

    @Nested
    @DisplayName("the summary")
    class Summary {

        @Test
        void netMovementIsTheTwoTotalsSubtracted() {
            PartyStatementSummary summary = new PartyStatementSummary(
                    new BigDecimal("500"), new BigDecimal("1200"), new BigDecimal("300"),
                    new BigDecimal("1400"));
            assertEquals(new BigDecimal("900.00"), summary.netMovement());
            assertTrue(summary.rowsExplainTheBalance());
        }

        /**
         * When a filter hides movements, the shown rows no longer carry the balance from
         * opening to closing — and the screen has to say so rather than leave a reader
         * subtracting two numbers that do not meet.
         */
        @Test
        void aNarrowedListDoesNotExplainTheBalance() {
            PartyStatementSummary summary = new PartyStatementSummary(
                    new BigDecimal("500"), new BigDecimal("100"), new BigDecimal("0"),
                    new BigDecimal("1400"));
            assertFalse(summary.rowsExplainTheBalance());
        }

        @Test
        void everyFigureRoundsToTwoPlaces() {
            PartyStatementSummary summary = new PartyStatementSummary(
                    new BigDecimal("10.005"), new BigDecimal("1.111"), null, null);
            assertEquals(2, summary.openingBalance().scale());
            assertEquals(2, summary.totalDebit().scale());
            assertEquals(new BigDecimal("0.00"), summary.closingBalance());
        }
    }

    @Nested
    @DisplayName("the filter")
    class Filter {

        private PartyStatementFilter filter(LocalDate from, LocalDate to, int page, int size) {
            return new PartyStatementFilter(PartyKind.CUSTOMER, 7, from, to, Set.of(),
                    null, null, null, null, "", false, page, size);
        }

        @Test
        void refusesAPeriodThatRunsBackwards() {
            assertThrows(IllegalArgumentException.class,
                    () -> filter(DAY, DAY.minusDays(1), 0, 10));
        }

        @Test
        void refusesAPartyItCannotName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PartyStatementFilter(PartyKind.CUSTOMER, 0, DAY, DAY, Set.of(),
                            null, null, null, null, "", false, 0, 10));
        }

        @Test
        void refusesAPageSizeOutsideTheAllowedRange() {
            assertThrows(IllegalArgumentException.class, () -> filter(DAY, DAY, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> filter(DAY, DAY, 0, PartyStatementFilter.MAX_PAGE_SIZE + 1));
        }

        @Test
        void refusesAnAmountRangeThatRunsBackwards() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                            null, null, new BigDecimal("100"), new BigDecimal("10"), "", false, 0, 10));
        }

        /** One row more than the page holds is what answers "is there another page". */
        @Test
        void asksForOneRowMoreThanThePageHolds() {
            assertEquals(51, filter(DAY, DAY, 0, 50).queryLimit());
            assertEquals(100, filter(DAY, DAY, 2, 50).offset());
        }

        /**
         * The period alone is not a narrowing: a statement always has one, and a screen that
         * warned about it on every opening would be teaching users to ignore the warning.
         */
        @Test
        void aPeriodAloneDoesNotNarrowTheRows() {
            assertFalse(filter(DAY.minusMonths(1), DAY, 0, 50).narrowsRows());
        }

        @Test
        void everyOtherFilterDoesNarrowTheRows() {
            assertTrue(new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY,
                    Set.of(PartyMovementKind.PAYMENT), null, null, null, null, "", false, 0, 50)
                    .narrowsRows());
            assertTrue(new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                    3, null, null, null, "", false, 0, 50).narrowsRows());
            assertTrue(new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                    null, 2, null, null, "", false, 0, 50).narrowsRows());
            assertTrue(new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                    null, null, BigDecimal.TEN, null, "", false, 0, 50).narrowsRows());
            assertTrue(new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                    null, null, null, null, "cheque", false, 0, 50).narrowsRows());
            assertTrue(new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                    null, null, null, null, "", true, 0, 50).narrowsRows());
        }

        /** The kinds reach SQL as one bound, sorted, comma-joined value. Never as text in the statement. */
        @Test
        void kindCodesAreSortedAndNullWhenEverythingIsWanted() {
            assertEquals(null, filter(DAY, DAY, 0, 50).kindCodes());
            assertEquals("2,4", new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY,
                    Set.of(PartyMovementKind.RETURN, PartyMovementKind.PAYMENT),
                    null, null, null, null, "", false, 0, 50).kindCodes());
        }

        @Test
        void blankTextIsNoText() {
            assertEquals("", new PartyStatementFilter(PartyKind.CUSTOMER, 7, DAY, DAY, Set.of(),
                    null, null, null, null, "   ", false, 0, 50).text());
        }
    }

    @Nested
    @DisplayName("the movement kind")
    class Kind {

        /** The codes are the {@code information} column, and have been since {@code V1}. */
        @Test
        void carriesTheInformationCodesTheViewsUse() {
            assertEquals(1, PartyMovementKind.OPENING.code());
            assertEquals(2, PartyMovementKind.PAYMENT.code());
            assertEquals(3, PartyMovementKind.INVOICE.code());
            assertEquals(4, PartyMovementKind.RETURN.code());
            for (PartyMovementKind kind : PartyMovementKind.values()) {
                assertEquals(kind, PartyMovementKind.fromCode(kind.code()));
            }
        }

        /** Named in the message, because a bare failure inside a row mapper names nothing. */
        @Test
        void namesAnUnknownCodeWhenItRefusesIt() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> PartyMovementKind.fromCode(9));
            assertTrue(thrown.getMessage().contains("9"), thrown.getMessage());
        }

        @Test
        void onlyDocumentsHaveLines() {
            assertTrue(PartyMovementKind.INVOICE.hasDocumentLines());
            assertTrue(PartyMovementKind.RETURN.hasDocumentLines());
            assertFalse(PartyMovementKind.OPENING.hasDocumentLines());
            assertFalse(PartyMovementKind.PAYMENT.hasDocumentLines());
        }

        /**
         * Every kind's label is translated in all three bundles.
         * <p>
         * {@code MessageKeyArchitectureTest} cannot see these: it scans the arguments of
         * {@code getString} calls, and the screen calls {@code getString(kind.messageKey())}
         * - a variable. A missing key would render as {@code party.movement.invoice} in the
         * statement's busiest column, with only a log line to say so. So they are checked
         * here, where they are declared, and the bundles are read off disk the way that
         * test reads them - the properties files belong to {@code controlsfx} and are not on
         * this module's test classpath as a resource bundle.
         */
        @Test
        void everyKindIsTranslatedInEveryBundle() {
            Path bundleDir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
            for (String bundleName : new String[]{
                    "messages.properties", "messages_ar.properties", "messages_en.properties"}) {
                Properties bundle = read(bundleDir.resolve(bundleName));
                for (PartyMovementKind kind : PartyMovementKind.values()) {
                    String value = bundle.getProperty(kind.messageKey());
                    assertNotNull(value, kind.messageKey() + " is missing from " + bundleName);
                    assertFalse(value.isBlank(), kind.messageKey() + " is blank in " + bundleName);
                }
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
}
