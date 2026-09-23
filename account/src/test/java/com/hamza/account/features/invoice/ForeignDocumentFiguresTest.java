package com.hamza.account.features.invoice;

import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A document typed in a foreign currency, its header worked out in the base (docs/currency-plan.md §15
 * ق-د٤): the total from the lines' own base figures, the discount and the cash converted, and nothing
 * left behind by the rounding.
 */
class ForeignDocumentFiguresTest {

    private static final BigDecimal RATE = new BigDecimal("48.37");

    @Test
    @DisplayName("an amount converts half up to money")
    void anAmountConverts() {
        assertEquals(new BigDecimal("100.13"), ForeignDocumentFigures.toBase(new BigDecimal("2.07"), RATE));
        assertEquals(new BigDecimal("0.00"), ForeignDocumentFigures.toBase(null, RATE));
    }

    @Test
    @DisplayName("the total is the lines' base figures added up, never the typed total converted")
    void theTotalIsTheLinesOwn() {
        // 20.61 typed; the lines in the base came to 996.90, not 20.61 x 48.37 = 996.91.
        InvoicePaymentTerms base = ForeignDocumentFigures.header(
                typed(InvoiceType.DEFER, "20.61", "0", "0"), new BigDecimal("996.90"), RATE, null);
        assertEquals(new BigDecimal("996.90"), base.subtotalAmount());
        assertEquals(new BigDecimal("996.90"), base.remainingAmount());
    }

    @Test
    @DisplayName("the discount and the cash are converted, and what is left on the account is what they did not cover")
    void theDiscountAndTheCashConvert() {
        InvoicePaymentTerms base = ForeignDocumentFigures.header(
                typed(InvoiceType.DEFER, "20.61", "0.61", "5.00"), new BigDecimal("996.90"), RATE, null);
        assertEquals(new BigDecimal("29.51"), base.discountAmount());
        assertEquals(new BigDecimal("967.39"), base.netAmount());
        assertEquals(new BigDecimal("241.85"), base.paidAmount());
        assertEquals(new BigDecimal("725.54"), base.remainingAmount());
    }

    @Test
    @DisplayName("paid in full is paid the net exactly - a cash invoice leaves nothing on the account in the base")
    void paidInFullLeavesNothing() {
        InvoicePaymentTerms base = ForeignDocumentFigures.header(
                typed(InvoiceType.CASH, "20.61", "0.61", "20.00"), new BigDecimal("996.90"), RATE, null);
        assertEquals(base.netAmount(), base.paidAmount());
        assertEquals(0, base.remainingAmount().signum());
    }

    @Test
    @DisplayName("nothing paid is nothing paid, and all of it off is the whole base total off")
    void theEnds() {
        InvoicePaymentTerms unpaid = ForeignDocumentFigures.header(
                typed(InvoiceType.DEFER, "20.61", "0", "0"), new BigDecimal("996.90"), RATE, null);
        assertEquals(new BigDecimal("0.00"), unpaid.paidAmount());

        InvoicePaymentTerms allOff = ForeignDocumentFigures.header(
                typed(InvoiceType.CASH, "20.61", "20.61", "0"), new BigDecimal("996.90"), RATE, null);
        assertEquals(new BigDecimal("996.90"), allOff.discountAmount(), "not 20.61 x 48.37 = 996.91");
        assertEquals(0, allOff.netAmount().signum());
        assertEquals(0, allOff.paidAmount().signum());
    }

    @Test
    @DisplayName("the cash converted is never more than the net, whatever the rounding did")
    void theCashIsHeldToTheNet() {
        // 20.60 of 20.61 paid converts to 996.42 against a base net of 996.40.
        InvoicePaymentTerms base = ForeignDocumentFigures.header(
                typed(InvoiceType.DEFER, "20.61", "0", "20.60"), new BigDecimal("996.40"), RATE, null);
        assertEquals(new BigDecimal("996.40"), base.paidAmount());
        assertEquals(0, base.remainingAmount().signum());
    }

    @Test
    @DisplayName("a return naming its invoice takes the base share of that invoice's discount, not its own converted")
    void aReturnTakesTheSourcesShare() {
        InvoicePaymentTerms base = ForeignDocumentFigures.header(
                typed(InvoiceType.DEFER, "5.16", "0.52", "0"), new BigDecimal("250.00"), RATE,
                new BigDecimal("25.00"));
        assertEquals(new BigDecimal("25.00"), base.discountAmount(), "0.52 x 48.37 would be 25.15");
        assertEquals(new BigDecimal("225.00"), base.netAmount());
    }

    private static InvoicePaymentTerms typed(InvoiceType type, String subtotal, String discount, String paid) {
        BigDecimal net = new BigDecimal(subtotal).subtract(new BigDecimal(discount));
        BigDecimal cash = type == InvoiceType.CASH ? net : new BigDecimal(paid);
        return new InvoicePaymentTerms(type, new BigDecimal(subtotal), new BigDecimal(discount), net, cash,
                net.subtract(cash));
    }
}
