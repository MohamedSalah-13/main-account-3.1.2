package com.hamza.account.features.invoice;

import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceTenderTest {

    private static final InvoicePaymentTerms CASH_42_50 =
            InvoicePaymentTerms.preview(InvoiceType.CASH, 45, 2.5, 0);

    @Test
    void theChangeIsWhatWasHandedOverBeyondWhatIsDue() {
        InvoiceTender tender = InvoiceTender.of(CASH_42_50, new BigDecimal("100"));

        assertAmount("42.50", tender.due());
        assertAmount("57.50", tender.change());
        assertAmount("0", tender.remaining());
        assertTrue(tender.accepted());
    }

    /** The drawer keeps 42.50 of the 100; the invoice may not claim the treasury moved by 100. */
    @Test
    void whatIsPaidIsNeverMoreThanWhatIsDue() {
        assertAmount("42.50", InvoiceTender.of(CASH_42_50, new BigDecimal("100")).paid());
    }

    @Test
    void theExactAmountLeavesNoChange() {
        InvoiceTender tender = InvoiceTender.of(CASH_42_50, new BigDecimal("42.50"));

        assertAmount("42.50", tender.paid());
        assertAmount("0", tender.change());
        assertTrue(tender.accepted());
    }

    @Test
    void aCashInvoiceHandedLessThanItIsOwedIsShortByTheDifference() {
        InvoiceTender tender = InvoiceTender.of(CASH_42_50, new BigDecimal("40"));

        assertEquals(InvoiceTender.Problem.SHORT, tender.problem());
        assertAmount("2.50", tender.shortBy());
        assertFalse(tender.accepted());
    }

    @Test
    void nothingHandedOverIsShortByTheWholeAmount() {
        InvoiceTender tender = InvoiceTender.of(CASH_42_50, null);

        assertEquals(InvoiceTender.Problem.SHORT, tender.problem());
        assertAmount("42.50", tender.shortBy());
    }

    @Test
    void aDeferredInvoiceTakesWhatIsHandedOverAndLeavesTheRestOnAccount() {
        InvoicePaymentTerms deferred = InvoicePaymentTerms.preview(InvoiceType.DEFER, 42.5, 0, 0);
        InvoiceTender tender = InvoiceTender.of(deferred, new BigDecimal("20"));

        assertAmount("20", tender.paid());
        assertAmount("22.50", tender.remaining());
        assertAmount("0", tender.change());
        assertTrue(tender.accepted());
    }

    @Test
    void aDeferredInvoiceMayBePaidNothingNow() {
        InvoicePaymentTerms deferred = InvoicePaymentTerms.preview(InvoiceType.DEFER, 42.5, 0, 0);
        InvoiceTender tender = InvoiceTender.of(deferred, BigDecimal.ZERO);

        assertAmount("0", tender.paid());
        assertAmount("42.50", tender.remaining());
        assertTrue(tender.accepted());
    }

    @Test
    void aDeferredInvoiceHandedMoreIsSettledAndGivesChange() {
        InvoicePaymentTerms deferred = InvoicePaymentTerms.preview(InvoiceType.DEFER, 42.5, 0, 0);
        InvoiceTender tender = InvoiceTender.of(deferred, new BigDecimal("50"));

        assertAmount("42.50", tender.paid());
        assertAmount("7.50", tender.change());
        assertAmount("0", tender.remaining());
    }

    @Test
    void aNegativeAmountIsRefusedWhateverTheInvoice() {
        InvoicePaymentTerms deferred = InvoicePaymentTerms.preview(InvoiceType.DEFER, 42.5, 0, 0);

        assertEquals(InvoiceTender.Problem.NEGATIVE,
                InvoiceTender.of(deferred, new BigDecimal("-5")).problem());
        assertEquals(InvoiceTender.Problem.NEGATIVE,
                InvoiceTender.of(CASH_42_50, new BigDecimal("-5")).problem());
        assertAmount("0", InvoiceTender.of(deferred, new BigDecimal("-5")).paid());
    }

    @Test
    void suggestsTheNextRoundAmountEachNoteMakes() {
        assertAmounts(List.of("45", "50", "60", "100"), InvoiceTender.suggestions(new BigDecimal("42.50")));
    }

    /** An amount that is already round is offered as the exact amount, not as a suggestion. */
    @Test
    void anAmountThatIsAlreadyRoundIsNotSuggestedAgain() {
        assertAmounts(List.of("60", "100", "200"), InvoiceTender.suggestions(new BigDecimal("50")));
    }

    @Test
    void suggestsNothingForNothingDue() {
        assertTrue(InvoiceTender.suggestions(BigDecimal.ZERO).isEmpty());
        assertTrue(InvoiceTender.suggestions(null).isEmpty());
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static void assertAmounts(List<String> expected, List<BigDecimal> actual) {
        assertEquals(expected.size(), actual.size(), () -> "was " + actual);
        for (int i = 0; i < expected.size(); i++) {
            assertAmount(expected.get(i), actual.get(i));
        }
    }
}
