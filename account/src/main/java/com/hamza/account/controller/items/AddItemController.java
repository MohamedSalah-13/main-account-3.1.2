package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.dataByName.OpenAddAreaApplication;
import com.hamza.account.controller.dataByName.impl.MainGroupImpl2;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.*;
import com.hamza.account.view.AddGroupApp;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.account.controller.setting.ComboSetting.comboSubSetting;
import static com.hamza.account.controller.setting.ComboSetting.comboTypeSetting;
import static com.hamza.controlsfx.others.Utils.*;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "items/addItem-view.fxml")
public class AddItemController implements AppSettingInterface {

    private final int codeItem;
    private final DataPublisher dataPublisher;
    private final ImageChoose imageChoose = new ImageChoose();


    private final UnitsService unitsService = ServiceRegistry.get(UnitsService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final SelPriceItemService selPriceItemService = ServiceRegistry.get(SelPriceItemService.class);

    private final ObservableList<Items_Stock_Model> stockBalances = FXCollections.observableArrayList();

    private int mainId;
    private int subId;

    @FXML
    private ComboBox<String> comboMainGroup, comboSupGroup, comboType;

    @FXML
    private TextField txtCode, txtBarcode, txtItemName, txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3,
            txtMiniQuantity, txtBalance;

    @FXML
    private Label labelCode, labelBarcode, labelName, labelBuyPrice, labelSelPrice, labelSelPrice2, labelSelPrice3,
            labelMiniQuantity, labelMainGroup, labelSupGroup, labelType, labelFirstBalance;

    @FXML
    private TabPane tabPane;
    @FXML
    private HBox boxMain;
    @FXML
    @Getter
    private Button btnAddMainGroup, btnAddSubGroup, btnSave, btnSaveDuplicate, btnClose, btnBarcode;

    @FXML
    private StackPane stackPane;

    @FXML
    private CheckBox checkItemValidate, checkItemActive;

    @FXML
    private TextField textDaysValidate, textAlertBefore;

    @FXML
    private TableView<Items_Stock_Model> tableStockBalances;

    @FXML
    private ImageView imageAdd;

    @FXML
    private Button btnAddImage, btnClearImage;

    private TableUnitsSetting tableUnitsSetting;

    public AddItemController(int codeItem, DataPublisher dataPublisher) {
        this.codeItem = codeItem;
        this.dataPublisher = dataPublisher;

        dataPublisher.getPublisherAddMainGroup().addObserver(message -> {
            comboMainGroup.setItems(FXCollections.observableList(getMainGroupsNames()));
            comboMainGroup.getSelectionModel().selectLast();
        });

        dataPublisher.getPublisherAddSubGroup().addObserver(message -> {
            List<String> groupListByMainId = getSubGroupsNamesByMainId();
            comboSupGroup.setItems(FXCollections.observableList(groupListByMainId));
        });

        dataPublisher.getPublisherAddStock().addObserver(message -> reloadStockBalancesKeepingValues());
    }

    @FXML
    public void initialize() {
        stockBalancesTableSetting();
        otherSetting();
        comboTypeOption();
        addValidate();
        nameSetting();
        action();
        addBarcode();
        selectGroupSubAndType();

        btnClearImage.fire();
        permButtons();
        buttonGraphic();
        selectData();
    }


    private void stockBalancesTableSetting() {
        tableStockBalances.setEditable(true);
        tableStockBalances.setItems(stockBalances);
        tableStockBalances.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Items_Stock_Model, String> stockColumn = new TableColumn<>("المخزن");
        stockColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getStock() == null ? "" : cell.getValue().getStock().getName()
        ));
        stockColumn.setPrefWidth(260);

