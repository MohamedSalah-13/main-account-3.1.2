package com.hamza.account.controller.invoice;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.controller.setting.SettingTabLanguageController;
import com.hamza.account.features.key_setting.MoveRow;
import com.hamza.account.features.key_setting.UpdateInterface;
import com.hamza.account.features.key_setting.UpdateQuantity;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.BarcodeProcessor;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.CardItemService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.type.ProcessType;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.SearchItemsApplication;
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.notifications.NotificationAction;
import com.hamza.controlsfx.notifications.NotificationAdd;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import com.hamza.controlsfx.util.MaxNumberList;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
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
    private final DataPublisher dataPublisher;
    private final ActionTextBuy actionTextBuy;
    private final DaoFactory daoFactory;
    private final ObjectProperty<ItemsModel> itemsModel = new SimpleObjectProperty<>(new ItemsModel());
    private List<ModelPrintInvoice> modelPrintInvoices = new ArrayList<>();
    private int priceTypeByNameId = 1; // use a first price type
    private int codeAccount;
    private double discountValue;
    private int invNumber;
    private StringProperty textSearchName, textSearchItems;
    @FXML
    private Label labelNum, labelName, labelBarcode, labelDate, labelStockName, labelCondition, labelDelegate, labelTreasury, labelSearchBy, labelPrice, labelQuantity, labelItemBalance, labelTotals, last1, last2, last3, last4, last5, labelNotes, labelInvoiceTotal;
    @FXML
    @Getter
    private Button btnAdd, btnSave, btnPrintSave, btnNew, btnSearch, btnUpdateItem;
    @FXML
    private ComboBox<String> comboStock, comboType, comboDelegate, comboTreasury;
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
    private MaskerPaneSetting maskerPaneSetting;

    public BuyController2(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, int numInvoiceUpdate) throws Exception {
        super(dataInterface, dataPublisher, daoFactory, numInvoiceUpdate);
        this.dataPublisher = dataPublisher;
        this.daoFactory = daoFactory;
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
        // labels
        last1.setText(Setting_Language.COUNT_ITEMS);
        last2.setText(Setting_Language.THE_NUMBER_OF_PIECES);
        last3.setText(Setting_Language.WORD_TOTAL);
        last4.setText(Setting_Language.TOTAL_DISCOUNT);
        last5.setText(Setting_Language.TOTAL_AFTER_DISCOUNT);
        labelDelegate.setText(Setting_Language.DELEGATE);
        labelNum.setText(Setting_Language.WORD_NUM_INV);
        labelName.setText(Setting_Language.WORD_NAME);
        labelBarcode.setText(Setting_Language.WORD_BARCODE);
        labelDate.setText(Setting_Language.WORD_DATE);
        labelStockName.setText(Setting_Language.WORD_STOCK);
        labelCondition.setText(Setting_Language.WORD_TYPE);
        labelSearchBy.setText(Setting_Language.NAME_ITEM);
        labelPrice.setText(Setting_Language.PRICE);
        labelQuantity.setText(Setting_Language.WORD_QUANTITY);
        labelItemBalance.setText(Setting_Language.WORD_BALANCE);
        labelTotals.setText(Setting_Language.WORD_TOTAL);
        labelTreasury.setText(Setting_Language.TREASURY);
        // combo
        comboTreasury.setPromptText(Setting_Language.TREASURY);
        comboDelegate.setPromptText(Setting_Language.DELEGATE);
        comboStock.setPromptText(Setting_Language.WORD_STOCK);
        comboType.setPromptText((Setting_Language.WORD_TYPE));
        // text
        txtBarcode.setPromptText(Setting_Language.WORD_BARCODE);
        // buttons
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnPrintSave.setText(Setting_Language.SAVE_AND_PRINT);
    }

    private void addTextSearchName() {
        try {
            TextSearchApplication<T3> customersTextSearchApplication = new TextSearchApplication<>(dataInterface.nameAndAccountInterface().searchInterface());
            textSearchName = customersTextSearchApplication.getTextSearchController().textNameProperty();
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
            reset_all();
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

        table.editingCellProperty().addListener(observable -> sumTotals());
        myObservableList.addListener((ListChangeListener<BasePurchasesAndSales>) change -> {
            sumTotals();
            comboStock.setDisable(!table.getItems().isEmpty());
//            triggerAutosave();
        });

        comboType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            if (itemsModel.get() == null) return;

            var model = itemsModel.get();
            var itemsPrice = invoiceBuy.getItemsPrice(model, priceTypeByNameId);
//            var unitsModelList = model.getItemsUnitsModelList()
//                    .stream().filter(unitsModel -> unitsModel.getUnitsModel().getUnit_name().equals(newValue)).findFirst();

            // add all units
            var unitsModelList = getUnitsModelList().stream()
                    .filter(unitsModel -> unitsModel.getUnit_name().equals(newValue)).findFirst();

            if (unitsModelList.isPresent()) {
                var unitsModel = unitsModelList.get();
                var value = unitsModel.getValue();
                var roundedBalance = roundToTwoDecimalPlaces(model.getSumAllBalance() / value);
                txtItemBalance.setText(String.valueOf(roundedBalance));
                txtPrice.setText(String.valueOf(itemsPrice * value));
            } else {
                txtItemBalance.setText(String.valueOf(model.getSumAllBalance()));
                txtPrice.setText(String.valueOf(itemsPrice));
            }
        });
    }

    private List<UnitsModel> getUnitsModelList() {
        try {
            return unitsService.getUnitsModelList();
        } catch (DaoException e) {
            logError(e);
            return Collections.emptyList();
        }
    }

    private void openSearchItems() {
        try {
            SearchItemsApplication<T1, T2, T3, T4> itemsApplication
                    = new SearchItemsApplication<>(dataInterface, daoFactory, comboStock.getSelectionModel().getSelectedItem());

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
                                addDataToComboType(item);

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
                                AllAlerts.alertError("خطأ في قراءة باركود الميزان: " + e.getMessage());
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
            var id = getStockIdBySelectedStock().getId();
            if (searchByName) {
                var itemByItemNameAndStockId = itemsService.getItemByItemNameAndStockId(itemName, id);
                if (itemByItemNameAndStockId == null) {
                    Utils.clearAll(txtItemBalance, txtPrice, txtQuantity, txtTotals, txtBarcode);
                    throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                }

                // إذا كانت فاتورة مشتريات لا يمكن إضافة اصناف التى تحتها مجموعات
                checkItemCompatibility(itemByItemNameAndStockId);

                txtBarcode.setText(itemByItemNameAndStockId.getBarcode());
                itemsModel.set(itemByItemNameAndStockId);
            } else {
                var itemByBarcodeAndStockId = itemsService.getItemByBarcodeAndStockId(itemName, id);
                if (itemByBarcodeAndStockId == null) {
                    Utils.clearAll(txtItemBalance, txtPrice, txtQuantity, txtTotals, txtBarcode);
                    txtBarcode.requestFocus();
                    throw new Exception("لا يوجد هذا الباركود: " + itemName);
                }

                // إذا كانت فاتورة مشتريات لا يمكن إضافة اصناف التى تحتها مجموعات
                checkItemCompatibility(itemByBarcodeAndStockId);

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

            // add data to type
            addDataToComboType(model);

            // check name first to select sel price
            var s = textSearchName.get();
            if (s == null) {
                throw new Exception(Setting_Language.PLEASE_INSERT_ALL_DATA + ":- \n ادخل الاسم");
            }
            var object = nameService.getObject(nameAndAccountInterface.nameList(), s);
            priceTypeByNameId = t3NameData.priceId(object);

            txtItemBalance.setText(String.valueOf(model.getSumAllBalance()));
            txtPrice.setText(String.valueOf(invoiceBuy.getItemsPrice(model, priceTypeByNameId)));
            txtQuantity.setText("1");
        } catch (Exception e) {
            logError(e);
        }
    }

    private void checkItemCompatibility(ItemsModel itemByItemNameAndStockId) throws Exception {
        // إذا كانت فاتورة مشتريات لا يمكن إضافة اصناف التى تحتها مجموعات
        if (!dataInterface.designInterface().showDataForCustomer()) {
            if (itemByItemNameAndStockId.isHasPackage())
                throw new Exception("لا يمكن إضافة اصناف تحتها مجموعات فى فاتورة مشتريات");
        }
    }

    private void addDataToComboType(ItemsModel model) {

        var list = getUnitsModelList()
                .stream()
                .map(UnitsModel::getUnit_name).toList();

        comboType.setItems(FXCollections.observableArrayList(list));

        // select data
        list.stream().filter(name -> name.equals(model.getUnitsType().getUnit_name())).findFirst().ifPresent(name -> comboType.getSelectionModel().select(name));

        comboType.setDisable(model.getUnitsType().getValue() > 1);
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
        if (!increaseTheItemByOneIfPresentInTable(quantity, model)) {
            UnitsModel unitsModel = unitsService.getUnitsByName(comboType.getSelectionModel().getSelectedItem());

            T1 object = invoiceBuy.object_TableData(0, num_invoice_update, numItem, price, quantity, discount, total, unitsModel, model, expireDate);
            myObservableList.add(object);
        }

        if (dataInterface.designInterface().showDataForCustomer()) {
            notifyItems(model);
        }

        sumTotals();
//        numItem = 0;
        return 1;
    }

    private void notifyItems(ItemsModel itemsModel) {
        if (itemsModel.getSumAllBalance() == itemsModel.getMini_quantity())
            new NotificationAdd(new NotificationAction() {
                @Override
                public String titleName() {
                    return " رصيد قارب على الانتهاء";
                }

                @Override
                public String text() {
                    return itemsModel.getNameItem() + " :" + itemsModel.getSumAllBalance();
                }

                @Override
                public Node graphic_design() {
                    return null;
                }

                @Override
                public void action() {

                }
            });

    }

    private void addData() {
        try {
            double quantity = DoubleSetting.parseDoubleOrDefault(txtQuantity.getText());
            double price = DoubleSetting.parseDoubleOrDefault(txtPrice.getText());
            double total = DoubleSetting.parseDoubleOrDefault(txtTotals.getText());
            double balance = DoubleSetting.parseDoubleOrDefault(txtItemBalance.getText());
            double discount = 0;

            // check quantity
            if (quantity <= 0) {
                txtQuantity.requestFocus();
                throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            // check quantity before add row
            // don`t use condition if item id =1
            if (designInterface.showDataForCustomer())
                if (!getSelWithoutBalance()) {
                    if (quantity > balance) throw new Exception(Error_Text_Show.NO_BALANCE);
                }

            ExpireDateInterface anInterface = getDatePicker();
            if (dataInterface.designInterface().showDataForCustomer()) {
                anInterface = getDateList(cardItemService);
            }

            if (itemsModel.get().isHasValidate()) {
                var choiceItemExpireDate = new ChoiceItemExpireDate(anInterface);
                var s = choiceItemExpireDate.showAndWait();
                s.ifPresentOrElse(choiceItemExpireDate1 -> {
                    try {
                        if (actionTextBuy.addRowToTable(itemsModel.get().getBarcode(), quantity, price, discount, total, choiceItemExpireDate1) == 1) {
                            clearData();
                        }
                    } catch (Exception e) {
                        logError(e);
                    }
                }, () -> AllAlerts.alertError("من فضلك حدد تاريخ الانتهاء"));
            } else if (actionTextBuy.addRowToTable(itemsModel.get().getBarcode(), quantity, price, discount, total, null) == 1) {
                clearData();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private boolean increaseTheItemByOneIfPresentInTable(double newQuantity, ItemsModel itemsModel) {
        String equals = String.valueOf(getSettingBarcodeStart());
        if (itemsModel.getBarcode().startsWith(equals)) return false;
        if (getInvoiceIncreaseItemOneTable())
            if (!table.getItems().isEmpty()) {
                Optional<T1> checkItemsExistingInTable = table.getItems()
                        .stream()
                        .filter(t1 -> purchaseSalesInterface.getItems(t1).getId() == itemsModel.getId()).findFirst();

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
            String nameStock = dataById.getStockData().getName();
            String invoiceDate = dataById.getDate();
            String getDelegate = totalsDataInterface.getDelegateData(dataById).getName();

            date.setValue(LocalDate.parse(invoiceDate));
            textSearchName.set(name);
            comboStock.getSelectionModel().select(nameStock);
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
        try {
            if (table.getItems().isEmpty()) {
                throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            if (dataInterface.designInterface().showDataForCustomer())
                if (comboDelegate.getSelectionModel().getSelectedItem() == null) {
                    Platform.runLater(() -> comboDelegate.requestFocus());
                    throw new Exception("من فضلك حدد المندوب");
                }

            if (comboTreasury.getSelectionModel().getSelectedItem() == null) {
                Platform.runLater(() -> comboTreasury.requestFocus());
                throw new Exception("من فضلك حدد الخزينة");
            }

            if (!ShiftContext.requireOpenShift()) {
                return;
            }

            if (AllAlerts.confirmSave()) {
                String invoiceDate = date.getValue().toString();
                double total = Double.parseDouble(txtSumTotals.getText());
                discountValue = Double.parseDouble(txtOtherDiscount.getText());
                double after = Double.parseDouble(textInvoiceTotal.getText());
                double paidValue = Double.parseDouble(txtPaid.getText());
                double remainingBalance = 0; //Double.parseDouble(paneRightController.getRest());
                String notes = txtNotes.getText();

                InvoiceType invoiceType = radioCash.isSelected() ? InvoiceType.CASH : InvoiceType.DEFER;
                TreasuryModel treasuryByName = treasuryService.getTreasuryByName(comboTreasury.getSelectionModel().getSelectedItem());
                Employees employees = employeeService.getDelegateByName(comboDelegate.getSelectionModel().getSelectedItem());
                T3 t3 = invoiceBuy.objectName(codeAccount, textSearchName.get());

                // check code account
                if (codeAccount == 0) throw new DaoException("لا يوجد بيانات الاسم");

                // check to get code for update or insert
                invNumber = num_invoice_update > 0 ? num_invoice_update : getInvNumber();

                DiscountType discountType = radioAmount.isSelected() ? DiscountType.AMOUNT : DiscountType.RATE;
                List<T1> list = listOfItemsPurchase(invNumber);
                T2 t2 = invoiceBuy.object_Totals(invNumber, invoiceType, invoiceDate, total, discountValue, discountType, after, paidValue, remainingBalance, notes,
                        t3, getStockIdBySelectedStock(), employees, list, treasuryByName);


                DaoList<T2> totalDaoList = totalsAndPurchaseList.totalDao();
                int save;
                if (num_invoice_update > 0)
                    save = totalDaoList.update(t2);
                else save = totalDaoList.insert(t2);
                if (save == 1) {
                    AllAlerts.alertSave();
                    // Show change dialog only for cash invoices
                    if (dataInterface.designInterface().showScreenPaidInInvoice()) {
                        if (getInvoiceShowScreenPaid())
                            if (invoiceType.equals(InvoiceType.CASH)) {
                                showCashChangeDialog(after);
                            }
                    }

                    reset_all();
                    // for print invoice
                    printInvoice(print, t2);

                    // if portable version don`t create backup
                    handlePurchaseAndSales();

                    // close stage if open when update
                    if (num_invoice_update > 0) {
                        table.getScene().getWindow().hide();
                    }
                }
            }
        } catch (Exception e) {
            logError(e);
        }

    }

    private void handlePurchaseAndSales() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(5000);
                dataInterface.publisherPurchaseOrSales().notifyObservers();
                if (getInvoiceBackupAfterSave())
                    SaveDatabaseFile.saveBeforeClose(false);
            } catch (Exception e) {
                logError(e);
            }
        });
        thread.start();
    }

    private int getInvNumber() {
        try {
            MaxNumberList<T2> totalBuyMaxNumberList = new MaxNumberList<>(BaseTotals::getId, dataInterface.totalsAndPurchaseList().totalDao().loadAll());
            return totalBuyMaxNumberList.getCode();
        } catch (DaoException e) {
            AllAlerts.alertError(Error_Text_Show.NO_DATA + e.getMessage());
        }
        return 1;
    }

    private List<T1> listOfItemsPurchase(int num_invoice) {
        modelPrintInvoices = new ArrayList<>();
        List<T1> list = new ArrayList<>();
        for (int i = 0; i < table.getItems().size(); i++) {
            T1 t11 = table.getItems().get(i);
            ItemsModel itemsModel = purchaseSalesInterface.getItems(t11);
            int numItem = itemsModel.getId();
            double price = purchaseSalesInterface.getPrice(t11);
            double quantity = purchaseSalesInterface.getQuantity(t11);
            double discount = purchaseSalesInterface.getDiscount(t11);
            double totals = purchaseSalesInterface.getTotal(t11);
            UnitsModel type = purchaseSalesInterface.getUnitsType(t11);
            int id = purchaseSalesInterface.id(t11);

            //
            if (itemsModel.isHasValidate())
                if (t11.getExpiration_date() == null) {
                    throw new RuntimeException("cant be null");
                }

            T1 t1 = invoiceBuy.object_TableData(id, num_invoice, numItem, price, quantity, discount, totals, type, itemsModel, t11.getExpiration_date());
            list.add(t1);
            modelPrintInvoices.add(new ModelPrintInvoice(itemsModel.getNameItem(), itemsModel.getBarcode()
                    , type.getUnit_name(), price, quantity, totals, discount, totals - discount));
        }
        return list;
    }

    private Stock getStockIdBySelectedStock() {
        try {
            return stockService.getStockByName(comboStock.getSelectionModel().getSelectedItem());
        } catch (DaoException e) {
            logError(e);
            throw new RuntimeException(e.getMessage());
        }
    }

    private void printInvoice(boolean print, T2 t2) {
        // print invoice
        if (print) {
            maskerPaneSetting.showMaskerPane(() -> {
                Print_Reports printReports = new Print_Reports();
                if (getPrintPaperReceiptInvoice()) {
                    printReports.printReceiptInvoice(modelPrintInvoices, textSearchName.getValue(), invNumber
                            , discountValue, LocalDateTime.now().format(DATE_TIME_FORMATTER), date.getValue().toString(), 0);
                } else
                    printReports.printInvoice(modelPrintInvoices, ShowInvoiceDetails.invoiceDetails(dataInterface, t2), dataInterface.designInterface().nameTextOfInvoice());

                modelPrintInvoices = new ArrayList<>();
                discountValue = 0.0;
            });

        }
    }

    private void totalItemQuantityAndPrice() {
        double price = DoubleSetting.parseDoubleOrDefault(txtPrice.getText());
        double quantity = DoubleSetting.parseDoubleOrDefault(txtQuantity.getText());
        double sum = roundToTwoDecimalPlaces(price * quantity);
        txtTotals.setText(String.valueOf(sum));
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
        whenEnterPressed(txtBarcode, txtPrice, txtQuantity, btnAdd);
        setTextFormatter(txtPaid, txtOtherDiscount, txtItemBalance, txtPrice, txtQuantity, txtTotals);
        Utils.replaceNonDigitChar(txtBarcode);
        txtNum.setText(num_invoice_update > 0 ? String.valueOf(num_invoice_update) : Setting_Language.generate);
        // stock data
        comboStock.setItems(FXCollections.observableArrayList(getStockNames()));
        comboStock.getSelectionModel().select(getName());
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

    @NotNull
    private List<String> getStockNames() {
        try {
            return stockService.getStockNames();
        } catch (DaoException e) {
            logError(e);
        }
        return List.of();
    }

    private String getName() {
        try {
            return stockService.getStockById(1).getName();
        } catch (DaoException e) {
            logError(e);
        }
        return "";
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
        dataPublisher.getPublisherAddEmployee().addObserver(message -> comboDelegate.setItems(FXCollections.observableArrayList(getDelegateNames())));
        dataPublisher.getPublisherAddStock().addObserver(message -> comboStock.setItems(FXCollections.observableArrayList(getStockNames())));
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
        txtPaid.disableProperty().bind(radioDeffer.selectedProperty().not());
        radioCash.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                radioDeffer.setSelected(false);
                txtPaid.setText(txtRestAfterDiscount.getText());
            }
        });
        txtOtherDiscount.textProperty().addListener((observable, oldValue, newValue) -> otherDiscount());
        txtSumTotals.textProperty().addListener((observableValue, s, t1) -> otherDiscount());

        txtPaid.textProperty().addListener((observableValue, s, t1) -> {
            double paid = DoubleSetting.parseDoubleOrDefault(t1);
            double afterDiscount = DoubleSetting.parseDoubleOrDefault(txtRestAfterDiscount.getText());
            txtRestAfterPaid.setText(String.valueOf(afterDiscount - paid));
        });

        txtRestAfterDiscount.textProperty().addListener((observableValue, s, t1) -> {
            double paid = DoubleSetting.parseDoubleOrDefault(txtPaid.getText());
            double rad = DoubleSetting.parseDoubleOrDefault(t1);
            txtRestAfterPaid.setText(String.valueOf(rad - paid));
        });

        textInvoiceTotal.textProperty().bind(txtRestAfterDiscount.textProperty());
    }

    private void otherDiscount() {
        double totalCost = DoubleSetting.parseDoubleOrDefault(txtSumTotals.getText());
        double discountValue = DoubleSetting.parseDoubleOrDefault(txtOtherDiscount.getText());
        txtRestAfterDiscount.setText(String.valueOf(totalCost - discountValue));

        // paid setting
        if (radioCash.isSelected()) {
            txtPaid.setText(txtRestAfterDiscount.getText());
        }
    }

    private void sumTotals() {
        double total = getSumBuyFunction(BasePurchasesAndSales::getTotal, table.getItems());
        double quantity = getSumBuyFunction(BasePurchasesAndSales::getQuantity, table.getItems());
        double discount = getSumBuyFunction(BasePurchasesAndSales::getDiscount, table.getItems());
        textSumCount.setText(String.valueOf(table.getItems().size()));
        txtSumQuantity.setText(String.valueOf(quantity));
        txtBeforeDiscount.setText(String.valueOf(total));
        txtSumDiscount.setText(String.valueOf(discount));
        txtSumTotals.setText(String.valueOf(total - discount));
        checkTableForZeroBalanceOrPriceBoolean.set(checkTableForZeroBalanceOrPrice(table.getItems()));
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
            purchase.setPrice(t.getNewValue() == null ? 0.0 : t.getNewValue());
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
        var items = itemsService.getItemByItemIdAndStockId(purchase.getItems().getId(), 1);
        items.setNameItem(purchase.getItems().getNameItem());
        items.setItemsUnitsModelList(new ArrayList<>());
        items.setItems_packageList(new ArrayList<>());

        // update price
        var unitIdPurchase = purchase.getUnitsType().getValue();
        var b = invoiceBuy.updateItemPrice(items, roundToTwoDecimalPlaces(purchase.getPrice() / unitIdPurchase), priceTypeByNameId);
        if (b) {
            var i = itemsService.commitItemUpdate(items);
            if (i >= 1) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        LoadDataAndList.get2ItemsLoad();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e.getCause());
                    }
                }).start();
            }
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

        BooleanBinding binding = txtSumTotals.textProperty().lessThanOrEqualTo(String.valueOf(0.0))
                .or(comboStock.valueProperty().isNull())
                .or(table.itemsProperty().isNull());

        if (designInterface.showDataForCustomer()) binding.or(comboDelegate.valueProperty().isNull());

        btnPrintSave.disableProperty().bind(binding);
        btnSave.disableProperty().bind(binding);
        var observableValue = new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return num_invoice_update > 0;
            }
        };
        btnNew.disableProperty().bind(observableValue);
        btnUpdateItem.disableProperty().bind(observableValue);
    }

    private void addItem(int num) {
        try {
            new AddItemApplication(num, dataPublisher, daoFactory).start(new Stage());
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
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
    }

    private void addColumnType() {
        TableColumn<T1, String> typeColumn = new TableColumn<>(Setting_Language.WORD_TYPE);
        typeColumn.setCellValueFactory(features -> features.getValue().getUnitsType().unit_nameProperty());
        typeColumn.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(getUnitsModelList().stream()
                .map(UnitsModel::getUnit_name)
                .toList())));
        typeColumn.setOnEditCommit(event -> {
            T1 item = event.getRowValue();
            try {
                UnitsModel unitsModel = unitsService.getUnitsByName(event.getNewValue());
                item.setUnitsType(unitsModel);
                var selPrice1 = dataInterface.invoiceBuy().getItemsPrice(item.getItems(), priceTypeByNameId);
                item.setPrice(selPrice1 * unitsModel.getValue());
//                item.getUnitsType().setUnit_name(event.getNewValue());
                updateData(item);
                table.refresh();
            } catch (DaoException e) {
                logError(e);
            }
        });
        table.getColumns().add(2, typeColumn);
    }

}
