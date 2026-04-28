package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.dataByName.OpenAddAreaApplication;
import com.hamza.account.controller.dataByName.impl.MainGroupImpl2;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.AddGroupApp;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.ImageChoose;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DoubleSetting;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.hamza.account.config.Configs.ADD_PACKAGE_TO_ITEMS;
import static com.hamza.account.controller.setting.ComboSetting.comboSubSetting;
import static com.hamza.account.controller.setting.ComboSetting.comboTypeSetting;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;
import static com.hamza.controlsfx.others.Utils.*;

@Log4j2
@FxmlPath(pathFile = "items/addItem-view.fxml")
public class AddItemController extends ServiceData implements AppSettingInterface {

    private final int codeItem;
    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;
    private final ImageChoose imageChoose = new ImageChoose();
    private final ItemsPackageController itemsPackageController;
    private int mainId, subId;
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
    private ComboBox<String> comboOtherTypes;
    @FXML
    private TableView<ItemsUnitsModel> tableUnits;
    @FXML
    private TextField textUnitQuantity, textUnitBarcode;
    @FXML
    private Button btnAdd;
    @FXML
    private ImageView imageAdd;
    @FXML
    private Button btnAddImage, btnClearImage;
    private TableUnitsSetting tableUnitsSetting;

    public AddItemController(int codeItem, DataPublisher dataPublisher, DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.codeItem = codeItem;
        this.dataPublisher = dataPublisher;
        this.daoFactory = daoFactory;
        this.itemsPackageController = new ItemsPackageController(daoFactory);
        dataPublisher.getPublisherAddMainGroup().addObserver(message -> {
            comboMainGroup.setItems(FXCollections.observableList(getMainGroupsNames()));
            comboMainGroup.getSelectionModel().selectLast();
        });

        dataPublisher.getPublisherAddSubGroup().addObserver(message -> {
            List<String> groupListByMainId = getSubGroupsNamesByMainId();
            comboSupGroup.setItems(FXCollections.observableList(groupListByMainId));
        });
    }

    @FXML
    public void initialize() {
        unitSetting();
        otherSetting();
        comboTypeOption();
        addValidate();
        nameSetting();
        action();
        addBarcode();
        selectGroupSubAndType();

        // add image if insert new before select data
        btnClearImage.fire();
        permButtons();
        buttonGraphic();
//        if (ADD_PACKAGE_TO_ITEMS) addPackaged();
        selectData();

        tabPane.getTabs().getFirst().setDisable(true);
        tabPane.getSelectionModel().select(1);
    }

    private void unitSetting() {
        this.tableUnitsSetting = new TableUnitsSetting(unitsService, tableUnits);
        tableUnitsSetting.selectedTypeProperty().bind(comboOtherTypes.getSelectionModel().selectedItemProperty());
        tableUnitsSetting.textUnitBarcodeProperty().bindBidirectional(textUnitBarcode.textProperty());
//        tableUnitsSetting.textUnitQuantityProperty().bindBidirectional(textUnitQuantity.textProperty());
        textUnitQuantity.setDisable(true);
    }

    private void buttonGraphic() {
        // Introduce variable: single instance to access all streams once per call
        var images = new Image_Setting();
        btnAdd.setGraphic(createIcon(images.add));
        btnSave.setGraphic(createIcon(images.save));
        btnBarcode.setGraphic(createIcon(images.barcode));
        btnAddImage.setGraphic(createIcon(images.search));
        btnClose.setGraphic(createIcon(images.cancel));
        btnAddMainGroup.setGraphic(createIcon(images.reports));
        btnAddSubGroup.setGraphic(createIcon(images.vertical_align_bottom)); // separate ImageView, same Image
        btnSaveDuplicate.setGraphic(createIcon(images.duplicate));
        btnClearImage.setGraphic(createIcon(images.erase));
    }

    private void otherSetting() {
        whenEnterPressed(txtItemName, txtBarcode, txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3, txtBalance, txtMiniQuantity);
        setTextFormatter(txtBalance, txtBuyPrice, txtMiniQuantity, txtSelPrice, txtSelPrice2, txtSelPrice3);
        getFocusToName();
        comboOtherTypes.getItems().addAll(getUnitsModelNames());
        checkItemActive.setSelected(true);
    }

    private void addPackaged() {
        try {
            var pane = new OpenFxmlApplication(itemsPackageController).getPane();
            tabPane.getTabs().add(1, new Tab(Setting_Language.ADD_PACKAGE, pane));
        } catch (Exception e) {
            log.error("Failed to load packaged items view", e);
        }
    }

