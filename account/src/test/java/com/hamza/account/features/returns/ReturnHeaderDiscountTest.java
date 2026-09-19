package com.hamza.account.features.returns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnHeaderDiscountTest {

    @Test
    void aWholeReturnGivesBackTheWholeDiscount() {
        assertEquals(100.0, ReturnHeaderDiscount.shareFor(1000, 100, 1000));
    }

    @Test
    void aPartialReturnGivesBackItsShareByValue() {
        assertEquals(25.0, ReturnHeaderDiscount.shareFor(1000, 100, 250));
    }

    @Test
    void theShareIsRoundedAsMoney() {
        // 10 off 30, a third returned: 3.333... is 3.33 on a document.
        assertEquals(3.33, ReturnHeaderDiscount.shareFor(30, 10, 10));
    }

    @Test
    void noDiscountOnTheSourceIsNoneOnTheReturn() {
        assertEquals(0.0, ReturnHeaderDiscount.shareFor(1000, 0, 400));
    }

    @Test
    void aReturnCanNeverGiveBackMoreThanTheSourceTook() {
        // The source was edited down after the return was written: its total is now
        // smaller than the return's. The share stops at the whole discount.
        assertEquals(100.0, ReturnHeaderDiscount.shareFor(1000, 100, 1500));
    }

    @Test
    void aSourceWithNoTotalHasNothingToShare() {
        assertEquals(0.0, ReturnHeaderDiscount.shareFor(0, 100, 400));
    }

    @Test
    void aPiastreEitherWayIsTheSameShare() {
        assertTrue(ReturnHeaderDiscount.matches(3.34, 3.33));
        assertTrue(ReturnHeaderDiscount.matches(3.32, 3.33));
        assertFalse(ReturnHeaderDiscount.matches(3.35, 3.33));
        assertFalse(ReturnHeaderDiscount.matches(0, 100));
    }
}
