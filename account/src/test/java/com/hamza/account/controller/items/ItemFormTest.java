package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ItemForm} holds no {@code javafx.scene.control} reference, so this runs
 * as a plain unit test with no JavaFX toolkit - the same reason
 * {@code StockLevelTest} does not need one.
 */
class ItemFormTest {

    @Nested
    @DisplayName("load / applyTo round trip")
    class RoundTrip {

        @Test
        @DisplayName("a loaded item survives applyTo unchanged")
        void loadThenApplyPreservesValues() {
            var saved = new ItemsModel();
            saved.setBarcode("12345");
            saved.setNameItem("Widget");
            saved.setBuyPrice(10.0);
            saved.setSelPrice1(15.0);
            saved.setSelPrice2(14.0);
            saved.setSelPrice3(13.0);
            saved.setMini_quantity(2.0);
            saved.setFirstBalanceForStock(50.0);
            saved.setActiveItem(false);
            saved.setHasValidate(true);
            saved.setNumberValidityDays(30);
            saved.setAlertDaysBeforeExpiry(5);

            var form = new ItemForm();
            form.load(saved);

            var rebuilt = new ItemsModel();
            form.applyTo(rebuilt);

            assertEquals(saved.getBarcode(), rebuilt.getBarcode());
            assertEquals(saved.getNameItem(), rebuilt.getNameItem());
            assertEquals(saved.getBuyPrice(), rebuilt.getBuyPrice());
            assertEquals(saved.getSelPrice1(), rebuilt.getSelPrice1());
            assertEquals(saved.getSelPrice2(), rebuilt.getSelPrice2());
            assertEquals(saved.getSelPrice3(), rebuilt.getSelPrice3());
            assertEquals(saved.getMini_quantity(), rebuilt.getMini_quantity());
            assertEquals(saved.getFirstBalanceForStock(), rebuilt.getFirstBalanceForStock());
            assertEquals(saved.isActiveItem(), rebuilt.isActiveItem());
            assertEquals(saved.isHasValidate(), rebuilt.isHasValidate());
            assertEquals(saved.getNumberValidityDays(), rebuilt.getNumberValidityDays());
            assertEquals(saved.getAlertDaysBeforeExpiry(), rebuilt.getAlertDaysBeforeExpiry());
        }

        @Test
        @DisplayName("the barcode and name are trimmed on the way into the model")
        void applyToTrimsBarcodeAndName() {
            var form = new ItemForm();
            form.setBarcode("  12345  ");
            form.setName("  Widget  ");
            form.setBuyPrice("10");
            form.setSelPrice1("15");

            var model = new ItemsModel();
            form.applyTo(model);

            assertEquals("12345", model.getBarcode());
            assertEquals("Widget", model.getNameItem());
        }

        @Test
        @DisplayName("a blank price field defaults to zero rather than failing")
        void applyToDefaultsBlankNumbersToZero() {
            var form = new ItemForm();
            form.setBarcode("1");
            form.setName("Widget");
            form.setBuyPrice("10");
            form.setSelPrice1("15");
            // selPrice2/3, miniQuantity and firstBalance left at their "" default

            var model = new ItemsModel();
            form.applyTo(model);

            assertEquals(0.0, model.getSelPrice2());
            assertEquals(0.0, model.getSelPrice3());
            assertEquals(0.0, model.getMini_quantity());
            assertEquals(0.0, model.getFirstBalanceForStock());
        }

