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
import com.hamza.account.features.key_setting.MoveRow;
import com.hamza.account.features.key_setting.UpdateInterface;
import com.hamza.account.features.key_setting.UpdateQuantity;
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
import com.hamza.account.otherSetting.BarcodeProcessor;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.*;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.type.ProcessType;
import com.hamza.account.document.DocumentType;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.SearchItemsApplication;
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
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
import static com.hamza.account.controller.invoice.UpdateInvoiceRow.updateData;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;
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
    private final ActionTextBuy actionTextBuy;
    private final ObjectProperty<ItemsModel> itemsModel = new SimpleObjectProperty<>(new ItemsModel());
    private final BooleanProperty saveInProgress = new SimpleBooleanProperty();
    private final InvoicePaymentViewModel paymentViewModel = new InvoicePaymentViewModel();
    private final InvoicePrintService invoicePrintService = new InvoicePrintService();
    private final CustomerService customerService = ServiceRegistry.get(CustomerService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
    private final InvoiceSaveService<T1, T2, T3, T4> invoiceSaveService;
    private final InvoicePostSaveService invoicePostSaveService;
    private int priceTypeByNameId = 1; // use a first price type
    private int codeAccount;
    private boolean updatingPaymentUi;
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
        this.actionTextBuy = new ActionTextBuy() {
            @Override
            public int addRowToTable(String barcode, double quantity, double price, double discount, double total, LocalDate expireDate) throws Exception {
                ActionTextBuy.super.addRowToTable(barcode, quantity, price, discount, total, expireDate);
                return addRowT(quantity, price, discount, total, expireDate);
            }
        };
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
                if (string != null) {
                    searchItemByTypeAndName(string, true, false);
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

        table.editingCellProperty().addListener((observable, oldPosition, newPosition) -> {
            sumTotals();
            // when a cell edit ends (Enter/Tab/focus-out), the editor TextField is removed
            // from the scene and JavaFX hands focus to the first focus-traversable control
            // instead - which is btnNew - so a second Enter would otherwise fire btnNew and
            // wipe the whole table. Reclaim focus on the table itself once the edit is done.
            if (newPosition == null) {
                Platform.runLater(table::requestFocus);
            }
        });
        myObservableList.addListener((ListChangeListener<BasePurchasesAndSales>) change -> {
            sumTotals();
//            triggerAutosave();
        });

        comboType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            if (itemsModel.get() == null) return;

            var model = itemsModel.get();
            var itemsPrice = invoiceBuy.getItemsPrice(model, priceTypeByNameId);

            // The factor is the one this item defines for the unit, not the
            // database-wide units.value_d - a carton is not the same multiple
            // for every item.
            var unitsModel = ItemUnits.unitByName(model, newValue);

            txtItemBalance.setText(String.valueOf(roundToTwoDecimalPlaces(ItemUnits.fromBase(model.getSumAllBalance(), unitsModel))));
            // The unit's own price where it has one, and the item's scaled by the
            // factor where it does not.
            txtPrice.setText(String.valueOf(ItemUnits.sellPrice(model, unitsModel, priceTypeByNameId, itemsPrice)));
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
        if (keyEvent.getCode() == KeyCode.ENTER) {
            if (!txtBarcode.getText().isEmpty()) {
                if (getSettingBarcodeScaleActive()) {
                    // التحقق من أول رقمين (كود الميزان)
                    String barcode = txtBarcode.getText();
                    int scaleCodeLength = getSettingBarcodeCountScale();

                    if (barcode.length() >= scaleCodeLength) {
                        String scalePrefix = barcode.substring(0, scaleCodeLength);
                        String expectedPrefix = String.format("%0" + scaleCodeLength + "d", getSettingBarcodeStart());

                        if (scalePrefix.equals(expectedPrefix)) {
                            try {
                                var barcodeResult = new BarcodeProcessor(itemsService).processBarcode(barcode, true);
                                var item = barcodeResult.item();

                                itemsModel.set(item);
                                txtBarcode.setText(item.getBarcode());
                                textSearchItems.set(item.getNameItem());
                                // A scale barcode carries a weight, so the row is
                                // always in the unit the item is weighed in. The
                                // price and quantity below come from the barcode
                                // and deliberately overwrite what selecting the
                                // unit just filled in.
                                addDataToComboType(item, ItemUnits.baseUnit(item));

                                txtItemBalance.setText(String.valueOf(item.getSumAllBalance()));
                                txtPrice.setText(String.valueOf(barcodeResult.selPrice()));
                                txtQuantity.setText(String.valueOf(barcodeResult.quantity()));
                                txtTotals.setText(String.valueOf(barcodeResult.total()));

                                if (getInvoiceAddItemDirect()) {
                                    addData();
                                } else {
                                    txtPrice.requestFocus();
                                }
                                return;
                            } catch (Exception e) {
                                log.error(e.getMessage());
                                AllAlerts.handleError("قراءة باركود الميزان", e);
                                txtBarcode.requestFocus();
                                return;
                            }
                        }
                    }
                }

                searchItemByTypeAndName(txtBarcode.getText(), false, false);

                if (getInvoiceAddItemDirect()) {
                    addData();
                } else {
                    txtPrice.requestFocus();
                }
            }
        }
    }

    private void searchItemByTypeAndName(String itemName, boolean searchByName, boolean useScaleBarcode) {
        try {
            var id = DefaultStock.ID;
            if (searchByName) {
                var itemByItemNameAndStockId = itemsService.getItemByItemNameAndStockId(itemName, id);
                if (itemByItemNameAndStockId == null) {
                    Utils.clearAll(txtItemBalance, txtPrice, txtQuantity, txtTotals, txtBarcode);
                    throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                }

                txtBarcode.setText(itemByItemNameAndStockId.getBarcode());
                itemsModel.set(itemByItemNameAndStockId);
            } else {
                var itemByBarcodeAndStockId = itemsService.getItemByBarcodeAndStockId(itemName, id);
                if (itemByBarcodeAndStockId == null) {
                    Utils.clearAll(txtItemBalance, txtPrice, txtQuantity, txtTotals, txtBarcode);
                    txtBarcode.requestFocus();
                    throw new Exception("لا يوجد هذا الباركود: " + itemName);
                }


                itemsModel.set(itemByBarcodeAndStockId);
                textSearchItems.set(itemsModel.get().getNameItem());
            }

            if (itemsModel.get() == null) {
                throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }
            if (itemsModel.get().getId() == 0) {
                throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            // add type
            var model = itemsModel.get();
            txtPrice.requestFocus();

            // check name first to select sel price
            var s = textSearchName.get();
            if (s == null) {
                throw new Exception(Setting_Language.PLEASE_INSERT_ALL_DATA + ":- \n ادخل الاسم");
            }
            var object = nameService.getObject(nameAndAccountInterface.nameList(), s);
            priceTypeByNameId = t3NameData.priceId(object);

            // A code can belong to a unit rather than to the item, so a carton
            // scanned here selects the carton. Searching by name has no code to
            // go on and starts from the base unit. The price type is resolved
            // first, because selecting the unit is what fills the price in.
            var scannedUnit = searchByName ? ItemUnits.baseUnit(model) : ItemUnits.unitByBarcode(model, itemName);
            addDataToComboType(model, scannedUnit);

            txtQuantity.setText("1");
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Fills the unit combo from the item and selects {@code selected}. Selecting
     * it is what puts the balance and the price for that unit into their fields -
     * the combo's listener does both - so nothing here writes them.
     */
    private void addDataToComboType(ItemsModel model, UnitsModel selected) {

        // Only the units this item defines. Offering every unit in the database
        // let a row be priced and counted by a factor the item never declared.
        var units = ItemUnits.unitsFor(model);
        var list = units.stream().map(UnitsModel::getUnit_name).toList();

        comboType.setItems(FXCollections.observableArrayList(list));

        String name = selected == null ? null : selected.getUnit_name();
        String toSelect = list.contains(name) ? name : (list.isEmpty() ? null : list.getFirst());
        if (toSelect != null) {
            // setItems cleared the selection, so this always fires the listener
            // and the fields are filled even when the same unit is scanned twice.
            comboType.getSelectionModel().select(toSelect);
        }

        // Nothing to choose between when the item sells in one unit only.
        comboType.setDisable(list.size() < 2);
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

    private int addRowT(double quantity, double price, double discount, double total, LocalDate expireDate) throws DaoException {
        var model = itemsModel.get();
        var numItem = model.getId();
        UnitsModel unitsModel = ItemUnits.unitByName(model, comboType.getSelectionModel().getSelectedItem());

        if (!increaseTheItemByOneIfPresentInTable(quantity, model, unitsModel)) {
            T1 object = invoiceBuy.object_TableData(0, num_invoice_update, numItem, price, quantity, discount, total, unitsModel, model, expireDate);
            myObservableList.add(object);
        }

        sumTotals();
//        numItem = 0;
        return 1;
    }

    private void addData() {
        try {
            long startTime = System.nanoTime();
            double quantity = DoubleSetting.parseDoubleOrDefault(txtQuantity.getText());
            double price = DoubleSetting.parseDoubleOrDefault(txtPrice.getText());
            double total = DoubleSetting.parseDoubleOrDefault(txtTotals.getText());
            double discount = 0;

            // check quantity
            if (quantity <= 0) {
                txtQuantity.requestFocus();
                throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            if (designInterface.showDataForCustomer()) {
                var model = itemsModel.get();
                UnitsModel selectedUnit = ItemUnits.unitByName(model, comboType.getSelectionModel().getSelectedItem());

                // never sell below cost - the unit's own cost where it has one,
                // otherwise the item's scaled to the unit being sold in
                double buyPriceForUnit = ItemUnits.buyPrice(model, selectedUnit, model.getBuyPrice());
                if (price < buyPriceForUnit) {
                    txtPrice.requestFocus();
                    throw new Exception("لا يمكن البيع بسعر أقل من سعر الشراء");
                }

                // check quantity before add row: compare against the item's real available
                // balance, counting quantity already added for this item elsewhere in the
                // same invoice (converted to the item's base unit, since rows can use
                // different units)
                if (!getSelWithoutBalance()) {
                    double newBaseQuantity = ItemUnits.toBase(quantity, selectedUnit);

                    double alreadyInTableBaseQuantity = table.getItems().stream()
                            .filter(t1 -> purchaseSalesInterface.getItems(t1).getId() == model.getId())
                            .mapToDouble(t1 -> ItemUnits.toBase(purchaseSalesInterface.getQuantity(t1), purchaseSalesInterface.getUnitsType(t1)))
                            .sum();

                    if (alreadyInTableBaseQuantity + newBaseQuantity > model.getSumAllBalance()) {
                        throw new Exception(Error_Text_Show.NO_BALANCE);
                    }
                }
            }


            // Captured before the row is added: clearData() resets itemsModel, and the
            // lambda below runs after a modal date dialog has closed.
            final ItemsModel addedItem = itemsModel.get();

            if (itemsModel.get().isHasValidate()) {
                ExpireDateInterface anInterface = getDatePicker();
                if (dataInterface.designInterface().showDataForCustomer()) {
                    anInterface = getDateList(cardItemService);
                }
                var choiceItemExpireDate = new ChoiceItemExpireDate(anInterface);
                var s = choiceItemExpireDate.showAndWait();
                s.ifPresentOrElse(choiceItemExpireDate1 -> {
                    try {
                        if (actionTextBuy.addRowToTable(itemsModel.get().getBarcode(), quantity, price, discount, total, choiceItemExpireDate1) == 1) {
                            warnIfStockIsLow(addedItem);
                            clearData();
                        }
                    } catch (Exception e) {
                        logError(e);
                    }
                }, () -> AllAlerts.alertError("من فضلك حدد تاريخ الانتهاء"));
            } else if (actionTextBuy.addRowToTable(itemsModel.get().getBarcode(), quantity, price, discount, total, null) == 1) {
                warnIfStockIsLow(addedItem);
                clearData();
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
            double alreadyOnInvoice = table.getItems().stream()
                    .filter(t1 -> purchaseSalesInterface.getItems(t1).getId() == item.getId())
                    .mapToDouble(t1 -> ItemUnits.toBase(purchaseSalesInterface.getQuantity(t1),
                            purchaseSalesInterface.getUnitsType(t1)))
                    .sum();

            StockLevelAlert.check(item, StockLevelAlert.remainingAfter(item, alreadyOnInvoice));
        } catch (Exception e) {
            // The row is already added and the sale is fine; only the warning failed.
            log.error("Could not check the stock level after adding item {}", item.getId(), e);
        }
    }

    /**
     * Folds a repeated scan into the row that is already there, but only into a
     * row in the same unit: a piece and a carton of the same item are separate
     * lines, priced separately, and adding one to the other would sell a carton
     * for the price of a piece.
     */
    private boolean increaseTheItemByOneIfPresentInTable(double newQuantity, ItemsModel itemsModel, UnitsModel unit) {
        String equals = String.valueOf(getSettingBarcodeStart());
        if (itemsModel.getBarcode().startsWith(equals)) return false;
        if (getInvoiceIncreaseItemOneTable())
            if (!table.getItems().isEmpty()) {
                Optional<T1> checkItemsExistingInTable = table.getItems()
                        .stream()
                        .filter(t1 -> purchaseSalesInterface.getItems(t1).getId() == itemsModel.getId())
                        .filter(t1 -> unit == null || purchaseSalesInterface.getUnitsType(t1) == null
                                || purchaseSalesInterface.getUnitsType(t1).getUnit_id() == unit.getUnit_id())
                        .findFirst();

                if (checkItemsExistingInTable.isPresent()) {
                    T1 purchasesAndSales = checkItemsExistingInTable.get();
                    double quantity = purchaseSalesInterface.getQuantity(purchasesAndSales);
//                    invoiceBuy.setQuantity(purchasesAndSales, quantity + newQuantity);
                    purchasesAndSales.setQuantity(quantity + newQuantity);
                    updateData(purchasesAndSales);
                    return true;
                }
            }
        return false;
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
        new TableColumnAnnotation().getTable(table, BasePurchasesAndSales.class);

        // add column
        addColumn(table, Setting_Language.WORD_BARCODE, 0, (Callback<TableColumn.CellDataFeatures<T1, String>, ObservableValue<String>>) features -> features.getValue().getItems().barcodeProperty());
        addColumn(table, Setting_Language.WORD_NAME, 1, (Callback<TableColumn.CellDataFeatures<T1, String>, ObservableValue<String>>) features -> features.getValue().getItems().nameItemProperty());

        // add column type
        addColumn(table, Setting_Language.WORD_TYPE, 2, (Callback<TableColumn.CellDataFeatures<T1, String>, ObservableValue<String>>) features -> features.getValue().getUnitsType().unit_nameProperty());
        // Add editable type column with ComboBox
//        addColumnType();

        table.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                table.getItems().remove(i);
                table.refresh();
            }
        }));


        table.setItems(myObservableList);
        // edit column name
        new ColumnSetting().enableStringEditing(1, t -> {
            int row = t.getTablePosition().getRow();
            BasePurchasesAndSales purchase = t.getTableView().getItems().get(row);
            if (t.getNewValue() != null) {
                purchase.getItems().setNameItem(t.getNewValue());
                updateItem(purchase);
            }
        }, table);

        new ColumnSetting().enableDoubleEditing(3, t -> {
            int row = t.getTablePosition().getRow();
            BasePurchasesAndSales purchase = t.getTableView().getItems().get(row);
            purchase.setQuantity(t.getNewValue() == null ? 1.0 : t.getNewValue());
            updateData(purchase);
        }, table);

        new ColumnSetting().enableDoubleEditing(4, t -> {
            int row = t.getTablePosition().getRow();
            BasePurchasesAndSales purchase = t.getTableView().getItems().get(row);
            double newPrice = t.getNewValue() == null ? 0.0 : t.getNewValue();

            if (designInterface.showDataForCustomer()) {
                double buyPriceForUnit = ItemUnits.buyPrice(purchase.getItems(), purchase.getUnitsType(),
                        purchase.getItems().getBuyPrice());
                if (newPrice < buyPriceForUnit) {
                    AllAlerts.alertError("لا يمكن البيع بسعر أقل من سعر الشراء");
                    table.refresh();
                    return;
                }
            }

            purchase.setPrice(newPrice);
            updateData(purchase);
            if (getInvoiceUpdatePrice()) {
                updateItem(purchase);
            }
        }, table);

        new ColumnSetting().enableDoubleEditing(6, t -> {
            int row = t.getTablePosition().getRow();
            BasePurchasesAndSales purchase = t.getTableView().getItems().get(row);
            purchase.setDiscount(t.getNewValue() == null ? 0.0 : t.getNewValue());
            updateData(purchase);
        }, table);


        table.setEditable(true);
        table.getSelectionModel().setCellSelectionEnabled(true);
        // move selected rows
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Enable multiple selection
        // Unified key handler: combines Alt+Arrow movement and existing quantity key behavior
        table.setOnKeyPressed(createTableKeyHandler());

        // hide data table if not admin
        var b = LogApplication.usersVo.getId() == 1;
        if (b) {
            // show table menu
            TableSetting.tableMenuSetting(getClass(), table);
        }

//        table.getColumns().get(8).setVisible(b);
//        table.getColumns().get(9).setVisible(b);
    }

    private void updateItem(BasePurchasesAndSales purchase) throws DaoException {
        var items = itemsService.getItemByItemIdAndStockId(purchase.getItems().getId(), DefaultStock.ID);

        // A unit priced by hand says nothing about what one base unit is worth -
        // a carton sold at a wholesale price is cheaper than twelve pieces on
        // purpose, and dividing it back out would drag the item's price down.
        if (ItemUnits.hasOwnSellPrice(items, purchase.getUnitsType(), priceTypeByNameId)) {
            return;
        }

        // The list stays as loaded: ItemsDao replaces the item's units from it,
        // so blanking it here to "not touch them" would delete them instead.
        items.setNameItem(purchase.getItems().getNameItem());

        // update price - the row is priced per its own unit, the item per base unit
        var unitFactor = ItemUnits.factor(purchase.getUnitsType());
        var b = invoiceBuy.updateItemPrice(items, roundToTwoDecimalPlaces(purchase.getPrice() / unitFactor), priceTypeByNameId);
        if (b) {
            var i = itemsService.commitItemUpdate(items);
        }
    }

    private EventHandler<KeyEvent> createTableKeyHandler() {
        final EventHandler<KeyEvent> quantityHandler = tableKeyPressed(); // existing behavior
        return event -> {
            MoveRow<T1> t1MoveRow = new MoveRow<>(table, myObservableList);
            if (event.isAltDown()) {
                switch (event.getCode()) {
                    case UP -> {
                        t1MoveRow.moveSelectedRowsUp();
                        event.consume();
                        return; // prevent delegation
                    }
                    case DOWN -> {
                        t1MoveRow.moveSelectedRowsDown();
                        event.consume();
                        return; // prevent delegation
                    }
                    default -> { /* no-op */ }
                }
            }
            if (!event.isConsumed() && quantityHandler != null) {
                quantityHandler.handle(event);
            }
        };
    }

    private EventHandler<KeyEvent> tableKeyPressed() {
        return new UpdateQuantity(new UpdateInterface() {
            @Override
            public TableView<? extends BasePurchasesAndSales> getTable() {
                return table;
            }

            @Override
            public void update(BasePurchasesAndSales basePurchasesAndSales) {
                updateData(basePurchasesAndSales);
            }

            @Override
            public void sum() {
                sumTotals();
            }
        }).tableKeyPressed();
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

    private ExpireDateInterface getDatePicker() {
        return new ExpireDateInterface() {

            final DatePicker datePicker = new DatePicker();

            @Override
            public Node node() {
                return datePicker;
            }

            @Override
            public LocalDate getDate() {
                return datePicker.getValue();
            }
        };
    }

    private ExpireDateInterface getDateList(CardItemService cardItemService) throws Exception {
        final ListView<LocalDate> localDateListView = new ListView<>();
        var cardItems = cardItemService.cardItemsListByNumItem(1);
        var purchase = cardItems.stream().filter(cardItems1 -> cardItems1.getProcessType().equals(ProcessType.PURCHASE))
                .map(CardItems::getEndDate)
                .toList();

        localDateListView.getItems().addAll(purchase);

        return new ExpireDateInterface() {
            @Override
            public Node node() {
                return localDateListView;
            }

            @Override
            public LocalDate getDate() {

                return localDateListView.getSelectionModel().getSelectedItem();
            }
        };
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

    private void addColumnType() {
        TableColumn<T1, String> typeColumn = new TableColumn<>(Setting_Language.WORD_TYPE);
        typeColumn.setCellValueFactory(features -> features.getValue().getUnitsType().unit_nameProperty());
        // The choices belong to the row's item, so they are filled in as the cell
        // starts editing rather than baked into the factory from one global list.
        typeColumn.setCellFactory(column -> new ComboBoxTableCell<>() {
            @Override
            public void startEdit() {
                var row = getTableRow();
                T1 rowValue = row == null ? null : row.getItem();
                getItems().setAll(rowValue == null
                        ? List.<String>of()
                        : ItemUnits.unitsFor(rowValue.getItems()).stream().map(UnitsModel::getUnit_name).toList());

                // One unit is not a choice, and an empty list would open a blank
                // combo whose commit would clear the row's unit.
                if (getItems().size() < 2) {
                    return;
                }
                super.startEdit();
            }
        });
        typeColumn.setOnEditCommit(event -> {
            T1 item = event.getRowValue();
            UnitsModel unitsModel = ItemUnits.unitByName(item.getItems(), event.getNewValue());
            item.setUnitsType(unitsModel);
            var selPrice1 = dataInterface.invoiceBuy().getItemsPrice(item.getItems(), priceTypeByNameId);
            item.setPrice(ItemUnits.sellPrice(item.getItems(), unitsModel, priceTypeByNameId, selPrice1));
            updateData(item);
            table.refresh();
        });
        table.getColumns().add(2, typeColumn);
    }

}
