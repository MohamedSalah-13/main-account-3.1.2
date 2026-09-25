package com.hamza.account.features.offers;

import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.hamza.account.features.offers.OfferEngineTest.d;
import static com.hamza.account.features.offers.OfferEngineTest.offer;
import static com.hamza.account.features.offers.OfferEngineTest.run;
import static com.hamza.account.features.offers.OfferEngineTest.soap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The save refuses a sale whose lines say anything the engine, run again, does not. */
class OfferGuardTest {

    private final Offer tenPercent = offer(1, OfferKind.PERCENT, "10", null, OfferTarget.everything());
    private final OfferEngine.Result expected = run(List.of(soap(0, "3", "40")), tenPercent);
    private final OfferGuard.OfferNames names = id -> id == 7 ? "عرض قديم" : "?";

    private static String text(String key, String name) {
        return LanguageManager.getInstance().getString(key, name);
    }

    @Test
    void theEnginesAnswerPasses() {
        assertDoesNotThrow(() -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 1, d("12.00"), d("12.00"))), expected, names));
    }

    @Test
    @DisplayName("an offer the line claims that no longer reaches it - named by the id the line carries")
    void gone() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 7, d("12.00"), d("12.00"))), expected, names));
        assertEquals(text("offer.guard.error.gone", "عرض قديم"), refused.getMessage());
        assertThrows(BusinessRuleException.class, () -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 7, d("1.00"), d("1.00"))), OfferEngine.Result.none(), names));
    }

    @Test
    @DisplayName("an offer that has started since the screen last looked")
    void appeared() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, null, BigDecimal.ZERO, BigDecimal.ZERO)), expected, names));
        assertEquals(text("offer.guard.error.appeared", tenPercent.name()), refused.getMessage());
    }

    @Test
    @DisplayName("the same offer giving another figure")
    void changed() {
        assertThrows(BusinessRuleException.class, () -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 1, d("11.99"), d("11.99"))), expected, names));
    }

    @Test
    @DisplayName("the same figure over another count of units: the global limit counts units, so it is judged too")
    void changedQuantity() {
        assertThrows(BusinessRuleException.class, () -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 1, d("12.00"), d("12.00"), d("2.000"))), expected, names));
        assertDoesNotThrow(() -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 1, d("12.00"), d("12.00"), d("3.000"))), expected, names));
    }

    @Test
    @DisplayName("a manual discount beside the offer: the offer takes its place (ق-ع٨)")
    void manualBesideTheOffer() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> OfferGuard.require(
                List.of(new OfferGuard.Claim(0, 1, d("12.00"), d("15.00"))), expected, names));
        assertEquals(text("offer.guard.error.manual", tenPercent.name()), refused.getMessage());
    }

    @Test
    @DisplayName("a line nothing reaches may carry any manual discount it likes")
    void noOfferNoRule() {
        assertDoesNotThrow(() -> OfferGuard.require(
                List.of(new OfferGuard.Claim(3, null, BigDecimal.ZERO, d("9.00"))), expected, names));
    }

    @Test
    @DisplayName("a document the engine does not run on claims nothing")
    void none() {
        assertDoesNotThrow(() -> OfferGuard.requireNone(
                List.of(new OfferGuard.Claim(0, null, BigDecimal.ZERO, d("4.00"))), names));
        assertThrows(BusinessRuleException.class, () -> OfferGuard.requireNone(
                List.of(new OfferGuard.Claim(0, 7, d("4.00"), d("4.00"))), names));
    }
}
