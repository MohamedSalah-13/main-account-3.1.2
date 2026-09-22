package com.hamza.account.features.party.profile;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.trend.TrendGranularity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyProfileTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate MAR_31 = LocalDate.of(2026, 3, 31);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static PartyItemRow item(int id, String name, int group, String groupName, String quantity,
                                     String amount, String returned) {
        return new PartyItemRow(id, name, "قطعة", group, groupName, d(quantity), d(amount), d(returned), 1);
    }

    private static PartyProfileDay sale(LocalDate day, int documents, String net, String cash, String discount) {
        return new PartyProfileDay(day, documents, d(net), d(cash), d(discount), 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static PartyProfileDay returned(LocalDate day, int returns, String net, String refunded, String discount) {
        return new PartyProfileDay(day, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, returns, d(net),
                d(refunded), d(discount));
    }

    private static PartyProfile profile(List<PartyItemRow> items, List<PartyItemRow> previous,
                                        List<PartyProfileDay> days) {
        return PartyProfile.build(new PartyProfileFilter(PartyKind.CUSTOMER, 7, JAN_1, MAR_31),
                items, previous, days, Optional.of(MAR_31), Optional.empty());
    }

    @Nested
    class TheSummary {

        /**
         * Two invoices on two days - lines of 1,000 and 500 with 50 off the first document - and a
         * return of 200. The lines less the headers' own discounts are the headers' net, which is
         * the reconciliation a profile shows under its items.
         */
        @Test
        void theLinesLessTheHeaderDiscountsAreTheNet() {
            PartyProfile profile = profile(
                    List.of(item(1, "عصير", 1, "مشروبات", "10", "1000", "200"),
                            item(2, "أرز", 2, "بقالة", "5", "500", "0")),
                    List.of(),
                    List.of(sale(LocalDate.of(2026, 1, 10), 1, "950", "950", "50"),
                            sale(LocalDate.of(2026, 2, 3), 1, "500", "100", "0"),
                            returned(LocalDate.of(2026, 2, 20), 1, "200", "200", "0")));

            PartyProfileSummary summary = profile.summary();
            assertEquals(d("1250"), summary.net());
            assertEquals(d("1300"), summary.itemsNet());
            assertEquals(d("50"), summary.headerDiscount());
            assertEquals(0, summary.unexplained().signum(), "every saved document explains itself");
            assertEquals(d("850"), summary.cash(), "the refund left the till");
            assertEquals(d("400"), summary.deferred());
            assertEquals(2, summary.documents());
            assertEquals(1, summary.returns());
            assertEquals(Optional.of(d("725.00")), summary.averageDocument(), "documents only, returns are not visits");
        }

        @Test
        void aRowWhoseTotalIsNotItsLinesShowsAsUnexplained() {
            PartyProfile profile = profile(List.of(item(1, "عصير", 1, "مشروبات", "1", "100", "0")),
                    List.of(), List.of(sale(JAN_1, 1, "101", "101", "0")));

            assertEquals(d("1"), profile.summary().unexplained());
        }

        @Test
        void aFigureWithNothingToDivideByIsAbsent() {
            PartyProfileSummary empty = profile(List.of(), List.of(), List.of()).summary();
            assertTrue(empty.averageDocument().isEmpty());
            assertTrue(empty.averageGapDays().isEmpty());
            assertTrue(empty.firstDay().isEmpty());

            PartyProfileSummary once = profile(List.of(), List.of(), List.of(sale(JAN_1, 2, "10", "10", "0"))).summary();
            assertTrue(once.averageGapDays().isEmpty(), "one day of visits has no gap");
        }

        @Test
        void theGapIsTheSpanOverTheIntervalsBetweenVisits() {
            PartyProfileSummary summary = profile(List.of(), List.of(), List.of(
                    sale(LocalDate.of(2026, 1, 1), 1, "10", "10", "0"),
                    sale(LocalDate.of(2026, 1, 11), 1, "10", "10", "0"),
                    returned(LocalDate.of(2026, 1, 15), 1, "5", "5", "0"),
                    sale(LocalDate.of(2026, 1, 31), 1, "10", "10", "0"))).summary();

            assertEquals(3, summary.activeDays(), "a day holding only a return is not a visit");
            assertEquals(Optional.of(d("15.0")), summary.averageGapDays());
            assertEquals(Optional.of(LocalDate.of(2026, 1, 31)), summary.lastDay());
        }
    }

    @Nested
    class TheViews {

        @Test
        void groupsAddTheirItemsAndAreSortedByNet() {
            PartyProfile profile = profile(List.of(
                    item(1, "عصير", 1, "مشروبات", "10", "300", "0"),
                    item(2, "أرز", 2, "بقالة", "5", "500", "0"),
                    item(3, "مياه", 1, "مشروبات", "4", "400", "100")), List.of(), List.of());

            List<PartyGroupRow> groups = profile.groups();
            assertEquals(List.of(new PartyGroupRow(1, "مشروبات", 2, d("600")),
                    new PartyGroupRow(2, "بقالة", 1, d("500"))), groups);
            assertEquals(Optional.of(d("54.5")), profile.share(groups.getFirst()));
            assertEquals(Optional.of(d("27.3")), profile.share(profile.items().get(2)));
        }

        @Test
        void aShareOfNothingIsAbsent() {
            PartyProfile profile = profile(List.of(item(1, "عصير", 1, "مشروبات", "1", "0", "0")), List.of(), List.of());
            assertTrue(profile.share(profile.items().getFirst()).isEmpty());
        }

        /** February bought nothing and is still a point - the gap is what the chart is for. */
        @Test
        void everyPeriodIsThereTheEmptyOnesIncluded() {
            PartyProfile profile = profile(List.of(), List.of(), List.of(
                    sale(LocalDate.of(2026, 1, 5), 1, "100", "100", "0"),
                    sale(LocalDate.of(2026, 1, 20), 2, "50", "50", "0"),
                    returned(LocalDate.of(2026, 3, 2), 1, "30", "30", "0")));

            List<PartyProfilePeriod> months = profile.periods(TrendGranularity.MONTH);
            assertEquals(List.of(
                    new PartyProfilePeriod(LocalDate.of(2026, 1, 1), "2026-01", 3, d("150")),
                    new PartyProfilePeriod(LocalDate.of(2026, 2, 1), "2026-02", 0, BigDecimal.ZERO),
                    new PartyProfilePeriod(LocalDate.of(2026, 3, 1), "2026-03", 0, d("-30"))), months);
        }

        @Test
        void tooManyPeriodsAreRefusedNotDrawn() {
            // Nine years and a quarter: 111 months fit under the ceiling, some 480 weeks do not.
            PartyProfile nineYears = PartyProfile.build(new PartyProfileFilter(PartyKind.CUSTOMER, 7,
                            LocalDate.of(2017, 1, 1), MAR_31), List.of(), List.of(), List.of(),
                    Optional.empty(), Optional.empty());
            assertFalse(nineYears.canGroupBy(TrendGranularity.WEEK));
            assertTrue(nineYears.canGroupBy(TrendGranularity.MONTH));
            assertThrows(IllegalArgumentException.class, () -> nineYears.periods(TrendGranularity.WEEK));
        }

        @Test
        void theWeekRunsFromSaturday() {
            // 2026-01-03 is a Saturday, 2026-01-09 a Friday.
            PartyProfile profile = profile(List.of(), List.of(), List.of(
                    sale(LocalDate.of(2026, 1, 3), 1, "100", "100", "0"),
                    sale(LocalDate.of(2026, 1, 10), 1, "40", "40", "0"),
                    sale(LocalDate.of(2026, 1, 9), 1, "25", "25", "0")));

            List<PartyWeekday> weekdays = profile.weekdays();
            assertEquals(7, weekdays.size());
            assertEquals(new PartyWeekday(DayOfWeek.SATURDAY, 2, d("140")), weekdays.getFirst());
            assertEquals(new PartyWeekday(DayOfWeek.FRIDAY, 1, d("25")), weekdays.getLast());
        }

        @Test
        void whatWasTakenBeforeAndNotNowHasLapsed() {
            PartyProfile profile = profile(
                    List.of(item(1, "عصير", 1, "مشروبات", "3", "30", "0"),
                            item(4, "زيت", 2, "بقالة", "0", "80", "80")),
                    List.of(item(1, "عصير", 1, "مشروبات", "5", "50", "0"),
                            item(2, "أرز", 2, "بقالة", "5", "500", "0"),
                            item(3, "مياه", 1, "مشروبات", "4", "40", "0"),
                            item(4, "زيت", 2, "بقالة", "1", "40", "0"),
                            item(5, "سكر", 2, "بقالة", "0", "20", "20")),
                    List.of());

            List<PartyLapsedItem> lapsed = profile.lapsed();
            // 1 is still bought; 5 was returned in full before, so never really bought; 4 was
            // bought now and returned in full, so it counts as not bought now.
            assertEquals(List.of("أرز", "زيت", "مياه"), lapsed.stream().map(PartyLapsedItem::itemName).toList());
            assertEquals(d("5"), lapsed.getFirst().previousQuantity());
        }
    }

    @Nested
    class TheFilter {

        @Test
        void thePeriodBeforeIsTheSameLengthStraightBefore() {
            PartyProfileFilter filter = new PartyProfileFilter(PartyKind.SUPPLIER, 3, JAN_1, MAR_31);
            assertEquals(LocalDate.of(2025, 12, 31), filter.previousTo());
            assertEquals(LocalDate.of(2025, 10, 3), filter.previousFrom(), "ninety days, as January to March is");
        }

        @Test
        void aProfileOpensOnTheLastTwelveMonths() {
            PartyProfileFilter filter = PartyProfileFilter.lastTwelveMonths(PartyKind.CUSTOMER, 7, LocalDate.of(2026, 9, 22));
            assertEquals(LocalDate.of(2025, 10, 1), filter.from());
            assertEquals(LocalDate.of(2026, 9, 22), filter.to());
        }

        @Test
        void aFilterThatExistsIsOneTheQueriesCanAnswer() {
            assertEquals(PartyProfileFilter.Problem.REVERSED, PartyProfileFilter.problem(MAR_31, JAN_1));
            assertEquals(PartyProfileFilter.Problem.MISSING, PartyProfileFilter.problem(null, JAN_1));
            assertThrows(IllegalArgumentException.class, () -> new PartyProfileFilter(PartyKind.CUSTOMER, 7, MAR_31, JAN_1));
            assertThrows(IllegalArgumentException.class, () -> new PartyProfileFilter(PartyKind.CUSTOMER, 0, JAN_1, MAR_31));
            assertThrows(IllegalArgumentException.class, () -> new PartyProfileFilter(null, 7, JAN_1, MAR_31));
        }
    }
}
