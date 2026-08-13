package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;

/** Authoritative cash/deferred payment calculation for an invoice. */
public record InvoicePaymentTerms(
        InvoiceType invoiceType,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal netAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount) {

    public InvoicePaymentTerms {
        subtotalAmount = MoneyMath.money(subtotalAmount);
        discountAmount = MoneyMath.money(discountAmount);
        netAmount = MoneyMath.money(netAmount);
        paidAmount = MoneyMath.money(paidAmount);
        remainingAmount = MoneyMath.money(remainingAmount);
    }

    public static InvoicePaymentTerms resolve(InvoiceType type, double subtotal,
                                              double discount, double enteredPaid)
            throws InvoiceValidationException {
        requireFinite(subtotal, InvoiceSaveValidator.Target.LINES,
                "إجمالي الفاتورة غير صالح");
        requireFinite(discount, InvoiceSaveValidator.Target.DISCOUNT,
                "قيمة الخصم غير صالحة");
        requireFinite(enteredPaid, InvoiceSaveValidator.Target.PAID,
                "قيمة المدفوع غير صالحة");
        return resolve(type, MoneyMath.decimal(subtotal), MoneyMath.decimal(discount),
                MoneyMath.decimal(enteredPaid));
    }

    public static InvoicePaymentTerms resolve(InvoiceType type, BigDecimal subtotal,
                                              BigDecimal discount, BigDecimal enteredPaid)
            throws InvoiceValidationException {
        if (type == null) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.PAYMENT_TYPE,
                    "من فضلك حدد نوع الفاتورة نقدي أو آجل");
        }
        requireAmount(subtotal, InvoiceSaveValidator.Target.LINES,
                "إجمالي الفاتورة غير صالح");
        requireAmount(discount, InvoiceSaveValidator.Target.DISCOUNT,
                "قيمة الخصم غير صالحة");
        requireAmount(enteredPaid, InvoiceSaveValidator.Target.PAID,
                "قيمة المدفوع غير صالحة");

        BigDecimal roundedSubtotal = MoneyMath.money(subtotal);
        BigDecimal roundedDiscount = MoneyMath.money(discount);
        if (roundedSubtotal.signum() <= 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.LINES,
                    "يجب أن يكون إجمالي الفاتورة أكبر من صفر");
        }
        if (roundedDiscount.signum() < 0
                || roundedDiscount.compareTo(roundedSubtotal) > 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DISCOUNT,
                    "يجب أن يكون الخصم بين صفر وإجمالي الفاتورة");
        }

        InvoicePaymentTerms terms = preview(type, roundedSubtotal, roundedDiscount, enteredPaid);
        if (terms.paidAmount.signum() < 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.PAID,
                    "لا يمكن أن يكون المدفوع أقل من صفر");
        }
        if (terms.paidAmount.compareTo(terms.netAmount) > 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.PAID,
                    "لا يمكن أن يكون المدفوع أكبر من صافي الفاتورة");
        }
        return terms;
    }

    /** Non-throwing calculation for live UI previews while the user is typing. */
    public static InvoicePaymentTerms preview(InvoiceType type, double subtotal,
                                              double discount, double enteredPaid) {
        return preview(type, MoneyMath.decimal(subtotal), MoneyMath.decimal(discount),
                MoneyMath.decimal(enteredPaid));
    }

    public static InvoicePaymentTerms preview(InvoiceType type, BigDecimal subtotal,
                                              BigDecimal discount, BigDecimal enteredPaid) {
        BigDecimal roundedSubtotal = MoneyMath.money(subtotal);
        BigDecimal roundedDiscount = MoneyMath.money(discount);
        BigDecimal net = MoneyMath.subtract(roundedSubtotal, roundedDiscount);
        BigDecimal paid = type == InvoiceType.CASH
                ? net
                : MoneyMath.money(enteredPaid);
        return new InvoicePaymentTerms(type, roundedSubtotal, roundedDiscount, net,
                paid, MoneyMath.subtract(net, paid));
    }

    public double subtotal() {
        return MoneyMath.asDouble(subtotalAmount);
    }

    public double discount() {
        return MoneyMath.asDouble(discountAmount);
    }

    public double net() {
        return MoneyMath.asDouble(netAmount);
    }

    public double paid() {
        return MoneyMath.asDouble(paidAmount);
    }

    public double remaining() {
        return MoneyMath.asDouble(remainingAmount);
    }

    public boolean deferred() {
        return invoiceType == InvoiceType.DEFER;
    }

    private static void requireFinite(double value, InvoiceSaveValidator.Target target,
                                      String message) throws InvoiceValidationException {
        if (!Double.isFinite(value)) {
            throw new InvoiceValidationException(target, message);
        }
    }

    private static void requireAmount(BigDecimal value, InvoiceSaveValidator.Target target,
                                      String message) throws InvoiceValidationException {
        if (value == null) {
            throw new InvoiceValidationException(target, message);
        }
    }
}
