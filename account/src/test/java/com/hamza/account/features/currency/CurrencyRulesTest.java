package com.hamza.account.features.currency;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.OLD_LIRA;
import static com.hamza.account.features.currency.CurrencyFixtures.SAR;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyRulesTest {

    private static final List<Currency> EXISTING = List.of(EGP, SAR, USD, OLD_LIRA);

    private static String refusal(CurrencyDraft draft) {
        return assertThrows(UserValidationException.class,
                () -> CurrencyRules.requireValid(draft, EXISTING)).getMessage();
    }

    private static CurrencyDraft draft(String code, String name, String symbol, int decimals) {
        return new CurrencyDraft(0, code, name, symbol, null, decimals, true, 0);
    }

    private static CurrencyDraft latin(String latinSymbol) {
        return new CurrencyDraft(0, "EUR", "يورو", "€", latinSymbol, 2, true, 0);
    }

    @Nested
    @DisplayName("a currency's own fields")
    class Fields {

        @Test
        @DisplayName("a new currency with an ISO code, a name, a symbol and 0-3 places is accepted")
        void accepted() {
            assertDoesNotThrow(() -> CurrencyRules.requireValid(draft("KWD", "دينار كويتي", "د.ك", 3), EXISTING));
            assertDoesNotThrow(() -> CurrencyRules.requireValid(draft("JPY", "ين", "¥", 0), EXISTING));
        }

        @Test
        @DisplayName("the code is typed in any case and stored in capitals")
        void codeIsUpperCased() {
            assertEquals("EUR", draft(" eur ", "يورو", "€", 2).code());
        }

        @Test
        @DisplayName("the code is exactly three Latin letters - the CHECK's own pattern")
        void codeShape() {
            assertEquals("currency.error.code", refusal(draft("EU", "يورو", "€", 2)));
            assertEquals("currency.error.code", refusal(draft("EURO", "يورو", "€", 2)));
            assertEquals("currency.error.code", refusal(draft("E1R", "يورو", "€", 2)));
            assertEquals("currency.error.code", refusal(draft("يور", "يورو", "€", 2)));
            assertEquals("currency.error.code", refusal(draft("", "يورو", "€", 2)));
        }

        @Test
        @DisplayName("a name and a symbol are required, within their columns")
        void nameAndSymbol() {
            assertEquals("currency.error.name", refusal(draft("EUR", "  ", "€", 2)));
            assertEquals("currency.error.name.length", refusal(draft("EUR", "ي".repeat(51), "€", 2)));
            assertEquals("currency.error.symbol", refusal(draft("EUR", "يورو", "", 2)));
            assertEquals("currency.error.symbol.length", refusal(draft("EUR", "يورو", "€".repeat(11), 2)));
        }

        @Test
        @DisplayName("the English symbol is optional, within its column, and has no Arabic letter")
        void latinSymbol() {
            assertDoesNotThrow(() -> CurrencyRules.requireValid(latin("€"), EXISTING));
            assertEquals(null, latin("   ").latinSymbol(), "a blank box is no symbol, not a symbol of spaces");
            assertDoesNotThrow(() -> CurrencyRules.requireValid(latin("   "), EXISTING));
            assertEquals("currency.error.latin.symbol.length", refusal(latin("E".repeat(11))));
            assertEquals("currency.error.latin.symbol.script", refusal(latin("ج.م")));
            assertEquals("currency.error.latin.symbol.script", refusal(latin("L.ج")));
        }

        @Test
        @DisplayName("the places run from 0 to 3, and the order is not negative")
        void placesAndOrder() {
            assertEquals("currency.error.decimals", refusal(draft("EUR", "يورو", "€", 4)));
            assertEquals("currency.error.decimals", refusal(draft("EUR", "يورو", "€", -1)));
            assertEquals("currency.error.sort", refusal(new CurrencyDraft(0, "EUR", "يورو", "€", null, 2, true, -1)));
        }
    }

    @Nested
    @DisplayName("against the currencies that exist")
    class AgainstTheOthers {

        @Test
        @DisplayName("a code or a name another currency has is refused, whatever its case")
        void taken() {
            assertEquals("currency.error.code.taken", refusal(draft("usd", "دولار آخر", "$", 2)));
            assertEquals("currency.error.name.taken", refusal(draft("EUR", "ريال سعودي", "€", 2)));
        }

        @Test
        @DisplayName("a currency keeps its own code and name when it is edited")
        void editingKeepsItsOwn() {
            assertDoesNotThrow(() -> CurrencyRules.requireValid(
                    new CurrencyDraft(USD.id(), "USD", USD.name(), "US$", null, 2, true, 9), EXISTING));
        }

        @Test
        @DisplayName("an edit of a currency that is not there is refused")
        void editingNothing() {
            assertEquals("currency.error.not.found",
                    refusal(new CurrencyDraft(99, "EUR", "يورو", "€", null, 2, true, 0)));
        }

        @Test
        @DisplayName("the base cannot be stopped - every amount in the books is in it")
        void baseIsNotStopped() {
            assertEquals("currency.error.base.stop",
                    refusal(CurrencyDraft.switching(EGP, false)));
            assertDoesNotThrow(() -> CurrencyRules.requireValid(CurrencyDraft.switching(SAR, false), EXISTING));
        }
    }

    @Nested
    @DisplayName("the base")
    class Base {

        @Test
        @DisplayName("moves only while no rate is recorded: before that it is a correction, after it a relabel")
        void lockedOnceARateExists() {
            assertDoesNotThrow(() -> CurrencyRules.requireCanBecomeBase(SAR, 0));
            assertEquals("currency.error.base.locked", assertThrows(UserValidationException.class,
                    () -> CurrencyRules.requireCanBecomeBase(SAR, 1)).getMessage());
        }

        @Test
        @DisplayName("a stopped currency or none at all cannot become it")
        void onlyAnActiveOne() {
            assertEquals("currency.error.base.inactive", assertThrows(UserValidationException.class,
                    () -> CurrencyRules.requireCanBecomeBase(OLD_LIRA, 0)).getMessage());
            assertEquals("currency.error.not.found", assertThrows(UserValidationException.class,
                    () -> CurrencyRules.requireCanBecomeBase(null, 0)).getMessage());
        }

        @Test
        @DisplayName("is never deleted; any other currency is left to the delete registry")
        void notDeleted() {
            assertEquals("currency.error.base.delete", assertThrows(UserValidationException.class,
                    () -> CurrencyRules.requireDeletable(EGP)).getMessage());
            assertDoesNotThrow(() -> CurrencyRules.requireDeletable(USD));
        }
    }
}
