package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.type.InvoiceType;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Observable state of an invoice editor, independent from its FXML controls.
 * It keeps line summaries and payment validation synchronized as rows change.
 */
public final class InvoiceEditorViewModel<T extends BasePurchasesAndSales> {

    private final ObservableList<T> lines = FXCollections.observableArrayList(
            line -> new Observable[]{
                    line.quantityProperty(), line.priceProperty(), line.totalProperty(),
                    line.discountProperty(), line.total_after_discountProperty()
            });
    private final ObjectProperty<ItemsModel> selectedItem =
            new SimpleObjectProperty<>(new ItemsModel());
    private final BooleanProperty saving = new SimpleBooleanProperty();
    private final ReadOnlyObjectWrapper<InvoiceLineTotals> totals =
            new ReadOnlyObjectWrapper<>(InvoiceLineTotals.from(lines));
    private final ReadOnlyBooleanWrapper invalidLines = new ReadOnlyBooleanWrapper(false);
    private final InvoicePaymentViewModel payment = new InvoicePaymentViewModel();

    public InvoiceEditorViewModel() {
        lines.addListener((ListChangeListener<T>) change -> refreshTotals());
    }

    public ObservableList<T> lines() {
        return lines;
    }

    public void replaceLines(Collection<? extends T> replacement) {
        lines.setAll(replacement);
    }

    public ItemsModel selectedItem() {
        return selectedItem.get();
    }

    public void selectItem(ItemsModel item) {
        selectedItem.set(item == null ? new ItemsModel() : item);
    }

    public ObjectProperty<ItemsModel> selectedItemProperty() {
        return selectedItem;
    }

    public boolean isSaving() {
        return saving.get();
    }

    public void setSaving(boolean value) {
        saving.set(value);
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    public InvoiceLineTotals totals() {
        return totals.get();
    }

    public ReadOnlyObjectProperty<InvoiceLineTotals> totalsProperty() {
        return totals.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty invalidLinesProperty() {
        return invalidLines.getReadOnlyProperty();
    }

    public void refreshTotals() {
        InvoiceLineTotals summary = InvoiceLineTotals.from(lines);
        totals.set(summary);
        invalidLines.set(summary.hasInvalidLine());
    }

    public InvoicePaymentTerms updatePayment(InvoiceType type,
                                             boolean resetDeferredPayment,
                                             BigDecimal discount,
                                             BigDecimal enteredPaid) {
        payment.selectInvoiceType(type, resetDeferredPayment);
        payment.updateAmounts(totals().netAmount(), discount,
                resetDeferredPayment ? MoneyMath.ZERO : enteredPaid);
        return payment.preview();
    }

    public InvoicePaymentTerms requireValidPayment() throws InvoiceValidationException {
        return payment.requireValid();
    }

    public boolean isPaymentValid() {
        return payment.isValid();
    }

    public InvoiceSaveValidator.Target invalidPaymentTarget() {
        return payment.invalidTarget();
    }
}
