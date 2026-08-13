package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.authorization.AppPermissions;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.TextSearchController;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.controller.setting.SettingTabLanguageController;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.features.invoice.InvoiceItemCatalogService;
import com.hamza.account.features.invoice.InvoiceExpiryOptions;
import com.hamza.account.features.invoice.InvoiceExpiryService;
import com.hamza.account.features.invoice.InvoiceLineDraft;
import com.hamza.account.features.invoice.InvoiceLineEditService;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.features.invoice.InvoiceLineTotals;
import com.hamza.account.features.invoice.InvoicePaymentTerms;
import com.hamza.account.features.invoice.InvoicePaymentViewModel;
import com.hamza.account.features.invoice.InvoicePostSaveService;
import com.hamza.account.features.invoice.InvoicePrintRequest;
import com.hamza.account.features.invoice.InvoicePrintService;
import com.hamza.account.features.invoice.InvoiceSaveCommand;
import com.hamza.account.features.invoice.InvoiceSaveResult;
import com.hamza.account.features.invoice.InvoiceSaveService;
import com.hamza.account.features.invoice.InvoiceSaveValidator;
import com.hamza.account.features.invoice.InvoiceValidationException;
import com.hamza.account.features.notification.StockLevelAlert;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
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
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.SearchItemsApplication;
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "invoice/buy-view2.fxml")
public class BuyController2<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends BuyData<T1, T2, T3, T4> implements Initializable, AppSettingInterface {