    private void comboTypeOption() {
        var unitsModelNames = getUnitsModelNames();
        ObservableList<String> unitsModelNamesObservableList = FXCollections.observableArrayList(unitsModelNames);
        FilteredList<String> filteredItems = new FilteredList<>(unitsModelNamesObservableList, s -> true);
        comboType.setItems(filteredItems);
        comboType.getSelectionModel().selectFirst();

        comboType.valueProperty().addListener((observableValue, stringSingleSelectionModel, t1) -> {
            try {
                var itemsUnitsModelList = tableUnitsSetting.getItemsUnitsModelList();
                if (!itemsUnitsModelList.isEmpty()) {

                    var unitName = itemsUnitsModelList.stream()
                            .skip(1)
                            .anyMatch(item -> item.getUnitsModel().getUnit_name().equals(t1));

                    if (unitName) {
                        comboType.getSelectionModel().select(stringSingleSelectionModel);
                        throw new Exception("لا يمكن إختيار نفس الوحده مرتين");
                    }

                    var unitsModelByName = getUnitsModelByName(t1);
                    itemsUnitsModelList.getFirst().unitsModelProperty().set(unitsModelByName);
                    tableUnits.refresh();
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
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnAddMainGroup::setDisable, UserPermissionType.MAIN_GROUP_SHOW);
        permissionDisableService.applyPermissionBasedDisable(btnAddSubGroup::setDisable, UserPermissionType.SUB_GROUP_SHOW);
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
                new OpenAddAreaApplication<>(new MainGroupImpl2(mainGroupService, dataPublisher), Setting_Language.WORD_MAIN_G);
            } catch (Exception e) {
                logError(e);
            }
        });
        btnAddSubGroup.setOnAction(actionEvent -> {
            try {
                new AddGroupApp(dataPublisher.getPublisherAddSubGroup(), daoFactory);
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

        comboOtherTypes.valueProperty().addListener((observableValue, stringSingleSelectionModel, t1) -> {
            var unitsModelByName = getUnitsModelByName(t1);
            textUnitQuantity.setText(String.valueOf(Objects.requireNonNull(unitsModelByName).getValue()));
        });

        // units setting
//        btnAdd.disableProperty().bind(textUnitQuantity.textProperty().isEmpty());
        btnAdd.setOnAction(actionEvent -> tableUnitsSetting.addUnit());
        tableUnits.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == javafx.scene.input.KeyCode.DELETE) {
                btnAdd.fire();
            }
        });

        tableUnits.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                var selectedItem = tableUnits.getSelectionModel().getSelectedItem();
                comboOtherTypes.getSelectionModel().select(selectedItem.getUnitsModel().getUnit_name());
                textUnitQuantity.setText(String.valueOf(selectedItem.getQuantityForUnit()));
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
        if (codeItem > 0)
            try {
                comboType.getSelectionModel().clearSelection();
                ItemsModel itemsModel = itemsService.getItemByItemIdAndStockId(codeItem, 1);
                if (itemsModel != null) {
                    int numItem = itemsModel.getId();
                    txtCode.setText(String.valueOf(numItem));
                    txtBarcode.setText(itemsModel.getBarcode());
                    txtItemName.setText(itemsModel.getNameItem());
                    txtBuyPrice.setText(String.valueOf(itemsModel.getBuyPrice()));
                    txtMiniQuantity.setText(String.valueOf(itemsModel.getMini_quantity()));
                    txtBalance.setText(String.valueOf(itemsModel.getFirstBalanceForStock()));
                    // combo restore data
                    mainId = itemsModel.getSubGroups().getMainGroups().getId();
                    subId = itemsModel.getSubGroups().getId();
                    comboMainGroup.getSelectionModel().select(mainGroupService.getMainGroupsById(itemsModel.getSubGroups().getMainGroups().getId()).getName());
                    comboSupGroup.getSelectionModel().select(supGroupService.getSubGroupsById(itemsModel.getSubGroups().getId()).getName());
                    comboType.getSelectionModel().select(itemsModel.getUnitsType().getUnit_name());
                    txtSelPrice.setText(String.valueOf(itemsModel.getSelPrice1()));
                    txtSelPrice2.setText(String.valueOf(itemsModel.getSelPrice2()));
                    txtSelPrice3.setText(String.valueOf(itemsModel.getSelPrice3()));

                    // check
                    checkItemActive.setSelected(itemsModel.isActiveItem());
                    checkItemValidate.setSelected(itemsModel.isHasValidate());
                    var numberValidityDays = itemsModel.getNumberValidityDays();
                    textDaysValidate.setText(String.valueOf(numberValidityDays));
                    textAlertBefore.setText(String.valueOf(itemsModel.getAlertDaysBeforeExpiry()));
                    tableUnitsSetting.selectTable(itemsModel);
                    var itemImage = itemsModel.getItem_image();

                    if (itemImage != null && itemImage.length > 0) {
                        imageAdd.setImage(new Image(new ByteArrayInputStream(itemImage)));
                    }

                    if (ADD_PACKAGE_TO_ITEMS) {
                        // get item package
                        if (itemsModel.isHasPackage()) {
                            itemsPackageController.setItemHasPackage(true);
//                        itemsPackageController.setItemPackageId(itemsModel.getId());
                            itemsPackageController.selectData(itemsModel.getId());
                        }
                    }

                }
            } catch (DaoException e) {
                logError(e);
            }
    }

