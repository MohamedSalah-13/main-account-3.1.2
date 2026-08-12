package com.hamza.account.period;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the accounting line falls, and which documents it protects.
 * <p>
 * The boundary is the part that goes wrong by one: closing "حتى 31 ديسمبر" has to
 * include the thirty-first, or closing a year leaves its last day editable and the
 * figure everyone signed can still be changed.
 */
class AccountingLockTest {

    private static AccountingLock closedUntil(String day) {
        return new AccountingLock(LocalDate.parse(day), "إقفال", null, 1);
    }

    @Nested
    @DisplayName("The boundary")
    class Boundary {

        @ParameterizedTest(name = "closed until {0}, document dated {1} -> covered {2}")
        @CsvSource({
                // the closing day itself is closed
                "2025-12-31, 2025-12-31, true",
                // anything before it
                "2025-12-31, 2025-12-30, true",
                "2025-12-31, 2020-01-01, true",
                // the day after is open, and it is the first open day
                "2025-12-31, 2026-01-01, false",
                "2025-12-31, 2026-06-15, false",
        })
        void theClosingDayIsClosed(String until, String document, boolean covered) {
            assertEquals(covered, closedUntil(until).covers(LocalDate.parse(document)));
        }

        @Test
        @DisplayName("the first open day is the day after the line")
        void firstOpenDayIsTheDayAfter() {
            assertEquals(LocalDate.parse("2026-01-01"), closedUntil("2025-12-31").firstOpenDay());
        }

        @Test
        @DisplayName("nothing is covered while nothing is closed")
        void anOpenPeriodCoversNothing() {
            assertFalse(AccountingLock.OPEN.isClosed());
            assertFalse(AccountingLock.OPEN.covers(LocalDate.parse("1999-01-01")));
            assertNull(AccountingLock.OPEN.firstOpenDay());
        }

        /**
         * A document with no date must not be refused. Refusing it would block a save
         * over a missing value while saying the period is closed, which sends whoever
         * hits it looking in the wrong place entirely.
         */
        @Test
        @DisplayName("a document with no date is not covered")
        void aMissingDateIsNotCovered() {
            assertFalse(closedUntil("2025-12-31").covers(null));
        }

        @Test
        @DisplayName("notes are never null, so the history table has nothing to guard against")
        void notesDefaultToEmpty() {
            assertEquals("", new AccountingLock(LocalDate.now(), null, null, 1).notes());
        }
    }

    /**
     * The invoice models carry their date as a string, so the check has to read one
     * before it can judge it.
     */
    @Nested
    @DisplayName("Reading an invoice's date")
    class ReadingDates {

        @Test
        @DisplayName("an ISO date is read, spaces and all")
        void readsAnIsoDate() {
            assertEquals(LocalDate.parse("2025-12-31"), PeriodLock.parse("2025-12-31"));
            assertEquals(LocalDate.parse("2025-12-31"), PeriodLock.parse("  2025-12-31  "));
        }

        /**
         * Answering null lets the save through. That is deliberate: a malformed date is a
         * different fault, the insert behind this reports it properly, and refusing it
         * here would blame the accounting period for something unrelated - sending
         * whoever hit it to the wrong screen.
         */
        @ParameterizedTest(name = "[{0}] cannot be judged")
        @CsvSource(value = {"''", "'   '", "'31/12/2025'", "'not a date'", "'2025-13-45'", "NULL"},
                nullValues = "NULL")
        void anUnreadableDateIsNotJudged(String value) {
            assertNull(PeriodLock.parse(value));
        }
    }

    @Nested
    @DisplayName("Protected documents")
    class Documents {

        private static List<LockedDocument> all() {
            return List.of(PeriodLockRegistry.SALES_INVOICE, PeriodLockRegistry.SALES_RETURN,
                    PeriodLockRegistry.PURCHASE_INVOICE, PeriodLockRegistry.PURCHASE_RETURN,
                    PeriodLockRegistry.CUSTOMER_ACCOUNT, PeriodLockRegistry.SUPPLIER_ACCOUNT,
                    PeriodLockRegistry.EXPENSE, PeriodLockRegistry.STOCK_COUNT,
                    PeriodLockRegistry.TREASURY_TRANSFER, PeriodLockRegistry.TREASURY_DEPOSIT,
                    PeriodLockRegistry.TREASURY_MOVEMENT);
        }

        @Test
        @DisplayName("both invoice sides, both returns, both account ledgers, expenses and the stock count")
        void everyDatedDocumentIsCovered() {
            List<String> tables = all().stream().map(LockedDocument::table).toList();

            assertTrue(tables.containsAll(List.of("total_sales", "total_sales_re",
                    "total_buy", "total_buy_re", "customers_accounts", "suppliers_accounts",
                    "expenses_details", "stock_count")), tables.toString());
        }

        @Test
        @DisplayName("no table is declared twice")
        void tablesAreDistinct() {
            Set<String> seen = new HashSet<>();
            for (LockedDocument document : all()) {
                assertTrue(seen.add(document.table()), () -> "declared twice: " + document.table());
            }
        }

        /**
         * The keys are not all {@code id}: the invoice tables are keyed by
         * {@code invoice_number} and the account ledgers by {@code account_num}. Reading
         * the wrong one would look up nothing, find no date, and let every delete
         * through - a lock that silently does not lock.
         */
        @Test
        @DisplayName("each document names its real key and its business date")
        void keysAndDatesAreTheRealColumns() {
            assertEquals("invoice_number", PeriodLockRegistry.SALES_INVOICE.idColumn());
            assertEquals("invoice_date", PeriodLockRegistry.SALES_INVOICE.dateColumn());

            assertEquals("id", PeriodLockRegistry.SALES_RETURN.idColumn());
            assertEquals("account_num", PeriodLockRegistry.CUSTOMER_ACCOUNT.idColumn());
            assertEquals("account_date", PeriodLockRegistry.CUSTOMER_ACCOUNT.dateColumn());
            assertEquals("date", PeriodLockRegistry.EXPENSE.dateColumn());
            assertEquals("count_date", PeriodLockRegistry.STOCK_COUNT.dateColumn());
            // The treasury tables each name their date differently, and none of them
            // calls it "date".
            assertEquals("transfer_date", PeriodLockRegistry.TREASURY_TRANSFER.dateColumn());
            assertEquals("date_inter", PeriodLockRegistry.TREASURY_DEPOSIT.dateColumn());
            assertEquals("movement_date", PeriodLockRegistry.TREASURY_MOVEMENT.dateColumn());
        }

        @Test
        @DisplayName("every document is named for the refusal message")
        void everyDocumentIsNamed() {
            for (LockedDocument document : all()) {
                assertFalse(document.label().isBlank(), () -> document.table() + " has no label");
            }
        }

        @Test
        @DisplayName("an identifier that is not one is refused before it reaches SQL")
        void identifiersAreChecked() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LockedDocument("x", "total_sales; DROP TABLE total_sales", "id", "invoice_date"));
            assertThrows(IllegalArgumentException.class,
                    () -> new LockedDocument("x", "total_sales", "id", "invoice_date OR 1=1"));
        }
    }
}
