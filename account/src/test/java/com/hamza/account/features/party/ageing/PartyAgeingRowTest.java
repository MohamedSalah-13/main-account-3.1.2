package com.hamza.account.features.party.ageing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one property that makes an ageing report checkable: it adds up.
 * <p>
 * A wrong ageing report looks exactly like a right one - five plausible columns of money.
 * The reconciliation is the only thing standing between a mistake in the SQL, or a mapper
 * reading a column into the wrong field, and a number somebody chases a customer over.
 */
class PartyAgeingRowTest {

    private static Map<AgeingBucket, BigDecimal> bands(String current, String b30, String b60,
                                                       String b90, String over) {
        Map<AgeingBucket, BigDecimal> buckets = new EnumMap<>(AgeingBucket.class);
        buckets.put(AgeingBucket.CURRENT, new BigDecimal(current));
        buckets.put(AgeingBucket.DAYS_1_30, new BigDecimal(b30));
        buckets.put(AgeingBucket.DAYS_31_60, new BigDecimal(b60));
        buckets.put(AgeingBucket.DAYS_61_90, new BigDecimal(b90));
        buckets.put(AgeingBucket.OVER_90, new BigDecimal(over));
        return buckets;
    }

    private static PartyAgeingRow row(Map<AgeingBucket, BigDecimal> buckets,
                                      String unallocated, String balance) {
        return new PartyAgeingRow(7, "ابنه صباح", "0100", "قنا", 30, buckets,
                new BigDecimal(unallocated), new BigDecimal(balance));
    }

    @Nested
    @DisplayName("The bands and the unallocated column equal the balance")
    class Reconciliation {

        @Test
        void theOrdinaryCase() {
            PartyAgeingRow party = row(bands("100", "50", "25", "0", "75"), "0", "250");

            assertEquals(new BigDecimal("250.00"), party.balance());
            assertEquals(new BigDecimal("250.00"), party.onOpenInvoices());
        }

        /**
         * The usual real shape: payments taken on account, so the invoices add to more than the
         * party owes and the difference is a credit sitting on nothing.
         */
        @Test
        @DisplayName("payments on account make unallocated negative, and it still adds up")
        void unallocatedCredit() {
            PartyAgeingRow party = row(bands("0", "0", "25", "0", "0"), "-25", "0");

            assertEquals(new BigDecimal("0.00"), party.balance());
            assertEquals(new BigDecimal("25.00"), party.onOpenInvoices());
            assertEquals(new BigDecimal("25.00"), party.overdue());
        }

        @Test
        @DisplayName("a row that does not add up is refused, not shown")
        void aWrongRowIsRefused() {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> row(bands("100", "0", "0", "0", "0"), "0", "250"));

            assertTrue(refused.getMessage().contains("does not reconcile"), refused.getMessage());
            assertTrue(refused.getMessage().contains("250"), refused.getMessage());
        }

        /** Two decimals per band, so a few piastres of rounding is not called a defect. */
        @Test
        void roundingWithinToleranceIsAccepted() {
            row(bands("100.01", "0", "0", "0", "0"), "0", "100.00");
        }

        @Test
        void roundingBeyondToleranceIsNot() {
            assertThrows(IllegalArgumentException.class,
                    () -> row(bands("100.50", "0", "0", "0", "0"), "0", "100.00"));
        }
    }

    @Nested
    @DisplayName("What counts as overdue")
    class Overdue {

        /** CURRENT is owed but not late, so it is not in the figure a manager acts on. */
        @Test
        void currentIsNotOverdue() {
            PartyAgeingRow party = row(bands("500", "0", "0", "0", "0"), "0", "500");

            assertEquals(new BigDecimal("0.00"), party.overdue());
            assertFalse(party.isOverdue());
        }

        @Test
        void theOtherFourAre() {
            PartyAgeingRow party = row(bands("10", "20", "30", "40", "50"), "0", "150");

            assertEquals(new BigDecimal("140.00"), party.overdue());
            assertTrue(party.isOverdue());
        }

        /**
         * <b>A party can owe nothing and still be overdue</b>, and this is the row the report
         * exists to surface: an old unpaid invoice offset by a newer payment nobody allocated.
         * Judging by the balance alone would hide it completely.
         */
        @Test
        @DisplayName("a zero balance can still hide an overdue invoice")
        void zeroBalanceCanStillBeOverdue() {
            PartyAgeingRow party = row(bands("0", "0", "0", "0", "1000"), "-1000", "0");

            assertEquals(new BigDecimal("0.00"), party.balance());
            assertEquals(new BigDecimal("1000.00"), party.overdue());
            assertTrue(party.isOverdue());
        }
    }

    @Nested
    @DisplayName("What the record normalises")
    class Normalising {

        @Test
        @DisplayName("every band is present even when the query returned none")
        void missingBandsBecomeZero() {
            PartyAgeingRow party = new PartyAgeingRow(1, "x", null, null, 0,
                    Map.of(), BigDecimal.ZERO, BigDecimal.ZERO);

            for (AgeingBucket bucket : AgeingBucket.values()) {
                assertEquals(new BigDecimal("0.00"), party.amount(bucket), bucket.name());
            }
            assertEquals("", party.phone());
            assertEquals("", party.areaName());
        }

        @Test
        void theBandsCannotBeChangedAfterwards() {
            PartyAgeingRow party = row(bands("1", "0", "0", "0", "0"), "0", "1");

            assertThrows(UnsupportedOperationException.class,
                    () -> party.buckets().put(AgeingBucket.CURRENT, BigDecimal.TEN));
        }
    }
}
