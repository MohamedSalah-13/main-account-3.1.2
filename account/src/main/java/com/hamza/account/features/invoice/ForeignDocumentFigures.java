package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A document written in a foreign currency, turned into the base figures its header stores
 * (V83, docs/currency-plan.md §15 ق-د٤).
 * <p>
 * What was typed is exact: the lines, the discount and the cash in the document's currency. The base is
 * derived from them at the document's rate, and the three header figures are derived <b>so that each
 * still means what it always meant</b>:
 * <ul>
 *   <li>the total is the lines' own base figures added up - never the typed total converted - so the
 *       lines and the header agree to the piastre, which every reader of the lines has always assumed;</li>
 *   <li>the discount is the typed one converted, and never more than the total; a return naming its
 *       invoice takes instead the base share of that invoice's discount (ق-د٥), because that is the
 *       figure {@code ReturnGuard} holds it to;</li>
 *   <li>the cash is the typed cash converted and held between nothing and the net - and a document
 *       paid in full is paid its net exactly, so a cash invoice leaves nothing on the account in the
 *       base as it leaves nothing in its own currency.</li>
 * </ul>
 */
public final class ForeignDocumentFigures {

    private ForeignDocumentFigures() {
    }

    /** An amount in the document's currency, in the base: times the rate, rounded half up to money. */
    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        Objects.requireNonNull(rate, "rate");
        return MoneyMath.money((amount == null ? BigDecimal.ZERO : amount).multiply(rate));
    }

    /**
     * The header's figures in the base.
     *
     * @param typed        the header as typed, in the document's currency
     * @param baseSubtotal the document's lines after their own discounts, in the base - their stored
     *                     figures added up
     * @param rate         base units per one unit of the document's currency
     * @param returnShare  for a return naming its invoice, the base share of that invoice's own
     *                     discount; {@code null} for anything else
     */
    public static InvoicePaymentTerms header(InvoicePaymentTerms typed, BigDecimal baseSubtotal,
                                             BigDecimal rate, BigDecimal returnShare) {
        Objects.requireNonNull(typed, "typed");
        BigDecimal subtotal = MoneyMath.money(baseSubtotal);
        BigDecimal discount;
        if (returnShare != null) {
            discount = MoneyMath.money(returnShare);
        } else if (typed.discountAmount().compareTo(typed.subtotalAmount()) >= 0) {
            // All of it off: the base discount is the base total, not the typed total converted.
            discount = subtotal;
        } else {
            discount = toBase(typed.discountAmount(), rate);
        }
        discount = clamp(discount, subtotal);
        BigDecimal net = MoneyMath.subtract(subtotal, discount);
        BigDecimal paid;
        if (typed.paidAmount().compareTo(typed.netAmount()) >= 0) {
            paid = net;
        } else if (typed.paidAmount().signum() <= 0) {
            paid = MoneyMath.ZERO;
        } else {
            paid = clamp(toBase(typed.paidAmount(), rate), net);
        }
        return new InvoicePaymentTerms(typed.invoiceType(), subtotal, discount, net, paid,
                MoneyMath.subtract(net, paid));
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal ceiling) {
        if (value.signum() < 0) {
            return MoneyMath.ZERO;
        }
        return value.compareTo(ceiling) > 0 ? ceiling : value;
    }
}
