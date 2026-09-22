package com.hamza.account.features.party.rfm;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerRfmRowTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    @Test
    void theValueIsWhatWasSoldLessWhatCameBack() {
        CustomerRfmRow row = row("1000.00", "150.00", "850.00", 5, 3, 4);
        assertEquals("5-3-4", row.scores());
        assertEquals(12, row.totalScore());

        assertThrows(IllegalArgumentException.class, () -> row("1000.00", "150.00", "1000.00", 5, 3, 4));
    }

    @Test
    void aScoreIsOneToFive() {
        assertThrows(IllegalArgumentException.class, () -> row("0", "0", "0", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> row("0", "0", "0", 1, 6, 1));
    }

    /** A customer who returned more in the period than they bought has a negative value, not an error. */
    @Test
    void aPeriodOfReturnsIsANegativeValue() {
        assertEquals(new BigDecimal("-200.00"), row("0", "200.00", "-200.00", 1, 1, 1).net());
    }

    private static CustomerRfmRow row(String sold, String returned, String net, int r, int f, int m) {
        return new CustomerRfmRow(7, "أحمد", DAY, 21, 3, new BigDecimal(sold), new BigDecimal(returned),
                new BigDecimal(net), r, f, m);
    }
}
