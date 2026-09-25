package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hamza.account.features.offers.OfferEngineTest.CARTON;
import static com.hamza.account.features.offers.OfferEngineTest.DAY;
import static com.hamza.account.features.offers.OfferEngineTest.DETERGENTS;
import static com.hamza.account.features.offers.OfferEngineTest.d;
import static com.hamza.account.features.offers.OfferEngineTest.offer;
import static com.hamza.account.features.offers.OfferEngineTest.soap;
import static com.hamza.account.features.offers.OfferEngineTest.soapCartons;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an offer says about one unit - the shelf label's price and the price-check screen's (phase E). */
class OfferPriceTagTest {

    private static Optional<OfferPriceTag> tag(OfferEngine.Line line, Offer... offers) {
        return OfferPriceTag.of(List.of(offers), line, DAY, 1);
    }

    @Test
    @DisplayName("10% off the detergents: a soap at 40 is tagged 36, whatever quantity the line was built with")
    void aPercentage() {
        OfferPriceTag tag = tag(soap(0, "7", "40"), offer(1, OfferKind.PERCENT, "10", null,
                OfferTarget.subGroup(DETERGENTS))).orElseThrow();
        assertTrue(tag.hasPrice());
        assertEquals(d("40.00"), tag.listPrice());
        assertEquals(d("36.00"), tag.offerPrice(), "one unit's price, not seven's");
    }

    @Test
    @DisplayName("of two that reach it, the one giving more: 5 off beats 10%; a higher priority beats both")
    void theBestOfTwo() {
        Offer tenPercent = offer(1, OfferKind.PERCENT, "10", null, OfferTarget.everything());
        Offer fiveOff = offer(2, OfferKind.AMOUNT, "5", null, OfferTarget.item(11));
        assertEquals(d("35.00"), tag(soap(0, "1", "40"), tenPercent, fiveOff).orElseThrow().offerPrice());
        Offer preferred = OfferEngineTest.withPriority(tenPercent, 5);
        assertEquals(1, tag(soap(0, "1", "40"), preferred, fiveOff).orElseThrow().offer().id());
    }

    @Test
    @DisplayName("a price written for the carton reaches the carton, not the piece")
    void aUnitsOwnPrice() {
        Offer cartonAt400 = offer(1, OfferKind.PRICE, "400", CARTON, OfferTarget.item(11));
        assertTrue(tag(soap(0, "1", "40"), cartonAt400).isEmpty());
        assertEquals(d("400.00"), tag(soapCartons(0, "1", "450"), cartonAt400).orElseThrow().offerPrice());
    }

    @Test
    @DisplayName("3 for 100 changes no unit's price: told in words, with no price for a label")
    void aQuantityOfferIsTold() {
        Offer threeFor100 = OfferEngineQuantityTest.quantityPrice(1, "3", "100", null, OfferTarget.item(11));
        OfferPriceTag tag = tag(soap(0, "1", "40"), threeFor100).orElseThrow();
        assertFalse(tag.hasPrice());
        assertNull(tag.offerPrice());
        // And where a price offer reaches the unit too, the price is what one unit is tagged with.
        Offer fiveOff = offer(2, OfferKind.AMOUNT, "5", null, OfferTarget.item(11));
        assertEquals(d("35.00"), tag(soap(0, "1", "40"), threeFor100, fiveOff).orElseThrow().offerPrice());
    }

    @Test
    @DisplayName("a bundle is told on each of its components; an invoice offer on no item at all")
    void bundlesAndInvoices() {
        Offer bundle = OfferEngineBundleTest.bundle(1, "100", OfferTarget.component(11, null, d("2")),
                OfferTarget.component(12, null, d("1")));
        assertEquals(1, tag(soap(0, "1", "40"), bundle).orElseThrow().offer().id());
        Offer invoice = OfferEngineBundleTest.invoicePercent(2, "1000", "5", OfferTarget.everything());
        assertTrue(tag(soap(0, "1", "40"), invoice).isEmpty(), "it would stand beside every item in the shop");
    }

    @Test
    @DisplayName("an offer for another tier, one not switched on, and one that gives nothing are no tag")
    void nothing() {
        Offer retailOnly = new Offer(1, "قطاعي", OfferKind.PERCENT, OfferStatus.ACTIVE, DAY.minusDays(1), null, null,
                0, d("10"), null, null, null, null, List.of(OfferTarget.everything()), Set.of(3), null);
        assertTrue(tag(soap(0, "1", "40"), retailOnly).isEmpty());
        Offer draft = new Offer(2, "مسودة", OfferKind.PERCENT, OfferStatus.DRAFT, DAY.minusDays(1), null, null, 0,
                d("10"), null, null, null, null, List.of(OfferTarget.everything()), Set.of(), null);
        assertTrue(tag(soap(0, "1", "40"), draft).isEmpty());
        Offer above = offer(3, OfferKind.PRICE, "50", null, OfferTarget.item(11));
        assertTrue(tag(soap(0, "1", "40"), above).isEmpty(), "a price offer never raises a price");
    }
}
