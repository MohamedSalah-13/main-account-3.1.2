package com.hamza.account.controller.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.features.invoice.ReturnLineSelectionService;
import com.hamza.account.features.invoice.ReturnedStatusService;
import com.hamza.account.features.returns.JdbcReturnableRepository;
import com.hamza.account.features.invoice.InvoiceLineEditService;
import com.hamza.account.features.returns.ReturnHeaderDiscount;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.returns.ReturnReason;
import com.hamza.account.features.returns.ReturnSettlementAdvice;
import com.hamza.account.features.returns.ReturnableRepository;
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
    private final ReasonPrompt reasonPrompt;
    private final SourcePicker sourcePicker;
    private final PartyBalances partyBalances;

    private int sourceInvoiceNumber;
    private ReturnReason selectedReturnReason;
    /** The picked source's header figures, or {@code null} while the return names none. */
    private ReturnableRepository.SourceAmounts sourceAmounts;

    public ReturnEntryCoordinator(DocumentType documentType,
                                  Controls controls,
                                  ReturnLineSelectionService.ItemLookup itemLookup,
                                  LineAppender lineAppender,
                                  DelegateLookup delegateLookup,
                                  ErrorHandler errorHandler,
                                  ReasonPrompt reasonPrompt,
                                  PartyBalances partyBalances,
                                  SourcePicker sourcePicker) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.lineAppender = Objects.requireNonNull(lineAppender, "lineAppender");
        this.delegateLookup = Objects.requireNonNull(delegateLookup, "delegateLookup");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.reasonPrompt = Objects.requireNonNull(reasonPrompt, "reasonPrompt");
        this.sourcePicker = Objects.requireNonNull(sourcePicker, "sourcePicker");
        this.partyBalances = Objects.requireNonNull(partyBalances, "partyBalances");
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
        try {
            adoptSourceAmounts(this.sourceInvoiceNumber);
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }

    /** Clears the picked source and reason, and hides the badge. */
    public void reset() {
        sourceInvoiceNumber = 0;
        selectedReturnReason = null;
        sourceAmounts = null;
        controls.headerDiscount().lock(false);
        show(controls.returnedBadge(), false);
    }

    /**
     * Keeps the return's own discount equal to its share of the source invoice's, every time
     * the lines change - the screen's half of {@link ReturnHeaderDiscount}, whose other half
     * is {@code ReturnGuard.validateDiscount} refusing a save that disagrees. The box is
     * locked while a source is named, for the reason a picked line's price is: a figure the
     * save will accept no other value for is not a figure to offer for typing.
     * <p>
     * Does nothing on a return with no source, where the discount is the user's to enter.
     */
    public void totalsChanged() {
        if (sourceAmounts == null || sourceInvoiceNumber <= 0) {
            return;
        }
        controls.headerDiscount().show(ReturnHeaderDiscount.shareFor(
                sourceAmounts.total(), sourceAmounts.discount(),
                controls.headerDiscount().returnTotal()));
    }

    /**
     * What a picked return row's source line sold and took off - how
     * {@link InvoiceLineEditService} keeps the row's discount share in step when its
     * quantity is edited. Empty on anything that is not a return naming an invoice.
     */
    public Optional<InvoiceLineEditService.SourceLineTerms.Terms> sourceLineTerms(
            int sourceLineId) throws com.hamza.controlsfx.database.DaoException {
        if (lineSelection == null || sourceInvoiceNumber <= 0) {
            return Optional.empty();
        }
        return lineSelection.lineTerms(sourceInvoiceNumber, sourceLineId);
    }

    private void adoptSourceAmounts(int invoiceNumber) throws Exception {
        sourceAmounts = lineSelection == null || invoiceNumber <= 0
                ? null
                : lineSelection.sourceAmounts(invoiceNumber).orElse(null);
        controls.headerDiscount().lock(sourceAmounts != null);
        totalsChanged();
    }

    /**
     * Everything this screen has to ask before a return is written: why it has no invoice
     * behind it, and whether the way it is being settled is really what was meant.
     * <p>
     * One method rather than three call sites in {@code saveInvoice}, because they are one
     * conversation with the person at the counter and the order matters - the reason is asked
     * first, since answering it may well be the moment they realise they meant to pick the
     * invoice after all.
     *
     * @param deferred whether the return is being settled on the account rather than in cash
     * @param partyId  the customer or supplier it is booked to
     * @return whether the save may go ahead
     */
    public boolean confirmBeforeSave(boolean deferred, int partyId) {
        if (!documentType.isReturn()) {
            return true;
        }
        return confirmIfUnlinked() && confirmSettlement(deferred, partyId);
    }

    /**
     * A return with no source invoice is the one document nothing here can check - no
     * quantity to compare against, no cost to recover, no batch to pick from. Allowed,
     * and always was, but not silently.
     * <p>
     * It asks for the reason at the same time, and that is the only place a free return can be
     * asked for one: the reason combo lives in the picker dialog, which a free return never
     * opens, so every return entered without an invoice reached the reasons report with no
     * reason however deliberate the person had been. Cancelling the reason is not cancelling
     * the save - "not given" is an answer, and it is the one every free return used to give.
     */
    private boolean confirmIfUnlinked() {
        if (sourceInvoiceNumber > 0) {
            return true;
        }
        var lang = LanguageManager.getInstance();
        if (!AllAlerts.confirm_all(lang.getString("confirm"),
                lang.getString("return.confirm.no.source"))) {
            return false;
        }
        selectedReturnReason = reasonPrompt.ask(selectedReturnReason);
        return true;
    }

    /**
     * The two things about a return's settlement that are worth a word and not a refusal -
     * {@link ReturnSettlementAdvice} states both and why. The balance behind the second is read
     * here rather than in the rule, and a reader who may not see it simply gets no warning: the
     * party screens are permission-guarded, and a warning is not worth failing a save over.
     */
    private boolean confirmSettlement(boolean deferred, int partyId) {
        BigDecimal balance = deferred ? null : partyBalance(partyId);
        ReturnSettlementAdvice.Concern concern = ReturnSettlementAdvice.of(
                sourceInvoiceNumber > 0, deferred, isDefaultCashParty(partyId), balance);
        if (concern.isSilent()) {
            return true;
        }
        var lang = LanguageManager.getInstance();
        return AllAlerts.confirm_all(lang.getString("confirm"),
                lang.getString(concern.messageKey(),
                        MoneyMath.text(orZero(balance))));
    }

    /**
     * Whether this is the party the default-customer setting names - the account a cash sale
     * lands on when nobody is named. Read from the setting rather than compared against
     * {@code 1}: that row is seeded, not fixed, and a constant for a row of an editable table is
     * the mistake {@code UsersType} and {@code DELEGATE_JOB} each cost this repository once.
     */
    private boolean isDefaultCashParty(int partyId) {
        if (documentType.partyKind() != PartyKind.CUSTOMER || partyId <= 0) {
            return false;
        }
        try {
            return partyId == Integer.parseInt(
                    PropertiesName.getSettingSaveNameCustomer().trim());
        } catch (NumberFormatException notAnId) {
            return false;
        }
    }

    /** What the party owes today, or {@code null} when it cannot be read. */
    private BigDecimal partyBalance(int partyId) {
        if (partyId <= 0) {
            return null;
        }
        try {
            return partyBalances.of(documentType.partyKind(), partyId);
        } catch (Exception cannotRead) {
            return null;
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
        Optional<Integer> picked = sourcePicker.pick();
        if (picked.isEmpty()) {
            return;
        }
        int invoiceNumber = picked.get();

        // A return reverses one document. Picking from a second invoice while the first
        // one's lines are still on the table used to be refused much later and much
        // more confusingly - ReturnGuard would report "this item is not on invoice N",
        // which is true but says nothing about the two sources being mixed.
        if (sourceInvoiceNumber > 0 && sourceInvoiceNumber != invoiceNumber
                && !lineAppender.isEmpty()) {
            boolean replace = AllAlerts.confirm_all(lang.getString("confirm"),
                    lang.getString("return.confirm.replace.source",
                            sourceInvoiceNumber, invoiceNumber));
            if (!replace) {
                return;
            }
            lineAppender.clear();
            reset();
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
            adoptSourceAmounts(invoiceNumber);
            applySourceParty(invoiceNumber);
            applySourceDelegate(invoiceNumber);
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }

    /**
     * Points the return at the party the source invoice was with, so the ordinary flow
     * lands on the right one without the user having to remember. Goods bought from one
     * supplier cannot be returned to another - {@code ReturnGuard} refuses that at save
     * - and this is the half that means nobody meets the refusal by accident.
     */
    private void applySourceParty(int invoiceNumber) throws Exception {
        Optional<Integer> partyId = lineSelection.sourcePartyId(invoiceNumber);
        if (partyId.isEmpty()) {
            return;
        }
        controls.partySelector().select(partyId.get());
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
                           NameSelector delegateSelector, PartySelector partySelector,
                           HeaderDiscount headerDiscount) {
        public Controls {
            Objects.requireNonNull(headerDiscount, "headerDiscount");
            Objects.requireNonNull(returnFromInvoice, "returnFromInvoice");
            Objects.requireNonNull(returnedBadge, "returnedBadge");
            Objects.requireNonNull(delegateSelector, "delegateSelector");
            Objects.requireNonNull(partySelector, "partySelector");
        }
    }

    /** The form's additional-discount box, and the lines' total it is a share of. */
    public interface HeaderDiscount {
        /** The return's lines after their own discounts - what its header stores as total. */
        double returnTotal();

        void show(double discount);

        void lock(boolean locked);
    }

    /** Puts a name into one of the form's own selectors - the delegate combo, or the party search. */
    @FunctionalInterface
    public interface NameSelector {
        void select(String name);
    }

    /**
     * Which document this return reverses. Empty when the person closed the picker without
     * choosing - which is not an error and must leave the screen exactly as it was.
     */
    @FunctionalInterface
    public interface SourcePicker {
        Optional<Integer> pick();
    }

    /**
     * Asks why the goods came back, for a return that names no invoice - the picker's own combo
     * is the sourced half of the same question. Answers with what was chosen, or {@code null}
     * for "not given", which is a legitimate answer and must not block the save.
     */
    @FunctionalInterface
    public interface ReasonPrompt {
        ReturnReason ask(ReturnReason current);
    }

    /** What a party owes today; may throw, and a throw simply means no warning. */
    @FunctionalInterface
    public interface PartyBalances {
        BigDecimal of(PartyKind kind, int partyId) throws Exception;
    }

    /**
     * Points the screen at a party by its id.
     * <p>
     * It used to be a {@link NameSelector}: this class held the id, asked a lookup for the
     * name, and handed the name over - and the screen then read every customer in the
     * database to find the party again. Two whole-table reads to select a party whose id
     * was in hand the entire time.
     */
    @FunctionalInterface
    public interface PartySelector {
        void select(int partyId) throws Exception;
    }

    /**
     * The invoice's line table, as much of it as this coordinator needs: append a
     * picked line and hand it back for tagging, and clear the table when the user
     * switches to a different source invoice.
     */
    public interface LineAppender {
        BasePurchasesAndSales append(InvoiceLineDraft draft) throws Exception;

        boolean isEmpty();

        void clear();
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