    private final ObservableList<T1> myObservableList = FXCollections.observableArrayList();
    private final Subscriptions subscriptions = new Subscriptions();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final DataPublisher dataPublisher;
    private final ObjectProperty<ItemsModel> itemsModel = new SimpleObjectProperty<>(new ItemsModel());
    private final BooleanProperty saveInProgress = new SimpleBooleanProperty();
    private final InvoicePaymentViewModel paymentViewModel = new InvoicePaymentViewModel();
    private final InvoicePrintService invoicePrintService = new InvoicePrintService();
    private final CustomerService customerService = ServiceRegistry.get(CustomerService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final InvoiceSaveService<T1, T2, T3, T4> invoiceSaveService;
    private final InvoicePostSaveService invoicePostSaveService;
    private final InvoiceLineService<T1> invoiceLineService;
    private final InvoiceExpiryService invoiceExpiryService;
    private final InvoiceItemSelectionService invoiceItemSelectionService;
    private int priceTypeByNameId = 1; // use a first price type
    private int codeAccount;
    private boolean updatingPaymentUi;
    private boolean applyingItemSelection;
    private StringProperty textSearchName, textSearchItems;
    private TextSearchController<T3> nameSearchController;
    @FXML
    private Label labelNum, labelName, labelBarcode, labelDate, labelCondition, labelDelegate, labelTreasury, labelSearchBy, labelPrice, labelQuantity, labelItemBalance, labelTotals, last1, last2, last3, last4, last5, labelNotes, labelInvoiceTotal, labelPaid, labelRemaining, labelNetAfterDiscount;
    @FXML
    @Getter
    private Button btnAdd, btnSave, btnPrintSave, btnNew, btnSearch, btnUpdateItem;
    @FXML
    private ComboBox<String> comboType, comboDelegate, comboTreasury;
    @FXML
    private TextField txtNum, txtBarcode, txtPrice, txtQuantity, txtItemBalance, txtTotals, txtOtherDiscount, txtPaid, txtRestAfterPaid, txtRestAfterDiscount;
    @FXML
    private TableView<T1> table;
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
    private MaskerPaneSetting maskerPaneSetting;

    public BuyController2(DataInterface<T1, T2, T3, T4> dataInterface, DataPublisher dataPublisher, int numInvoiceUpdate) throws Exception {
        super(dataInterface, dataPublisher, numInvoiceUpdate);
        this.dataPublisher = dataPublisher;
        this.invoiceSaveService = new InvoiceSaveService<>(dataInterface,
                treasuryService::getTreasuryByName, employeeService::getDelegateByName);
        this.invoicePostSaveService = new InvoicePostSaveService(
                eventBus, dataInterface.invoiceSide());
        this.invoiceLineService = new InvoiceLineService<>(
                dataInterface.designInterface().documentType(), numInvoiceUpdate,
                dataInterface.invoiceBuy()::object_TableData);
        CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
        this.invoiceExpiryService = new InvoiceExpiryService(
                dataInterface.designInterface().documentType(), numInvoiceUpdate,
                cardItemService::expiryBalancesByItem);
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
        otherSetting();
        addTextSearchName();
        addTextSearchItems();
        action();
        publisherData(dataPublisher);
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

        if (invoiceName != null && invoiceName.contains(Setting_Language.WORD_RE_SALES)) {
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
        var lang = com.hamza.controlsfx.language.LanguageManager.getInstance();

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
        labelNotes.setText(lang.getString("invoice.notes"));

        // combo prompts - نصوص الاختيارات
        comboTreasury.setPromptText(lang.getString("invoice.treasury"));
        comboDelegate.setPromptText(lang.getString("invoice.delegate"));
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

            textSearchItems.addListener((observableValue, s, string) -> {
                if (string != null && !applyingItemSelection) {
                    selectItemByName(string);
                }
            });

        } catch (IOException e) {
            logError(e);
        }
    }

    private void action() {
        btnUpdateItem.setOnAction(actionEvent -> {
            if (txtBarcode.getText().isEmpty()) {
                addItem(0);
            } else
                addItem(itemsModel.get().getId());
        });

        btnAdd.setOnAction(actionEvent -> addData());
        btnNew.setOnAction(actionEvent -> {
            if (table.getItems().isEmpty() || AllAlerts.confirm_all("تأكيد", "هل تريد إلغاء الفاتورة الحالية والبدء من جديد؟ سيتم فقد كل الأصناف المُضافة غير المحفوظة.")) {
                reset_all();
            }
        });
        btnSave.setOnAction(event -> saveInvoice(false));
        btnPrintSave.setOnAction(actionEvent -> saveInvoice(true));
        btnSearch.setOnAction(actionEvent -> openSearchItems());
        txtBarcode.setOnKeyPressed(this::processBarcodeEntry);
        txtPrice.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER || keyEvent.getCode() == KeyCode.TAB) {
                txtQuantity.requestFocus();
            }
        });

        txtQuantity.textProperty().addListener((observableValue, string, t1) -> {
            if (t1.isEmpty() || t1.equals("0")) {
                txtQuantity.setText("1");
            }
            totalItemQuantityAndPrice();
        });
        txtPrice.textProperty().addListener(observable -> totalItemQuantityAndPrice());

        comboType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            ItemsModel model = itemsModel.get();
            if (model == null || model.getId() <= 0) return;
            try {
                var selection = invoiceItemSelectionService.selectUnit(
                        model, newValue, priceTypeByNameId);
                txtItemBalance.setText(String.valueOf(
                        roundToTwoDecimalPlaces(selection.balance())));
                txtPrice.setText(String.valueOf(selection.price()));
            } catch (Exception e) {
                logError(e);
            }
        });
    }

    private void openSearchItems() {
        try {
            SearchItemsApplication<T1> itemsApplication = new SearchItemsApplication<>(dataInterface);

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

    private void processBarcodeEntry(KeyEvent keyEvent) {
        if (keyEvent.getCode() != KeyCode.ENTER || txtBarcode.getText().isBlank()) {
            return;
        }

        String barcode = txtBarcode.getText();
        var scaleSettings = scaleBarcodeSettings();
        try {
            priceTypeByNameId = resolveSelectedPriceTier();
            InvoiceItemSelection selection = invoiceItemSelectionService.selectByBarcode(
                    barcode, DefaultStock.ID, priceTypeByNameId, scaleSettings);
            applyItemSelection(selection, true);

            if (getInvoiceAddItemDirect()) {
                addData();
            } else {
                txtPrice.requestFocus();
            }
        } catch (Exception e) {
            if (scaleSettings.matches(barcode)) {
                AllAlerts.handleError("قراءة باركود الميزان", e);
            } else {
                logError(e);
            }
            txtBarcode.requestFocus();
        }
    }

    private void selectItemByName(String itemName) {
        try {
            priceTypeByNameId = resolveSelectedPriceTier();
            InvoiceItemSelection selection = invoiceItemSelectionService.selectByName(
                    itemName, DefaultStock.ID, priceTypeByNameId);
            applyItemSelection(selection, false);
            txtPrice.requestFocus();
        } catch (Exception e) {
            Utils.clearAll(txtItemBalance, txtPrice, txtQuantity, txtTotals, txtBarcode);
            logError(e);
        }
    }

    private int resolveSelectedPriceTier() throws Exception {
        String selectedName = textSearchName == null ? null : textSearchName.get();
        if (selectedName == null || selectedName.isBlank()) {
            throw new UserValidationException(
                    Setting_Language.PLEASE_INSERT_ALL_DATA + ":- \n ادخل الاسم");
        }
        T3 party = nameService.getObject(nameAndAccountInterface.nameList(), selectedName);
        if (party == null) {
            throw new UserValidationException("الاسم المحدد غير موجود");
        }
        return t3NameData.priceId(party);
    }

    private InvoiceItemSelectionService.ScaleBarcodeSettings scaleBarcodeSettings() {
        return new InvoiceItemSelectionService.ScaleBarcodeSettings(
                getSettingBarcodeScaleActive(), getSettingBarcodeStart(),
                getSettingBarcodeCountScale());
    }

    private void applyItemSelection(InvoiceItemSelection selection, boolean updateSearchName) {
        applyingItemSelection = true;
        try {
            itemsModel.set(selection.item());
            txtBarcode.setText(selection.barcode());
            if (updateSearchName) {
                textSearchItems.set(selection.item().getNameItem());
            }

            List<String> unitNames = selection.units().stream()
                    .map(UnitsModel::getUnit_name)
                    .toList();
            comboType.setItems(FXCollections.observableArrayList(unitNames));
            comboType.getSelectionModel().select(selection.selectedUnit().getUnit_name());
            comboType.setDisable(unitNames.size() < 2);

            txtItemBalance.setText(String.valueOf(
                    roundToTwoDecimalPlaces(selection.balance())));
            txtPrice.setText(String.valueOf(selection.price()));
            txtQuantity.setText(String.valueOf(selection.quantity()));
            txtTotals.setText(String.valueOf(selection.total()));
        } finally {
            applyingItemSelection = false;
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
        invoiceLineService.add(myObservableList, draft, mergeRepeated, getSelWithoutBalance());
        sumTotals();
        return 1;
    }

    private void addData() {
        try {
            long startTime = System.nanoTime();
            double quantity = DoubleSetting.parseDoubleOrDefault(txtQuantity.getText());
            double price = DoubleSetting.parseDoubleOrDefault(txtPrice.getText());
            double discount = 0;

            // Captured before the row is added: clearData() resets itemsModel after
            // the optional modal expiry dialog has closed.
            final ItemsModel addedItem = itemsModel.get();
            UnitsModel selectedUnit = ItemUnits.unitByName(
                    addedItem, comboType.getSelectionModel().getSelectedItem());
            InvoiceLineDraft draft = new InvoiceLineDraft(
                    addedItem, selectedUnit, quantity, price, discount, null);
            invoiceLineService.validate(table.getItems(), draft, getSelWithoutBalance());

            InvoiceExpiryOptions expiryOptions = invoiceExpiryService.optionsFor(
                    addedItem, table.getItems());
            if (expiryOptions.mode() == InvoiceExpiryOptions.Mode.NOT_REQUIRED) {
                if (addRowT(draft) == 1) {
                    warnIfStockIsLow(addedItem);
                    clearData();
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
                    clearData();
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
//            T2 dataById = totalsAndPurchaseList.totalDao().getDataById(num_invoice_update);
            T2 dataById = totalsAndPurchaseList.totalDao().getDataById(num_invoice_update);
            TotalsDataInterface<T2> totalsDataInterface = dataInterface.totalDesignInterface().totalsDataInterface();
            int id = dataById.getId();
            String name = totalsDataInterface.getNameData(dataById);
            InvoiceType invoiceType = dataById.getInvoiceType();
            String invoiceDate = dataById.getDate();
            String getDelegate = totalsDataInterface.getDelegateData(dataById).getName();

            date.setValue(LocalDate.parse(invoiceDate));
            textSearchName.set(name);
            comboDelegate.getSelectionModel().select(getDelegate);
            txtNum.setText(String.valueOf(id));
            codeAccount = totalsDataInterface.getIdData(dataById);
//            List<T1> collection = dataInterface.totalsAndPurchaseList().purchaseOrSalesDao().loadAllById(num_invoice_update);
            List<T1> collection = dataInterface.totalsAndPurchaseList().purchaseOrSalesList(id, id);
            myObservableList.setAll(collection);
            invoiceLineService.captureOriginalLines(collection);
            invoiceExpiryService.captureOriginalLines(collection);
            radioCash.setSelected(invoiceType.equals(InvoiceType.CASH));
            radioDeffer.setSelected(invoiceType.equals(InvoiceType.DEFER));
            txtPaid.setText(String.valueOf(dataById.getPaid()));
            txtNotes.setText(dataById.getNotes());
            txtOtherDiscount.setText(String.valueOf(dataById.getDiscount()));
        } catch (Exception e) {
            logError(e);
        }
    }

    private void saveInvoice(boolean print) {
        if (saveInProgress.get()) {
            return;
        }
        try {
            validateInvoiceForSave();

            if (!ShiftContext.requireOpenShift()) {
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

    private InvoiceSaveCommand<T1> captureSaveCommand() throws InvoiceValidationException {
        DiscountType discountType = radioAmount.isSelected()
                ? DiscountType.AMOUNT
                : DiscountType.RATE;
        updatePaymentViewModel(false);
        InvoicePaymentTerms payment = paymentViewModel.requireValid();
        return new InvoiceSaveCommand<>(
                num_invoice_update, date.getValue(), payment.invoiceType(),
                payment.discountAmount(), discountType, payment.paidAmount(),
                txtNotes.getText(), codeAccount, textSearchName.get(),
                comboTreasury.getSelectionModel().getSelectedItem(),
                comboDelegate.getSelectionModel().getSelectedItem(),
                getSelWithoutBalance(),
                List.copyOf(table.getItems()));
    }

    private void saveInBackground(boolean print, InvoiceSaveCommand<T1> command) {
        saveInProgress.set(true);
        javafx.concurrent.Task<InvoiceSaveResult<T1, T2>> task = new javafx.concurrent.Task<>() {
            @Override
            protected InvoiceSaveResult<T1, T2> call() throws Exception {
                return invoiceSaveService.save(command);
            }
        };
        task.runningProperty().addListener((observable, wasRunning, running) ->
                {
                    saveInProgress.set(running);
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

    private void afterSuccessfulSave(boolean print, InvoiceSaveCommand<T1> command,
                                     InvoiceSaveResult<T1, T2> result) {
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
        InvoiceLineTotals totals = InvoiceLineTotals.from(table.getItems());
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
        paymentViewModel.requireValid();
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
                                                    InvoiceSaveCommand<T1> command,
                                                    InvoiceSaveResult<T1, T2> result) {
        if (!print) {
            return null;
        }
        return invoicePrintService.prepare(command.lines(), command.partyName(),
                result.invoiceNumber(), result.payment().discount(),
                LocalDateTime.now().format(DATE_TIME_FORMATTER), command.invoiceDate(),
                getPrintPaperReceiptInvoice(),
                ShowInvoiceDetails.invoiceDetails(dataInterface, result.invoice()),
                dataInterface.designInterface().nameTextOfInvoice());
    }

    private void printInvoice(InvoicePrintRequest request) {
        if (request != null) {
            maskerPaneSetting.showMaskerPane("طباعة الفاتورة",
                    () -> invoicePrintService.print(request));
        }
    }

    private void totalItemQuantityAndPrice() {
        double price = DoubleSetting.parseDoubleOrDefault(txtPrice.getText());
        double quantity = DoubleSetting.parseDoubleOrDefault(txtQuantity.getText());
        txtTotals.setText(MoneyMath.text(MoneyMath.multiply(price, quantity)));
    }

    private void otherSetting() {
        labelNotes.setText(Setting_Language.NOTES);
        txtNotes.setPromptText(Setting_Language.NOTES);
        labelInvoiceTotal.setText(Setting_Language.TOTAL);
        radioCash.setText(Setting_Language.WORD_CASH);
        radioDeffer.setText(Setting_Language.WORD_DEFER);
        radioAmount.setText(Setting_Language.THE_AMOUNT);
        radioRate.setText(Setting_Language.WORD_RATE);
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
        txtNum.setText(num_invoice_update > 0 ? String.valueOf(num_invoice_update) : Setting_Language.generate);
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
    private List<String> getListTreasuryModelNames() {
        try {
            return treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            logError(e);
        }
        return new ArrayList<>();
    }


    private void clearData() {
        textSearchItems.set(null);
        comboType.setDisable(false);
        comboType.getItems().clear();
        Utils.clearAll(txtItemBalance, txtPrice, txtQuantity, txtTotals, txtBarcode);
        txtBarcode.requestFocus();
    }

    private void reset_all() {
        table.getItems().clear();
        txtNum.setText(Setting_Language.generate);
        txtPrice.setText(String.valueOf(0));
        txtQuantity.setText(String.valueOf(0));
        txtTotals.setText(String.valueOf(0));
        txtItemBalance.setText(String.valueOf(0));

        txtOtherDiscount.setText("0.0");
        txtPaid.setText("0.0");
        txtNotes.clear();
        radioCash.setSelected(true);
        radioDeffer.setSelected(false);
        sumTotals();
    }

    private void publisherData(DataPublisher dataPublisher) {
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
        txtPaid.setPromptText("دفعة نقدية مقدمة للفاتورة الآجلة");
        radioCash.setTooltip(new Tooltip("يُسدد صافي الفاتورة بالكامل تلقائيًا"));
        radioDeffer.setTooltip(new Tooltip("يمكن إدخال دفعة مقدمة ويُرحّل المتبقي إلى الحساب"));
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
            updatePaymentViewModel(resetDeferredPayment);
            InvoicePaymentTerms terms = paymentViewModel.preview();
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
        boolean deferred = type == InvoiceType.DEFER;
        labelPaid.setText(deferred ? "دفعة مقدمة" : "المدفوع نقدًا");
        labelRemaining.setText(deferred ? "المتبقي على الحساب" : "المتبقي");
        labelNetAfterDiscount.setText("الصافي بعد الخصم");
    }

    private void updatePaymentValidationStyle() {
        setValidationError(txtOtherDiscount, false);
        setValidationError(txtPaid, false);
        InvoiceSaveValidator.Target target = paymentViewModel.invalidTarget();
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
        return !paymentViewModel.isValid();
    }

    private void updatePaymentViewModel(boolean resetDeferredPayment) {
        paymentViewModel.selectInvoiceType(selectedInvoiceType(), resetDeferredPayment);
        InvoiceLineTotals totals = InvoiceLineTotals.from(table.getItems());
        paymentViewModel.updateAmounts(
                totals.netAmount(),
                MoneyMath.parseOrZero(txtOtherDiscount.getText()),
                resetDeferredPayment
                        ? MoneyMath.ZERO
                        : MoneyMath.parseOrZero(txtPaid.getText()));
    }

    private void sumTotals() {
        InvoiceLineTotals totals = InvoiceLineTotals.from(table.getItems());
        textSumCount.setText(String.valueOf(totals.lineCount()));
        txtSumQuantity.setText(String.valueOf(totals.quantity()));
        txtBeforeDiscount.setText(String.valueOf(totals.gross()));
        txtSumDiscount.setText(String.valueOf(totals.discount()));
        txtSumTotals.setText(String.valueOf(totals.net()));
        checkTableForZeroBalanceOrPriceBoolean.set(totals.hasInvalidLine());
    }

    private void tableSetting() {
        DocumentType documentType = designInterface.documentType();
        InvoiceItemCatalogService catalogService = new InvoiceItemCatalogService(
                documentType, itemsService, invoiceBuy::updateItemPrice);
        InvoiceLineEditService editService = new InvoiceLineEditService(
                documentType, catalogService, DefaultStock.ID);
        new InvoiceTableCoordinator<>(table, myObservableList, editService,
                () -> priceTypeByNameId, () -> getInvoiceUpdatePrice(),
                this::sumTotals, getClass(), LogApplication.usersVo.getId() == 1)
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
                .or(checkTableForZeroBalanceOrPriceBoolean)
                .or(comboTreasury.valueProperty().isNull());

        BooleanBinding invalidPayment = Bindings.createBooleanBinding(
                this::paymentDraftInvalid,
                txtSumTotals.textProperty(), txtOtherDiscount.textProperty(),
                txtPaid.textProperty(), radioCash.selectedProperty(),
                radioDeffer.selectedProperty(), checkTableForZeroBalanceOrPriceBoolean);
        PermissionKey writePermission = num_invoice_update > 0
                ? designInterface.documentType().updatePermission()
                : designInterface.documentType().createPermission();
        BooleanBinding writeDenied = Bindings.createBooleanBinding(
                () -> !AuthorizationGuard.isGranted(writePermission));
        binding = binding.or(invalidPayment).or(writeDenied).or(saveInProgress);

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
        String style = dataInterface.designInterface().styleSheet();
        pane.getStylesheets().addAll(style);
        return pane;
    }

    @Override
    public String title() {
        return Setting_Language.WORD_UPDATE;
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void logError(Exception e) {
        AllAlerts.handleError("تنفيذ عملية الفاتورة", e);
    }

}
