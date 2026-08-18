package com.hamza.account.controller.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.features.invoice.ReturnLineSelectionService;
import com.hamza.account.features.invoice.ReturnedStatusService;
import com.hamza.account.features.returns.JdbcReturnableRepository;
import com.hamza.account.features.returns.ReturnReason;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.Employees;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns everything about an invoice form that only exists because the document is (or
 * is not) a return: the "return from invoice" button and its picker flow, the
 * "returned: N of M" badge on a sale or purchase, and the source invoice and reason
 * that the save command carries.
 * <p>
 * Extracted from {@code BuyController2} in the shape
 * {@link InvoiceItemEntryCoordinator} already established for the item-entry section
 * of the same form. The controller serves all four document types through
 * {@code DataInterface}, and the return work had left six fields there - two of them
 * mutually exclusive nulls, two meaningless outside a return, and two controls each
 * hidden for the opposite case. They are one object now.
 * <p>
 * Deliberately not a separate return <em>screen</em>: 97% of {@code BuyController2}
 * is shared by all four types (item entry, units, price tiers, totals, discount,
 * payment terms, saving, printing, stock alerts, period locks, shifts), and splitting
 * it would duplicate all of that to isolate this.
 */
public final class ReturnEntryCoordinator {

    private final DocumentType documentType;
    private final Controls controls;
    private final ReturnLineSelectionService lineSelection;
    private final ReturnedStatusService returnedStatus;
    private final LineAppender lineAppender;
    private final DelegateLookup delegateLookup;
    private final ErrorHandler errorHandler;

    private int sourceInvoiceNumber;
    private ReturnReason selectedReturnReason;

    public ReturnEntryCoordinator(DocumentType documentType,
                                  Controls controls,
                                  ReturnLineSelectionService.ItemLookup itemLookup,
                                  LineAppender lineAppender,
                                  DelegateLookup delegateLookup,
                                  ErrorHandler errorHandler) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.lineAppender = Objects.requireNonNull(lineAppender, "lineAppender");
        this.delegateLookup = Objects.requireNonNull(delegateLookup, "delegateLookup");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        Objects.requireNonNull(itemLookup, "itemLookup");