    private BooleanBinding checkEnableButton() {
        return (txtItemName.textProperty().isEmpty())
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
        double firstBalance = DoubleSetting.parseDoubleOrDefault(txtBalance.getText());

        // add subgroup
        getSubId(comboSupGroup.getSelectionModel().getSelectedItem());

        if (barcode.isEmpty() || barcode.equals("0")) {
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
        itemsModel.setSubGroups(new SubGroups(subId));
        itemsModel.setSelPrice1(selPrice1);
        itemsModel.setSelPrice2(selPrice2);
        itemsModel.setSelPrice3(selPrice3);
        itemsModel.setActiveItem(checkItemActive.isSelected());
        itemsModel.setHasValidate(checkItemValidate.isSelected());
        itemsModel.setNumberValidityDays(Integer.parseInt(textDaysValidate.getText()));
        itemsModel.setAlertDaysBeforeExpiry(Integer.parseInt(textAlertBefore.getText()));


        if (ADD_PACKAGE_TO_ITEMS) {
            if (itemsPackageController.isItemHasPackage()) {
                itemsModel.setHasPackage(true);
                var itemsPackageList = itemsPackageController.getItems_packageList();
                itemsModel.setItems_packageList(itemsPackageList);
            } else itemsModel.setItems_packageList(new ArrayList<>());
        } else itemsModel.setHasPackage(false);

        // Set image data
        if (imageAdd.getImage() != null) {
            itemsModel.setItem_image(imageChoose.convertFxImageToBytes(imageAdd.getImage()));
        }

        // check this - add to dao
        itemsModel.setUnitsType(getUnitsModelByName(comboType.getSelectionModel().getSelectedItem()));
        if (itemsUnitsModelList.size() > 1) {
            itemsModel.setItemsUnitsModelList(itemsUnitsModelList.stream().skip(1).toList());
        } else itemsModel.setItemsUnitsModelList(new ArrayList<>());
        return itemsModel;
    }

    private void saveData(boolean isDuplicate) {
        try {
            if (AllAlerts.confirmSave()) {
                var itemsModel = insertData();
                var i = itemsService.updateItem(itemsModel);
                if (i == 1) {
                    dataPublisher.getPublisherAddItem().setAvailability(itemsModel);
                    tableUnits.getItems().clear();
                    AllAlerts.alertSave();
                    imageAdd.setImage(null);
                    if (!isDuplicate) {
                        clearAll(txtCode, txtBarcode, txtItemName, txtBalance, txtBuyPrice, txtSelPrice, txtMiniQuantity);
                    }
                    addBarcode();
                    getFocusToName();

                    if (ADD_PACKAGE_TO_ITEMS) {
                        itemsPackageController.deleteAllData();
                    }
                    // close after update
                    if (codeItem > 0) {
                        btnClose.fire();
                    }
                }
            }
        } catch (Exception e) {
            logError(e);
        }
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
            } else subId = 1;
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
        labelFirstBalance.setText(Setting_Language.FIRST_BALANCE);

        comboMainGroup.setPromptText(Setting_Language.WORD_MAIN_G);
        comboSupGroup.setPromptText(Setting_Language.WORD_SUB_G);
        comboType.setPromptText(Setting_Language.WORD_TYPE);
        txtItemName.setPromptText(Setting_Language.WORD_NAME + " " + Setting_Language.WORD_ITEMS);
        txtSelPrice.setPromptText(Setting_Language.WORD_SEL_PRICE);
        txtBuyPrice.setPromptText(Setting_Language.WORD_BUY_PRICE);
        txtBalance.setPromptText(Setting_Language.FIRST_BALANCE);
        txtMiniQuantity.setPromptText("اقل كمية");

        btnSave.setText(Setting_Language.WORD_SAVE + " F10");
        btnSaveDuplicate.setText("حفظ وتكرار");
        btnClose.setText(Setting_Language.WORD_CLOSE);
        btnBarcode.setText(Setting_Language.WORD_BARCODE);

        // sel price names
        loadNamesPrices();

    }

    private void loadNamesPrices() {
        try {
            var priceList = getSelPriceItemService().getSelPriceTypeList();
            labelSelPrice.setText(priceList.getFirst().getName());
            labelSelPrice2.setText(priceList.get(1).getName());
            labelSelPrice3.setText(priceList.get(2).getName());
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void addBarcode() {
//        String randomItemBarcode = generateRandomBarcode(itemsService.getMaxItemId() + 1);
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

        textDaysValidate.textProperty().addListener((observable
                , oldValue, newValue) -> textAction(newValue, textDaysValidate));
        textAlertBefore.textProperty().addListener((observable
                , oldValue, newValue) -> textAction(newValue, textAlertBefore));
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
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
        e.printStackTrace();
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        String title = Setting_Language.WORD_ADD_ITEM;
        if (codeItem > 0) title = Setting_Language.UPDATE_ITEM;
        return title;
    }
}

