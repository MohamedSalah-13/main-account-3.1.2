package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.features.invoice.InvoiceLineTotals;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserFacingException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.List;

/**
 * The quick invoice screen: a till's screen, where the lines table is the only entry surface and a
 * hundred items are a hundred scans. {@link QuickInvoiceTable} owns the trailing entry row and the
 * keyboard; this class supplies what it needs and lays the rest out compactly - one header row,
 * the table, and a footer whose largest figure is what the customer pays.
 *
 * <p>It is a screen of its own since the screens were split, and it asks for its own key
 * ({@link DocumentType#quickEntryPermission()}) when it opens. Saving still asks the document's
 * create key inside {@code InvoiceSaveService}: the quick key chooses a screen, it grants no write.
 * It opens for a <b>new</b> sale or purchase only - a saved document is reopened on the standard
 * screen, and a return is written there against its source invoice.
 *
 * <p>A scan that finds nothing is answered in the status line under the table, with a beep,
 * rather than with a dialog. The operator's hands are on the scanner and the goods; a dialog has
 * to be dismissed before the next scan lands anywhere, and a scan that lands on its OK button
 * dismisses it without anybody reading it. What went wrong stays written there until the next
 * line goes in. A technical failure still reaches the error dialog with its reference code.
 */
@FxmlPath(pathFile = "invoice/quick-invoice.fxml")
public class QuickInvoiceController<T3 extends BaseNames, T4 extends BaseAccount>
        extends InvoiceScreenController<T3, T4> {

    private static final String STATUS_REFUSED = "quick-entry-status-refused";

    @FXML
    private HBox partyBox;
    @FXML
    private Label labelEntryStatus;
    private QuickInvoiceTable quickTable;

    public QuickInvoiceController(DataInterface<?, ?, T3, T4> dataInterface) throws Exception {
        super(dataInterface, 0, InvoiceScreenMode.QUICK);
        DocumentType documentType = dataInterface.designInterface().documentType();
        AuthorizationGuard.require(documentType.quickEntryPermission().orElseThrow(() ->
                new BusinessRuleException(LanguageManager.getInstance().getString("invoice.quick.unavailable"))));
    }

    @Override
    protected void configureItemEntrySurface() {
        quickTable = new QuickInvoiceTable(table, new QuickInvoiceTable.Host() {
            @Override
            public InvoiceItemSelection selectByBarcode(String barcode) throws Exception {
                return invoiceItemSelectionService.selectByBarcode(barcode, invoiceStockId,
                        resolveSelectedPriceTier(), scaleBarcodeSettings());
            }

            @Override
            public boolean addBundle(String barcode) throws Exception {
                return QuickInvoiceController.this.addBundle(barcode);
            }

            @Override
            public InvoiceItemSelection selectByName(String itemName) throws Exception {
                return invoiceItemSelectionService.selectByName(itemName, invoiceStockId,
                        resolveSelectedPriceTier());
            }

            @Override
            public BasePurchasesAndSales addLine(InvoiceLineDraft draft) throws Exception {
                return QuickInvoiceController.this.addLine(draft);
            }

            @Override
            public void editQuantity(BasePurchasesAndSales line, double quantity) throws Exception {
                lineEditService.editQuantity(line, quantity);
            }

            @Override
            public List<ItemsModel> searchItems(String text) throws Exception {
                return itemsService.getFilterItems(text);
            }

            @Override
            public double priceOf(ItemsModel item) {
                return itemPriceForThisScreen(item);
            }

            @Override
            public void createItem(String typedText) {
                addItem(0, typedText);
            }

            @Override
            public BasePurchasesAndSales newEntryRow() {
                return dataInterface.invoiceBuy().object_TableData(0, 0, 0,
                        0, 0, 0, 0, new UnitsModel(), new ItemsModel(), null);
            }

            @Override
            public void handleError(Exception error, boolean scaleBarcode) {
                refuseEntry(error);
            }

            @Override
            public void totalsChanged() {
                editor.refreshTotals();
            }

            @Override
            public void lineAdded() {
                String note = noteForAddedLine;
                noteForAddedLine = null;
                clearEntryStatus();
                if (note != null) {
                    labelEntryStatus.setText(note);
                }
            }
        });
        quickTable.configure();
        clearEntryStatus();
    }

    /** Said about the line just added (V84: priced at tier 1), kept past the status line's clearing. */
    private String noteForAddedLine;

    /** The status line is where this screen says anything - a dialog would be closed by the next scan. */
    @Override
    protected void showPricingNote(String text) {
        clearEntryStatus();
        if (text != null && !text.isBlank()) {
            labelEntryStatus.setText(text);
        }
    }

    /** A note about the line being added waits for the line to be in, since adding clears the status. */
    @Override
    protected void noteAddedLine(String text) {
        noteForAddedLine = text;
    }

    /**
     * A refusal the operator can act on goes to the status line with a beep; anything else is a
     * fault, and a fault is reported where every fault is - behind a reference code.
     */
    private void refuseEntry(Exception error) {
        if (!(error instanceof UserFacingException refusal)) {
            logError(error);
            return;
        }
        labelEntryStatus.setText(refusal.userMessage());
        if (!labelEntryStatus.getStyleClass().contains(STATUS_REFUSED)) {
            labelEntryStatus.getStyleClass().add(STATUS_REFUSED);
        }
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    private void clearEntryStatus() {
        labelEntryStatus.setText(LanguageManager.getInstance().getString("invoice.quick.status.ready"));
        labelEntryStatus.getStyleClass().remove(STATUS_REFUSED);
    }

    @Override
    protected void placePartyField(Node partyField) {
        HBox.setHgrow(partyField, Priority.ALWAYS);
        partyBox.getChildren().add(partyField);
    }

    @Override
    protected void focusItemEntry() {
        if (quickTable != null) {
            quickTable.focusEntryRow();
        }
    }

    @Override
    protected void resetItemEntry() {
        clearEntryStatus();
        focusItemEntry();
    }

    /**
     * F4: the item on the focused line, to correct it; on the entry row, a new item. The item
     * screen asks its own keys when it saves - these are the same hints the standard screen
     * gives, so F4 does nothing for somebody who could not save what it opens.
     */
    @Override
    public void openCurrentItem() {
        BasePurchasesAndSales line = table.getFocusModel().getFocusedItem();
        if (line != null && !InvoiceLineTotals.isPlaceholder(line)) {
            if (AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE)) {
                addItem(line.getItems().getId(), null);
            }
        } else if (AuthorizationGuard.isGranted(AppPermissions.ITEMS_CREATE)) {
            addItem(0, "");
        }
    }
}
