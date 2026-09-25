package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.controller.search.ItemSuggestionField;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.Utils;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static com.hamza.account.config.PropertiesName.getInvoiceAddItemDirect;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The standard invoice screen: a barcode, name, unit, price and quantity form above the lines
 * table, read by {@link InvoiceItemEntryCoordinator}. It is the screen every saved document is
 * opened in for editing, and the one every return is written in. Everything else - the header,
 * the table, the payment and the save - is {@link InvoiceScreenController}'s.
 */
@FxmlPath(pathFile = "invoice/buy-view2.fxml")
public class BuyController2<T3 extends BaseNames, T4 extends BaseAccount>
        extends InvoiceScreenController<T3, T4> {

    @FXML
    private Label labelBarcode, labelCondition, labelSearchBy, labelPrice, labelQuantity,
            labelItemBalance, labelTotals;
    @FXML
    private TextField txtBarcode, txtPrice, txtQuantity, txtItemBalance, txtTotals;
    @FXML
    private ComboBox<String> comboType;
    @FXML
    private Button btnAdd;
    @FXML
    private GridPane gridPane;
    private InvoiceItemEntryCoordinator itemEntry;

    public BuyController2(DataInterface<?, ?, T3, T4> dataInterface, int numInvoiceUpdate) throws Exception {
        super(dataInterface, numInvoiceUpdate, InvoiceScreenMode.STANDARD);
    }

    @Override
    protected void configureItemEntrySurface() {
        var lang = LanguageManager.getInstance();
        labelBarcode.setText(lang.getString("invoice.barcode"));
        labelCondition.setText(lang.getString("invoice.type"));
        labelSearchBy.setText(lang.getString("invoice.search.by"));
        labelPrice.setText(lang.getString("invoice.price"));
        labelQuantity.setText(lang.getString("invoice.quantity"));
        labelItemBalance.setText(lang.getString("invoice.item.balance"));
        labelTotals.setText(lang.getString("invoice.totals"));
        comboType.setPromptText(lang.getString("invoice.type"));
        txtBarcode.setPromptText(lang.getString("invoice.barcode"));
        btnAdd.setText(lang.getString("invoice.btn.add"));

        whenEnterPressed(txtBarcode, txtPrice, txtQuantity, btnAdd);
        setTextFormatter(txtItemBalance, txtPrice, txtQuantity, txtTotals);
        Utils.replaceNonDigitChar(txtBarcode);
        txtBarcode.clear();

        // The item-by-name search: ItemSuggestionField searches as you type, off the JavaFX
        // thread and debounced. Its chosenName - not its text - is what the coordinator
        // listens on, so the lookup that builds a whole line runs once per choice.
        ItemSuggestionField itemSearchField = new ItemSuggestionField(itemsService::getFilterItems);
        itemSearchField.setPriceResolver(this::itemPriceForThisScreen);
        itemSearchField.setOnCreateRequested(typed -> addItem(0, typed));
        gridPane.add(itemSearchField, 3, 2);

        itemEntry = new InvoiceItemEntryCoordinator(
                new InvoiceItemEntryCoordinator.Controls(
                        txtBarcode, txtPrice, txtQuantity, txtItemBalance, txtTotals,
                        comboType, btnAdd),
                editor,
                invoiceItemSelectionService,
                itemSearchField.chosenNameProperty(),
                () -> invoiceStockId,
                this::resolveSelectedPriceTier,
                this::scaleBarcodeSettings,
                () -> getInvoiceAddItemDirect(),
                this::addData,
                num -> addItem(num, txtBarcode.getText()),
                this::handleItemEntryError);
        itemEntry.setBundleEntry(this::addBundle);
        itemEntry.configure();
    }

    private void addData() {
        try {
            if (addLine(itemEntry.draft()) != null) {
                itemEntry.clear();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    @Override
    protected void placePartyField(Node partyField) {
        gridPane.add(partyField, 1, 1);
    }

    @Override
    protected void focusItemEntry() {
        txtBarcode.requestFocus();
    }

    @Override
    protected void resetItemEntry() {
        txtPrice.setText(String.valueOf(0));
        txtQuantity.setText(String.valueOf(0));
        txtTotals.setText(String.valueOf(0));
        txtItemBalance.setText(String.valueOf(0));
    }

    @Override
    protected void priceTierChanged(int priceTier) {
        if (itemEntry != null) {
            itemEntry.setPriceTier(priceTier);
        }
    }

    /**
     * F4: edit the item on the form, or create one. The same two conditions the removed
     * toolbar button was disabled by - a saved invoice being edited, or no permission to
     * create or update the item - so the shortcut does exactly what the button did.
     */
    @Override
    public void openCurrentItem() {
        if (num_invoice_update > 0
                || !AuthorizationGuard.isGranted(itemMutationPermission(txtBarcode.getText()))) {
            return;
        }
        itemEntry.openCurrentItem();
    }
}
