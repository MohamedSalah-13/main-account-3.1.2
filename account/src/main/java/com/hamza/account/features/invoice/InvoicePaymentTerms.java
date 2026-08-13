package com.hamza.account.features.invoice;

import com.hamza.account.type.InvoiceType;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

/** Authoritative cash/deferred payment calculation for an invoice. */
public record InvoicePaymentTerms(InvoiceType invoiceType, double subtotal,
                                  double discount, double net, double paid,
                                  double remaining) {

    public static InvoicePaymentTerms resolve(InvoiceType type, double subtotal,
                                              double discount, double enteredPaid)
            throws InvoiceValidationException {
        if (type == null) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.PAYMENT_TYPE,
                    "من فضلك حدد نوع الفاتورة نقدي أو آجل");
        }
        requireFinite(subtotal, InvoiceSaveValidator.Target.LINES, "إجمالي الفاتورة غير صالح");
        requireFinite(discount, InvoiceSaveValidator.Target.DISCOUNT, "قيمة الخصم غير صالحة");
        requireFinite(enteredPaid, InvoiceSaveValidator.Target.PAID, "قيمة المدفوع غير صالحة");
        if (subtotal <= 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.LINES,
                    "يجب أن يكون إجمالي الفاتورة أكبر من صفر");
        }
        if (discount < 0 || discount > subtotal) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DISCOUNT,
                    "يجب أن يكون الخصم بين صفر وإجمالي الفاتورة");
        }

        InvoicePaymentTerms terms = preview(type, subtotal, discount, enteredPaid);
        if (terms.paid < 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.PAID,
                    "لا يمكن أن يكون المدفوع أقل من صفر");
        }
        if (terms.paid > terms.net) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.PAID,
                    "لا يمكن أن يكون المدفوع أكبر من صافي الفاتورة");
        }
        return terms;
    }

    /** Non-throwing calculation for live UI previews while the user is typing. */
    public static InvoicePaymentTerms preview(InvoiceType type, double subtotal,
                                              double discount, double enteredPaid) {
        double roundedSubtotal = roundToTwoDecimalPlaces(subtotal);
        double roundedDiscount = roundToTwoDecimalPlaces(discount);
        double net = roundToTwoDecimalPlaces(roundedSubtotal - roundedDiscount);
        double paid = type == InvoiceType.CASH
                ? net
                : roundToTwoDecimalPlaces(enteredPaid);
        return new InvoicePaymentTerms(type, roundedSubtotal, roundedDiscount, net,
                paid, roundToTwoDecimalPlaces(net - paid));
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
}
