package com.hamza.account.features.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure decision logic - no database, no JavaFX, in the shape of {@code InvoiceStockGuardTest}. */
class ReturnEligibilityTest {

    private static final int ITEM = 42;

    private static ReturnableDocument sourceWith(double soldQuantity) {
        return new ReturnableDocument(
                com.hamza.account.document.DocumentType.SALES, 1, Map.of(ITEM, soldQuantity));
    }

    @Nested
    @DisplayName("the default policy - a return may not exceed its source")
    class DefaultPolicy {

        @Test
        void returningLessThanWhatWasSoldIsAllowed() {
            var decision = ReturnEligibility.check(sourceWith(5), Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 3)), ReturnPolicy.DEFAULT);

            assertTrue(decision.isAllowed());
        }

        @Test
        void returningExactlyWhatWasSoldIsAllowed() {
            var decision = ReturnEligibility.check(sourceWith(5), Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 5)), ReturnPolicy.DEFAULT);

            assertTrue(decision.isAllowed());
        }

        @Test
        void returningMoreThanWasSoldIsRefused() {
            var decision = ReturnEligibility.check(sourceWith(5), Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 6)), ReturnPolicy.DEFAULT);

            assertFalse(decision.isAllowed());
            var refused = assertInstanceOf(ReturnEligibility.Decision.Refused.class, decision);
            assertTrue(refused.message().contains(String.valueOf(ITEM)), refused.message());
        }

        @Test
        void anItemNotOnTheSourceInvoiceIsAlwaysRefused() {
            // Even a tiny quantity: the item was never sold on this invoice at all.
            ReturnableDocument nothingSold = new ReturnableDocument(
                    com.hamza.account.document.DocumentType.SALES, 1, Map.of());
            var decision = ReturnEligibility.check(nothingSold, Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 1)), ReturnPolicy.DEFAULT);

            assertFalse(decision.isAllowed());
        }

        @Test
        void whatAnEarlierReturnAlreadyTookReducesWhatIsLeft() {
            // 5 sold, 3 already returned by another return against the same source: 2 left.
            var decision = ReturnEligibility.check(sourceWith(5), Map.of(ITEM, 3.0),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 2)), ReturnPolicy.DEFAULT);
            assertTrue(decision.isAllowed());

            var refused = ReturnEligibility.check(sourceWith(5), Map.of(ITEM, 3.0),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 3)), ReturnPolicy.DEFAULT);
            assertFalse(refused.isAllowed());
        }

        @Test
        void twoLinesOfTheSameItemAreSummedBeforeChecking() {
            // 3 + 3 = 6 against 5 sold - refused even though no single line exceeds it.
            var decision = ReturnEligibility.check(sourceWith(5), Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 3),
                            new ReturnEligibility.LineQuantity(ITEM, 3)),
                    ReturnPolicy.DEFAULT);
            assertFalse(decision.isAllowed());
        }

        @Test
        void aTinyOverageWithinFloatingPointNoiseIsAllowed() {
            var decision = ReturnEligibility.check(sourceWith(5.0000001), Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 5.0000002)),
                    ReturnPolicy.DEFAULT);
            assertTrue(decision.isAllowed());
        }
    }

    @Nested
    @DisplayName("a policy that allows exceeding the source")
    class PermissivePolicy {

        private static final ReturnPolicy ALLOW_OVER = new ReturnPolicy(true, false, 0);

        @Test
        void returningMoreThanWasSoldIsAllowed() {
            var decision = ReturnEligibility.check(sourceWith(5), Map.of(),
                    List.of(new ReturnEligibility.LineQuantity(ITEM, 50)), ALLOW_OVER);
            assertTrue(decision.isAllowed());
        }

        @Test
        void anItemNotOnTheSourceIsStillRefused() {
            // The policy only relaxes the quantity check; it was never sold at all,
            // so there is nothing for this source invoice to say about it.
            var decision = ReturnEligibility.check(
                    new ReturnableDocument(com.hamza.account.document.DocumentType.SALES, 1, Map.of()),
                    Map.of(), List.of(new ReturnEligibility.LineQuantity(ITEM, 1)), ALLOW_OVER);
            assertFalse(decision.isAllowed());
        }
    }

    @Test
    void anEmptyProposalIsTriviallyAllowed() {
        assertTrue(ReturnEligibility.check(sourceWith(5), Map.of(), List.of(), ReturnPolicy.DEFAULT)
                .isAllowed());
    }

    @Test
    void remainingOnTheDocumentItselfIsSoldMinusAlreadyReturned() {
        ReturnableDocument source = sourceWith(10);
        assertEquals(10.0, source.remaining(ITEM, Map.of()));
        assertEquals(4.0, source.remaining(ITEM, Map.of(ITEM, 6.0)));
        assertEquals(0.0, source.remaining(999, Map.of()));
    }
}
