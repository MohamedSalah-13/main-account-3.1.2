package com.hamza.account.controller.items;

import com.codejava.commons.fx.validation.InputValidator;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.dataByName.MasterDataController;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.GroupLevel;
import com.hamza.account.features.events.GroupsChanged;
import com.hamza.account.features.events.ItemSaved;
import com.hamza.account.features.events.UnitsChanged;
import com.hamza.account.features.masterdata.MasterDataKind;
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
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
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
    private final String initialBarcode;
    private final Subscriptions subscriptions = new Subscriptions();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    /**
     * Every unit that exists, loaded once and refreshed only when
     * {@code UnitsChanged} says otherwise. {@link #getUnitsModelByName} reads
     * this instead of asking the database again on every combo selection - a
     * unit picked to price a carton, typed into a barcode field, or set as the
     * item's base unit used to be a full round trip each time.
     */
    private final ObservableList<UnitsModel> unitsCache = FXCollections.observableArrayList();
    /** Names, derived from {@link #unitsCache} - what the unit combos actually display. */
    private final ObservableList<String> unitNames = FXCollections.observableArrayList();
    private final ImageChoose imageChoose = new ImageChoose();

    private final UnitsService unitsService = ServiceRegistry.get(UnitsService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final SupGroupService supGroupService = ServiceRegistry.get(SupGroupService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final SelPriceItemService selPriceItemService = ServiceRegistry.get(SelPriceItemService.class);
    /**
     * True while the write to the database - insertMultiData's transaction across
     * the item, its opening stock row, its units and its barcodes - is in flight
     * on a background thread. Folded into the save buttons' disable bindings so a
     * second click cannot start a second transaction, and into the close button's
     * so the window cannot go away out from under the callback that is still
     * going to touch its controls.
     */
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    /**
     * The item's own scalar fields - see {@link ItemForm}. Bound to its controls
     * once, in {@link #bindItemForm()}.
     */
    private final ItemForm itemForm = new ItemForm();
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
    private Tab tabUnits;
    @FXML
    @Getter
    private Button btnAddMainGroup, btnAddSubGroup, btnSave, btnSaveDuplicate, btnClose, btnBarcode;
    @FXML
    private Button btnClearPrices;
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
    private ExtraBarcodesTabController extraBarcodesTab;
    /**
     * Asks the database whether a code is already some other item's, one code at
     * a time, as it is entered. The save still checks the whole set - see
     * {@link #checkBarcodesAreFree} - this is what stops a duplicate being typed
     * in and carried through the rest of the form first.
     */
    private BarcodeAvailability barcodeAvailability;

    public AddItemController(int codeItem) {
        this(codeItem, null);
    }

    public AddItemController(int codeItem, String initialBarcode) {
        this.codeItem = codeItem;
        this.initialBarcode = initialBarcode;
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
            refreshUnitsCache();
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
        // Built first: action() hangs a focus listener on the barcode field off it,
        // and the extra-barcodes tab is handed it.
        barcodeAvailability = new BarcodeAvailability(itemsService::itemNameHoldingBarcode, () -> codeItem);
        bindItemForm();
        refreshUnitsCache();
        unitSetting();
        otherSetting();
        comboTypeOption();
        addValidate();
        nameSetting();
        action();
        extraBarcodesTab = new ExtraBarcodesTabController(
                listExtraBarcodes, textExtraBarcode, btnAddExtraBarcode, btnRemoveExtraBarcode, itemForm::getBarcode,
                barcodeAvailability);
        addBarcode();
        selectGroupSubAndType();

        // add image if insert new before select data
        btnClearImage.fire();
        if (codeItem == 0 && initialBarcode != null && !initialBarcode.isBlank()) {
            itemForm.setBarcode(initialBarcode.trim());
        }
        permButtons();
        buttonGraphic();
//        if (ADD_PACKAGE_TO_ITEMS) addPackaged();
        selectData();

        // Was select(1) - the tab index rather than the tab. That opened on
        // "أخرى" ("other"), not the units tab the index was meant to name.
        tabPane.getSelectionModel().select(tabUnits);

        // The dialog is opened once per item added or edited, so this instance and
        // its two observers have to go when the window does.
        subscribeToEvents();
        subscriptions.disposeWith(stackPane);
    }

    /**
     * Binds every field {@link ItemForm} owns to its control, once. From here on
     * the form is the source of truth for those fields; {@code selectData},
     * {@code insertData} and the post-save reset go through it instead of the
     * controls directly.
     */
    private void bindItemForm() {
        itemForm.barcodeProperty().bindBidirectional(txtBarcode.textProperty());
        itemForm.nameProperty().bindBidirectional(txtItemName.textProperty());
        itemForm.buyPriceProperty().bindBidirectional(txtBuyPrice.textProperty());
        itemForm.selPrice1Property().bindBidirectional(txtSelPrice.textProperty());
        itemForm.selPrice2Property().bindBidirectional(txtSelPrice2.textProperty());
        itemForm.selPrice3Property().bindBidirectional(txtSelPrice3.textProperty());
        itemForm.miniQuantityProperty().bindBidirectional(txtMiniQuantity.textProperty());
        itemForm.firstBalanceProperty().bindBidirectional(txtBalance.textProperty());
        itemForm.activeProperty().bindBidirectional(checkItemActive.selectedProperty());
        itemForm.hasValidateProperty().bindBidirectional(checkItemValidate.selectedProperty());
        itemForm.validityDaysProperty().bindBidirectional(textDaysValidate.textProperty());
        itemForm.alertBeforeExpiryProperty().bindBidirectional(textAlertBefore.textProperty());
    }

    private void unitSetting() {
        this.tableUnitsSetting = new TableUnitsSetting(unitsService, tableUnits);
        tableUnitsSetting.selectedTypeProperty().bind(comboOtherTypes.getSelectionModel().selectedItemProperty());
        tableUnitsSetting.textUnitBarcodeProperty().bindBidirectional(textUnitBarcode.textProperty());
        // A barcode is digits, not a number - setTextFormatter's numeric
        // converter would be the wrong tool here (it would happily reformat the
        // text and drop a leading zero), so this is filtered the same way
        // ExtraBarcodesTabController filters textExtraBarcode.
        InputValidator.makeNumericOnly(textUnitBarcode);
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
        btnClearPrices.setGraphic(createIcon(images.erase));
    }

    private void otherSetting() {
        whenEnterPressed(txtItemName, txtBarcode, txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3, txtBalance, txtMiniQuantity);
        setTextFormatter(txtBalance, txtBuyPrice, txtMiniQuantity, txtSelPrice, txtSelPrice2, txtSelPrice3);
        getFocusToName();
        comboOtherTypes.getItems().addAll(unitNames);
        checkItemActive.setSelected(true);
    }

    private void comboTypeOption() {
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

    /**
     * Reloads {@link #unitsCache} and the names derived from it, from one query
     * instead of the two ({@code getUnitsModelNames} for the combo, then
     * {@code getUnitsByName} again for every selection) this used to make.
     */
    private void refreshUnitsCache() {
        List<UnitsModel> units;
        try {
            units = unitsService.getUnitsModelList();
        } catch (DaoException e) {
            logError(e);
            units = new ArrayList<>();
        }
        unitsCache.setAll(units);
        unitNames.setAll(units.stream().map(UnitsModel::getUnit_name).toList());
    }

    private void permButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnAddMainGroup::setDisable, AppPermissions.MAIN_GROUP_SHOW);
        permissionDisableService.applyPermissionBasedDisable(btnAddSubGroup::setDisable, AppPermissions.SUB_GROUP_SHOW);
    }

    private void action() {

        btnSave.disableProperty().bind(checkEnableButton().or(saving));
        btnSaveDuplicate.disableProperty().bind(checkEnableButton().or(saving).or(new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return codeItem > 0;
            }
        }));
        btnClose.disableProperty().bind(saving);
        bindSaveTooltip();
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
                MasterDataController.showWindow(MasterDataKind.MAIN);
            } catch (Exception e) {
                logError(e);
            }
        });
        btnAddSubGroup.setOnAction(actionEvent -> {
            try {
                MasterDataController.showWindow(MasterDataKind.SUB);
            } catch (Exception e) {
                logError(e);
            }
        });


        btnClose.setOnAction(actionEvent -> ((Stage) btnClose.getScene().getWindow()).close());
        btnSave.setOnAction(actionEvent -> saveData(false));
        btnSaveDuplicate.setOnAction(actionEvent -> saveData(true));
        btnBarcode.setOnAction(actionEvent -> addBarcode());

        // Checked when the field is left, not on every keystroke: a barcode is
        // typed or scanned digit by digit, and every prefix of it would otherwise
        // be a query. Enter moves the focus on (whenEnterPressed), so a scanner
        // ending its read triggers this too.
        txtBarcode.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) verifyBarcodeIsFree(txtBarcode);
        });

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
            // A unit carries a code of its own, so it is the third way a
            // duplicate gets onto this screen - refused here for the same reason
            // the extra-barcode list refuses one.
            if (!verifyBarcodeIsFree(textUnitBarcode)) return;
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

        // A barcode typed into the wrong field by mistake used to be hard to
        // fully erase - see the fix in TextFormat.DefaultStringConverter - so
        // this is a fast way out rather than backspacing it out digit by digit.
        btnClearPrices.setOnAction(event -> clearAll(txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3));
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
                    itemForm.load(itemsModel);
                    lockOpeningBalanceIfItemHasMoved(numItem);
                    // combo restore data
                    mainId = itemsModel.getSubGroups().getMainGroups().getId();
                    subId = itemsModel.getSubGroups().getId();
                    comboMainGroup.getSelectionModel().select(mainGroupService.getMainGroupsById(itemsModel.getSubGroups().getMainGroups().getId()).getName());
                    comboSupGroup.getSelectionModel().select(supGroupService.getSubGroupsById(itemsModel.getSubGroups().getId()).getName());
                    comboType.getSelectionModel().select(itemsModel.getUnitsType().getUnit_name());
                    tableUnitsSetting.selectTable(itemsModel);
                    extraBarcodesTab.setItems(itemsModel.getExtraBarcodes());
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
        return itemForm.incompleteProperty()
                .or(comboMainGroup.valueProperty().isNull())
                .or(comboSupGroup.valueProperty().isNull())
                .or(comboType.valueProperty().isNull());
    }

    /**
     * Says why the save button is disabled, since {@link #checkEnableButton()}
     * on its own does not: a user staring at a greyed-out button with nothing
     * filled in wrong that they can see has no way to know what is missing.
     * <p>
     * The tooltip is swapped onto the button rather than left permanently
     * installed, because an installed tooltip on a control still shows once
     * nothing is missing - it would just be reporting nothing was wrong.
     */
    private void bindSaveTooltip() {
        var missing = Bindings.createStringBinding(this::missingRequirementsMessage,
                itemForm.nameProperty(), itemForm.buyPriceProperty(),
                comboMainGroup.valueProperty(), comboSupGroup.valueProperty(), comboType.valueProperty());

        var tooltip = new Tooltip();
        tooltip.textProperty().bind(missing);

        missing.addListener((observable, oldText, newText) -> btnSave.setTooltip(newText == null ? null : tooltip));
        btnSave.setTooltip(missing.get() == null ? null : tooltip);
    }

    private String missingRequirementsMessage() {
        var lm = LanguageManager.getInstance();
        List<String> missing = new ArrayList<>();
        if (itemForm.isNameBlank()) missing.add(lm.getString("column.name_item"));
        if (itemForm.isBuyPriceNotPositive()) missing.add(lm.getString("BuyPrice"));
        if (comboMainGroup.getValue() == null) missing.add(lm.getString("mainGroup"));
        if (comboSupGroup.getValue() == null) missing.add(lm.getString("subGroup"));
        if (comboType.getValue() == null) missing.add(lm.getString("type"));

        return missing.isEmpty() ? null
                : lm.getString("item.tooltip.missing.fields") + ": " + String.join("، ", missing);
    }

    /**
     * Toggles the style class the theme keys {@code .validation-error} styling
     * off (see {@code app-theme.css}), the way {@code BuyController2} already
     * does for its own fields.
     */
    private void setValidationError(Control control, boolean invalid) {
        if (invalid) {
            if (!control.getStyleClass().contains("validation-error")) {
                control.getStyleClass().add("validation-error");
            }
        } else {
            control.getStyleClass().remove("validation-error");
        }
    }

    private void clearValidationErrors() {
        setValidationError(txtItemName, false);
        setValidationError(txtBarcode, false);
        setValidationError(txtSelPrice, false);
        setValidationError(comboSupGroup, false);
    }

    private ItemsModel insertData() throws Exception {
        clearValidationErrors();

        // add subgroup
        resolveSubGroupId(comboSupGroup.getSelectionModel().getSelectedItem());

        // The save button's binding only asks that the field is not empty, and a
        // name of spaces is not empty. isNameBlank() trims first, which is what
        // gets stored.
        if (itemForm.isNameBlank()) {
            setValidationError(txtItemName, true);
            txtItemName.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.name.required"));
        }

        if (itemForm.isBarcodeBlank()) {
            setValidationError(txtBarcode, true);
            txtBarcode.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("msg.insert.all"));
        }

        if (itemForm.isBarcodeTooLong()) {
            setValidationError(txtBarcode, true);
            txtBarcode.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.barcode.too.long"));
        }

        if (itemForm.isSellPriceNotAboveBuy()) {
            setValidationError(txtSelPrice, true);
            txtSelPrice.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.sell.not.above.buy"));
        }

        if (subId <= 0) {
            setValidationError(comboSupGroup, true);
            comboSupGroup.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.group.required"));
        }
        var itemsUnitsModelList = tableUnitsSetting.getItemsUnitsModelList();
        if (itemsUnitsModelList.isEmpty()) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.units.required"));
        }

        var baseUnit = getUnitsModelByName(comboType.getSelectionModel().getSelectedItem());
        // Trimmed here, once, because the codes used to be compared in three
        // places that did not agree: this screen matched them as typed, while
        // ItemsService.firstBarcodeTakenByAnotherItem trims before asking the
        // database. "123 " and "123" passed as two different codes locally and
        // then collided on the unique index.
        String barcode = itemForm.getBarcode().trim();
        checkBarcodesAreFree(barcode, baseUnit, itemsUnitsModelList);

        var itemsModel = new ItemsModel();
        itemsModel.setId(codeItem);
        itemForm.applyTo(itemsModel);
        itemsModel.setSubGroups(new SubGroups(subId));

        // Set image data
        if (imageAdd.getImage() != null) {
            itemsModel.setItem_image(imageChoose.convertFxImageToBytes(imageAdd.getImage()));
        }

        // The whole list goes down: ItemsDao drops the base unit's row by matching
        // it against items.unit_id, which does not depend on it being first.
        itemsModel.setUnitsType(baseUnit);
        itemsModel.setItemsUnitsModelList(new ArrayList<>(itemsUnitsModelList));
        itemsModel.setExtraBarcodes(new ArrayList<>(extraBarcodesTab.getItems()));
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
        extraBarcodesTab.getItems().stream().map(String::trim).forEach(codes::add);

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
        }

        // One query for the whole set, rather than one per code - an item with a
        // handful of extra barcodes and a few priced units used to mean a full
        // round trip to the database for each of them just to learn none clashed.
        String taken = itemsService.firstBarcodeTakenByAnotherItem(codes, codeItem);
        if (taken != null) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString("item.error.barcode.used.by.other", taken));
        }
    }

    /**
     * Marks and reports a code that belongs to another item, and answers whether
     * the field is usable.
     * <p>
     * Deliberately does not pull the focus back: this runs from a focus-lost
     * listener, and a field that takes the focus again on its way out is a field
     * the user cannot leave - the close button included. The red marking and the
     * message are the hint; the refusal is {@link #checkBarcodesAreFree}, which
     * the save goes through whatever happened here.
     */
    private boolean verifyBarcodeIsFree(TextField field) {
        try {
            barcodeAvailability.requireFree(field.getText());
            setValidationError(field, false);
            return true;
        } catch (Exception e) {
            setValidationError(field, true);
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.save.title"), e);
            return false;
        }
    }

    private void saveData(boolean isDuplicate) {
        try {
            if (!AllAlerts.confirmSave()) return;
            // insertData reads and marks @FXML controls on a failed check, which is
            // only safe from the FX thread, so validation - including the barcode
            // uniqueness reads it makes along the way - stays synchronous here.
            var itemsModel = insertData();
            runSaveTask(itemsModel, isDuplicate);
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Runs the write itself - {@code insertMultiData}'s transaction across the
     * item, its opening stock row, its units and its barcodes - off the FX
     * thread, so a slow connection does not freeze the dialog underneath it.
     * {@code saving} keeps a second click from starting a second transaction
     * while this one is in flight.
     */
    private void runSaveTask(ItemsModel itemsModel, boolean isDuplicate) {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws DaoException {
                return itemsService.updateItem(itemsModel);
            }
        };
        task.setOnSucceeded(event -> {
            saving.set(false);
            onItemSaved(itemsModel, task.getValue(), isDuplicate);
        });
        task.setOnFailed(event -> saving.set(false));
        AllAlerts.handleTaskFailure(LanguageManager.getInstance().getString("item.dialog.save.title"), task);

        saving.set(true);
        Thread thread = new Thread(task, "item-save");
        thread.setDaemon(true);
        thread.start();
    }

    private void onItemSaved(ItemsModel itemsModel, int rowsAffected, boolean isDuplicate) {
        if (rowsAffected != 1) return;

        if (eventBus != null) eventBus.publish(new ItemSaved(itemsModel));
        tableUnits.getItems().clear();
        extraBarcodesTab.clear();
        AllAlerts.alertSave();
        imageAdd.setImage(null);
        if (!isDuplicate) {
            txtCode.clear();
            itemForm.reset();
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

    /**
     * Resolves a name to its {@link UnitsModel} from {@link #unitsCache} - no
     * database call, since the cache already holds every unit that exists.
     * {@code null} selects the base unit (id 1, the {@code DEFAULT} on every
     * {@code type} column) the same way a blank {@code comboType} used to.
     */
    private UnitsModel getUnitsModelByName(String selectedItemType) {
        if (selectedItemType == null) {
            return unitsCache.stream().filter(unit -> unit.getUnit_id() == 1).findFirst().orElse(null);
        }
        return unitsCache.stream()
                .filter(unit -> unit.getUnit_name().equals(selectedItemType))
                .findFirst()
                .orElse(null);
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

    /**
     * Offers a code for a new item: the first number from {@code max(item id) + 1}
     * upwards that no item already answers to.
     * <p>
     * It used to offer {@code max(item id) + 1} itself, unchecked. That is a row
     * number, not a barcode, and a shop that types its own short numeric codes -
     * or that has deleted items and had ids reused - collides with it regularly;
     * the collision then surfaced at save, on the one field the user never
     * touched. The generated code is now checked against the same three tables
     * every other code on this screen is checked against.
     * <p>
     * A code cannot always be found - a database refusing the lookup, or a
     * thousand consecutive taken numbers - and that is not a reason to refuse to
     * open the dialog. The field is left empty and the user types one; the save
     * already refuses a blank barcode.
     */
    private void addBarcode() {
        if (codeItem != 0) return;

        String generated = null;
        try {
            generated = barcodeAvailability.firstFreeFrom(itemsService.getMaxItemId() + 1L);
        } catch (Exception e) {
            log.warn("Could not generate a free barcode; leaving the field empty", e);
        }

        txtBarcode.setText(generated == null ? "" : generated);
        txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
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