        // Only a return has a source to pick from; only a sale or purchase has
        // anything to report about having been returned. Never both.
        this.lineSelection = documentType.isReturn()
                ? new ReturnLineSelectionService(documentType,
                        new JdbcReturnableRepository(), itemLookup)
                : null;
        this.returnedStatus = documentType.isReturn()
                ? null
                : new ReturnedStatusService(new JdbcReturnableRepository());
    }

    /** Wires the button and sets each control's visibility for this document type. */
    public void configure() {
        boolean isReturn = documentType.isReturn();
        show(controls.returnFromInvoice(), isReturn);
        show(controls.returnedBadge(), false);
        controls.returnFromInvoice().setOnAction(event -> openPicker());
    }

    /** The invoice this return reverses, or {@code 0} - what the save command carries. */
    public int sourceInvoiceNumber() {
        return sourceInvoiceNumber;
    }

    /** Why it was returned, or {@code null}. */
    public ReturnReason selectedReturnReason() {
        return selectedReturnReason;
    }

    /**
     * Restores what a saved return was linked to, when one is opened for editing.
     * <p>
     * Without this every guard is silently off on the edit path: {@code ReturnGuard}
     * treats a source of {@code 0} as "a free return, nothing to compare against" and
     * returns immediately, so a saved-and-linked return could be reopened, its
     * quantities and prices changed to anything at all, and saved again unchecked.
     *
     * @param sourceInvoiceNumber as stored, or {@code 0} if the return has no source
     * @param storedReason        the stored {@code return_reason}, or {@code null}
     */
    public void restoreSource(int sourceInvoiceNumber, String storedReason) {
        this.sourceInvoiceNumber = Math.max(sourceInvoiceNumber, 0);
        this.selectedReturnReason = ReturnReason.fromStoredValue(storedReason);
    }

    /** Clears the picked source and reason, and hides the badge. */
    public void reset() {
        sourceInvoiceNumber = 0;
        selectedReturnReason = null;
        show(controls.returnedBadge(), false);
    }

    /**
     * A return with no source invoice is the one document nothing here can check - no
     * quantity to compare against, no cost to recover, no batch to pick from. Allowed,
     * and always was, but not silently.
     *
     * @return whether the save may go ahead
     */
    public boolean confirmIfUnlinked() {
        if (!documentType.isReturn() || sourceInvoiceNumber > 0) {
            return true;
        }
        var lang = LanguageManager.getInstance();
        return AllAlerts.confirm_all(lang.getString("confirm"),
                lang.getString("return.confirm.no.source"));
    }

    /**
     * Shows "returned: N of M" beside a saved sale or purchase that has since been
     * returned against. Nothing to show on a return's own screen, or where nothing
     * has been returned.
     */
    public void showReturnedStatus(int invoiceNumber) {
        if (returnedStatus == null) {
            return;
        }
        try {
            var status = returnedStatus.statusOf(documentType, invoiceNumber);
            if (!status.hasAnyReturn()) {
                show(controls.returnedBadge(), false);
                return;
            }
            String key = status.isFullyReturned()
                    ? "invoice.returned.badge.full"
                    : "invoice.returned.badge.partial";
            controls.returnedBadge().setText(LanguageManager.getInstance().getString(key,
                    quantityText(status.returnedBaseQuantity()),
                    quantityText(status.soldBaseQuantity())));
            show(controls.returnedBadge(), true);
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }

    /**
     * Prompts for the invoice this return reverses, lets the user pick which lines and
     * how much of each, and appends them through {@link LineAppender} - each row
     * tagged with its {@code sourceLineId}, which is what lets
     * {@code ReturnCostResolver} recover the original sale's cost and
     * {@code InvoiceExpiryService} offer its batches once the return is saved.
     */
    private void openPicker() {
        if (lineSelection == null) {
            return;
        }
        var lang = LanguageManager.getInstance();
        TextInputDialog numberDialog = new TextInputDialog();
        numberDialog.setTitle(lang.getString("return.dialog.title"));
        numberDialog.setHeaderText(null);
        numberDialog.setContentText(lang.getString("return.dialog.invoice.number.prompt"));
        Optional<String> entered = numberDialog.showAndWait();
        if (entered.isEmpty() || entered.get().isBlank()) {
            return;
        }
        int invoiceNumber;
        try {
            invoiceNumber = Integer.parseInt(entered.get().trim());
        } catch (NumberFormatException e) {
            AllAlerts.alertError(lang.getString("return.dialog.invoice.number.required"));
            return;
        }

        try {
            var lines = lineSelection.selectableLines(invoiceNumber);
            var result = DialogReturnFromInvoice.show(invoiceNumber, lines);
            if (result.isEmpty() || result.get().selectedLines().isEmpty()) {
                return;
            }
            for (var selected : result.get().selectedLines()) {
                BasePurchasesAndSales appended = lineAppender.append(
                        selected.line().draftFor(selected.quantityInUnit()));
                appended.setSourceLineId(selected.line().sourceLineId());
            }
            sourceInvoiceNumber = invoiceNumber;
            selectedReturnReason = result.get().reason();
            applySourceDelegate(invoiceNumber);
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }

    /**
     * A sales return reverses one delegate's sale, so the commission it takes back
     * belongs to whoever made it - not to whoever the screen happens to have selected.
     * Purchases carry no delegate at all.
     */
    private void applySourceDelegate(int invoiceNumber) throws Exception {
        if (documentType != DocumentType.SALES_RETURN) {
            return;
        }
        Optional<Integer> delegateId = lineSelection.sourceDelegateId(invoiceNumber);
        if (delegateId.isEmpty()) {
            return;
        }
        Employees delegate = delegateLookup.byId(delegateId.get());
        if (delegate != null) {
            controls.delegateSelector().select(delegate.getName());
        }
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static String quantityText(double value) {
        return MoneyMath.text(BigDecimal.valueOf(value));
    }

    /** The form controls this coordinator owns. */
    public record Controls(Button returnFromInvoice, Label returnedBadge,
                           DelegateSelector delegateSelector) {
        public Controls {
            Objects.requireNonNull(returnFromInvoice, "returnFromInvoice");
            Objects.requireNonNull(returnedBadge, "returnedBadge");
            Objects.requireNonNull(delegateSelector, "delegateSelector");
        }
    }

    @FunctionalInterface
    public interface DelegateSelector {
        void select(String delegateName);
    }

    /** Appends one picked line to the invoice table and hands it back for tagging. */
    @FunctionalInterface
    public interface LineAppender {
        BasePurchasesAndSales append(InvoiceLineDraft draft) throws Exception;
    }

    @FunctionalInterface
    public interface DelegateLookup {
        Employees byId(int delegateId) throws Exception;
    }

    @FunctionalInterface
    public interface ErrorHandler {
        void handle(Exception error);
    }
}
