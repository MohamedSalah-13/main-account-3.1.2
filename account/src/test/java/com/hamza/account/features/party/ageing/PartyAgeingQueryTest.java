package com.hamza.account.features.party.ageing;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.balances.PartyBalanceFilter;
import com.hamza.account.features.party.balances.PartyBalanceQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the ageing statements say, and the two properties that make the report trustworthy:
 * every band is measured as at one day, and the balance is read the way the rest of the
 * application reads it.
 */
class PartyAgeingQueryTest {

    private static PartyAgeingFilter plain(PartyKind kind) {
        return new PartyAgeingFilter(kind, LocalDate.of(2026, 9, 10), null, false, true,
                null, "", 0, 50);
    }

    private static long placeholders(String sql) {
        return sql.chars().filter(character -> character == '?').count();
    }

    @Nested
    @DisplayName("The balance is not a second definition")
    class OneBalance {

        /**
         * The expression is character for character the one the balances screen uses, which is
         * the arithmetic {@code account_customer_totals} uses. Two statements of one rule is
         * exactly the defect the party work exists to remove, and this report adding a fifth
         * would be the worst of them - it is the report a manager acts on.
         */
        @Test
        void itIsTheBalancesScreensExpression() {
            String ageing = PartyAgeingQuery.pageSql(plain(PartyKind.CUSTOMER));
            String balances = PartyBalanceQuery.pageSql(PartyBalanceFilter.allToday(PartyKind.CUSTOMER));

            assertTrue(ageing.contains(PartyAgeingQuery.BALANCE),
                    "the ageing report must use the shared balance expression");
            assertTrue(balances.contains(PartyAgeingQuery.BALANCE),
                    "the balances screen no longer uses it - the two have drifted");
        }

        @Test
        @DisplayName("and unallocated is the balance less the bands, never its own sum")
        void unallocatedIsDerived() {
            String sql = PartyAgeingQuery.pageSql(plain(PartyKind.CUSTOMER));

            assertTrue(sql.contains("AS unallocated"));
            for (AgeingBucket bucket : AgeingBucket.values()) {
                assertTrue(sql.contains("COALESCE(aged." + PartyAgeingQuery.column(bucket) + ", 0)"),
                        bucket.name() + " is not part of the unallocated derivation");
            }
        }
    }

    @Nested
    @DisplayName("Every part is measured as at the same day")
    class AsOf {

        /**
         * The balance, the allocations and the invoices all take {@code asOf}. If any one of
         * them did not, the bands and the balance would answer different questions and the
         * reconciliation in {@code PartyAgeingRow} would start failing on live data - which is
         * a better failure than a wrong report, but a failure all the same.
         */
        @ParameterizedTest(name = "{0}")
        @EnumSource(PartyKind.class)
        void allFiveBindings(PartyKind kind) {
            String sql = PartyAgeingQuery.pageSql(plain(kind));

            assertTrue(sql.contains("m.account_date <= ?"), "the balance must stop at asOf");
            assertTrue(sql.contains("a.account_date <= ?"), "allocations must stop at asOf");
            assertTrue(sql.contains("WHERE d."), "invoices must be bounded");
            assertTrue(sql.contains("DATEDIFF(?,"), "the bands must be measured from asOf");
        }

        @Test
        @DisplayName("the plain page binds asOf five times, then the limit and the offset")
        void parameterCount() {
            assertEquals(7, placeholders(PartyAgeingQuery.pageSql(plain(PartyKind.CUSTOMER))));
        }

        @Test
        void theCountBindsTheSameFiveAndNoPaging() {
            assertEquals(5, placeholders(PartyAgeingQuery.countSql(plain(PartyKind.CUSTOMER))));
        }
    }

    @Nested
    @DisplayName("The bands are decided by number, never by a label")
    class Bands {

