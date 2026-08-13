package com.hamza.account.features.invoice;

import com.hamza.account.type.InvoiceType;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * UI-independent state for the cash/deferred section of an invoice form.
 * The controller supplies numeric inputs and binds controls to the read-only result.
 */
public final class InvoicePaymentViewModel {

    private final ReadOnlyObjectWrapper<InvoiceType> invoiceType =
            new ReadOnlyObjectWrapper<>(InvoiceType.CASH);
    private final ReadOnlyObjectWrapper<InvoicePaymentTerms> preview =
            new ReadOnlyObjectWrapper<>(InvoicePaymentTerms.preview(InvoiceType.CASH, 0, 0, 0));
    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<InvoiceSaveValidator.Target> invalidTarget =
            new ReadOnlyObjectWrapper<>(InvoiceSaveValidator.Target.LINES);

    private double subtotal;
    private double discount;
    private double enteredPaid;

    public void selectInvoiceType(InvoiceType type, boolean resetDeferredPayment) {
        invoiceType.set(type);
        if (resetDeferredPayment && type == InvoiceType.DEFER) {
            enteredPaid = 0;
        }
        recalculate();
    }

    public void updateAmounts(double subtotal, double discount, double enteredPaid) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.enteredPaid = enteredPaid;
        recalculate();
    }

    public InvoicePaymentTerms requireValid() throws InvoiceValidationException {
        return InvoicePaymentTerms.resolve(invoiceType.get(), subtotal, discount, enteredPaid);
    }

    private void recalculate() {
        preview.set(InvoicePaymentTerms.preview(
                invoiceType.get(), subtotal, discount, enteredPaid));
        try {
            preview.set(requireValid());
            valid.set(true);
            invalidTarget.set(null);
        } catch (InvoiceValidationException e) {
            valid.set(false);
            invalidTarget.set(e.target());
        }
    }

    public InvoiceType invoiceType() {
        return invoiceType.get();
    }

    public ReadOnlyObjectProperty<InvoiceType> invoiceTypeProperty() {
        return invoiceType.getReadOnlyProperty();
    }

    public InvoicePaymentTerms preview() {
        return preview.get();
    }

    public ReadOnlyObjectProperty<InvoicePaymentTerms> previewProperty() {
        return preview.getReadOnlyProperty();
    }

    public boolean isValid() {
        return valid.get();
    }

    public ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }

    public InvoiceSaveValidator.Target invalidTarget() {
        return invalidTarget.get();
    }

    public ReadOnlyObjectProperty<InvoiceSaveValidator.Target> invalidTargetProperty() {
        return invalidTarget.getReadOnlyProperty();
    }
}
