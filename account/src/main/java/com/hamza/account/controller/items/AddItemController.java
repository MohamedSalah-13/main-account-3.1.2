package com.hamza.account.controller.items;

import com.codejava.commons.fx.validation.InputValidator;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.dataByName.OpenAddAreaApplication;
import com.hamza.account.controller.dataByName.impl.MainGroupImpl2;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.GroupLevel;
import com.hamza.account.features.events.GroupsChanged;
import com.hamza.account.features.events.ItemSaved;
import com.hamza.account.features.events.UnitsChanged;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.*;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.AddGroupApp;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.util.*;

import static com.hamza.account.controller.setting.ComboSetting.comboSubSetting;
import static com.hamza.account.controller.setting.ComboSetting.comboTypeSetting;
import static com.hamza.controlsfx.others.Utils.*;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "items/addItem-view.fxml")
public class AddItemController implements AppSettingInterface {

    private final int codeItem;
    private final Subscriptions subscriptions = new Subscriptions();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    /**
     * Backs the unit combo, so a unit added while this dialog is open can be
     * dropped in without rebuilding the combo and its listeners.
     */
    private final ObservableList<String> unitNames = FXCollections.observableArrayList();
    private final ImageChoose imageChoose = new ImageChoose();

    private final UnitsService unitsService = ServiceRegistry.get(UnitsService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final SelPriceItemService selPriceItemService = ServiceRegistry.get(SelPriceItemService.class);
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
    private TextField textUnitBuyPrice, textUnitSelPrice, textUnitSelPrice2, textUnitSelPrice3;
    @FXML
    private Button btnAdd;
    @FXML
    private TextField textExtraBarcode;
    @FXML
    private Button btnAddExtraBarcode, btnRemoveExtraBarcode;
    @FXML
    private ListView<String> listExtraBarcodes;
    @FXML
    private ImageView imageAdd;
    @FXML
    private Button btnAddImage, btnClearImage;
    private TableUnitsSetting tableUnitsSetting;

    public AddItemController(int codeItem) {
        this.codeItem = codeItem;
    }

    /**
     * The two events this dialog reacts to.
     * <p>
     * Subscribed from {@code initialize()} rather than from the constructor: both
     * handlers read {@code @FXML} fields, which the loader has not injected yet
     * while the constructor runs, so anything arriving in between would have hit a
     * null combo. Nothing published one there today - the dialog is constructed and
     * loaded back to back on the FX thread - which is exactly why it was worth
     * moving before something does.
     */
    private void subscribeToEvents() {
        if (eventBus == null) return;

        // The units screen can be opened over this dialog; keep the selection,
        // since renaming some other unit must not silently change this item's.
        subscriptions.add(eventBus.subscribe(UnitsChanged.class, event -> {
            var selected = comboType.getSelectionModel().getSelectedItem();
            unitNames.setAll(getUnitsModelNames());
            if (unitNames.contains(selected)) comboType.getSelectionModel().select(selected);
        }));

        subscriptions.add(eventBus.subscribe(GroupsChanged.class, event -> {
            if (event.level() == GroupLevel.MAIN) {
                comboMainGroup.setItems(FXCollections.observableList(getMainGroupsNames()));
                comboMainGroup.getSelectionModel().selectLast();
            } else {
                comboSupGroup.setItems(FXCollections.observableList(getSubGroupsNamesByMainId()));
            }
        }));
    }

    @FXML
    public void initialize() {
        unitSetting();
        otherSetting();
        comboTypeOption();
        addValidate();
        nameSetting();
        action();
        extraBarcodesSetting();
        addBarcode();
        selectGroupSubAndType();

        // add image if insert new before select data
        btnClearImage.fire();
        permButtons();
        buttonGraphic();
//        if (ADD_PACKAGE_TO_ITEMS) addPackaged();
        selectData();

//        tabPane.getTabs().getFirst().setDisable(true);
        tabPane.getSelectionModel().select(1);

        InputValidator.makeNumericOnly(textExtraBarcode);

        // The dialog is opened once per item added or edited, so this instance and
        // its two observers have to go when the window does.
        subscribeToEvents();
        subscriptions.disposeWith(stackPane);
    }

    private void extraBarcodesSetting() {
        listExtraBarcodes.setItems(FXCollections.observableArrayList());

        btnAddExtraBarcode.setOnAction(actionEvent -> addExtraBarcode());
        textExtraBarcode.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == javafx.scene.input.KeyCode.ENTER) {
                addExtraBarcode();
            }
        });

        btnRemoveExtraBarcode.setOnAction(actionEvent -> {
            var selected = listExtraBarcodes.getSelectionModel().getSelectedItem();
            if (selected != null) {
                listExtraBarcodes.getItems().remove(selected);
            }
        });
    }

    private void addExtraBarcode() {
        try {
            String barcode = textExtraBarcode.getText().trim();
            if (barcode.isEmpty()) {
                return;
            }
            if (barcode.length() > 14) {
                throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.too.long"));
            }
            if (barcode.equals(txtBarcode.getText()) || listExtraBarcodes.getItems().contains(barcode)) {
                throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.duplicate.same.item"));
            }

            listExtraBarcodes.getItems().add(barcode);
            textExtraBarcode.clear();
            textExtraBarcode.requestFocus();
        } catch (Exception e) {
            logError(e);
        }
    }

    private void unitSetting() {
        this.tableUnitsSetting = new TableUnitsSetting(unitsService, tableUnits);
        tableUnitsSetting.selectedTypeProperty().bind(comboOtherTypes.getSelectionModel().selectedItemProperty());
        tableUnitsSetting.textUnitBarcodeProperty().bindBidirectional(textUnitBarcode.textProperty());
        // The factor belongs to the item, not to the unit: picking a unit fills
        // this in with its default, and the field is where that gets corrected
        // to what a carton of *this* item actually holds.
        tableUnitsSetting.textUnitQuantityProperty().bindBidirectional(textUnitQuantity.textProperty());

        // A unit may be priced outright - a carton is sold cheaper than twelve
        // pieces on purpose. Left blank, it is priced from the item as before.
        tableUnitsSetting.textUnitBuyPriceProperty().bindBidirectional(textUnitBuyPrice.textProperty());
        tableUnitsSetting.textUnitSelPriceProperty().bindBidirectional(textUnitSelPrice.textProperty());
        tableUnitsSetting.textUnitSelPrice2Property().bindBidirectional(textUnitSelPrice2.textProperty());
        tableUnitsSetting.textUnitSelPrice3Property().bindBidirectional(textUnitSelPrice3.textProperty());

        setTextFormatter(textUnitQuantity, textUnitBuyPrice, textUnitSelPrice, textUnitSelPrice2, textUnitSelPrice3);
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

    private void comboTypeOption() {
        unitNames.setAll(getUnitsModelNames());
        FilteredList<String> filteredItems = new FilteredList<>(unitNames, s -> true);
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
                        throw new UserValidationException(LanguageManager.getInstance().getString("item.error.unit.duplicate"));
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

    /**
     * Zero is not a price, it is the absence of one - show it as an empty field
     * so the unit reads as priced from the item, which is what it is.
     */
    private void showPrice(TextField field, double price) {
        field.setText(price > 0 ? String.valueOf(price) : "");
    }

    private void clearUnitEntryFields() {
        textUnitBarcode.clear();
        textUnitBuyPrice.clear();
        textUnitSelPrice.clear();
        textUnitSelPrice2.clear();
        textUnitSelPrice3.clear();
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
        permissionDisableService.applyPermissionBasedDisable(btnAddMainGroup::setDisable, AppPermissions.MAIN_GROUP_SHOW);
        permissionDisableService.applyPermissionBasedDisable(btnAddSubGroup::setDisable, AppPermissions.SUB_GROUP_SHOW);
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
        comboSupGroup.valueProperty().addListener((observable, oldValue, newValue) -> resolveSubGroupId(newValue));

        btnAddMainGroup.setOnAction(actionEvent -> {
            try {
                new OpenAddAreaApplication<>(new MainGroupImpl2(), LanguageManager.getInstance().getString("mainGroup"));
            } catch (Exception e) {
                logError(e);
            }
        });
        btnAddSubGroup.setOnAction(actionEvent -> {
            try {
                new AddGroupApp();
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
            // A unit deleted from the units screen while this dialog is open no
            // longer resolves; that is not a reason to throw an NPE out of the FX
            // event loop, where nothing catches it.
            var unitsModelByName = getUnitsModelByName(t1);
            if (unitsModelByName == null) return;
            textUnitQuantity.setText(String.valueOf(unitsModelByName.getValue()));
        });

        // units setting
        btnAdd.setOnAction(actionEvent -> {
            tableUnitsSetting.addUnit();
            // The next unit starts from a clean sheet - a price left in the field
            // would otherwise be charged for a unit nobody priced, and a barcode
            // left there is a code two units of the item both claim.
            clearUnitEntryFields();
        });
        // DELETE used to fire btnAdd, which added a unit rather than removing one.
        tableUnits.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == javafx.scene.input.KeyCode.DELETE) {
                tableUnitsSetting.removeSelectedUnit();
            }
        });

        tableUnits.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                // A double-click below the last row selects nothing.
                var selectedItem = tableUnits.getSelectionModel().getSelectedItem();
                if (selectedItem == null || selectedItem.getUnitsModel() == null) return;
                comboOtherTypes.getSelectionModel().select(selectedItem.getUnitsModel().getUnit_name());
                textUnitQuantity.setText(String.valueOf(selectedItem.getQuantityForUnit()));
                textUnitBarcode.setText(selectedItem.getItemsBarcode());
                showPrice(textUnitBuyPrice, selectedItem.getBuyPrice());
                showPrice(textUnitSelPrice, selectedItem.getSelPrice());
                showPrice(textUnitSelPrice2, selectedItem.getSelPrice2());
                showPrice(textUnitSelPrice3, selectedItem.getSelPrice3());
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
                    lockOpeningBalanceIfItemHasMoved(numItem);
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
                    listExtraBarcodes.setItems(FXCollections.observableArrayList(itemsModel.getExtraBarcodes()));
                    var itemImage = itemsModel.getItem_image();

                    if (itemImage != null && itemImage.length > 0) {
                        imageAdd.setImage(new Image(new ByteArrayInputStream(itemImage)));
                    }

                }
            } catch (DaoException e) {
                logError(e);
            }
    }

    /**
     * Greys the opening-balance field once the item has moved, and says why.
     * <p>
     * The rule is enforced in {@code ItemsDao.update}, not here - a disabled field is a
     * hint, and the same save is reachable from the Excel import. This is so the user
     * finds out before typing rather than by having the save refused afterwards.
     * <p>
     * A failure to read it leaves the field enabled: the DAO will still refuse a change,
     * so the worst case is a message at the wrong moment rather than a corrupted balance.
     */
    private void lockOpeningBalanceIfItemHasMoved(int itemId) {
        try {
            if (!itemsService.isOpeningBalanceLocked(itemId)) {
                txtBalance.setDisable(false);
                txtBalance.setTooltip(null);
                return;
            }
            txtBalance.setDisable(true);
            txtBalance.setTooltip(new Tooltip(
                    LanguageManager.getInstance().getString("item.tooltip.opening.balance.locked")));
        } catch (DaoException e) {
            logError(e);
        }
    }

    private BooleanBinding checkEnableButton() {
        return (txtItemName.textProperty().isEmpty())
                .or(isNotPositive(txtBuyPrice))
                .or(comboMainGroup.valueProperty().isNull())
                .or(comboSupGroup.valueProperty().isNull())
                .or(comboType.valueProperty().isNull());
    }

    /**
     * Whether the field holds a number greater than zero.
     * <p>
     * This was {@code textProperty().lessThanOrEqualTo("0.0")}, which compares the
     * text - and text does not order like the number it spells. "0.00" sorts after
     * "0.0" because it is longer, so a buy price of zero typed with two decimals
     * read as positive and enabled the save.
     */
    private BooleanBinding isNotPositive(TextField field) {
        return Bindings.createBooleanBinding(
                () -> DoubleSetting.parseDoubleOrDefault(field.getText()) <= 0,
                field.textProperty());
    }

    private ItemsModel insertData() throws Exception {
        // Trimmed here, once, because the codes are compared in three places that
        // did not agree: this screen matched them as typed, while
        // ItemsService.isBarcodeTakenByAnotherItem trims before asking the
        // database. "123 " and "123" passed as two different codes locally and
        // then collided on the unique index.
        String barcode = txtBarcode.getText().trim();
        String nameItem = txtItemName.getText().trim();
        double buy = DoubleSetting.parseDoubleOrDefault(txtBuyPrice.getText());
        double selPrice1 = DoubleSetting.parseDoubleOrDefault(txtSelPrice.getText());
        double selPrice2 = DoubleSetting.parseDoubleOrDefault(txtSelPrice2.getText());
        double selPrice3 = DoubleSetting.parseDoubleOrDefault(txtSelPrice3.getText());
        double miniQuantity = DoubleSetting.parseDoubleOrDefault(txtMiniQuantity.getText());
        double firstBalance = DoubleSetting.parseDoubleOrDefault(txtBalance.getText());

        // add subgroup
        resolveSubGroupId(comboSupGroup.getSelectionModel().getSelectedItem());

        // The save button's binding only asks that the field is not empty, and a
        // name of spaces is not empty. It is empty once trimmed, which is what
        // gets stored.
        if (nameItem.isEmpty()) {
            txtItemName.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.name.required"));
        }

        if (barcode.isEmpty() || barcode.equals("0")) {
            txtBarcode.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("msg.insert.all"));
        }

        if (barcode.length() > 14) {
            txtBarcode.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.too.long"));
        }

        if (selPrice1 <= buy) {
            txtSelPrice.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.sell.not.above.buy"));
        }

        if (subId <= 0) {
            comboSupGroup.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.group.required"));
        }
        var itemsUnitsModelList = tableUnitsSetting.getItemsUnitsModelList();
        if (itemsUnitsModelList.isEmpty()) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.units.required"));
        }

        var baseUnit = getUnitsModelByName(comboType.getSelectionModel().getSelectedItem());
        checkBarcodesAreFree(barcode, baseUnit, itemsUnitsModelList);

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

        // Set image data
        if (imageAdd.getImage() != null) {
            itemsModel.setItem_image(imageChoose.convertFxImageToBytes(imageAdd.getImage()));
        }

        // The whole list goes down: ItemsDao drops the base unit's row by matching
        // it against items.unit_id, which does not depend on it being first.
        itemsModel.setUnitsType(baseUnit);
        itemsModel.setItemsUnitsModelList(new ArrayList<>(itemsUnitsModelList));
        itemsModel.setExtraBarcodes(new ArrayList<>(listExtraBarcodes.getItems()));
        return itemsModel;
    }

    /**
     * No two codes on this item may be the same, and none of them may belong to
     * another item.
     * <p>
     * Each of the three tables holding barcodes has its own unique index and
     * none can see the others, so without this a carton could carry the code of
     * a different item entirely - and a scan would then resolve to whichever
     * table the query happened to look at first.
     */
    private void checkBarcodesAreFree(String itemBarcode, UnitsModel baseUnit, List<ItemsUnitsModel> units) throws Exception {
        List<String> codes = new ArrayList<>();
        codes.add(itemBarcode);
        listExtraBarcodes.getItems().stream().map(String::trim).forEach(codes::add);

        // The base unit's row carries the item's own barcode and is not stored,
        // so counting it here would report the item as clashing with itself.
        int baseUnitId = baseUnit == null ? 0 : baseUnit.getUnit_id();
        units.stream()
                .filter(unit -> unit.getUnitsModel() == null || unit.getUnitsModel().getUnit_id() != baseUnitId)
                .map(ItemsUnitsModel::getItemsBarcode)
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .forEach(codes::add);

        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (!seen.add(code)) {
                throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.duplicate.in.item", code));
            }
            if (itemsService.isBarcodeTakenByAnotherItem(code, codeItem)) {
                throw new BusinessRuleException(LanguageManager.getInstance().getString("item.error.barcode.used.by.other", code));
            }
        }
    }

    private void saveData(boolean isDuplicate) {
        try {
            if (AllAlerts.confirmSave()) {
                var itemsModel = insertData();
                var i = itemsService.updateItem(itemsModel);
                if (i == 1) {
                    if (eventBus != null) eventBus.publish(new ItemSaved(itemsModel));
                    tableUnits.getItems().clear();
                    listExtraBarcodes.getItems().clear();
                    AllAlerts.alertSave();
                    imageAdd.setImage(null);
                    if (!isDuplicate) {
                        // The second and third selling prices were left out, so they
                        // carried over onto the next item entered - unseen, since the
                        // fields still read as the price of an item already saved.
                        clearAll(txtCode, txtBarcode, txtItemName, txtBalance, txtBuyPrice,
                                txtSelPrice, txtSelPrice2, txtSelPrice3, txtMiniQuantity);
                        // The form is a blank item again, and a blank item has moved
                        // nothing - so the opening balance is open for entry.
                        lockOpeningBalanceIfItemHasMoved(0);
                    }
                    addBarcode();
                    getFocusToName();

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

    /**
     * Resolves the selected sub-group to its id, or to 0 when there is nothing to
     * resolve.
     * <p>
     * Zero is what makes the {@code subId <= 0} check in {@link #insertData()} do
     * something. This used to fall back to 1 - the seeded group - so an item saved
     * with no group chosen was filed under it silently, and the check that was
     * meant to refuse that could never fire. A failed lookup answers 0 for the same
     * reason: keeping the previous selection's id would file the item under
     * whichever group happened to be picked before.
     */
    private void resolveSubGroupId(String subGroupName) {
        if (subGroupName == null || subGroupName.isBlank()) {
            subId = 0;
            return;
        }
        try {
            var subGroup = supGroupService.getSubGroupsByMainID(subGroupName, mainId);
            subId = subGroup == null ? 0 : subGroup.getId();
        } catch (DaoException e) {
            subId = 0;
            logError(e);
        }
    }

    private void selectGroupSubAndType() {
        comboSubSetting(comboSupGroup, supGroupService, false, comboMainGroup);
        comboTypeSetting(comboType, unitsService, false);
    }

    private void nameSetting() {
        var lm = LanguageManager.getInstance();
        labelCode.setText(lm.getString("code"));
        labelBarcode.setText(lm.getString("barcode"));
        labelName.setText(lm.getString("name"));
        labelMainGroup.setText(lm.getString("mainGroup"));
        labelSupGroup.setText(lm.getString("subGroup"));
        labelType.setText(lm.getString("item.small.unit"));
        labelBuyPrice.setText(lm.getString("BuyPrice"));
        labelMiniQuantity.setText(lm.getString("item.mini.quantity"));
        labelFirstBalance.setText(lm.getString("firstBalance"));

        comboMainGroup.setPromptText(lm.getString("mainGroup"));
        comboSupGroup.setPromptText(lm.getString("subGroup"));
        comboType.setPromptText(lm.getString("type"));
        txtItemName.setPromptText(lm.getString("column.name_item"));
        txtSelPrice.setPromptText(lm.getString("selPrice"));
        txtBuyPrice.setPromptText(lm.getString("BuyPrice"));
        txtBalance.setPromptText(lm.getString("firstBalance"));
        txtMiniQuantity.setPromptText(lm.getString("item.mini.quantity"));

        btnSave.setText(lm.getString("common.save") + " F10");
        btnSaveDuplicate.setText(lm.getString("item.btn.save.duplicate"));
        btnClose.setText(lm.getString("common.close"));
        btnBarcode.setText(lm.getString("barcode"));

        // sel price names
        loadNamesPrices();

    }

    /**
     * Names the three price tiers from {@code sel_price_type}.
     * <p>
     * The rows are read by position and the table is user-editable, so a missing
     * row is a screen that would not open at all: the three reads used to be
     * {@code getFirst()}, {@code get(1)} and {@code get(2)}, and a list of two
     * threw {@code IndexOutOfBoundsException} out of {@code initialize()}. A tier
     * nobody named falls back to its generic caption instead.
     */
    private void loadNamesPrices() {
        List<SelPriceTypeModel> priceList;
        try {
            priceList = selPriceItemService.getSelPriceTypeList();
        } catch (DaoException e) {
            logError(e);
            priceList = List.of();
        }
        var lm = LanguageManager.getInstance();
        setPriceLabel(labelSelPrice, priceList, 0, lm.getString("selPrice"));
        setPriceLabel(labelSelPrice2, priceList, 1, lm.getString("selPrice") + "2");
        setPriceLabel(labelSelPrice3, priceList, 2, lm.getString("selPrice") + "3");
    }

    private void setPriceLabel(Label label, List<SelPriceTypeModel> priceList, int index, String fallback) {
        String name = index < priceList.size() ? priceList.get(index).getName() : null;
        label.setText(name == null || name.isBlank() ? fallback : name);
    }

    private void addBarcode() {
//        String randomItemBarcode = generateRandomBarcode(itemsService.getMaxItemId() + 1);
        String randomItemBarcode = String.valueOf(itemsService.getMaxItemId() + 1);
        if (codeItem == 0) {
            txtBarcode.setText(randomItemBarcode);
            txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
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
        AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.save.title"), e);
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        var lm = LanguageManager.getInstance();
        return codeItem > 0 ? lm.getString("updateItem") : lm.getString("addItem");
    }
}

