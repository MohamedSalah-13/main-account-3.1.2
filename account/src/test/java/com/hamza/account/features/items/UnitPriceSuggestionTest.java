package com.hamza.account.features.items;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitPriceSuggestionTest {

    @Nested
    @DisplayName("the figure")
    class Figure {

        @Test
        @DisplayName("is the item's price times what the unit holds")
        void scalesByTheFactor() {
            assertEquals(240, UnitPriceSuggestion.forFactor(20, 12));
        }

        @Test
        @DisplayName("follows a fractional factor - half a kilo is half the price")
        void fractionalFactor() {
            assertEquals(10, UnitPriceSuggestion.forFactor(20, 0.5));
        }

        @Test
        @DisplayName("is money, so it is rounded to two places")
        void rounded() {
            assertEquals(33.33, UnitPriceSuggestion.forFactor(3.3333, 10));
        }
    }

    @Nested
    @DisplayName("saying nothing")
    class Silence {

        @Test
        @DisplayName("an item with no price yet has nothing to scale")
        void noItemPrice() {
            assertEquals(0, UnitPriceSuggestion.forFactor(0, 12));
        }

        @Test
        @DisplayName("a factor of zero is not a unit - and would suggest a price of zero")
        void noFactor() {
            assertEquals(0, UnitPriceSuggestion.forFactor(20, 0));
        }

        @Test
        @DisplayName("a negative on either side is refused rather than shown as a price")
        void negatives() {
            assertEquals(0, UnitPriceSuggestion.forFactor(-20, 12));
            assertEquals(0, UnitPriceSuggestion.forFactor(20, -12));
        }
    }

    @Nested
    @DisplayName("how it reads")
    class Reading {

        @Test
        @DisplayName("a whole number loses its decimal point")
        void wholeNumber() {
            assertEquals("240", UnitPriceSuggestion.plain(240.0));
        }

        @Test
        @DisplayName("a real fraction keeps it")
        void fraction() {
            assertEquals("12.5", UnitPriceSuggestion.plain(12.5));
        }
    }
}
