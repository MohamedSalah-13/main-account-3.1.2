package com.hamza.account.features.party.rfm;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerRfmFilterTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate MAR = LocalDate.of(2026, 3, 31);

    @Test
    void aPeriodMustBeWholeAndTheRightWayRound() {
        assertEquals(CustomerRfmFilter.Problem.MISSING, CustomerRfmFilter.problem(null, MAR));
        assertEquals(CustomerRfmFilter.Problem.REVERSED, CustomerRfmFilter.problem(MAR, JAN));
        assertEquals(CustomerRfmFilter.Problem.NONE, CustomerRfmFilter.problem(JAN, JAN));
        assertThrows(IllegalArgumentException.class, () -> filter(MAR, JAN, "", 0));
    }

    @Test
    void theTextIsTrimmedAndItsWildcardsAreEscaped() {
        CustomerRfmFilter filter = filter(JAN, MAR, "  50%_off!  ", 0);
        assertEquals("50%_off!", filter.text());
        assertEquals("%50!%!_off!!%", filter.pattern());
        assertEquals("", filter(JAN, MAR, null, 0).text());
    }

    @Test
    void noCashCustomerSettingExcludesNobody() {
        assertEquals(0, filter(JAN, MAR, "", -3).excludedParty());
        assertEquals(1, filter(JAN, MAR, "", 1).excludedParty());
    }

    @Test
    void aPageFetchesOneRowMoreThanItShows() {
        CustomerRfmFilter filter = filter(JAN, MAR, "", 0).withPage(2).withPageSize(20);
        assertEquals(21, filter.fetchSize());
        assertEquals(40, filter.offset());
        assertThrows(IllegalArgumentException.class, () -> filter.withPageSize(0));
        assertThrows(IllegalArgumentException.class, () -> filter.withPage(-1));
    }

    @Test
    void theDefaultIsTheProfilesTwelveMonths() {
        CustomerRfmFilter filter = CustomerRfmFilter.lastTwelveMonths(LocalDate.of(2026, 9, 22), 1);
        assertEquals(LocalDate.of(2025, 10, 1), filter.from());
        assertEquals(LocalDate.of(2026, 9, 22), filter.to());
        assertEquals(CustomerRfmOrder.SCORE, filter.order());
    }

    /** The order combo resolves these through a variable, which the message-key scan cannot see. */
    @Test
    void everyOrderHasALabelInAllThreeBundles() throws Exception {
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            java.util.Properties properties = new java.util.Properties();
            try (var in = java.nio.file.Files.newInputStream(
                    java.nio.file.Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(in);
            }
            for (CustomerRfmOrder order : CustomerRfmOrder.values()) {
                org.junit.jupiter.api.Assertions.assertTrue(properties.containsKey(order.messageKey()),
                        bundle + " has no " + order.messageKey());
            }
        }
    }

    private static CustomerRfmFilter filter(LocalDate from, LocalDate to, String text, int excluded) {
        return new CustomerRfmFilter(from, to, text, excluded, CustomerRfmOrder.SCORE, 0, 50);
    }
}
