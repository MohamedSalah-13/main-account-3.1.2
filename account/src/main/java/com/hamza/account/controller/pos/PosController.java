package com.hamza.account.controller.pos;


import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.controller.name_account.NameController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.features.key_setting.UpdateInterface;
import com.hamza.account.features.key_setting.UpdateQuantity;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.account.controller.invoice.DialogCashPaid.showCashChangeDialog;
import static com.hamza.account.controller.invoice.DialogCashPaid.showPriceSelectionDialog;
import static com.hamza.account.controller.invoice.UpdateInvoiceRow.updateData;
import static com.hamza.account.otherSetting.Currency_Setting.getCurrency;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;
import static com.hamza.controlsfx.others.TextFormat.createNumericTextFormatter;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "pos/pos-view.fxml")
public class PosController extends ButtonSetting {

    private static final int PAGE_SIZE = 100;
    private final Publisher<String> publisherAddCustomer;
    private final DaoFactory daoFactory;
    private final List<Button> paneList = new ArrayList<>();
    private final DataPublisher dataPublisher;
    private final NameController<Sales, Total_Sales, Customers, CustomerAccount> nameController;
    private final DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterface;
    private final Map<BasePurchasesAndSales, String> originalNames = new HashMap<>();
    private final Map<Integer, List<Button>> groupButtonsCache = new HashMap<>();
    private final Map<Integer, List<ItemsModel>> groupItemsCache = new HashMap<>();
    private final Map<Integer, Button> itemButtonCache = new HashMap<>();
    private final Map<Integer, ItemRef> itemRefById = new HashMap<>();
    private final Map<String, List<ItemRef>> searchIndex = new HashMap<>();
    private final List<ItemRef> allIndexedItems = new ArrayList<>();
    private final Label loadingLabel = new Label("جاري التحميل...");
    private final Button loadMoreButton = new Button("المزيد");
    private List<ItemsModel> itemsList;
    private List<MainGroups> mainGroupList = new ArrayList<>();
    private MainGroups selectedMainGroup;
    private List<Button> currentPagedButtons = new ArrayList<>();
    private int currentPage = 0;
    private boolean inSearchMode = false;
    private int customerId = 0;
    @FXML
    private FlowPane hBox;
    @FXML
    private FlowPane flowPane;
    @FXML
    private TableView<BasePurchasesAndSales> tableView;
    @FXML
    private SplitPane splitPane;
    @Getter
    @FXML
    private Button btnPay;
    @FXML
    private Button btnZero, btnOne, btnTwo, btnThree, btnFour, btnFive, btnSix, btnSeven, btnEight, btnNine, btnDecimalPoint;
    @FXML
    private Button btnQuantity, btnDiscount, btnDiscountPercent, btnPrice, btnBackSpace, btnAdds, btnClear, btnInvoiceSuspension, btnOutstandingBills;
    @FXML
    private Button btnAddCustom, btnCustomers, btnUpdate;
    @FXML
    private TextField textCode, textCustomName, textTel, textAddress, textSearch;
    @FXML
    private Label labelCode, labelCustomName, labelDate;
    @FXML
    private DatePicker datePicker;
    @FXML
    private Text textTotalCost, textTotal, textDiscount, textAddons, textCount;
    @FXML
    private ComboBox<String> comboArea;
    @FXML
    private StackPane stackPane;
    @FXML
    private ComboBox<String> searchBy;
    private MaskerPaneSetting maskerPaneSetting;
    private String bounds = "";

