package com.hamza.account.features.invoice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnQuantityInputTest {

    @Test
    void readsAnOrdinaryQuantity() {
        assertEquals(2.5, ReturnQuantityInput.within("2.5", 10));
    }

    @Test
    void readsTheDigitsAnArabicKeyboardProduces() {
        // Double.parseDouble read this as nothing, and the line was left off the return.
        assertEquals(3.0, ReturnQuantityInput.within("٣", 10));
    }

    @Test
    void holdsTheQuantityToWhatIsLeft() {
        assertEquals(4.0, ReturnQuantityInput.within("9", 4));
    }

    @Test
    void blankAndNonsenseAndNegativeAreAllNotReturned() {
        assertEquals(0.0, ReturnQuantityInput.within("", 4));
        assertEquals(0.0, ReturnQuantityInput.within(null, 4));
        assertEquals(0.0, ReturnQuantityInput.within("abc", 4));
        assertEquals(0.0, ReturnQuantityInput.within("-2", 4));
    }

    @Test
    void nothingLeftMeansNothingToReturn() {
        assertEquals(0.0, ReturnQuantityInput.within("1", 0));
    }
}