        @ParameterizedTest(name = "{0} has its own condition")
        @EnumSource(AgeingBucket.class)
        void everyBandIsSelected(AgeingBucket bucket) {
            String sql = PartyAgeingQuery.pageSql(plain(PartyKind.CUSTOMER));

            assertTrue(sql.contains(PartyAgeingQuery.column(bucket)),
                    bucket.name() + " has no column");
            assertTrue(sql.contains(PartyAgeingQuery.bandCondition(bucket)),
                    bucket.name() + " has no condition");
        }

        /** The SQL bounds come from the enum, so the screen and the export cannot disagree. */
        @Test
        void theConditionsCarryTheEnumsOwnBounds() {
            assertEquals("open.days_overdue <= 0",
                    PartyAgeingQuery.bandCondition(AgeingBucket.CURRENT));
            assertEquals("open.days_overdue BETWEEN 1 AND 30",
                    PartyAgeingQuery.bandCondition(AgeingBucket.DAYS_1_30));
            assertEquals("open.days_overdue BETWEEN 31 AND 60",
                    PartyAgeingQuery.bandCondition(AgeingBucket.DAYS_31_60));
            assertEquals("open.days_overdue BETWEEN 61 AND 90",
                    PartyAgeingQuery.bandCondition(AgeingBucket.DAYS_61_90));
            assertEquals("open.days_overdue >= 91",
                    PartyAgeingQuery.bandCondition(AgeingBucket.OVER_90));
        }

        /**
         * The due date is the invoice date plus the party's own agreed days - the column
         * {@code V56} added for this and nothing had read.
         */
        @Test
        void overdueIsMeasuredFromTheDueDate() {
            String sql = PartyAgeingQuery.pageSql(plain(PartyKind.CUSTOMER));

            assertTrue(sql.contains("INTERVAL op.payment_terms_days DAY"),
                    "the band must be measured from the due date, not the invoice date");
        }

        /** An invoice settled exactly is not open and must occupy no band. */
        @Test
        void onlyWhatIsStillOwedIsAged() {
            assertTrue(PartyAgeingQuery.pageSql(plain(PartyKind.CUSTOMER))
                    .contains("HAVING remaining > 0"));
        }
    }

    @Nested
    @DisplayName("Filters narrow which parties are listed, never which debts are aged")
    class Filters {

        @Test
        void theAreaFilterBindsOnce() {
            PartyAgeingFilter byArea = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.of(2026, 9, 10), 6, false, true, null, "", 0, 50);

            assertEquals(8, placeholders(PartyAgeingQuery.pageSql(byArea)));
            assertTrue(PartyAgeingQuery.pageSql(byArea).contains("p.area_id = ?"));
        }

        @Test
        @DisplayName("the text search binds three and escapes its wildcards")
        void theTextFilter() {
            PartyAgeingFilter byText = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.of(2026, 9, 10), null, false, true, null, "50%", 0, 50);