        TableColumn<Items_Stock_Model, Double> firstBalanceColumn = new TableColumn<>("رصيد أول المدة");
        firstBalanceColumn.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getFirstBalance()).asObject());
        firstBalanceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        firstBalanceColumn.setOnEditCommit(event -> {
            Items_Stock_Model row = event.getRowValue();

            if (row == null) {
                tableStockBalances.refresh();
                return;
            }

            double oldValue = event.getOldValue() == null ? 0 : event.getOldValue();
            double newValue = event.getNewValue() == null ? 0 : event.getNewValue();

            if (newValue < 0) {
                AllAlerts.alertError("لا يمكن إدخال رصيد أقل من صفر");
                row.setFirstBalance(oldValue);
                row.setCurrentQuantity(oldValue);
            } else {
                row.setFirstBalance(newValue);
                row.setCurrentQuantity(newValue);
            }

            updateTotalOpeningBalanceText();
            tableStockBalances.refresh();
        });
        firstBalanceColumn.setPrefWidth(180);

        TableColumn<Items_Stock_Model, Double> currentQuantityColumn = new TableColumn<>("الرصيد الحالي");
        currentQuantityColumn.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getCurrentQuantity()).asObject());
        currentQuantityColumn.setPrefWidth(180);

        tableStockBalances.getColumns().setAll(stockColumn, firstBalanceColumn, currentQuantityColumn);

        loadAllStocksForOpeningBalances();
    }

    private void loadAllStocksForOpeningBalances() {
        try {
            stockBalances.clear();

            for (String stockName : stockService.getStockNames()) {
                Stock stock = stockService.getStockByName(stockName);

                if (stock == null) {
                    continue;
                }

                Items_Stock_Model model = new Items_Stock_Model();
                model.setStock(stock);
                model.setFirstBalance(0);
                model.setCurrentQuantity(0);
                stockBalances.add(model);
            }

            updateTotalOpeningBalanceText();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void reloadStockBalancesKeepingValues() {
        List<Items_Stock_Model> oldValues = new ArrayList<>(stockBalances);
        loadAllStocksForOpeningBalances();

        for (Items_Stock_Model row : stockBalances) {
            if (row.getStock() == null) {
                continue;
            }

            oldValues.stream()
                    .filter(old -> old.getStock() != null)
                    .filter(old -> old.getStock().getId() == row.getStock().getId())
                    .findFirst()
                    .ifPresent(old -> {
                        row.setId(old.getId());
                        row.setItemsModel(old.getItemsModel());
                        row.setFirstBalance(old.getFirstBalance());
                        row.setCurrentQuantity(old.getCurrentQuantity());
                    });
        }

        updateTotalOpeningBalanceText();
        tableStockBalances.refresh();
    }

    private void updateTotalOpeningBalanceText() {
        double total = stockBalances.stream()
                .mapToDouble(Items_Stock_Model::getFirstBalance)
                .sum();

        txtBalance.setText(String.valueOf(total));
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnSave.setGraphic(createIcon(images.save));
        btnBarcode.setGraphic(createIcon(images.barcode));
        btnAddImage.setGraphic(createIcon(images.search));
        btnClose.setGraphic(createIcon(images.cancel));
        btnAddMainGroup.setGraphic(createIcon(images.reports));
        btnAddSubGroup.setGraphic(createIcon(images.vertical_align_bottom));
        btnSaveDuplicate.setGraphic(createIcon(images.duplicate));
        btnClearImage.setGraphic(createIcon(images.erase));
    }

    private void otherSetting() {
        whenEnterPressed(txtItemName, txtBarcode, txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3, txtMiniQuantity);
        setTextFormatter(txtBuyPrice, txtMiniQuantity, txtSelPrice, txtSelPrice2, txtSelPrice3);

        txtBalance.setEditable(false);
        txtBalance.setFocusTraversable(false);

        getFocusToName();
        checkItemActive.setSelected(true);
    }


    private void comboTypeOption() {
        var unitsModelNames = getUnitsModelNames();
        ObservableList<String> unitsModelNamesObservableList = FXCollections.observableArrayList(unitsModelNames);
        FilteredList<String> filteredItems = new FilteredList<>(unitsModelNamesObservableList, s -> true);

        comboType.setItems(filteredItems);
        comboType.getSelectionModel().selectFirst();

        comboType.valueProperty().addListener((observableValue, oldValue, newValue) -> {
            try {
                var itemsUnitsModelList = tableUnitsSetting.getItemsUnitsModelList();

                if (!itemsUnitsModelList.isEmpty()) {
                    var unitName = itemsUnitsModelList.stream()
                            .skip(1)
                            .anyMatch(item -> item.getUnitsModel().getUnit_name().equals(newValue));

                    if (unitName) {
                        comboType.getSelectionModel().select(oldValue);
                        throw new Exception("لا يمكن إختيار نفس الوحده مرتين");
                    }

                    var unitsModelByName = getUnitsModelByName(newValue);

                    if (unitsModelByName != null) {
                        itemsUnitsModelList.getFirst().unitsModelProperty().set(unitsModelByName);
                    }
                }
            } catch (Exception e) {
                logError(e);
            }
        });
    }

    private List<String> getUnitsModelNames() {
        try {
            return unitsService.getUnitsModelNames();
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private void permButtons() {

    }

    private void action() {
        btnSave.disableProperty().bind(checkEnableButton());

        btnSaveDuplicate.disableProperty().bind(checkEnableButton().or(new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return codeItem > 0;
            }
        }));

        comboMainGroup.setItems(FXCollections.observableList(getMainGroupsNames()));

        comboMainGroup.valueProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if (newValue == null) {
                    comboSupGroup.setItems(null);
                    return;
                }

                mainId = mainGroupService.getMainGroupsByName(newValue).getId();
                List<String> groupListByMainId = getSubGroupsNamesByMainId();
                comboSupGroup.setItems(FXCollections.observableList(groupListByMainId));
            } catch (NullPointerException e) {
                comboSupGroup.setItems(null);
            } catch (DaoException e) {
                logError(e);
            }
        });

        comboSupGroup.valueProperty().addListener((observable, oldValue, newValue) -> getSubId(newValue));

        btnAddMainGroup.setOnAction(actionEvent -> {
            try {
                new OpenAddAreaApplication<>(new MainGroupImpl2(dataPublisher), Setting_Language.WORD_MAIN_G);
            } catch (Exception e) {
                logError(e);
            }
        });

        btnAddSubGroup.setOnAction(actionEvent -> {
            try {
                new AddGroupApp(dataPublisher.getPublisherAddSubGroup());
            } catch (Exception e) {
                logError(e);
            }
        });

        btnClose.setOnAction(actionEvent -> ((Stage) btnClose.getScene().getWindow()).close());
        btnSave.setOnAction(actionEvent -> saveData(false));
        btnSaveDuplicate.setOnAction(actionEvent -> saveData(true));
        btnBarcode.setOnAction(actionEvent -> addBarcode());

        txtBarcode.textProperty().addListener((observable, oldValue, newValue) -> {
            var itemsUnitsModelList = tableUnitsSetting.getItemsUnitsModelList();

            if (itemsUnitsModelList.isEmpty()) {
                var e = new ItemsUnitsModel();
                e.setItemsBarcode(newValue);
                e.setUnitsModel(getUnitsModelByName(comboType.getSelectionModel().getSelectedItem()));
                e.setQuantityForUnit(1);
                itemsUnitsModelList.add(e);
            } else {
                itemsUnitsModelList.getFirst().setItemsBarcode(newValue);
            }
        });


        btnAddImage.setOnAction(actionEvent -> {
            try {
                imageChoose.onAddImage(imageAdd);
            } catch (FileNotFoundException e) {
                logError(e);
            }
        });

        btnClearImage.setOnAction(event -> imageAdd.setImage(null));
    }

    private List<String> getSubGroupsNamesByMainId() {
        try {
            return supGroupService.getSubGroupsNamesByMainId(mainId);
        } catch (Exception e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private List<String> getMainGroupsNames() {
        try {
            return mainGroupService.getMainGroupsNames();
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private void selectData() {
        if (codeItem <= 0) {
            return;
        }

        try {
            comboType.getSelectionModel().clearSelection();

            ItemsModel itemsModel = itemsService.findItemWithStockBalancesById(codeItem);

            if (itemsModel == null) {
                return;
            }

            int numItem = itemsModel.getId();
            txtCode.setText(String.valueOf(numItem));
            txtBarcode.setText(itemsModel.getBarcode());
            txtItemName.setText(itemsModel.getNameItem());
            txtBuyPrice.setText(String.valueOf(itemsModel.getBuyPrice()));
            txtMiniQuantity.setText(String.valueOf(itemsModel.getMini_quantity()));
            txtBalance.setText(String.valueOf(itemsModel.getFirstBalanceForStock()));

            selectStockBalances(itemsModel);

            mainId = itemsModel.getSubGroups().getMainGroups().getId();
            subId = itemsModel.getSubGroups().getId();

            comboMainGroup.getSelectionModel().select(
                    mainGroupService.getMainGroupsById(itemsModel.getSubGroups().getMainGroups().getId()).getName()
            );
            comboSupGroup.getSelectionModel().select(
                    supGroupService.getSubGroupsById(itemsModel.getSubGroups().getId()).getName()
            );
            comboType.getSelectionModel().select(itemsModel.getUnitsType().getUnit_name());

            txtSelPrice.setText(String.valueOf(itemsModel.getSelPrice1()));
            txtSelPrice2.setText(String.valueOf(itemsModel.getSelPrice2()));
            txtSelPrice3.setText(String.valueOf(itemsModel.getSelPrice3()));

            checkItemActive.setSelected(itemsModel.isActiveItem());
            checkItemValidate.setSelected(itemsModel.isHasValidate());
            textDaysValidate.setText(String.valueOf(itemsModel.getNumberValidityDays()));
            textAlertBefore.setText(String.valueOf(itemsModel.getAlertDaysBeforeExpiry()));

            tableUnitsSetting.selectTable(itemsModel);

            var itemImage = itemsModel.getItem_image();

            if (itemImage != null && itemImage.length > 0) {
                imageAdd.setImage(new Image(new ByteArrayInputStream(itemImage)));
            }

        } catch (DaoException e) {
            logError(e);
        }
    }

    private void selectStockBalances(ItemsModel itemsModel) {
        if (itemsModel.getItemStockBalances() == null || itemsModel.getItemStockBalances().isEmpty()) {
            updateTotalOpeningBalanceText();
            return;
        }

        for (Items_Stock_Model tableRow : stockBalances) {
            if (tableRow.getStock() == null) {
                continue;
            }

            itemsModel.getItemStockBalances().stream()
                    .filter(savedRow -> savedRow.getStock() != null)
                    .filter(savedRow -> savedRow.getStock().getId() == tableRow.getStock().getId())
                    .findFirst()
                    .ifPresent(savedRow -> {
                        tableRow.setId(savedRow.getId());
                        tableRow.setItemsModel(savedRow.getItemsModel());
                        tableRow.setFirstBalance(savedRow.getFirstBalance());
                        tableRow.setCurrentQuantity(savedRow.getCurrentQuantity());
                    });
        }

        updateTotalOpeningBalanceText();
        tableStockBalances.refresh();
    }

    private BooleanBinding checkEnableButton() {
        return txtItemName.textProperty().isEmpty()
                .or(txtBuyPrice.textProperty().lessThanOrEqualTo("0.0"))
                .or(comboMainGroup.valueProperty().isNull())
                .or(comboSupGroup.valueProperty().isNull())
                .or(comboType.valueProperty().isNull());
    }

    private ItemsModel insertData() throws Exception {
        String barcode = txtBarcode.getText();
        String nameItem = txtItemName.getText();
        double buy = DoubleSetting.parseDoubleOrDefault(txtBuyPrice.getText());
        double selPrice1 = DoubleSetting.parseDoubleOrDefault(txtSelPrice.getText());
        double selPrice2 = DoubleSetting.parseDoubleOrDefault(txtSelPrice2.getText());
        double selPrice3 = DoubleSetting.parseDoubleOrDefault(txtSelPrice3.getText());
        double miniQuantity = DoubleSetting.parseDoubleOrDefault(txtMiniQuantity.getText());
        double firstBalance = getTotalOpeningBalance();

        getSubId(comboSupGroup.getSelectionModel().getSelectedItem());

        if (barcode == null || barcode.isEmpty() || barcode.equals("0")) {
            txtBarcode.requestFocus();
            throw new Exception(Setting_Language.PLEASE_INSERT_ALL_DATA);
        }

        if (barcode.length() > 14) {
            txtBarcode.requestFocus();
            throw new Exception(Setting_Language.ALERT_ERROR);
        }

        if (selPrice1 <= buy) {
            txtSelPrice.requestFocus();
            throw new Exception("لا يمكن إدخال سعر اقل من او يساوى سعر الشراء");
        }

        if (subId <= 0) {
            comboSupGroup.requestFocus();
            throw new Exception("يجب اختيار المجموعة");
        }

        var itemsUnitsModelList = tableUnitsSetting.getItemsUnitsModelList();

        if (itemsUnitsModelList.isEmpty()) {
            throw new Exception("يجب إدخال وحدات الصنف");
        }

        var itemsModel = new ItemsModel();
        itemsModel.setId(codeItem);
        itemsModel.setBarcode(barcode);
        itemsModel.setNameItem(nameItem);
        itemsModel.setBuyPrice(buy);
        itemsModel.setMini_quantity(miniQuantity);
        itemsModel.setFirstBalanceForStock(firstBalance);
        itemsModel.setItemStockBalances(getValidStockBalances());
        itemsModel.setSubGroups(new SubGroups(subId));
        itemsModel.setSelPrice1(selPrice1);
        itemsModel.setSelPrice2(selPrice2);
        itemsModel.setSelPrice3(selPrice3);
        itemsModel.setActiveItem(checkItemActive.isSelected());
        itemsModel.setHasValidate(checkItemValidate.isSelected());
        itemsModel.setNumberValidityDays(Integer.parseInt(textDaysValidate.getText()));
        itemsModel.setAlertDaysBeforeExpiry(Integer.parseInt(textAlertBefore.getText()));
        itemsModel.setHasPackage(false);

        if (imageAdd.getImage() != null) {
            itemsModel.setItem_image(imageChoose.convertFxImageToBytes(imageAdd.getImage()));
        }

        itemsModel.setUnitsType(getUnitsModelByName(comboType.getSelectionModel().getSelectedItem()));

        if (itemsUnitsModelList.size() > 1) {
            itemsModel.setItemsUnitsModelList(itemsUnitsModelList.stream().skip(1).toList());
        } else {
            itemsModel.setItemsUnitsModelList(new ArrayList<>());
        }

        return itemsModel;
    }

    private double getTotalOpeningBalance() {
        double total = stockBalances.stream()
                .mapToDouble(Items_Stock_Model::getFirstBalance)
                .sum();

        txtBalance.setText(String.valueOf(total));
        return total;
    }

    private List<Items_Stock_Model> getValidStockBalances() throws Exception {
        List<Items_Stock_Model> result = new ArrayList<>();

        for (Items_Stock_Model row : stockBalances) {
            if (row == null || row.getStock() == null) {
                continue;
            }

            if (row.getFirstBalance() < 0) {
                throw new Exception("لا يمكن إدخال رصيد أقل من صفر للمخزن: " + row.getStock().getName());
            }

            Items_Stock_Model model = new Items_Stock_Model();
            model.setId(row.getId());
            model.setStock(row.getStock());
            model.setFirstBalance(row.getFirstBalance());
            model.setCurrentQuantity(row.getFirstBalance());

            if (codeItem > 0) {
                model.setItemsModel(new ItemsModel(codeItem));
            }

            result.add(model);
        }

        return result;
    }

    private void saveData(boolean isDuplicate) {
        try {
            if (AllAlerts.confirmSave()) {
                var itemsModel = insertData();
                var i = itemsService.updateItem(itemsModel);

                if (i == 1) {
                    dataPublisher.getPublisherAddItem().setAvailability(itemsModel);

                    AllAlerts.alertSave();
                    imageAdd.setImage(null);

                    if (!isDuplicate) {
                        clearAll(txtCode, txtBarcode, txtItemName, txtBalance, txtBuyPrice, txtSelPrice, txtMiniQuantity);
                        clearStockBalances();
                    }

                    addBarcode();
                    getFocusToName();

                    if (codeItem > 0) {
                        btnClose.fire();
                    }
                }
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void clearStockBalances() {
        for (Items_Stock_Model row : stockBalances) {
            row.setId(null);
            row.setItemsModel(null);
            row.setFirstBalance(0);
            row.setCurrentQuantity(0);
        }

        updateTotalOpeningBalanceText();
        tableStockBalances.refresh();
    }

    private UnitsModel getUnitsModelByName(String selectedItemType) {
        try {
            if (selectedItemType == null) {
                return unitsService.getUnitsById(1);
            }

            return unitsService.getUnitsByName(selectedItemType);
        } catch (DaoException e) {
            logError(e);
            return null;
        }
    }

    private void getSubId(String newValue) {
        try {
            if (!comboSupGroup.getSelectionModel().isEmpty()) {
                subId = supGroupService.getSubGroupsByMainID(newValue, mainId).getId();
            } else {
                subId = 1;
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void selectGroupSubAndType() {
        comboSubSetting(comboSupGroup, supGroupService, false, comboMainGroup);
        comboTypeSetting(comboType, unitsService, false);
    }

    private void nameSetting() {
        labelCode.setText(Setting_Language.WORD_CODE);
        labelBarcode.setText(Setting_Language.WORD_BARCODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelMainGroup.setText(Setting_Language.WORD_MAIN_G);
        labelSupGroup.setText(Setting_Language.WORD_SUB_G);
        labelType.setText("الوحدة الصغرى");
        labelBuyPrice.setText(Setting_Language.WORD_BUY_PRICE);
        labelMiniQuantity.setText("اقل كمية");
        labelFirstBalance.setText("إجمالي رصيد أول المدة");

        comboMainGroup.setPromptText(Setting_Language.WORD_MAIN_G);
        comboSupGroup.setPromptText(Setting_Language.WORD_SUB_G);
        comboType.setPromptText(Setting_Language.WORD_TYPE);

        txtItemName.setPromptText(Setting_Language.WORD_NAME + " " + Setting_Language.WORD_ITEMS);
        txtSelPrice.setPromptText(Setting_Language.WORD_SEL_PRICE);
        txtBuyPrice.setPromptText(Setting_Language.WORD_BUY_PRICE);
        txtBalance.setPromptText("إجمالي رصيد أول المدة");
        txtMiniQuantity.setPromptText("اقل كمية");

        btnSave.setText(Setting_Language.WORD_SAVE + " F10");
        btnSaveDuplicate.setText("حفظ وتكرار");
        btnClose.setText(Setting_Language.WORD_CLOSE);
        btnBarcode.setText(Setting_Language.WORD_BARCODE);

        loadNamesPrices();
    }

    private void loadNamesPrices() {
        try {
            var priceList = selPriceItemService.getSelPriceTypeList();
            labelSelPrice.setText(priceList.getFirst().getName());
            labelSelPrice2.setText(priceList.get(1).getName());
            labelSelPrice3.setText(priceList.get(2).getName());
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void addBarcode() {
        String randomItemBarcode = String.valueOf(itemsService.getMaxItemId() + 1);

        if (codeItem == 0) {
            txtBarcode.setText(randomItemBarcode);
            txtCode.setText(Setting_Language.generate);
        }
    }

    private void addValidate() {
        textDaysValidate.disableProperty().bind(checkItemValidate.selectedProperty().not());
        textAlertBefore.disableProperty().bind(checkItemValidate.selectedProperty().not());

        textDaysValidate.setText("0");
        textAlertBefore.setText("0");

        textDaysValidate.textProperty().addListener((observable, oldValue, newValue) -> textAction(newValue, textDaysValidate));
        textAlertBefore.textProperty().addListener((observable, oldValue, newValue) -> textAction(newValue, textAlertBefore));
    }

    private void textAction(String newValue, TextField textField) {
        if (newValue == null || newValue.trim().isEmpty()) {
            textField.setText("0");
            return;
        }

        if (newValue.matches("\\d*")) {
            try {
                int value = Integer.parseInt(newValue);

                if (value < 0) {
                    textField.setText("0");
                }
            } catch (NumberFormatException e) {
                textField.setText("0");
            }
        } else {
            textField.setText("0");
        }
    }

    private void getFocusToName() {
        Platform.runLater(() -> txtItemName.requestFocus());
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        String title = Setting_Language.WORD_ADD_ITEM;

        if (codeItem > 0) {
            title = Setting_Language.UPDATE_ITEM;
        }

        return title;
    }
}