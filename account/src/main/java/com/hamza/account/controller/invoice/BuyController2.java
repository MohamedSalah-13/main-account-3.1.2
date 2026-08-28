package com.hamza.account.controller.invoice;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.authorization.AppPermissions;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.TextSearchController;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.controller.setting.SettingTabLanguageController;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.features.invoice.InvoiceItemCatalogService;
import com.hamza.account.features.invoice.InvoiceExpiryOptions;
import com.hamza.account.features.invoice.InvoiceExpiryService;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.features.invoice.InvoiceLineEditService;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.features.invoice.InvoiceLineTotals;
import com.hamza.account.features.invoice.InvoiceEditorViewModel;
import com.hamza.account.features.invoice.InvoicePaymentTerms;
import com.hamza.account.features.invoice.InvoicePostSaveService;
import com.hamza.account.features.invoice.InvoicePrintRequest;
import com.hamza.account.features.invoice.InvoicePrintService;
import com.hamza.account.features.invoice.InvoiceSaveCommand;
import com.hamza.account.features.invoice.InvoiceSaveResult;
import com.hamza.account.features.invoice.InvoiceSaveValidator;
import com.hamza.account.features.invoice.InvoiceValidationException;
import com.hamza.account.features.notification.StockLevelAlert;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.InvoiceHeaderView;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.*;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.document.DocumentType;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.view.SearchItemsApplication;
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.account.controller.invoice.DialogCashPaid.showCashChangeDialog;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "invoice/buy-view2.fxml")
public class BuyController2<T3 extends BaseNames, T4 extends BaseAccount>
        extends BuyData<T3, T4> implements Initializable, AppSettingInterface {

    private final InvoiceEditorViewModel<BasePurchasesAndSales> editor = new InvoiceEditorViewModel<>();
    private final Subscriptions subscriptions = new Subscriptions();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final InvoicePrintService invoicePrintService = new InvoicePrintService();
    private final CustomerService customerService = ServiceRegistry.get(CustomerService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final InvoicePostSaveService invoicePostSaveService;
    private final InvoiceLineService<BasePurchasesAndSales> invoiceLineService;
    private final InvoiceExpiryService invoiceExpiryService;
    private final InvoiceItemSelectionService invoiceItemSelectionService;
    private InvoiceItemEntryCoordinator itemEntry;
    private int priceTypeByNameId = 1; // use a first price type
    /** The invoice stock context; kept at the legacy default until warehouse selection is exposed. */
    private int invoiceStockId = DefaultStock.ID;
    private int codeAccount;
    private boolean updatingPaymentUi;
    private StringProperty textSearchName, textSearchItems;
    private TextSearchController<T3> nameSearchController;
    @FXML
    private Label labelNum, labelName, labelStock, labelBarcode, labelDate, labelCondition, labelDelegate, labelTreasury, labelSearchBy, labelPrice, labelQuantity, labelItemBalance, labelTotals, last1, last2, last3, last4, last5, labelNotes, labelInvoiceTotal, labelPaid, labelRemaining, labelNetAfterDiscount;
    @FXML
    @Getter
    private Button btnAdd, btnSave, btnPrintSave, btnNew, btnSearch, btnUpdateItem;
    @FXML
    private Button btnReturnFromInvoice;
    @FXML
    private ComboBox<String> comboType, comboDelegate, comboTreasury;
    @FXML
    private ComboBox<Stock> comboStock;
    @FXML
    private TextField txtNum, txtBarcode, txtPrice, txtQuantity, txtItemBalance, txtTotals, txtOtherDiscount, txtPaid, txtRestAfterPaid, txtRestAfterDiscount;
    @FXML
    private TableView<BasePurchasesAndSales> table;
    @FXML
    private HBox boxTableArrow;
    @FXML
    private Text textSumCount, txtSumQuantity, txtBeforeDiscount, txtSumDiscount, txtSumTotals, textInvoiceTotal;
    @FXML
    private DatePicker date;
    @FXML
    private StackPane stackPane;
    @FXML
    private GridPane gridPane;
    @FXML
    private RadioButton radioCash, radioDeffer, radioRate, radioAmount;
    @FXML
    private TextArea txtNotes;
    @FXML
    private Label labelTitle;
    @FXML
    private Label labelReturnedBadge;
    private MaskerPaneSetting maskerPaneSetting;
    private ReturnEntryCoordinator returnEntry;

    public BuyController2(DataInterface<?, ?, T3, T4> dataInterface, int numInvoiceUpdate) throws Exception {
        super(dataInterface, numInvoiceUpdate);
        this.invoicePostSaveService = new InvoicePostSaveService(
                eventBus, dataInterface.invoiceSide());
        this.invoiceLineService = new InvoiceLineService<>(
                dataInterface.designInterface().documentType(), numInvoiceUpdate,
                dataInterface.invoiceBuy()::object_TableData);
        CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
        this.invoiceExpiryService = new InvoiceExpiryService(
                dataInterface.designInterface().documentType(), numInvoiceUpdate,
                itemId -> cardItemService.expiryBalancesByItem(invoiceStockId, itemId));
        this.invoiceItemSelectionService = new InvoiceItemSelectionService(
                dataInterface.designInterface().documentType(), itemsService,
                dataInterface.invoiceBuy()::getItemsPrice);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        applyInvoiceThemeClass();
        labelName();
        tableSetting();
        bindEditorState();
        otherSetting();
        addTextSearchName();
        addTextSearchItems();
        configureItemEntry();
        configureReturnEntry();
        action();
        publisherData();
        disableData();
        totalSetting();
        buttonGraphic();

        if (num_invoice_update > 0) {
            selectData();
        } else {
            getSavedCustomerAndDelegate();
        }
    }

    private void applyInvoiceThemeClass() {
        stackPane.getStyleClass().removeAll(
                "invoice-sales",
                "invoice-purchases",
                "invoice-return"
        );

        String invoiceName = dataInterface.designInterface().nameTextOfInvoice();
        labelTitle.setText(invoiceName);

        // The return-only controls are ReturnEntryCoordinator.configure()'s business.
        boolean isReturn = dataInterface.designInterface().documentType().isReturn();

        // isReturn(), not a comparison against SALES_RETURN alone: that left
        // PURCHASE_RETURN falling into the plain "purchases" branch below, styled
        // identically to a real purchase invoice with no visual distinction at all.
        if (isReturn) {
            stackPane.getStyleClass().add("invoice-return");
        } else if (dataInterface.designInterface().showDataForCustomer()) {
            stackPane.getStyleClass().add("invoice-sales");
        } else {
            stackPane.getStyleClass().add("invoice-purchases");
        }
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnNew.setGraphic(createIcon(images.add));
        btnUpdateItem.setGraphic(createIcon(images.update));
        btnSearch.setGraphic(createIcon(images.search));
        btnSave.setGraphic(createIcon(images.save));
        btnPrintSave.setGraphic(createIcon(images.print));
    }

    private void getSavedCustomerAndDelegate() {
        if (dataInterface.designInterface().showDataForCustomer())
            try {
                var s = SettingTabLanguageController.publishCustomer(customerService);
                textSearchName.set(s);

                var s1 = SettingTabLanguageController.publishDelegate(employeeService);
                comboDelegate.getSelectionModel().select(s1);
            } catch (Exception e) {
                logError(e);
            }
    }

    private void labelName() {
        var lang = LanguageManager.getInstance();

        // labels - التسميات
        last1.setText(lang.getString("invoice.count.items"));
        last2.setText(lang.getString("invoice.count.pieces"));
        last3.setText(lang.getString("invoice.total"));
        last4.setText(lang.getString("invoice.total.discount"));
        last5.setText(lang.getString("invoice.total.after.discount"));
        labelDelegate.setText(lang.getString("invoice.delegate"));
        labelNum.setText(lang.getString("invoice.code"));
        labelName.setText(lang.getString("invoice.name"));
        labelBarcode.setText(lang.getString("invoice.barcode"));
        labelDate.setText(lang.getString("invoice.date"));
        labelCondition.setText(lang.getString("invoice.type"));
        labelSearchBy.setText(lang.getString("invoice.search.by"));
        labelPrice.setText(lang.getString("invoice.price"));
        labelQuantity.setText(lang.getString("invoice.quantity"));
        labelItemBalance.setText(lang.getString("invoice.item.balance"));
        labelTotals.setText(lang.getString("invoice.totals"));
        labelTreasury.setText(lang.getString("invoice.treasury"));
        labelStock.setText(lang.getString("invoice.stock"));
        labelNotes.setText(lang.getString("invoice.notes"));

        // combo prompts - نصوص الاختيارات
        comboTreasury.setPromptText(lang.getString("invoice.treasury"));
        comboDelegate.setPromptText(lang.getString("invoice.delegate"));
        comboStock.setPromptText(lang.getString("invoice.stock"));
        comboType.setPromptText(lang.getString("invoice.type"));

        // text field prompts - نصوص الحقول
        txtBarcode.setPromptText(lang.getString("invoice.barcode"));

        // buttons - الأزرار
        btnNew.setText(lang.getString("invoice.btn.new"));
        btnSearch.setText(lang.getString("invoice.btn.search"));
        btnSave.setText(lang.getString("invoice.btn.save"));
        btnPrintSave.setText(lang.getString("invoice.btn.save.print"));
        btnUpdateItem.setText(lang.getString("invoice.btn.update.item"));
        btnAdd.setText(lang.getString("invoice.btn.add"));
    }

    private void addTextSearchName() {
        try {
            TextSearchApplication<T3> customersTextSearchApplication = new TextSearchApplication<>(dataInterface.nameAndAccountInterface().searchInterface());
            nameSearchController = customersTextSearchApplication.getTextSearchController();
            textSearchName = nameSearchController.textNameProperty();
            gridPane.add(customersTextSearchApplication.getPane(), 1, 1);

            textSearchName.addListener((observableValue, s, string) -> {
                try {
                    getCodeAccountAndBalance(string);
                    txtBarcode.requestFocus();
                    var object = nameService.getObject(nameAndAccountInterface.nameList(), string);
                    priceTypeByNameId = t3NameData.priceId(object);
                    if (itemEntry != null) {
                        itemEntry.setPriceTier(priceTypeByNameId);
                    }
//                updateAllPrices();
                } catch (Exception e) {
                    logError(e);
                }
            });

        } catch (Exception e) {
            logError(e);
        }
    }

    private void addTextSearchItems() {
        try {
            TextSearchApplication<ItemsModel> customersTextSearchApplication = new TextSearchApplication<>(new ItemsSearch(itemsService));
            textSearchItems = customersTextSearchApplication.getTextSearchController().textNameProperty();
            gridPane.add(customersTextSearchApplication.getPane(), 3, 2);
        } catch (IOException e) {
            logError(e);
        }
    }

    private void configureItemEntry() {
        itemEntry = new InvoiceItemEntryCoordinator(
                new InvoiceItemEntryCoordinator.Controls(
                        txtBarcode, txtPrice, txtQuantity, txtItemBalance, txtTotals,
                        comboType, btnAdd, btnUpdateItem),
                editor,
                invoiceItemSelectionService,
                textSearchItems,
                () -> invoiceStockId,
                this::resolveSelectedPriceTier,
                this::scaleBarcodeSettings,
                () -> getInvoiceAddItemDirect(),
                this::addData,
                this::addItem,
                this::handleItemEntryError);
        itemEntry.configure();
    }

    private void configureReturnEntry() {
        returnEntry = new ReturnEntryCoordinator(
                dataInterface.designInterface().documentType(),
                new ReturnEntryCoordinator.Controls(
                        btnReturnFromInvoice, labelReturnedBadge,
                        name -> comboDelegate.getSelectionModel().select(name),
                        // Setting the search text is what the party is chosen by
                        // everywhere on this screen; its listener resolves the code.
                        name -> textSearchName.set(name)),
                itemsService::findItemById,
                new ReturnEntryCoordinator.LineAppender() {
                    @Override
                    public BasePurchasesAndSales append(InvoiceLineDraft draft) throws DaoException {
                        return appendReturnLine(draft);
                    }

                    @Override
                    public boolean isEmpty() {
                        return table.getItems().isEmpty();
                    }

                    @Override
                    public void clear() {
                        table.getItems().clear();
                    }
                },
                employeeService::getDelegateById,
                this::partyNameById,
                error -> AllAlerts.handleError(
                        LanguageManager.getInstance().getString("return.dialog.title"), error));
        returnEntry.configure();
    }

    /**
     * mergeRepeated=false: two picked lines of the same item must stay distinct rows,
     * each keeping its own {@code sourceLineId}, rather than being folded into one row
     * that could only point at one of them.
     */
    /** The customer's or supplier's name, whichever side this screen is serving. */
    private String partyNameById(int partyId) throws Exception {
        for (T3 party : nameAndAccountInterface.nameList()) {
            if (party != null && party.getId() == partyId) {
                return party.getName();
            }
        }
        return null;
    }

    private BasePurchasesAndSales appendReturnLine(InvoiceLineDraft draft) throws DaoException {
        return invoiceLineService.add(
                table.getItems(), draft, false, getSelWithoutBalance()).line();
    }

    private void action() {
        btnNew.setOnAction(actionEvent -> {
            if (table.getItems().isEmpty() || AllAlerts.confirm_all(
                    LanguageManager.getInstance().getString("confirm"),
                    LanguageManager.getInstance().getString("invoice.confirm.new.invoice"))) {
                reset_all();
            }
        });
        btnSave.setOnAction(event -> saveInvoice(false));
        btnPrintSave.setOnAction(actionEvent -> saveInvoice(true));
        btnSearch.setOnAction(actionEvent -> openSearchItems());
    }

    private void openSearchItems() {
        try {
            SearchItemsApplication itemsApplication = new SearchItemsApplication(dataInterface);

            itemsApplication.start(new Stage());
            itemsApplication.getSearchItems().selectedItemProperty().addListener((observableValue, t1s, t1) -> {
                if (t1 != null) {
                    table.getItems().addAll(t1);
                }
            });
        } catch (Exception e) {
            logError(e);
        }
    }

    private int resolveSelectedPriceTier() throws Exception {
        String selectedName = textSearchName == null ? null : textSearchName.get();
        if (selectedName == null || selectedName.isBlank()) {
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("invoice.error.name.required"));
        }
        T3 party = nameService.getObject(nameAndAccountInterface.nameList(), selectedName);
        if (party == null) {
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("invoice.error.name.not.found"));
        }
        priceTypeByNameId = t3NameData.priceId(party);
        return priceTypeByNameId;
    }

    private InvoiceItemSelectionService.ScaleBarcodeSettings scaleBarcodeSettings() {
        return new InvoiceItemSelectionService.ScaleBarcodeSettings(
                getSettingBarcodeScaleActive(), getSettingBarcodeStart(),
                getSettingBarcodeCountScale());
    }

    private void handleItemEntryError(Exception error, boolean scaleBarcode) {
        if (scaleBarcode) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.scale.barcode.title"), error);
        } else {
            logError(error);
        }
    }

    private void getCodeAccountAndBalance(String newValue) {
        if (newValue != null) {
            List<T3> nameList;
            try {
                nameList = nameAndAccountInterface.nameList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            codeAccount = nameService.getCodeByName(nameList, newValue);
        }
    }

    private int addRowT(InvoiceLineDraft draft) throws DaoException {
        boolean mergeRepeated = getInvoiceIncreaseItemOneTable()
                && !draft.item().getBarcode().startsWith(String.valueOf(getSettingBarcodeStart()));
        invoiceLineService.add(editor.lines(), draft, mergeRepeated, getSelWithoutBalance());
        return 1;
    }

    private void addData() {
        try {
            long startTime = System.nanoTime();
            InvoiceLineDraft draft = itemEntry.draft();

            // Captured before the row is added: itemEntry.clear() resets the selection after
            // the optional modal expiry dialog has closed.
            final ItemsModel addedItem = draft.item();
            invoiceLineService.validate(table.getItems(), draft, getSelWithoutBalance());

            InvoiceExpiryOptions expiryOptions = invoiceExpiryService.optionsFor(
                    addedItem, table.getItems());
            if (expiryOptions.mode() == InvoiceExpiryOptions.Mode.NOT_REQUIRED) {
                if (addRowT(draft) == 1) {
                    warnIfStockIsLow(addedItem);
                    itemEntry.clear();
                }
            } else {
                LocalDate selectedExpiry = new ChoiceItemExpireDate(expiryOptions)
                        .showAndWait()
                        .orElse(null);
                if (selectedExpiry == null) {
                    return;
                }
                invoiceExpiryService.validateSelectedDate(
                        expiryOptions, selectedExpiry,
                        ItemUnits.toBase(draft.quantity(), draft.unit()));
                if (addRowT(draft.withExpirationDate(selectedExpiry)) == 1) {
                    warnIfStockIsLow(addedItem);
                    itemEntry.clear();
                }
            }

            long endTime = System.nanoTime();

            // تحويل النانو ثانية إلى مللي ثانية لسهولة القراءة
            long duration = (endTime - startTime) / 1_000_000;
            log.debug("Loaded the invoice screen in {} ms", duration);
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Raises the low-stock alert for a row that has just been added to a sales
     * invoice.
     * <p>
     * Sales only, and not sales returns: a return puts stock back, so a low balance
     * there is not something to warn about. The screen says which of the four documents
     * it is writing, so the question is asked directly - it used to be answered by
     * comparing the screen's permission against {@code SALES_SHOW}, because a permission
     * was the one field that differed between {@code DesignCustom} and
     * {@code DesignCustomReturn}.
     * <p>
     * The balance is read after the row is in the table, so the quantity just added
     * is already part of {@code alreadyOnInvoice} and the figure reported is what
     * the sale leaves behind.
     */
    private void warnIfStockIsLow(ItemsModel item) {
        if (item == null || dataInterface.designInterface().documentType() != DocumentType.SALES) {
            return;
        }
        try {
            double alreadyOnInvoice = invoiceLineService.quantityInBase(
                    table.getItems(), item.getId());

            StockLevelAlert.check(item, StockLevelAlert.remainingAfter(item, alreadyOnInvoice));
        } catch (Exception e) {
            // The row is already added and the sale is fine; only the warning failed.
            log.error("Could not check the stock level after adding item {}", item.getId(), e);
        }
    }

    private void selectData() {
        try {
            InvoiceHeaderView header = dataInterface.loadInvoiceHeader(num_invoice_update);
            invoiceStockId = header.stockId();
            if (comboStock != null) comboStock.getSelectionModel().select(
                    comboStock.getItems().stream().filter(stock -> stock.getId() == invoiceStockId).findFirst().orElse(null));
            BaseTotals dataById = header.totals();
            int id = dataById.getId();
            InvoiceType invoiceType = dataById.getInvoiceType();
            String invoiceDate = dataById.getDate();

            date.setValue(LocalDate.parse(invoiceDate));
            textSearchName.set(header.partyName());
            comboDelegate.getSelectionModel().select(header.delegateName());
            txtNum.setText(String.valueOf(id));
            codeAccount = header.partyId();
            List<? extends BasePurchasesAndSales> collection =
                    dataInterface.totalsAndPurchaseList().purchaseOrSalesList(id, id);
            editor.replaceLines(collection);
            invoiceLineService.captureOriginalLines(collection);
            invoiceExpiryService.captureOriginalLines(collection);
            radioCash.setSelected(invoiceType.equals(InvoiceType.CASH));
            radioDeffer.setSelected(invoiceType.equals(InvoiceType.DEFER));
            txtPaid.setText(String.valueOf(dataById.getPaid()));
            txtNotes.setText(dataById.getNotes());
            txtOtherDiscount.setText(String.valueOf(dataById.getDiscount()));
            // Before the guards can check an edit they have to know what this return
            // was linked to - without it ReturnGuard reads a source of 0 and treats the
            // whole document as a free return it has nothing to compare against.
            returnEntry.restoreSource(
                    header.sourceInvoiceNumber(), header.returnReason());
            returnEntry.showReturnedStatus(id);
        } catch (Exception e) {
            logError(e);
        }
    }


    private void saveInvoice(boolean print) {
        if (editor.isSaving()) {
            return;
        }
        try {
            validateInvoiceForSave();

            if (!ShiftContext.requireOpenShift()) {
                return;
            }

            if (!returnEntry.confirmIfUnlinked()) {
                return;
            }

            if (!AllAlerts.confirmSave()) {
                return;
            }
            saveInBackground(print, captureSaveCommand());
        } catch (InvoiceValidationException e) {
            focusValidationTarget(e.target());
            logError(e);
        } catch (Exception e) {
            logError(e);
        }
    }

    private InvoiceSaveCommand captureSaveCommand() throws InvoiceValidationException {
        DiscountType discountType = radioAmount.isSelected()
                ? DiscountType.AMOUNT
                : DiscountType.RATE;
        updatePaymentViewModel(false);
        InvoicePaymentTerms payment = editor.requireValidPayment();
        return new InvoiceSaveCommand(
                num_invoice_update, date.getValue(), payment.invoiceType(),
                payment.discountAmount(), discountType, payment.paidAmount(),
                txtNotes.getText(), codeAccount, textSearchName.get(),
                comboTreasury.getSelectionModel().getSelectedItem(),
                comboDelegate.getSelectionModel().getSelectedItem(),
                getSelWithoutBalance(), returnEntry.sourceInvoiceNumber(),
                returnEntry.selectedReturnReason(),
                List.copyOf(table.getItems()), invoiceStockId);
    }

    private void saveInBackground(boolean print, InvoiceSaveCommand command) {
        editor.setSaving(true);
        javafx.concurrent.Task<InvoiceSaveResult> task = new javafx.concurrent.Task<>() {
            @Override
            protected InvoiceSaveResult call() throws Exception {
                return dataInterface.saveInvoice(command);
            }
        };
        task.runningProperty().addListener((observable, wasRunning, running) ->
        {
            editor.setSaving(running);
            maskerPaneSetting.setVisible(running);
        });
        task.setOnSucceeded(event -> afterSuccessfulSave(print, command, task.getValue()));
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            if (failure instanceof InvoiceValidationException validation) {
                focusValidationTarget(validation.target());
            }
            logError(failure instanceof Exception exception
                    ? exception
                    : new RuntimeException(failure));
        });
        Thread worker = new Thread(task, "invoice-save");
        worker.setDaemon(true);
        worker.start();
    }

    private void afterSuccessfulSave(boolean print, InvoiceSaveCommand command,
                                     InvoiceSaveResult result) {
        AllAlerts.alertSave();
        if (designInterface.showScreenPaidInInvoice()
                && getInvoiceShowScreenPaid()
                && result.payment().invoiceType() == InvoiceType.CASH) {
            showCashChangeDialog(result.payment().net());
        }

        printInvoice(preparePrintRequest(print, command, result));
        if (result.updated()) {
            table.getScene().getWindow().hide();
        }
        reset_all();
        handlePostSave();
    }

    private void validateInvoiceForSave() throws InvoiceValidationException {
        InvoiceLineTotals totals = editor.totals();
        InvoiceSaveValidator.Problem problem = InvoiceSaveValidator.firstProblem(
                totals.lineCount(), totals.hasInvalidLine(), date.getValue(), LocalDate.now(),
                designInterface.documentType().hasDelegate(),
                comboDelegate.getSelectionModel().getSelectedItem() != null,
                comboTreasury.getSelectionModel().getSelectedItem() != null,
                codeAccount).orElse(null);
        if (problem != null) {
            throw new InvoiceValidationException(problem.target(), problem.message());
        }
        try {
            invoiceLineService.validateForSave(table.getItems(), getSelWithoutBalance());
        } catch (DaoException e) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.LINES, e.getMessage());
        }
        updatePaymentViewModel(false);
        editor.requireValidPayment();
    }

    private void focusValidationTarget(InvoiceSaveValidator.Target target) {
        Runnable requestFocus = switch (target) {
            case LINES -> table::requestFocus;
            case DATE -> date::requestFocus;
            case DELEGATE -> comboDelegate::requestFocus;
            case TREASURY -> comboTreasury::requestFocus;
            case ACCOUNT -> nameSearchController::requestFocus;
            case PAYMENT_TYPE -> radioCash::requestFocus;
            case DISCOUNT -> txtOtherDiscount::requestFocus;
            case PAID -> txtPaid::requestFocus;
        };
        Platform.runLater(requestFocus);
    }

    private void handlePostSave() {
        invoicePostSaveService.afterSave(getInvoiceBackupAfterSave())
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        Platform.runLater(() -> logError(asException(failure)));
                    }
                });
    }

    private Exception asException(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause instanceof Exception exception ? exception : new RuntimeException(cause);
    }

    private InvoicePrintRequest preparePrintRequest(boolean print,
                                                    InvoiceSaveCommand command,
                                                    InvoiceSaveResult result) {
        if (!print) {
            return null;
        }
        try {
            return invoicePrintService.prepare(command.lines(), command.partyName(),
                    result.invoiceNumber(), result.payment().discount(),
                    LocalDateTime.now().format(DATE_TIME_FORMATTER), command.invoiceDate(),
                    getPrintPaperReceiptInvoice(),
                    ShowInvoiceDetails.invoiceDetails(
                            dataInterface.loadInvoiceHeader(result.invoiceNumber())),
                    dataInterface.designInterface().nameTextOfInvoice());
        } catch (DaoException e) {
            logError(e);
            return null;
        }
    }

    private void printInvoice(InvoicePrintRequest request) {
        if (request != null) {
            maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("invoice.masker.printing"),
                    () -> invoicePrintService.print(request));
        }
    }

    private void otherSetting() {
        var lang = LanguageManager.getInstance();
        labelNotes.setText(lang.getString("invoice.notes"));
        txtNotes.setPromptText(lang.getString("invoice.notes"));
        labelInvoiceTotal.setText(lang.getString("total"));
        radioCash.setText(lang.getString("cash"));
        radioDeffer.setText(lang.getString("defer"));
        radioAmount.setText(lang.getString("invoice.amount"));
        radioRate.setText(lang.getString("invoice.rate"));
        radioRate.setDisable(true);
        radioAmount.setDisable(true);

        // others
        DateSetting.dateAction(date);
        // An invoice cannot be dated after today. Greyed out in the calendar here and
        // checked again in saveInvoice, because the value can also arrive from an
        // existing record rather than from the user picking it.
        DateSetting.noFutureDates(date);
        whenEnterPressed(txtBarcode, txtPrice, txtQuantity, btnAdd);
        setTextFormatter(txtPaid, txtOtherDiscount, txtItemBalance, txtPrice, txtQuantity, txtTotals);
        Utils.replaceNonDigitChar(txtBarcode);
        txtNum.setText(num_invoice_update > 0 ? String.valueOf(num_invoice_update) : lang.getString("invoice.number.generate"));
        // delegate data
        comboStock.setItems(FXCollections.observableArrayList(getStocks()));
        comboStock.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Stock stock) { return stock == null ? "" : stock.getName(); }
            @Override public Stock fromString(String value) { return null; }
        });
        comboStock.getSelectionModel().select(comboStock.getItems().stream()
                .filter(stock -> stock.getId() == DefaultStock.ID).findFirst().orElse(null));
        comboStock.getSelectionModel().selectedItemProperty().addListener((obs, oldStock, newStock) -> {
            if (newStock != null) invoiceStockId = newStock.getId();
        });
        comboStock.disableProperty().bind(Bindings.isNotEmpty(table.getItems()));

        // delegate data
        comboDelegate.setItems(FXCollections.observableArrayList(getDelegateNames()));
        // treasury data
        comboTreasury.setItems(FXCollections.observableArrayList(getListTreasuryModelNames()));

        try {
            comboTreasury.getSelectionModel().select(treasuryService.getTreasuryById(1).getName());
        } catch (DaoException e) {
            logError(e);
        }
        // for name and account

        this.txtBarcode.clear();
        Platform.runLater(() -> txtBarcode.requestFocus());

    }

    @NotNull
    private List<Stock> getStocks() {
        try { return stockService.getStocks(); } catch (DaoException e) { logError(e); return List.of(); }
    }

    @NotNull
    private List<String> getListTreasuryModelNames() {
        try {
            return treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            logError(e);
        }
        return new ArrayList<>();
    }


    private void reset_all() {
        table.getItems().clear();
        txtNum.setText(LanguageManager.getInstance().getString("invoice.number.generate"));
        txtPrice.setText(String.valueOf(0));
        txtQuantity.setText(String.valueOf(0));
        txtTotals.setText(String.valueOf(0));
        txtItemBalance.setText(String.valueOf(0));

        txtOtherDiscount.setText("0.0");
        txtPaid.setText("0.0");
        txtNotes.clear();
        radioCash.setSelected(true);
        radioDeffer.setSelected(false);
        returnEntry.reset();
    }

    private void publisherData() {
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(EmployeesChanged.class
                    , event -> comboDelegate.setItems(FXCollections.observableArrayList(getDelegateNames()))));
        }
        // An invoice window is opened per invoice and closed again; the bus behind
        // it lives for the whole process.
        subscriptions.disposeWith(stackPane);
    }

    private List<String> getDelegateNames() {
        try {
            return employeeService.getDelegateNames();
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private void totalSetting() {
        txtPaid.disableProperty().bind(radioCash.selectedProperty());
        txtPaid.setPromptText(LanguageManager.getInstance().getString("invoice.paid.prompt.deferred"));
        radioCash.setTooltip(new Tooltip(LanguageManager.getInstance().getString("invoice.tooltip.cash")));
        radioDeffer.setTooltip(new Tooltip(LanguageManager.getInstance().getString("invoice.tooltip.deferred")));
        radioCash.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                refreshPaymentSummary(false);
            }
        });
        radioDeffer.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                refreshPaymentSummary(true);
            }
        });
        txtOtherDiscount.textProperty().addListener((observable, oldValue, newValue) -> refreshPaymentSummary(false));
        txtSumTotals.textProperty().addListener((observable, oldValue, newValue) -> refreshPaymentSummary(false));
        txtPaid.textProperty().addListener((observable, oldValue, newValue) -> refreshPaymentSummary(false));
        textInvoiceTotal.textProperty().bind(txtRestAfterDiscount.textProperty());
        refreshPaymentSummary(false);
    }

    private void refreshPaymentSummary(boolean resetDeferredPayment) {
        if (updatingPaymentUi) {
            return;
        }
        updatingPaymentUi = true;
        try {
            InvoicePaymentTerms terms = updatePaymentViewModel(resetDeferredPayment);
            txtRestAfterDiscount.setText(MoneyMath.text(terms.netAmount()));
            if (terms.invoiceType() == InvoiceType.CASH || resetDeferredPayment) {
                txtPaid.setText(MoneyMath.text(terms.paidAmount()));
            }
            txtRestAfterPaid.setText(MoneyMath.text(terms.remainingAmount()));
            updatePaymentLabels(terms.invoiceType());
        } finally {
            updatingPaymentUi = false;
        }
        updatePaymentValidationStyle();
    }

    private void updatePaymentLabels(InvoiceType type) {
        var lang = LanguageManager.getInstance();
        boolean deferred = type == InvoiceType.DEFER;
        labelPaid.setText(deferred ? lang.getString("invoice.label.paid.advance") : lang.getString("invoice.label.paid.cash"));
        labelRemaining.setText(deferred ? lang.getString("invoice.label.remaining.account") : lang.getString("invoice.remaining"));
        labelNetAfterDiscount.setText(lang.getString("invoice.label.net.after.discount"));
    }

    private void updatePaymentValidationStyle() {
        setValidationError(txtOtherDiscount, false);
        setValidationError(txtPaid, false);
        InvoiceSaveValidator.Target target = editor.invalidPaymentTarget();
        if (target == InvoiceSaveValidator.Target.DISCOUNT) {
            setValidationError(txtOtherDiscount, true);
        } else if (target == InvoiceSaveValidator.Target.PAID) {
            setValidationError(txtPaid, true);
        }
    }

    private void setValidationError(Control control, boolean invalid) {
        if (invalid) {
            if (!control.getStyleClass().contains("validation-error")) {
                control.getStyleClass().add("validation-error");
            }
        } else {
            control.getStyleClass().remove("validation-error");
        }
    }

    private InvoiceType selectedInvoiceType() {
        if (radioCash.isSelected()) return InvoiceType.CASH;
        if (radioDeffer.isSelected()) return InvoiceType.DEFER;
        return null;
    }

    private boolean paymentDraftInvalid() {
        updatePaymentViewModel(false);
        return !editor.isPaymentValid();
    }

    private InvoicePaymentTerms updatePaymentViewModel(boolean resetDeferredPayment) {
        return editor.updatePayment(
                selectedInvoiceType(), resetDeferredPayment,
                MoneyMath.parseOrZero(txtOtherDiscount.getText()),
                MoneyMath.parseOrZero(txtPaid.getText()));
    }

    private void bindEditorState() {
        textSumCount.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(editor.totals().lineCount()), editor.totalsProperty()));
        txtSumQuantity.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(editor.totals().quantity()), editor.totalsProperty()));
        txtBeforeDiscount.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(editor.totals().gross()), editor.totalsProperty()));
        txtSumDiscount.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(editor.totals().discount()), editor.totalsProperty()));
        txtSumTotals.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(editor.totals().net()), editor.totalsProperty()));
    }

    private void tableSetting() {
        DocumentType documentType = designInterface.documentType();
        InvoiceItemCatalogService catalogService = new InvoiceItemCatalogService(
                documentType, itemsService, invoiceBuy::updateItemPrice);
        InvoiceLineEditService editService = new InvoiceLineEditService(
                documentType, catalogService, () -> invoiceStockId);
        new InvoiceTableCoordinator<>(table, editor.lines(), editService,
                () -> priceTypeByNameId, () -> getInvoiceUpdatePrice(),
                editor::refreshTotals, getClass(), CurrentUser.get().getId() == 1)
                .configure();
    }

    private void disableData() {
        comboDelegate.setVisible(designInterface.showDataForCustomer());
        labelDelegate.setVisible(designInterface.showDataForCustomer());

        BooleanBinding nonPositiveTotal = Bindings.createBooleanBinding(
                () -> {
                    try {
                        return Double.parseDouble(txtSumTotals.getText()) <= 0;
                    } catch (NumberFormatException ignored) {
                        return true;
                    }
                }, txtSumTotals.textProperty());
        BooleanBinding binding = nonPositiveTotal
                .or(Bindings.isEmpty(table.getItems()))
                .or(editor.invalidLinesProperty())
                .or(comboTreasury.valueProperty().isNull());

        BooleanBinding invalidPayment = Bindings.createBooleanBinding(
                this::paymentDraftInvalid,
                txtSumTotals.textProperty(), txtOtherDiscount.textProperty(),
                txtPaid.textProperty(), radioCash.selectedProperty(),
                radioDeffer.selectedProperty(), editor.invalidLinesProperty());
        PermissionKey writePermission = num_invoice_update > 0
                ? designInterface.documentType().updatePermission()
                : designInterface.documentType().createPermission();
        BooleanBinding writeDenied = Bindings.createBooleanBinding(
                () -> !AuthorizationGuard.isGranted(writePermission));
        binding = binding.or(invalidPayment).or(writeDenied).or(editor.savingProperty());

        if (designInterface.documentType().hasDelegate()) {
            binding = binding.or(comboDelegate.valueProperty().isNull());
        }

        btnPrintSave.disableProperty().bind(binding);
        btnSave.disableProperty().bind(binding);
        var observableValue = new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return num_invoice_update > 0;
            }
        };
        btnNew.disableProperty().bind(observableValue);
        BooleanBinding itemMutationDenied = Bindings.createBooleanBinding(
                () -> !AuthorizationGuard.isGranted(itemMutationPermission(txtBarcode.getText())),
                txtBarcode.textProperty());
        btnUpdateItem.disableProperty().bind(observableValue.or(itemMutationDenied));
    }

    static PermissionKey itemMutationPermission(String barcode) {
        return barcode == null || barcode.isBlank()
                ? AppPermissions.ITEMS_CREATE
                : AppPermissions.ITEMS_UPDATE;
    }

    private void addItem(int num) {
        try {
            new AddItemApplication(num).start(new Stage());
        } catch (Exception e) {
            logError(e);
        }
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        var pane = new OpenFxmlApplication(this).getPane();
        String style = ThemeManager.getStylesheet();
        pane.getStylesheets().addAll(style);
        return pane;
    }

    @Override
    public String title() {
        return LanguageManager.getInstance().getString("update");
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void logError(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.operation.title"), e);
    }

}
