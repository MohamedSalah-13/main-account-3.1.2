package com.hamza.account.controller.items;

import com.codejava.commons.fx.validation.InputValidator;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.dataByName.MasterDataController;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.setting.ComboSetting;
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
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.service.UnitsService;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hamza.controlsfx.others.Utils.*;

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
     * True when the signed-in user does not hold {@link #savePermission()}.
     * <p>
     * The rule is {@code ItemsService.updateItem}, which calls
     * {@code AuthorizationGuard.require} - this is only the hint. It is worth having
     * because nothing gates the screen itself: the dashboard button opens it on
     * {@code items.show}, and the invoice screen opens it with no check at all, so a
     * user without {@code items.create} could fill in a whole item, confirm the save,
     * and only then be told. Set in {@link #permButtons()}; the binding built in
     * {@link #action()} is live, so the order of those two does not matter.
     */
    private final BooleanProperty savePermissionMissing = new SimpleBooleanProperty(false);
    /**
     * The item's own scalar fields - see {@link ItemForm}. Bound to its controls
     * once, in {@link #bindItemForm()}.
     */
    private final ItemForm itemForm = new ItemForm();
    /**
     * True while {@link #applyScreen} is filling the controls in from what was loaded.
     * <p>
     * The group and unit combos carry listeners that answer a <i>user's</i> choice by
     * going to the database - the sub groups of the main group just picked, the id behind
     * a name. Selecting those same combos from the loaded data would fire them too, and
     * every answer they would look up was already read in the same background pass. So
     * they stand down for the length of the apply, and it sets the ids itself.
     */
    private boolean applying;
    private int mainId, subId;
    @FXML
    private ComboBox<String> comboMainGroup, comboSupGroup, comboType;
    @FXML
    private TextField txtCode, txtBarcode, txtItemName, txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3,
            txtMiniQuantity, txtBalance;
    @FXML
    // The only labels the controller still names: the three price tiers are titled from
    // sel_price_type at runtime. The other nine were injected solely to have their text
    // overwritten with the same key the FXML now carries itself, so they are gone - their
    // fx:id stays in the file, where it names the row for whoever reads it next.
    private Label labelSelPrice, labelSelPrice2, labelSelPrice3;
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
    private UnitsTabController unitsTab;
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
            // comboOtherTypes reads the same list, so its selection is dropped here too,
            // and it is deliberately not restored: selecting a unit is the gesture that
            // fills textUnitQuantity with that unit's default factor, so re-selecting it
            // from here would overwrite a factor the operator had already typed for this
            // item. Losing a visible combo selection is better than silently changing a
            // number - re-picking the unit is one click.
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

    /**
     * Wires the screen, then loads it.
     * <p>
     * The two are separate on purpose. Everything above {@link #loadScreen()} builds
     * controls, bindings and listeners and touches no database; the eight to twelve
     * queries the screen needs to open - the units, the price tiers, the group lists, the
     * saved defaults, the generated code, and on an edit the item itself with its opening
     * balance and its two group names - are read in <b>one background pass</b> and applied
     * afterwards on the JavaFX thread. It used to be that each of those methods went and
     * fetched its own, in order, on the thread that draws: the dialog could not paint
     * until the last of them came back, which on a database over a network is a window
     * that hangs before it appears.
     */
    @FXML
    public void initialize() {
        // Built first: bindBarcodeField() hangs a focus listener on the barcode field off
        // it, and the extra-barcodes tab is handed it.
        barcodeAvailability = new BarcodeAvailability(
                itemsService::itemNameHoldingBarcode, itemsService::takenBarcodesAmong, () -> codeItem);
        bindItemForm();
        unitSetting();
        otherSetting();
        comboTypeOption();
        addValidate();
        nameSetting();
        action();
        extraBarcodesTab = new ExtraBarcodesTabController(
                listExtraBarcodes, textExtraBarcode, btnAddExtraBarcode, btnRemoveExtraBarcode, itemForm::getBarcode,
                barcodeAvailability);

        // Not btnClearImage.fire(): firing a button to run logic ties start-up to that
        // button's state - a disabled button (a permission, one day) would silently skip
        // the step. The load fills the image in afterwards when there is one.
        imageAdd.setImage(null);
        permButtons();
        buttonGraphic();

        // Was select(1) - the tab index rather than the tab. That opened on
        // "أخرى" ("other"), not the units tab the index was meant to name.
        tabPane.getSelectionModel().select(tabUnits);

        // The dialog is opened once per item added or edited, so this instance and
        // its two observers have to go when the window does.
        subscribeToEvents();
        subscriptions.disposeWith(stackPane);

        loadScreen();
    }

    /**
     * Everything the screen reads to open, in one place so it can be read in one pass off
     * the JavaFX thread. Plain values and already-loaded models: nothing here touches a
     * control, which is what makes it safe to build on a worker thread.
     *
     * @param subGroupNames the sub groups of {@code mainGroupId}, so selecting the main
     *                      group during {@link #applyScreen} needs no query of its own
     * @param item          the item being edited, or null for a new one
     */
    private record ScreenData(List<UnitsModel> units,
                              List<SelPriceTypeModel> priceTiers,
                              List<String> mainGroupNames,
                              List<String> subGroupNames,
                              int mainGroupId, String mainGroupName,
                              int subGroupId, String subGroupName,
                              String unitName,
                              String generatedBarcode,
                              ItemsModel item,
                              boolean openingBalanceLocked) {
    }

    /**
     * Reads {@link ScreenData} on a worker thread behind the "please wait" overlay, and
     * applies it when it arrives. A failure is reported by
     * {@code MaskerPaneSetting}'s own handling and leaves an empty but usable screen -
     * the save still refuses anything incomplete.
     */
    private void loadScreen() {
        var masker = new MaskerPaneSetting(stackPane);
        var loaded = new java.util.concurrent.atomic.AtomicReference<ScreenData>();
        masker.showMaskerPane(title(), () -> loaded.set(readScreenData()));
        masker.getVoidTask().setOnSucceeded(event -> {
            ScreenData data = loaded.get();
            if (data != null) applyScreen(data);
        });
    }

    /** Runs on a worker thread. Must not touch a control. */
    private ScreenData readScreenData() throws Exception {
        List<UnitsModel> units = unitsService.getUnitsModelList();
        List<SelPriceTypeModel> priceTiers = selPriceItemService.getSelPriceTypeList();
        List<String> mainGroupNames = mainGroupService.getMainGroupsNames();

        ItemsModel item = codeItem > 0 ? itemsService.getItemByItemIdAndStockId(codeItem, DefaultStock.ID) : null;

        int mainGroupId = 0;
        int subGroupId = 0;
        String mainGroupName = null;
        String subGroupName = null;
        String unitName = null;
        boolean openingBalanceLocked = false;
        String generatedBarcode = null;

        if (item != null) {
            // The item carries its group's ids but not its names - they are read here
            // rather than by the combos, which is what selectData used to do on the FX
            // thread between two selections.
            mainGroupId = item.getSubGroups().getMainGroups().getId();
            subGroupId = item.getSubGroups().getId();
            mainGroupName = mainGroupService.getMainGroupsById(mainGroupId).getName();
            subGroupName = supGroupService.getSubGroupsById(subGroupId).getName();
            unitName = item.getUnitsType() == null ? null : item.getUnitsType().getUnit_name();
            openingBalanceLocked = itemsService.isOpeningBalanceLocked(item.getId());
        } else {
            // A new item opens on the defaults the settings screen stored, and on a code
            // of its own.
            var savedGroup = ComboSetting.savedSubGroup(supGroupService);
            if (savedGroup != null && savedGroup.getMainGroups() != null) {
                mainGroupId = savedGroup.getMainGroups().getId();
                subGroupId = savedGroup.getId();
                mainGroupName = savedGroup.getMainGroups().getName();
                subGroupName = savedGroup.getName();
            }
            var savedUnit = ComboSetting.savedUnit(unitsService);
            if (savedUnit != null) unitName = savedUnit.getUnit_name();
            generatedBarcode = generateBarcode();
        }

        List<String> subGroupNames = mainGroupId > 0
                ? supGroupService.getSubGroupsNamesByMainId(mainGroupId)
                : List.of();

        return new ScreenData(units, priceTiers, mainGroupNames, subGroupNames,
                mainGroupId, mainGroupName, subGroupId, subGroupName, unitName,
                generatedBarcode, item, openingBalanceLocked);
    }

    /**
     * Fills the screen in from what was read. On the JavaFX thread.
     * <p>
     * {@link #applying} is set for the length of it: selecting a group or a unit fires
     * listeners whose job is to answer <i>the user's</i> choice with a query, and every
     * answer they would look up is already in {@code data}.
     * <p>
     * The order is load-bearing. The unit is selected before the barcode is written,
     * because writing the barcode is what creates the units table's base row and it takes
     * the unit from that combo; and the item's own units are loaded after that, replacing
     * the row with the item's real ones.
     */
    private void applyScreen(ScreenData data) {
        applying = true;
        try {
            unitsCache.setAll(data.units());
            unitNames.setAll(data.units().stream().map(UnitsModel::getUnit_name).toList());
            applyPriceLabels(data.priceTiers());

            comboMainGroup.setItems(FXCollections.observableList(data.mainGroupNames()));
            comboSupGroup.setItems(FXCollections.observableList(new ArrayList<>(data.subGroupNames())));
            mainId = data.mainGroupId();
            subId = data.subGroupId();
            if (data.mainGroupName() != null) comboMainGroup.getSelectionModel().select(data.mainGroupName());
            if (data.subGroupName() != null) comboSupGroup.getSelectionModel().select(data.subGroupName());

            if (data.unitName() != null) comboType.getSelectionModel().select(data.unitName());
            else comboType.getSelectionModel().selectFirst();

            if (data.item() != null) applyItem(data.item());
            else applyNewItem(data.generatedBarcode());

            applyOpeningBalanceLock(data.openingBalanceLocked());
        } finally {
            applying = false;
        }
    }

    /** A saved item, shown. */
    private void applyItem(ItemsModel item) {
        txtCode.setText(String.valueOf(item.getId()));
        itemForm.load(item);
        unitsTab.load(item);
        extraBarcodesTab.setItems(item.getExtraBarcodes());

        var itemImage = item.getItem_image();
        if (itemImage != null && itemImage.length > 0) {
            imageAdd.setImage(new Image(new ByteArrayInputStream(itemImage)));
        }
    }

    /** A new item: the code it is offered, or the one it was opened with. */
    private void applyNewItem(String generatedBarcode) {
        showGeneratedBarcode(generatedBarcode);
        if (initialBarcode != null && !initialBarcode.isBlank()) {
            itemForm.setBarcode(initialBarcode.trim());
        }
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
        unitsTab = new UnitsTabController(tableUnits, comboOtherTypes, textUnitQuantity, textUnitBarcode,
                textUnitBuyPrice, textUnitSelPrice, textUnitSelPrice2, textUnitSelPrice3, btnAdd,
                unitNames, this::getUnitsModelByName, this::verifyBarcodeIsFree);
    }

    /**
     * The buttons' icons, from {@link AppIcon} rather than {@code Image_Setting}.
     * <p>
     * {@code new Image_Setting()} opens roughly forty {@code InputStream}s in its field
     * initialisers - one per icon in the application - and this screen used ten of them.
     * The other thirty were opened and never closed, once per opening of this dialog. A
     * {@code FontIcon} is also a font glyph rather than a decoded PNG, so it takes its
     * colour from the stylesheet and answers a theme change; see rule ق-ل4.
     * <p>
     * Two of the icons were also simply the wrong picture: the main-group button carried
     * the reports icon and the sub-group button carried {@code vertical_align_bottom},
     * which is what was nearest to hand in a PNG folder rather than what either button
     * does.
     */
    private void buttonGraphic() {
        btnAdd.setGraphic(AppIcon.ADD.graphic());
        btnSave.setGraphic(AppIcon.SAVE.graphic());
        btnBarcode.setGraphic(AppIcon.BARCODE.graphic());
        btnAddImage.setGraphic(AppIcon.SEARCH.graphic());
        btnClose.setGraphic(AppIcon.CLOSE.graphic());
        btnAddMainGroup.setGraphic(AppIcon.MAIN_GROUP.graphic());
        btnAddSubGroup.setGraphic(AppIcon.SUB_GROUP.graphic());
        btnSaveDuplicate.setGraphic(AppIcon.DUPLICATE.graphic());
        btnClearImage.setGraphic(AppIcon.DELETE.graphic());
        btnClearPrices.setGraphic(AppIcon.CLEAR.graphic());
    }

    private void otherSetting() {
        // The whole form, ending on the save button - rule ق-ل9. It used to stop at
        // txtMiniQuantity, so keyboard-only entry ran out of screen three fields early:
        // the unit and the two group combos are all required to save, and Enter never
        // reached any of them.
        whenEnterPressed(txtItemName, txtBarcode, txtBuyPrice, txtSelPrice, txtSelPrice2, txtSelPrice3,
                comboType, txtBalance, txtMiniQuantity, comboMainGroup, comboSupGroup, btnSave);
        setTextFormatter(txtBalance, txtBuyPrice, txtMiniQuantity, txtSelPrice, txtSelPrice2, txtSelPrice3);
        getFocusToName();
        checkItemActive.setSelected(true);
    }

    /**
     * The item's own unit: what its stock is counted in and what every other unit's
     * factor is a multiple of. Choosing it re-points row 0 of the units table, which is
     * that unit shown rather than a unit row of its own.
     */
    private void comboTypeOption() {
        FilteredList<String> filteredItems = new FilteredList<>(unitNames, s -> true);
        comboType.setItems(filteredItems);
        comboType.getSelectionModel().selectFirst();

        comboType.valueProperty().addListener((observable, oldName, name) -> {
            try {
                if (applying || unitsTab.units().isEmpty()) return;

                // The item cannot be stocked in a unit it is also sold by: that is two
                // rows meaning the same thing with different factors.
                if (unitsTab.isUnitAddedBesidesBase(name)) {
                    comboType.getSelectionModel().select(oldName);
                    throw new UserValidationException(LanguageManager.getInstance().getString("item.error.unit.duplicate"));
                }

                unitsTab.setBaseUnit(getUnitsModelByName(name));
            } catch (Exception e) {
                showError(e);
            }
        });
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
            showError(e);
            units = new ArrayList<>();
        }
        unitsCache.setAll(units);
        unitNames.setAll(units.stream().map(UnitsModel::getUnit_name).toList());
    }

    private void permButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnAddMainGroup::setDisable, AppPermissions.MAIN_GROUP_SHOW);
        permissionDisableService.applyPermissionBasedDisable(btnAddSubGroup::setDisable, AppPermissions.SUB_GROUP_SHOW);
        // Not applyPermissionBasedDisable: btnSave's disable property is bound, so it
        // cannot be set. The answer goes into the binding instead.
        savePermissionMissing.set(!AuthorizationGuard.isGranted(savePermission()));
    }

    /**
     * What saving this screen needs - creating an item and editing one are different
     * abilities, and this dialog is both screens.
     */
    private PermissionKey savePermission() {
        return codeItem == 0 ? AppPermissions.ITEMS_CREATE : AppPermissions.ITEMS_UPDATE;
    }

    /**
     * The screen's wiring, in the four groups it falls into. It was one 125-line method
     * that ran from the save buttons through the group combos and the barcode field to
     * the units tab and the image, and reading it meant reading all of it. The units-tab
     * half of it now lives in {@link UnitsTabController}.
     */
    private void action() {
        bindSaveButtons();
        bindGroupCombos();
        bindBarcodeField();
        bindImageButtons();
    }

    private void bindSaveButtons() {
        // Built once: two buttons ask the same question, and it used to be built twice.
        BooleanBinding incomplete = checkEnableButton();
        btnSave.disableProperty().bind(incomplete.or(saving));
        // A duplicate is a new item, so there is nothing to duplicate while editing one.
        // This was an anonymous BooleanBinding over no dependencies, which is a constant
        // written the long way.
        btnSaveDuplicate.disableProperty().bind(codeItem > 0
                ? new SimpleBooleanProperty(true)
                : incomplete.or(saving));
        btnClose.disableProperty().bind(saving);
        bindSaveTooltip();

        btnClose.setOnAction(actionEvent -> ((Stage) btnClose.getScene().getWindow()).close());
        btnSave.setOnAction(actionEvent -> saveData(false));
        btnSaveDuplicate.setOnAction(actionEvent -> saveData(true));
        btnBarcode.setOnAction(actionEvent -> addBarcode());
    }

    private void bindGroupCombos() {
        comboMainGroup.valueProperty().addListener((observable, oldValue, newValue) -> {
            // applyScreen already holds the id and the sub-group list this would go and
            // fetch; from then on this answers the user changing the group.
            if (applying) return;
            try {
                mainId = mainGroupService.getMainGroupsByName(newValue).getId();
                List<String> groupListByMainId = getSubGroupsNamesByMainId();
                comboSupGroup.setItems(FXCollections.observableList(groupListByMainId));
            } catch (NullPointerException e) {
                comboSupGroup.setItems(null);
            } catch (DaoException e) {
                showError(e);
            }
        });
        comboSupGroup.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (applying) return;
            resolveSubGroupId(newValue);
        });

        btnAddMainGroup.setOnAction(actionEvent -> {
            try {
                MasterDataController.showWindow(MasterDataKind.MAIN);
            } catch (Exception e) {
                showError(e);
            }
        });
        btnAddSubGroup.setOnAction(actionEvent -> {
            try {
                MasterDataController.showWindow(MasterDataKind.SUB);
            } catch (Exception e) {
                showError(e);
            }
        });
    }

    private void bindBarcodeField() {
        // Checked when the field is left, not on every keystroke: a barcode is
        // typed or scanned digit by digit, and every prefix of it would otherwise
        // be a query. Enter moves the focus on (whenEnterPressed), so a scanner
        // ending its read triggers this too.
        txtBarcode.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) verifyBarcodeIsFree(txtBarcode);
        });

        // Row 0 of the units table carries the item's own barcode; the tab creates that
        // row the first time there is a unit to create it with.
        txtBarcode.textProperty().addListener((observable, oldValue, newValue) ->
                unitsTab.applyItemBarcode(newValue,
                        () -> getUnitsModelByName(comboType.getSelectionModel().getSelectedItem())));
    }

    private void bindImageButtons() {
        btnAddImage.setOnAction(actionEvent -> {
            try {
                imageChoose.onAddImage(imageAdd);
            } catch (FileNotFoundException e) {
                showError(e);
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
            showError(e);
            return new ArrayList<>();
        }
    }

    private List<String> getMainGroupsNames() {
        try {
            return mainGroupService.getMainGroupsNames();
        } catch (DaoException e) {
            showError(e);
            return new ArrayList<>();
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
    private void applyOpeningBalanceLock(boolean locked) {
        txtBalance.setDisable(locked);
        txtBalance.setTooltip(locked
                ? new Tooltip(LanguageManager.getInstance().getString("item.tooltip.opening.balance.locked"))
                : null);
    }

    private BooleanBinding checkEnableButton() {
        return itemForm.incompleteProperty()
                .or(comboMainGroup.valueProperty().isNull())
                .or(comboSupGroup.valueProperty().isNull())
                .or(comboType.valueProperty().isNull())
                .or(savePermissionMissing);
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
                comboMainGroup.valueProperty(), comboSupGroup.valueProperty(), comboType.valueProperty(),
                savePermissionMissing);

        var tooltip = new Tooltip();
        tooltip.textProperty().bind(missing);

        missing.addListener((observable, oldText, newText) -> btnSave.setTooltip(newText == null ? null : tooltip));
        btnSave.setTooltip(missing.get() == null ? null : tooltip);
    }

    private String missingRequirementsMessage() {
        var lm = LanguageManager.getInstance();
        // Said on its own rather than added to the list: no amount of filling the form in
        // will enable the button, so naming the empty fields would be misleading.
        if (savePermissionMissing.get()) {
            return lm.getString("auth.error.permission.denied", savePermission().value());
        }
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
        setValidationError(comboType, false);
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
        var itemsUnitsModelList = unitsTab.units();
        if (itemsUnitsModelList.isEmpty()) {
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.units.required"));
        }

        // The unit the item is stocked in. It is written straight into items.unit_id, so a
        // null one reached ItemsDao.insertItem's getUnitsType().getUnit_id() and threw
        // there - on the save thread, at the end of a filled-in form, as a reference code.
        // It resolves to null when the units cache is empty, which is what a failed units
        // query leaves behind.
        var baseUnit = getUnitsModelByName(comboType.getSelectionModel().getSelectedItem());
        if (baseUnit == null) {
            setValidationError(comboType, true);
            comboType.requestFocus();
            throw new UserValidationException(LanguageManager.getInstance().getString("item.error.unit.base.missing"));
        }

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
            showError(e);
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

    /**
     * What follows a save that went through.
     * <p>
     * {@code rowsAffected} is 1 for every path that wrote: both {@code ItemsDao.insert}
     * and {@code .update} run inside {@code insertMultiData}, which answers 1 on commit
     * and throws otherwise - a failure is the task's {@code setOnFailed}, not a zero
     * here. The one caller that answers 0 is the trial cap, which shows its own message
     * before returning. So this branch is not reachable today; it is guarded rather than
     * assumed, because a silent save - the user confirms, and the screen does nothing at
     * all - is the worst way for a new zero to arrive.
     */
    private void onItemSaved(ItemsModel itemsModel, int rowsAffected, boolean isDuplicate) {
        if (rowsAffected != 1) {
            log.warn("The item save reported {} rows; the screen was left as it was", rowsAffected);
            return;
        }

        if (eventBus != null) eventBus.publish(new ItemSaved(itemsModel));
        unitsTab.clear();
        extraBarcodesTab.clear();
        AllAlerts.alertSave();
        imageAdd.setImage(null);
        if (!isDuplicate) {
            txtCode.clear();
            itemForm.reset();
            // The form is a blank item again, and a blank item has moved
            // nothing - so the opening balance is open for entry, and no query is
            // needed to know that.
            applyOpeningBalanceLock(false);
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
            showError(e);
        }
    }

    /**
     * The two pieces of text on this screen that the FXML cannot state for itself.
     * <p>
     * Everything else is a {@code %key} in {@code addItem-view.fxml} now. It used to be
     * fourteen {@code setText} and eight {@code setPromptText} calls over labels the FXML
     * had filled in with English placeholders - "Code", "Buy Price", "Sup Group" - so the
     * file said one thing, the screen showed another, and a label added to the file was
     * English until someone remembered to add a line here as well. The newer tabs in the
     * same file were already using {@code %key}; this is the rest of it catching up.
     */
    private void nameSetting() {
        // The accelerator is part of the label, and the key is not part of the translation.
        btnSave.setText(LanguageManager.getInstance().getString("common.save") + " F10");
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
    private void applyPriceLabels(List<SelPriceTypeModel> priceList) {
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
        showGeneratedBarcode(generateBarcode());
    }

    /**
     * The generated code itself - two queries, so it is read in the background pass when
     * the screen opens and only runs on the JavaFX thread when the user asks for another
     * one with the barcode button.
     */
    private String generateBarcode() {
        try {
            return barcodeAvailability.firstFreeFrom(itemsService.getMaxItemId() + 1L);
        } catch (Exception e) {
            log.warn("Could not generate a free barcode; leaving the field empty", e);
            return null;
        }
    }

    private void showGeneratedBarcode(String generated) {
        txtBarcode.setText(generated == null ? "" : generated);
        txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
    }

    /**
     * The two expiry fields: whole days, and only while the item tracks expiry.
     * <p>
     * Filtered with {@code InputValidator.makeNumericOnly}, the same tool the two barcode
     * fields on this screen use. It replaced a listener that wrote a zero back into the
     * field on every value it did not like - including an empty one, so clearing the field
     * to type a new number put a 0 in front of it and "30" was typed as "030". A field
     * left blank is still saved as zero: {@link ItemForm} parses it.
     */
    private void addValidate() {
        textDaysValidate.disableProperty().bind(checkItemValidate.selectedProperty().not());
        textAlertBefore.disableProperty().bind(checkItemValidate.selectedProperty().not());
        textDaysValidate.setText("0");
        textAlertBefore.setText("0");
        InputValidator.makeNumericOnly(textDaysValidate, textAlertBefore);
    }

    private void getFocusToName() {
        Platform.runLater(() -> txtItemName.requestFocus());
    }

    /**
     * Reports a failure to the user. Named for what it does: it was {@code logError},
     * which reads as a quiet log line, while {@code AllAlerts.handleError} puts a modal
     * dialog on the screen - and it is called from inside listeners, where that is a
     * dialog the user did not ask for. {@code handleError} classifies as it goes, so a
     * {@code UserValidationException} arrives as its own sentence and anything else as a
     * reference code.
     */
    private void showError(Exception e) {
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

