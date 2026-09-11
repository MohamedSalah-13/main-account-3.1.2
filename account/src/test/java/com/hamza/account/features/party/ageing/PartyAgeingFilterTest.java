package com.hamza.account.features.party.ageing;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the report may be asked for, and the one number that has to agree with another file. */
class PartyAgeingFilterTest {

    private static PartyAgeingFilter of(int page, int pageSize) {
        return new PartyAgeingFilter(PartyKind.CUSTOMER, LocalDate.of(2026, 9, 11),
                null, false, false, null, "", page, pageSize);
    }

    @Nested
    @DisplayName("The export's limit and the largest page are one number")
    class TheExportFits {

        /**
         * <b>They were two numbers in two files and they disagreed.</b> The cap was 500 and
         * {@code forExport} asked for 10,000, so every press of the export button threw out of
         * this constructor and reached the user as a reference code - while the screen itself
         * worked perfectly, which is why nothing noticed until the button was pressed.
         */
        @Test
        void theExportLimitIsAValidPageSize() {
            PartyAgeingFilter exported = of(0, PartyAgeingService.PRINT_LIMIT);

            assertEquals(PartyAgeingService.PRINT_LIMIT, exported.pageSize());
        }

        @Test
        @DisplayName("and what forExport actually builds is accepted")
        void whatTheServiceBuildsIsAccepted() {
            PartyAgeingFilter onScreen = PartyAgeingFilter.today(PartyKind.CUSTOMER);

            PartyAgeingFilter exported = onScreen.withPage(0)
                    .withPageSize(PartyAgeingService.PRINT_LIMIT);

            assertEquals(0, exported.page());
            assertEquals(PartyAgeingService.PRINT_LIMIT, exported.pageSize());
        }

        @Test
        void oneMoreThanTheLimitIsStillRefused() {
            assertThrows(IllegalArgumentException.class,
                    () -> of(0, PartyAgeingFilter.MAX_PAGE_SIZE + 1));
        }
    }

    @Nested
    @DisplayName("What the record refuses")
    class Refusals {

        /** An ageing report with no day is not a report - it has no bands and no balance. */
        @Test
        void aDayIsRequired() {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> new PartyAgeingFilter(PartyKind.CUSTOMER, null, null, false, false,
                            null, "", 0, 50));

            assertTrue(refused.getMessage().contains("as at a day"));
        }

        @Test
        void aKindIsRequired() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PartyAgeingFilter(null, LocalDate.now(), null, false, false,
                            null, "", 0, 50));
        }

        @ParameterizedTest(name = "page size {0}")
        @ValueSource(ints = {0, -1})
        void anImpossiblePageSize(int pageSize) {
            assertThrows(IllegalArgumentException.class, () -> of(0, pageSize));
        }

        @Test
        void aNegativePage() {
            assertThrows(IllegalArgumentException.class, () -> of(-1, 50));
        }
    }

    @Nested
    @DisplayName("Defaults and small conveniences")
    class Defaults {

        /** A debt report of rows that owe nothing is one nobody reads. */
        @Test
        void todayHidesSettledParties() {
            assertFalse(PartyAgeingFilter.today(PartyKind.CUSTOMER).includeSettled());
            assertFalse(PartyAgeingFilter.today(PartyKind.CUSTOMER).overdueOnly());
            assertEquals(LocalDate.now(), PartyAgeingFilter.today(PartyKind.CUSTOMER).asOf());
        }

        @Test
        @DisplayName("a wildcard in a name is escaped, not honoured")
        void theSearchEscapesItsWildcards() {
            PartyAgeingFilter filter = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.now(), null, false, false, null, "50%_!", 0, 50);

            assertEquals("%50!%!_!!%", filter.pattern());
        }

        @Test
        void aNumericSearchIsAlsoAnId() {
            assertEquals(164, new PartyAgeingFilter(PartyKind.CUSTOMER, LocalDate.now(), null,
                    false, false, null, "164", 0, 50).textAsId());
            assertEquals(0, new PartyAgeingFilter(PartyKind.CUSTOMER, LocalDate.now(), null,
                    false, false, null, "احمد", 0, 50).textAsId());
        }

        @Test
        void oneRowMoreThanThePageIsFetched() {
            assertEquals(51, of(0, 50).fetchSize());
            assertEquals(100, of(2, 50).offset());
        }

        @Test
        @DisplayName("a minimum balance of zero is a real floor, not the absence of one")
        void zeroIsNotNull() {
            PartyAgeingFilter noFloor = of(0, 50);
            PartyAgeingFilter zeroFloor = new PartyAgeingFilter(PartyKind.CUSTOMER,
                    LocalDate.now(), null, false, false, BigDecimal.ZERO, "", 0, 50);

            assertEquals(null, noFloor.minimumBalance());
            assertEquals(BigDecimal.ZERO, zeroFloor.minimumBalance());
        }
    }
}