    public PosController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory);
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.publisherAddCustomer = dataPublisher.getPublisherAddNameCustomer();
        this.dataInterface = new CustomData(daoFactory, dataPublisher);
        this.nameController = new NameController<>(dataInterface, daoFactory, dataPublisher);
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        setupLoadMoreButton();
        loadData();
        getTextCode();
        buttonGraphic();
        permissionButtons();


        addTable();
        setTableView(tableView);
        otherSetting();
        initializeButtonHandlers();
        setupKeyboardHandlers();
        splitPane.setDividerPosition(0, getSplitPaneDividerPos());

        dataPublisher.getPublisherAddItem().addObserver(message -> {
            if (!message.isActiveItem()) {
                paneList.removeIf(pane -> pane.getId().equals(message.getNameItem().toLowerCase()));
                flowPane.getChildren().removeIf(node -> node.getId().equals(message.getNameItem().toLowerCase()));
            }
        });

    }

    private void permissionButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnAddCustom::setDisable, UserPermissionType.CUSTOMER_SHOW);
        permissionDisableService.applyPermissionBasedDisable(btnCustomers::setDisable, UserPermissionType.CUSTOMER_SHOW);
    }

    private void otherSetting() {
        textTel.setTextFormatter(createNumericTextFormatter());
        textCode.setTextFormatter(createNumericTextFormatter());
        btnInvoiceSuspension.setDisable(true);
        btnOutstandingBills.setDisable(true);
        searchBy.setDisable(true);
        DateSetting.dateAction(datePicker);
        Platform.runLater(textCustomName::requestFocus);

        textCustomName.setPromptText(Setting_Language.WORD_NAME);
        textTel.setPromptText(Setting_Language.WORD_TEL);
        textAddress.setPromptText(Setting_Language.WORD_ADDRESS);
        comboArea.setPromptText(Setting_Language.AREA);

        // add area
        ObservableList<String> areaList = FXCollections.observableArrayList(getAreas().stream().map(Area::getArea_name).toList());
        comboArea.setItems(areaList);

        mainGroupList = getMainGroupList();
        mainGroupList.forEach(mainGroup -> {
            var e = new Button(mainGroup.getName());
            e.setOnAction(event -> {
                loadItemsByMainGroup(mainGroup, false);
            });
            hBox.getChildren().add(e);
        });

        var index = new Object() {
            int value = 0;
        };

        hBox.getChildren().forEach(node -> {
            if (node instanceof Button button) {
                index.value++;
                button.setFont(Font.font(getPosInvoiceFontNameSize()));
//                button.styleProperty().set("-fx-font-size: " + getPosInvoiceFontNameSize() + "px;");
                button.getStyleClass().add("category-button");

                if (index.value % 2 == 0) {
                    button.setStyle(button.getStyle() + "; -fx-background-color: #690b0b; -fx-text-fill: white;");
                } else {
                    button.setStyle(button.getStyle() + "; -fx-background-color: rgba(243,253,163,0.62); -fx-text-fill: black;");
                }
            }
        });

        if (!mainGroupList.isEmpty() && selectedMainGroup == null) {
            selectedMainGroup = mainGroupList.getFirst();
            loadItemsByMainGroup(selectedMainGroup, false);
        }
    }

    private void loadItemsByMainGroup(MainGroups mainGroup, boolean forceReload) {
        selectedMainGroup = mainGroup;
        if (!forceReload && tryUseCachedGroup(mainGroup.getId())) return;

        flowPane.getChildren().setAll(loadingLabel);
        maskerPaneSetting.showMaskerPane(() -> {
            paneList.clear();
            itemsList = loadItemsForGroup(mainGroup.getId());
            paneList.addAll(buildButtons(itemsList));
            cacheGroup(mainGroup.getId(), itemsList, paneList);
        });
        maskerPaneSetting.getVoidTask().setOnSucceeded(event -> showGroupFromCache(mainGroup.getId()));
    }

    private List<Area> getAreas() {
        try {
            return areaService.fetchAllAreas();
        } catch (DaoException e) {
            logError(e);
            return List.of();
        }
    }

    private List<MainGroups> getMainGroupList() {
        try {
            return mainGroupService.getMainGroupList();
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private void initializeButtonHandlers() {
        // buttons action
        btnZero.setOnAction(e -> handleNumberButton("0"));
        btnOne.setOnAction(e -> handleNumberButton("1"));
        btnTwo.setOnAction(e -> handleNumberButton("2"));
        btnThree.setOnAction(e -> handleNumberButton("3"));
        btnFour.setOnAction(e -> handleNumberButton("4"));
        btnFive.setOnAction(e -> handleNumberButton("5"));
        btnSix.setOnAction(e -> handleNumberButton("6"));
        btnSeven.setOnAction(e -> handleNumberButton("7"));
        btnEight.setOnAction(e -> handleNumberButton("8"));
        btnNine.setOnAction(e -> handleNumberButton("9"));
        btnDecimalPoint.setOnAction(e -> handleNumberButton("."));
        btnBackSpace.setOnAction(e -> handleBackspace());

        btnQuantity.setOnAction(e -> {
//            handleOperationButton("quantity");
            var quantity = promptAndSetNumber("الكمية", 1);
            quantity.ifPresent(q -> getSum());
        });
        btnPrice.setOnAction(e -> {
//            handleOperationButton("price");
            var price = promptAndSetNumber("السعر", 2);
            price.ifPresent(p -> getSum());

        });

        btnAddCustom.setOnAction(event -> addCustomer());
        btnCustomers.setOnAction(event -> getCustomers());

        splitPane.getDividers().getFirst().positionProperty().addListener(
                (obs, oldPos, newPos) ->
                        setSplitPaneDividerPos(splitPane.getDividers().getFirst().getPosition()));

        btnClear.setOnAction(event -> clearData());
        btnPay.setOnAction(actionEvent -> saveInvoice());
        textSearch.textProperty().addListener((observable, oldValue, newValue) -> searchAndSortNodes(newValue));
        btnUpdate.setOnAction(event -> refreshData());
        flowPane.getChildren().addListener((ListChangeListener<? super Node>) observable -> textCount.setText(Setting_Language.WORD_COUNT + " -: " + flowPane.getChildren().size()));


        btnDiscountPercent.setOnAction(e -> {
            var prompted = promptAndSetNumber("خصم نسبة", Double.parseDouble(textDiscount.getText()));
            var discount = prompted.map(d -> d / 100);
            var currentTotal = Double.parseDouble(textTotal.getText());
            discount.ifPresent(d -> textDiscount.setText(String.valueOf(d * currentTotal)));
        });

        btnDiscount.setOnAction(e -> {
            var prompted = promptAndSetNumber("خصم", Double.parseDouble(textDiscount.getText()));
            prompted.ifPresent(prompted1 -> textDiscount.setText(String.valueOf(prompted1)));
        });
        btnAdds.setOnAction(e -> {
            var add = promptAndSetNumber("إضافة", Double.parseDouble(textAddons.getText()));
            add.ifPresent(a -> textAddons.setText(String.valueOf(a)));
        });

        textAddons.textProperty().addListener((observable, oldValue, newValue) -> sumTotals());
        textDiscount.textProperty().addListener((observable, oldValue, newValue) -> sumTotals());
        textTotal.textProperty().addListener((observable, oldValue, newValue) -> sumTotals());


        btnInvoiceSuspension.setOnAction(e -> {
            var posInvoiceSetting = createPosInvoiceSetting();
            posInvoiceSetting.suspendInvoice();
        });

        btnOutstandingBills.setOnAction(e -> createPosInvoiceSetting().showSuspendedInvoices());

        textCustomName.textProperty().addListener((obs, oldVal, newVal) -> onCustomerNameChanged(newVal));
        textTel.textProperty().addListener((obs, oldVal, newVal) -> onCustomerTelChanged(newVal));

        isChangeDataProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                getSum();
                isChangeDataProperty().setValue(false);
            }
        });

    }

    private void addTable() {
        new TableColumnAnnotation().getTable(tableView, BasePurchasesAndSales.class);
        tableView.refresh();
        tableView.setEditable(true);
        tableView.getSelectionModel().setCellSelectionEnabled(true);

        final TableColumn<BasePurchasesAndSales, String> nameColumn = createNameColumn();
        tableView.getColumns().addFirst(nameColumn);

        tableView.getColumns().removeLast();
        tableView.getColumns().removeLast();
        tableView.getColumns().removeLast();
        tableView.getColumns().removeLast();

        tableView.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                removeItemFromTable(i);
            }
        }));

        tableView.getItems().addListener((ListChangeListener<? super BasePurchasesAndSales>) change -> {
            getSum();
            tableView.refresh();

        });

        // Add column width change listeners
        tableView.getColumns().forEach(column ->
                column.widthProperty().addListener((obs, oldWidth, newWidth) ->
                        setSplitPaneDividerPos(splitPane.getDividers().getFirst().getPosition())));

        // update quantity
        var updateQuantity = new UpdateQuantity(new UpdateInterface() {
            @Override
            public TableView<? extends BasePurchasesAndSales> getTable() {
                return tableView;
            }

            @Override
            public void update(BasePurchasesAndSales basePurchasesAndSales) {
                updateData(basePurchasesAndSales);
            }

            @Override
            public void sum() {
                getSum();
            }
        });
        tableView.setOnKeyPressed(updateQuantity.tableKeyPressed());
    }

    private void removeItemFromTable(int i) {
        BasePurchasesAndSales item = tableView.getItems().get(i);
        if (originalNames.containsKey(item)) {
            item.getItems().setNameItem(originalNames.get(item));
            originalNames.remove(item);
        }
        tableView.getItems().remove(i);
        tableView.refresh();
    }

    private TableColumn<BasePurchasesAndSales, String> createNameColumn() {
        final TableColumn<BasePurchasesAndSales, String> nameColumn =
                new TableColumn<>(Setting_Language.WORD_NAME);

        nameColumn.setEditable(true);
        nameColumn.setPrefWidth(200);

        nameColumn.setCellValueFactory(cellData ->
                cellData.getValue().getItems().nameItemProperty());

        nameColumn.setCellFactory(TextFieldTableCell.forTableColumn());

        nameColumn.setOnEditCommit(evt -> {
            BasePurchasesAndSales rowItem = evt.getRowValue();
            String newName = evt.getNewValue() == null ? "" : evt.getNewValue().trim();
            if (!originalNames.containsKey(rowItem)) {
                originalNames.put(rowItem, rowItem.getItems().getNameItem());
            }

            rowItem.getItems().setNameItem(newName);
            getSum();
            tableView.refresh();
        });

        return nameColumn;
    }

    private void refreshData() {
        maskerPaneSetting.showMaskerPane(LoadDataAndList::get2ItemsLoad);
        maskerPaneSetting.getVoidTask().setOnSucceeded(event -> {
            clearGroupCache();
            loadData();
        });
    }

    private void loadData() {
        if (selectedMainGroup != null) {
            loadItemsByMainGroup(selectedMainGroup, true);
        } else {
            flowPane.getChildren().clear();
        }
    }

    @NotNull
    private Button getButton(ItemsModel itemsModel) {
        var buttonWidth = 130;
        var buttonHeight = 150;
        var pound = getCurrency().stream().map(s -> s.getValue().getSymbol(s.getKey())).findFirst();// ج.م.
        pound.ifPresent(s -> bounds = s.split("\\.")[0]);
        var posInvoiceFontNameSize = getPosInvoiceFontNameSize();
        var posInvoiceFontPriceSize = getPosInvoiceFontPriceSize();

        // Create Text nodes for name and price with different sizes
        Text nameText = new Text(itemsModel.getNameItem());
        nameText.setFont(Font.font("Tahoma", posInvoiceFontNameSize));
        nameText.setWrappingWidth(buttonWidth - 10);
        nameText.setStyle("-fx-fill: black;");

        String priceName = itemsModel.getSelPrice1() + " ";
        if (getPosInvoiceShowSelectPrice()) {
            StringBuilder name = new StringBuilder();
            name.append("(").append(itemsModel.getSelPrice1());
            if (itemsModel.getSelPrice2() > 0) name.append("-").append(itemsModel.getSelPrice2());
            if (itemsModel.getSelPrice3() > 0) name.append("-").append(itemsModel.getSelPrice3());
            name.append(")");
            priceName = name.toString();
        }

        Text priceText = new Text(priceName + bounds);
        priceText.setFont(Font.font("Tahoma", posInvoiceFontPriceSize));
        priceText.setStyle("-fx-fill: #910a0a;");

        Button button = new Button();
        button.setTooltip(new Tooltip(itemsModel.getNameItem() + "\n" + itemsModel.getSelPrice1() + " " + bounds));
        button.setId(itemsModel.getNameItem().toLowerCase());
        button.setContentDisplay(ContentDisplay.TOP);
        var itemImage = itemsModel.getItem_image();

        var defaultBlog = new ByteArrayInputStream(new byte[0]);
        if (itemImage != null && itemImage.length > 0) {
            defaultBlog = new ByteArrayInputStream(itemImage);
        }
        var imageView = new ImageView(new Image(defaultBlog));
        imageView.setFitHeight(buttonHeight - 60);
        imageView.setFitWidth(buttonWidth - 20);

        // Create VBox to hold image and texts
        var vbox = new javafx.scene.layout.VBox(5);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.getChildren().addAll(imageView, nameText, priceText);
        button.setGraphic(vbox);

        button.setOnMouseClicked(event -> {
            if (getPosInvoiceShowSelectPrice()) {
                try {
                    var selectedPrice = showPriceSelectionDialog(itemsModel, selPriceItemService);
                    selectedPrice.ifPresentOrElse(price -> addDataToTable(itemsModel, price), () -> {
                        addDataToTable(itemsModel, itemsModel.getSelPrice1());
                    });
                } catch (DaoException e) {
                    logError(e);
                }
            } else {
                addDataToTable(itemsModel, itemsModel.getSelPrice1());
            }
        });


        button.setMaxSize(buttonWidth, buttonHeight);
        button.setMinSize(buttonWidth, buttonHeight);
        button.setPrefSize(buttonWidth, buttonHeight);
        return button;
    }


    private void getTextCode() {
        try {
            BaseTotals maxId = totalSalesService.getMaxId();
            textCode.setText(String.valueOf(maxId.getId() + 1));
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void saveInvoice() {
        try {
            Platform.runLater(this::getTextCode);
            if (textCustomName.getText().isEmpty() || customerId == 0) {
                customerId = 1;
            }

            if (tableView.getItems().isEmpty()) {
                throw new Exception("من فضلك أدخل جميع البيانات...؟");
            }

            // search quantity or price zero
            List<BasePurchasesAndSales> basePurchasesAndSalesList = new ArrayList<>();
            tableView.getItems().forEach(b -> {
                if (b.getQuantity() == 0 || b.getPrice() == 0) {
                    basePurchasesAndSalesList.add(b);
                }
            });

            if (!basePurchasesAndSalesList.isEmpty()) {
                throw new Exception("لا يمكن ان يكون الكمية والسعر يساوى صفر ...؟");
            }

            var customers = new Customers(customerId);

            // insert
            if (AllAlerts.confirmSave()) {
                Total_Sales totalSales = new Total_Sales();
                totalSales.setCustomers(customers);

                List<Sales> salesList = new ArrayList<>();
                var invoiceNumber = Integer.parseInt(textCode.getText());
                for (BasePurchasesAndSales basePurchasesAndSales : tableView.getItems()) {
                    Sales sales = new Sales();
                    var items = basePurchasesAndSales.getItems();
                    sales.setItems(items);
                    sales.setQuantity(basePurchasesAndSales.getQuantity());
                    sales.setPrice(basePurchasesAndSales.getPrice());
                    sales.setDiscount(basePurchasesAndSales.getDiscount());
                    sales.setInvoiceNumber(invoiceNumber);
                    sales.setNumItem(items.getId());
                    UnitsModel unitsModel = items.getUnitsType();
                    sales.setUnitsType(unitsModel);
                    sales.setBuy_price(roundToTwoDecimalPlaces(items.getBuyPrice() * unitsModel.getValue()));
                    sales.setExpiration_date(null);
                    salesList.add(sales);
                }

                var posPaymentMethods = new PosPaymentMethods().showAndWait();
                var totalAmount = Double.parseDouble(textTotal.getText());
                var discountValue = Double.parseDouble(textDiscount.getText());

                posPaymentMethods.ifPresentOrElse(paymentMethod -> {
                    setPrintCustomer(paymentMethod.isPrintCustomer());
                    setPrintToKitchen(paymentMethod.isPrintToKitchen());
                    setPrintInvoice(paymentMethod.isPrintInvoice());

                    var invoiceType = paymentMethod.getInvoiceType();
                    totalSales.setInvoiceType(invoiceType);
                    if (invoiceType.equals(InvoiceType.CASH)) {
                        totalSales.setPaid(roundToTwoDecimalPlaces(totalAmount - discountValue));
                    } else {
                        var paid = roundToTwoDecimalPlaces(paymentMethod.getPaid());
                        totalSales.setPaid(paid);
                    }
                    log.info("invoice type: {} : paid: {}", totalSales.getPaid(), totalSales.getInvoiceType());
                }, () -> {
                    totalSales.setInvoiceType(InvoiceType.CASH);
                    totalSales.setPaid(roundToTwoDecimalPlaces(totalAmount - discountValue));
                });

                totalSales.setId(invoiceNumber);
                totalSales.setDate(datePicker.getValue().toString());
                totalSales.setTotal(roundToTwoDecimalPlaces(totalAmount));
                totalSales.setDiscount(roundToTwoDecimalPlaces(discountValue));
                totalSales.setStockData(new Stock(1));
                totalSales.setEmployeeObject(new Employees(1));
                totalSales.setTreasuryModel(new TreasuryModel(1));
                totalSales.setUsers(LogApplication.usersVo);
                totalSales.setSalesList(salesList);

                int save = daoFactory.totalsSalesDao().insert(totalSales);
                if (save == 1) {
                    AllAlerts.alertSave();

                    // update items name after save
                    for (int i = 0; i < tableView.getItems().size(); i++) {
                        removeItemFromTable(i);

                    }

                    // show cash paid
                    if (getInvoiceShowScreenPaid()) {
                        if (totalSales.getInvoiceType().equals(InvoiceType.CASH)) {
                            showCashChangeDialog(totalAmount - discountValue);
                        }
                    }

                    // print invoice setting
                    printInvoice();
                    // update data
                    Thread thread = new Thread(() -> {
                        try {
                            Thread.sleep(5000);
                            dataInterface.publisherPurchaseOrSales().notifyObservers();
                        } catch (InterruptedException e) {
                            logError(e);
                        }
                    });
                    thread.setDaemon(true);
                    thread.start();
                }
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void printInvoice() {
        maskerPaneSetting.showMaskerPane(() -> {
            Print_Reports printReports = new Print_Reports();
            int invNumber = Integer.parseInt(textCode.getText());
            var discountValue = Double.parseDouble(textDiscount.getText());
            List<ModelPrintInvoice> modelPrintInvoices = new ArrayList<>();

            for (BasePurchasesAndSales basePurchasesAndSales : tableView.getItems()) {
                ModelPrintInvoice modelPrintInvoice
                        = new ModelPrintInvoice(basePurchasesAndSales.getItems().getNameItem(),
                        basePurchasesAndSales.getItems().getBarcode(), basePurchasesAndSales.getTypeName()
                        , basePurchasesAndSales.getPrice(), basePurchasesAndSales.getQuantity(), basePurchasesAndSales.getTotal()
                        , basePurchasesAndSales.getDiscount(), basePurchasesAndSales.getTotal() - basePurchasesAndSales.getDiscount()
                );
                modelPrintInvoices.add(modelPrintInvoice);
            }

            var format = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            var string = datePicker.getValue().toString();

            if (isPrintInvoice())
                printReports.printReceiptInvoice(modelPrintInvoices, dataInterface.designInterface().nameTextOfInvoice(), invNumber
                        , discountValue, format, string, Double.parseDouble(textAddons.getText()));

            if (isPrintToKitchen())
                printReports.printReceiptInvoiceKitchen(modelPrintInvoices, dataInterface.designInterface().nameTextOfInvoice(), invNumber
                        , discountValue, format, string);

            if (isPrintCustomer())
                printReports.printReceiptNames(textAddress.getText(), textTel.getText(), textCustomName.getText());
        });

        maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> clearData());
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnAddCustom.setGraphic(createIcon(images.add));
        btnCustomers.setGraphic(createIcon(images.personCustomer));
        btnClear.setGraphic(createIcon(images.erase));
        btnPay.setGraphic(createIcon(images.pay));
    }

    private void clearData() {
        tableView.getItems().clear();
        customerId = 0;
        textCustomName.clear();
        textTel.clear();
        textAddress.clear();
        textSearch.clear();
        textTotalCost.setText("0");
        textTotal.setText("0");
        textAddons.setText("0");
        textDiscount.setText("0");
//        comboArea.getSelectionModel().clearSelection();
        getTextCode();
    }

    private void addCustomer() {
        try {
            if (customerId == 1) {
                throw new Exception("لا يمكن التعديل");
            }

            if (customerId > 0) {
                var b = AllAlerts.confirm_all("هل تريد تعديل البيانات");
                if (!b) return;
            }

            if (textCustomName.getText().isEmpty()) {
                textCustomName.requestFocus();
                throw new Exception("من فضلك ادخل الاسم...؟");
            }
            if (comboArea.getSelectionModel().getSelectedItem() == null) {
                comboArea.requestFocus();
                throw new Exception("من فضلك ادخل جميع البيانات...");
            }

            if (!AllAlerts.confirmSave()) {
                return;
            }

            Customers customers = new Customers();
            customers.setId(customerId);
            customers.setName(textCustomName.getText());
            customers.setTel(textTel.getText());
            customers.setAddress(textAddress.getText());
            customers.setSelPriceObject(new SelPriceTypeModel(1));
            customers.setUsers(new Users(1));
            customers.setArea(getAreas().stream().filter(area -> area.getArea_name().equals(comboArea.getSelectionModel().getSelectedItem())).findFirst().orElseThrow());

            var insert = 0;
            if (customerId > 0)
                insert = customerService.nameDao().update(customers);
            else insert = customerService.nameDao().insert(customers);
            if (insert == 1) {
                AllAlerts.alertSave();
                publisherAddCustomer.notifyObservers();
            }

        } catch (Exception e) {
            logError(e);
        }
    }

    private void getCustomers() {
        try {
            var stage = new Stage();
            var customersTableOpen = new TableOpen<>(nameController);
            nameController.objectPropertyProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    customerId = newValue.getId();
                    textCustomName.setText(newValue.getName());
                    textTel.setText(newValue.getTel());
                    textAddress.setText(newValue.getAddress());
                    comboArea.getSelectionModel().select(newValue.areaProperty().get().getArea_name());
                    stage.close();
                }
            });

            customersTableOpen.start(stage);
        } catch (Exception e) {
            logError(e);
        }
    }

    private void searchAndSortNodes(String newValue) {
        refreshFlowPaneNodes(newValue);
    }

    private void refreshFlowPaneNodes(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            inSearchMode = false;
            if (selectedMainGroup != null) {
                showGroupFromCache(selectedMainGroup.getId());
            }
            return;
        }

        var lowered = normalizeSearchText(searchText);
        flowPane.getChildren().setAll(loadingLabel);
        maskerPaneSetting.showMaskerPane(() -> {
            ensureAllGroupsIndexed();
            List<ItemRef> results = searchItems(lowered);
            results.sort((a, b) -> a.item.getNameItem().compareToIgnoreCase(b.item.getNameItem()));
            currentPagedButtons = buildButtonsFromRefs(results);
        });
        maskerPaneSetting.getVoidTask().setOnSucceeded(event -> {
            inSearchMode = true;
            setPagedView(currentPagedButtons, true);
        });
    }

    private void onCustomerNameChanged(String newValue) {
        if (newValue == null || newValue.isBlank()) {
            clearCustomerSelection();
            return;
        }
        getCustomerList().stream()
                .filter(c -> c.getName().equals(newValue))
                .findFirst()
                .ifPresent(this::applyCustomerFromName);
    }

    private void onCustomerTelChanged(String newValue) {
        if (newValue == null || newValue.isBlank()) {
            clearCustomerSelection();
            return;
        }
        getCustomerList().stream()
                .filter(c -> c.getTel().equals(newValue))
                .findFirst()
                .ifPresent(this::applyCustomerFromTel);
    }

    private List<Customers> getCustomerList() {
        try {
            return customerService.getCustomerList();
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private void applyCustomerFromName(Customers customer) {
        applyCustomerCommon(customer);
        textTel.setText(customer.getTel());
    }

    private void applyCustomerFromTel(Customers customer) {
        applyCustomerCommon(customer);
        textCustomName.setText(customer.getName());
    }

    private void applyCustomerCommon(Customers customer) {
        customerId = customer.getId();
        textAddress.setText(customer.getAddress());
        comboArea.getSelectionModel().select(customer.getArea().getArea_name());
    }

    private void clearCustomerSelection() {
        customerId = 0;
        textTel.clear();
        textCustomName.clear();
        textAddress.clear();
        comboArea.getSelectionModel().clearSelection();
    }


    private void addDataToTable(ItemsModel itemsModel, double selectedPrice) {
        var existingItem = tableView.getItems().stream()
                .filter(item -> item.getItems().equals(itemsModel))
                .findFirst();
        if (existingItem.isPresent()) {
            var item = existingItem.get();
            item.setQuantity(item.getQuantity() + 1);
            updateData(item);
            getSum();
            tableView.refresh();
        } else {
            BasePurchasesAndSales basePurchasesAndSales = new Sales();
            basePurchasesAndSales.setItems(itemsModel);
            basePurchasesAndSales.setQuantity(1);
            basePurchasesAndSales.setPrice(selectedPrice);
            basePurchasesAndSales.setDiscount(0);
            basePurchasesAndSales.setTotal(selectedPrice);
            tableView.getItems().add(basePurchasesAndSales);
        }
    }

    private PosInvoiceSetting createPosInvoiceSetting() {
        PosInvoiceSetting posInvoiceSetting = new PosInvoiceSetting();
        posInvoiceSetting.setCustomerId(customerId);

        posInvoiceSetting.customerIdProperty().addListener((observable, oldValue, newValue) -> customerId = newValue.intValue());
        comboArea.valueProperty().bind(posInvoiceSetting.comboAreaProperty());
        textCode.textProperty().bindBidirectional(posInvoiceSetting.textCodeProperty());
        textCustomName.textProperty().bindBidirectional(posInvoiceSetting.textCustomNameProperty());
        textTel.textProperty().bindBidirectional(posInvoiceSetting.textTelProperty());
        textAddress.textProperty().bindBidirectional(posInvoiceSetting.textAddressProperty());

        posInvoiceSetting.textAddonsProperty().addListener((observable, oldValue, newValue) -> textAddons.setText(newValue));
        posInvoiceSetting.textDiscountProperty().addListener((observable, oldValue, newValue) -> textDiscount.setText(newValue));
        posInvoiceSetting.textTotalProperty().addListener((observable, oldValue, newValue) -> textTotal.setText(newValue));
        posInvoiceSetting.invoiceItemsProperty().bindBidirectional(tableView.itemsProperty());
        return posInvoiceSetting;
    }

    private void getSum() {
        double total = getSumBuyFunction(BasePurchasesAndSales::getTotal, tableView.getItems());
        textTotal.setText(String.valueOf(total));
        sumTotals();
    }

    private void sumTotals() {
        var total = Double.parseDouble(textTotal.getText());
        var addons = Double.parseDouble(textAddons.getText());
        var discount = Double.parseDouble(textDiscount.getText());
        textTotalCost.setText(String.valueOf(roundToTwoDecimalPlaces(total + addons - discount)));
    }

    protected double getSumBuyFunction(ToDoubleFunction<BasePurchasesAndSales> function, List<BasePurchasesAndSales> list) {
        return roundToTwoDecimalPlaces(list.stream().mapToDouble(function).sum());
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
    }

    private void addMainCustomer() {
        try {
            customerId = 1;
            var customerByName = customerService.getCustomerById(1);
            textCustomName.setText(customerByName.getName());
            textTel.setText(customerByName.getTel());
            textAddress.setText(customerByName.getAddress());
            comboArea.getSelectionModel().select(customerByName.getArea().getArea_name());
            textCustomName.requestFocus();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void clearGroupCache() {
        groupButtonsCache.clear();
        groupItemsCache.clear();
        itemButtonCache.clear();
        itemRefById.clear();
        searchIndex.clear();
        allIndexedItems.clear();
    }

    private List<ItemsModel> loadItemsForGroup(int mainGroupId) {
        try {
            return itemsService.getMainItemsListWithoutInactiveByMainGroupId(mainGroupId);
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private List<Button> buildButtons(List<ItemsModel> items) {
        List<Button> buttons = new ArrayList<>(items.size());
        for (ItemsModel itemsModel : items) {
            buttons.add(getButtonCached(itemsModel));
        }
        return buttons;
    }

    private List<Button> buildButtonsFromRefs(List<ItemRef> refs) {
        List<Button> buttons = new ArrayList<>(refs.size());
        for (ItemRef ref : refs) {
            buttons.add(getButtonCached(ref.item));
        }
        return buttons;
    }

    private Button getButtonCached(ItemsModel itemsModel) {
        return itemButtonCache.computeIfAbsent(itemsModel.getId(), id -> getButton(itemsModel));
    }

    private void cacheGroup(int mainGroupId, List<ItemsModel> items, List<Button> buttons) {
        groupItemsCache.put(mainGroupId, new ArrayList<>(items));
        groupButtonsCache.put(mainGroupId, new ArrayList<>(buttons));
        indexItems(mainGroupId, items);
    }

    private boolean tryUseCachedGroup(int mainGroupId) {
        var cachedButtons = groupButtonsCache.get(mainGroupId);
        if (cachedButtons == null) return false;
        paneList.clear();
        paneList.addAll(cachedButtons);
        setPagedView(cachedButtons, false);
        return true;
    }

    private void showGroupFromCache(int mainGroupId) {
        var cachedButtons = groupButtonsCache.get(mainGroupId);
        if (cachedButtons == null) return;
        setPagedView(cachedButtons, false);
    }

    private void setPagedView(List<Button> buttons, boolean searchMode) {
        inSearchMode = searchMode;
        currentPagedButtons = buttons;
        currentPage = 0;
        showPage(true);
    }

    private void showPage(boolean reset) {
        int fromIndex = 0;
        if (!reset) {
            fromIndex = currentPage * PAGE_SIZE;
        }
        int toIndex = Math.min(currentPagedButtons.size(), (currentPage + 1) * PAGE_SIZE);
        if (reset) {
            flowPane.getChildren().setAll(currentPagedButtons.subList(0, toIndex));
        } else {
            flowPane.getChildren().addAll(currentPagedButtons.subList(fromIndex, toIndex));
        }

        if (toIndex < currentPagedButtons.size()) {
            if (!flowPane.getChildren().contains(loadMoreButton)) {
                flowPane.getChildren().add(loadMoreButton);
            }
        } else {
            flowPane.getChildren().remove(loadMoreButton);
        }
    }

    private void showNextPage() {
        int nextPage = currentPage + 1;
        int maxPages = (currentPagedButtons.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (nextPage >= maxPages) return;
        currentPage = nextPage;
        showPage(false);
    }

    private void setupLoadMoreButton() {
        loadMoreButton.setOnAction(e -> showNextPage());
    }

    private void ensureAllGroupsIndexed() {
        for (MainGroups group : mainGroupList) {
            ensureGroupItemsCached(group.getId());
        }
    }

    private void ensureGroupItemsCached(int mainGroupId) {
        if (groupItemsCache.containsKey(mainGroupId)) return;
        List<ItemsModel> items = loadItemsForGroup(mainGroupId);
        groupItemsCache.put(mainGroupId, new ArrayList<>(items));
        indexItems(mainGroupId, items);
    }

    private void indexItems(int mainGroupId, List<ItemsModel> items) {
        for (ItemsModel item : items) {
            if (itemRefById.containsKey(item.getId())) continue;
            ItemRef ref = new ItemRef(item, mainGroupId);
            itemRefById.put(item.getId(), ref);
            allIndexedItems.add(ref);
            for (String token : tokenize(item.getNameItem())) {
                searchIndex.computeIfAbsent(token, k -> new ArrayList<>()).add(ref);
            }
        }
    }

    private List<ItemRef> searchItems(String text) {
        var tokens = tokenize(text);
        if (tokens.isEmpty()) return new ArrayList<>(allIndexedItems);

        List<ItemRef> candidates = null;
        for (String token : tokens) {
            var list = searchIndex.get(token);
            if (list == null) return new ArrayList<>();
            candidates = intersectById(candidates, list);
            if (candidates.isEmpty()) return candidates;
        }

        String lowered = normalizeSearchText(text);
        List<ItemRef> filtered = new ArrayList<>();
        for (ItemRef ref : candidates) {
            if (normalizeSearchText(ref.item.getNameItem()).contains(lowered)) {
                filtered.add(ref);
            }
        }
        return filtered;
    }

    private List<ItemRef> intersectById(List<ItemRef> a, List<ItemRef> b) {
        if (a == null) return new ArrayList<>(b);
        Map<Integer, ItemRef> map = new HashMap<>();
        for (ItemRef ref : b) {
            map.put(ref.item.getId(), ref);
        }
        List<ItemRef> out = new ArrayList<>();
        for (ItemRef ref : a) {
            if (map.containsKey(ref.item.getId())) out.add(ref);
        }
        return out;
    }

    private List<String> tokenize(String text) {
        String normalized = normalizeSearchText(text);
        if (normalized.isBlank()) return List.of();
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) tokens.add(part);
        }
        return tokens;
    }

    private String normalizeSearchText(String text) {
        return text == null ? "" : text.trim().toLowerCase();
    }

    private static class ItemRef {
        private final ItemsModel item;
        private final int groupId;

        private ItemRef(ItemsModel item, int groupId) {
            this.item = item;
            this.groupId = groupId;
        }
    }

}
