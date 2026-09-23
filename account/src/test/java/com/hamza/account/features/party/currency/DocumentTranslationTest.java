package com.hamza.account.features.party.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.JPY;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.KWD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A document's header translated into its party's currency (docs/currency-plan.md §14 ق-ج٣). What matters
 * most is that the rounding leaves nothing behind: a cash invoice owes nothing in the party's currency,
 * exactly as it owes nothing in the base.
 */
class DocumentTranslationTest {

    @Test
    @DisplayName("a deferred invoice: every figure divided by the rate, rounded to the currency's places")
    void aDeferredInvoice() {
        DocumentTranslation t = DocumentTranslation.of(bd("4850.00"), bd("0.00"), bd("0.00"), bd("48.5"), USD);

        assertEquals(bd("100.00"), t.total());
        assertEquals(bd("0.00"), t.discount());
        assertEquals(bd("0.00"), t.paid());
        assertEquals(bd("100.00"), t.remainder());
        assertEquals(bd("48.5"), t.rate());
    }

    @Test
    @DisplayName("a cash invoice leaves exactly zero in the party's currency, whatever the rounding")
    void aCashInvoiceLeavesNothing() {
        // 990 / 48.37 = 20.4672..., and the total and discount round on their own - translated one by one
        // the three would leave a cent on the account.
        DocumentTranslation t = DocumentTranslation.of(bd("1000.00"), bd("10.00"), bd("990.00"), bd("48.37"), USD);

        assertEquals(0, t.remainder().signum(), t.toString());
        assertEquals(t.net(), t.paid());
        assertEquals(bd("20.67"), t.total());
        assertEquals(bd("20.47"), t.net());
    }

    @Test
    @DisplayName("the net is the translated net, and the discount is what the total is above it")
    void theNetIsTranslatedOnce() {
        DocumentTranslation t = DocumentTranslation.of(bd("100.00"), bd("33.33"), bd("20.00"), bd("3"), USD);

        assertEquals(bd("33.33"), t.total());
        assertEquals(bd("22.22"), t.net(), "66.67 / 3 = 22.2233");
        assertEquals(bd("11.11"), t.discount());
        assertEquals(bd("15.56"), t.remainder(), "46.67 / 3 = 15.5567");
        assertEquals(bd("6.66"), t.paid(), "the net less what the cash did not cover");
    }

    @Test
    @DisplayName("a Kuwaiti dinar keeps three places, a yen none")
    void theCurrencysOwnPlaces() {
        assertEquals(bd("6.231"),
                DocumentTranslation.of(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, bd("160.5"), KWD).total());
        assertEquals(bd("3226"),
                DocumentTranslation.of(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, bd("0.31"), JPY).total());
    }

    @Test
    @DisplayName("a return is translated as it is stored - its figures positive, the view negates them")
    void aReturnIsTranslatedAsStored() {
        DocumentTranslation t = DocumentTranslation.of(bd("970.00"), bd("0.00"), bd("485.00"), bd("48.5"), USD);

        assertEquals(bd("20.00"), t.total());
        assertEquals(bd("10.00"), t.paid());
        assertEquals(bd("10.00"), t.remainder());
    }
}
