package com.hamza.account.features.scalebarcode;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScaleBarcodeAmountsTest {

    private static final double MIN = 0.001;
    private static final double MAX = 99.999;

    @Nested
    @DisplayName("a scale that prints the weight")
    class Weight {

        @Test
        @DisplayName("the digits are grams, and the total is worked out from the price")
        void gramsBecomeKilos() throws Exception {
            ScaleBarcodeAmounts amounts =
                    ScaleBarcodeAmounts.of(1500, 20, ScaleBarcodeValueType.WEIGHT, MIN, MAX);

            assertEquals(1.5, amounts.weight());
            assertEquals(30.0, amounts.total());
        }

        @Test
        @DisplayName("the money is rounded to piastres, not left with a tail")
        void totalIsRoundedToTwoPlaces() throws Exception {
            ScaleBarcodeAmounts amounts =
                    ScaleBarcodeAmounts.of(333, 19.99, ScaleBarcodeValueType.WEIGHT, MIN, MAX);

            assertEquals(6.66, amounts.total());
        }
    }

    @Nested
    @DisplayName("a scale that prints the total price")
    class TotalPrice {

        @Test
        @DisplayName("the digits are piastres, and the weight is worked out from the price")
        void piastresBecomePounds() throws Exception {
            ScaleBarcodeAmounts amounts =
                    ScaleBarcodeAmounts.of(3000, 20, ScaleBarcodeValueType.TOTAL_PRICE, MIN, MAX);

            assertEquals(30.0, amounts.total());
            assertEquals(1.5, amounts.weight());
        }

        @Test
        @DisplayName("an item priced at zero is refused, not reported as a weight of Infinity")
        void aZeroPriceIsRefused() {
            assertThrows(UserValidationException.class,
                    () -> ScaleBarcodeAmounts.of(3000, 0, ScaleBarcodeValueType.TOTAL_PRICE, MIN, MAX));
        }
    }

    @Nested
    @DisplayName("the two readings are not interchangeable")
    class TheyDiffer {

        @Test
        void sameDigitsDifferentMeaning() throws Exception {
            ScaleBarcodeAmounts asWeight =
                    ScaleBarcodeAmounts.of(1500, 20, ScaleBarcodeValueType.WEIGHT, MIN, MAX);
            ScaleBarcodeAmounts asPrice =
                    ScaleBarcodeAmounts.of(1500, 20, ScaleBarcodeValueType.TOTAL_PRICE, MIN, MAX);

            assertEquals(30.0, asWeight.total());
            assertEquals(15.0, asPrice.total());
        }
    }

    @Nested
    class Limits {

        @Test
        void belowTheMinimum() {
            assertThrows(UserValidationException.class,
                    () -> ScaleBarcodeAmounts.of(0.5, 20, ScaleBarcodeValueType.WEIGHT, 1, MAX));
        }

        @Test
        void aboveTheMaximum() {
            assertThrows(UserValidationException.class,
                    () -> ScaleBarcodeAmounts.of(500000, 20, ScaleBarcodeValueType.WEIGHT, MIN, MAX));
        }
    }
}