        @Test
        @DisplayName("a corrupted validity-day field is read as zero, not thrown")
        void applyToToleratesUnparsableValidityDays() {
            var form = new ItemForm();
            form.setBarcode("1");
            form.setName("Widget");
            form.setBuyPrice("10");
            form.setSelPrice1("15");
            form.setValidityDays("not a number");
            form.setAlertBeforeExpiry("");

            var model = new ItemsModel();
            form.applyTo(model);

            assertEquals(0, model.getNumberValidityDays());
            assertEquals(0, model.getAlertDaysBeforeExpiry());
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("blanks the fields a saved item leaves behind")
        void resetBlanksEnteredFields() {
            var form = new ItemForm();
            form.setBarcode("12345");
            form.setName("Widget");
            form.setBuyPrice("10");
            form.setSelPrice1("15");
            form.setSelPrice2("14");
            form.setSelPrice3("13");
            form.setMiniQuantity("2");
            form.setFirstBalance("50");

            form.reset();

            assertEquals("", form.getBarcode());
            assertEquals("", form.getName());
            assertEquals("", form.getBuyPrice());
            assertEquals("", form.getSelPrice1());
            assertEquals("", form.getSelPrice2());
            assertEquals("", form.getSelPrice3());
            assertEquals("", form.getMiniQuantity());
            assertEquals("", form.getFirstBalance());
        }

        @Test
        @DisplayName("leaves active, hasValidate and the validity days alone")
        void resetDoesNotTouchTheOtherFields() {
            var form = new ItemForm();
            form.setActive(false);
            form.setHasValidate(true);
            form.setValidityDays("30");
            form.setAlertBeforeExpiry("5");

            form.reset();

            assertFalse(form.isActive());
            assertTrue(form.isHasValidate());
            assertEquals("30", form.getValidityDays());
            assertEquals("5", form.getAlertBeforeExpiry());
        }
    }

    @Nested
    @DisplayName("validation predicates")
    class Validation {

        @ParameterizedTest(name = "name \"{0}\" is blank: {1}")
        @CsvSource({
                "'', true",
                "'   ', true",
                "Widget, false",
        })
        void isNameBlank(String name, boolean expected) {
            var form = new ItemForm();
            form.setName(name);
            assertEquals(expected, form.isNameBlank());
        }

        @ParameterizedTest(name = "barcode \"{0}\" is blank: {1}")
        @CsvSource({
                "'', true",
                "0, true",
                "'   ', true",
                "12345, false",
        })
        void isBarcodeBlank(String barcode, boolean expected) {
            var form = new ItemForm();
            form.setBarcode(barcode);
            assertEquals(expected, form.isBarcodeBlank());
        }

        @Test
        @DisplayName("a barcode over 14 characters is too long")
        void isBarcodeTooLong() {
            var form = new ItemForm();
            form.setBarcode("123456789012345");
            assertTrue(form.isBarcodeTooLong());

            form.setBarcode("12345678901234");
            assertFalse(form.isBarcodeTooLong());
        }

        @ParameterizedTest(name = "buy {0}, sell {1} -> not above buy: {2}")
        @CsvSource({
                "10, 15, false",
                "10, 10, true",
                "10, 5,  true",
        })
        void isSellPriceNotAboveBuy(String buy, String sell, boolean expected) {
            var form = new ItemForm();
            form.setBuyPrice(buy);
            form.setSelPrice1(sell);
            assertEquals(expected, form.isSellPriceNotAboveBuy());
        }

        @Test
        @DisplayName("a buy price of zero typed with two decimals is still not positive")
        void isBuyPriceNotPositiveIsNumericNotTextual() {
            // The bug this guards: comparing "0.00" to "0.0" as text says 0.00 is
            // greater, because it is the longer string. Parsing to a number first
            // means the same zero reads as zero regardless of how it was typed.
            var form = new ItemForm();
            form.setBuyPrice("0.00");
            assertTrue(form.isBuyPriceNotPositive());
        }
    }

    @Nested
    @DisplayName("incompleteProperty")
    class Incomplete {

        @Test
        @DisplayName("is true until both a name and a positive buy price are entered")
        void reactsToNameAndBuyPrice() {
            var form = new ItemForm();
            var incomplete = form.incompleteProperty();

            assertTrue(incomplete.get());

            form.setName("Widget");
            assertTrue(incomplete.get(), "buy price is still missing");

            form.setBuyPrice("10");
            assertFalse(incomplete.get());

            form.setBuyPrice("0");
            assertTrue(incomplete.get(), "buy price dropped back to zero");
        }
    }
}