            assertEquals(10, placeholders(PartyAgeingQuery.pageSql(byText)));
            assertTrue(PartyAgeingQuery.pageSql(byText).contains("ESCAPE '!'"));
            assertEquals("%50!%%", byText.pattern());
        }

        /**
         * "Only the overdue" asks about the four bands, not about the balance - so a party who
         * owes nothing on balance but has a ninety-day-old invoice is still listed.
         */
        @Test
        void overdueOnlyAsksAboutTheBandsNotTheBalance() {
            PartyAgeingFilter overdue = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.of(2026, 9, 10), null, true, true, null, "", 0, 50);
            String sql = PartyAgeingQuery.pageSql(overdue);

            assertTrue(sql.contains("HAVING"));
            assertFalse(sql.contains("HAVING balance"), "it must not judge by the balance");
            assertTrue(sql.contains(PartyAgeingQuery.column(AgeingBucket.OVER_90)));
            assertFalse(sql.contains("HAVING COALESCE(aged."
                            + PartyAgeingQuery.column(AgeingBucket.CURRENT)),
                    "CURRENT is not overdue and must not be counted as it");
        }

        @Test
        void aMinimumBalanceBindsOnce() {
            PartyAgeingFilter floor = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.of(2026, 9, 10), null, false, true, new BigDecimal("100"), "", 0, 50);

            assertEquals(8, placeholders(PartyAgeingQuery.pageSql(floor)));
            assertTrue(PartyAgeingQuery.pageSql(floor).contains("balance >= ?"));
        }

        /** Settled parties are left out by default: a debt report of zeros is unreadable. */
        @Test
        void settledPartiesAreHiddenUnlessAskedFor() {
            PartyAgeingFilter withSettled = plain(PartyKind.CUSTOMER);
            PartyAgeingFilter withoutSettled = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.of(2026, 9, 10), null, false, false, null, "", 0, 50);

            assertFalse(withSettled.includeSettled() == withoutSettled.includeSettled());
            assertFalse(PartyAgeingQuery.pageSql(withSettled).contains("balance <> 0"));
            assertTrue(PartyAgeingQuery.pageSql(withoutSettled).contains("balance <> 0"));
            assertTrue(PartyAgeingFilter.today(PartyKind.CUSTOMER).includeSettled() == false);
        }
    }

    @Nested
    @DisplayName("The page, the count and the summary describe one set")
    class OneSet {

        @Test
        void allThreeCarryTheSameFilters() {
            PartyAgeingFilter filter = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.of(2026, 9, 10), 6, true, false, new BigDecimal("100"), "احمد", 0, 50);

            String page = PartyAgeingQuery.pageSql(filter);
            String count = PartyAgeingQuery.countSql(filter);
            String summary = PartyAgeingQuery.summarySql(filter);

            for (String condition : new String[]{"p.area_id = ?", "ESCAPE '!'", "balance >= ?"}) {
                assertTrue(page.contains(condition), "page: " + condition);
                assertTrue(count.contains(condition), "count: " + condition);
                assertTrue(summary.contains(condition), "summary: " + condition);
            }
            assertEquals(placeholders(page) - 2, placeholders(count));
            assertEquals(placeholders(page) - 2, placeholders(summary));
        }

        @Test
        @DisplayName("and the summary totals every band")
        void theSummaryCoversEveryBand() {
            String summary = PartyAgeingQuery.summarySql(plain(PartyKind.CUSTOMER));

            for (AgeingBucket bucket : AgeingBucket.values()) {
                assertTrue(summary.contains("SUM(aged." + PartyAgeingQuery.column(bucket) + ")"),
                        bucket.name() + " is not summed in the footer");
            }
            assertTrue(summary.contains("COUNT(*) AS parties"));
        }
    }

    @Nested
    @DisplayName("Both sides of the ledger")
    class BothKinds {

        @ParameterizedTest(name = "{0}")
        @EnumSource(PartyKind.class)
        void theRightTablesForEachKind(PartyKind kind) {
            String sql = PartyAgeingQuery.pageSql(plain(kind));
            boolean customer = kind == PartyKind.CUSTOMER;

            assertTrue(sql.contains(customer ? "FROM custom p" : "FROM suppliers p"));
            assertTrue(sql.contains(customer ? "account_customer_table" : "account_suppliers_table"));
            assertTrue(sql.contains(customer ? "FROM total_sales d" : "FROM total_buy d"));
            assertTrue(sql.contains(customer ? "customers_accounts a" : "suppliers_accounts a"));
        }

        /** A supplier's invoice is a purchase, and their returns are not aged either. */
        @Test
        void theInvoiceFamilyIsTheNonReturnOne() {
            assertFalse(PartyAgeingQuery.invoiceType(PartyKind.CUSTOMER).isReturn());
            assertFalse(PartyAgeingQuery.invoiceType(PartyKind.SUPPLIER).isReturn());
        }
    }
}
