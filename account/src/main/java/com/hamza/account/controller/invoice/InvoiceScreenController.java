package com.hamza.account.controller.invoice;

import com.hamza.account.features.items.StockScope;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.ItemSuggestionField;
import com.hamza.account.controller.search.PartySuggestionField;
import com.hamza.account.controller.setting.SettingTabLanguageController;
import com.hamza.account.controller.users.ShiftCorrectionReasonPrompt;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.backup.BackupPolicy;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.ItemSaved;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.OffersChanged;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.features.offers.OfferService;
import com.hamza.account.features.invoice.*;
import com.hamza.account.features.pricing.PriceTier;
import com.hamza.account.features.pricing.PriceTierCatalog;
import com.hamza.account.features.pricing.PriceTierService;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.features.returns.JdbcReturnableRepository;
import com.hamza.account.features.notification.StockLevelAlert;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.scalebarcode.ScaleBarcodeValueType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.InvoiceHeaderView;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.service.*;
import com.hamza.account.treasury.DefaultTreasury;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.SearchItemsApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * What the two invoice screens share: the header (party, date, delegate, treasury, warehouse and
 * their pins), the lines table, the payment and totals footer, and the whole save, print and
 * return path. The one thing that differs is <b>how a line is entered</b>, and a subclass answers
 * only that: {@link BuyController2} has the barcode/name/price/quantity form above the table, and
 * {@link QuickInvoiceController} has no form - the table itself is the entry surface.
 *
 * <p>The two used to be one class switching on a mode, which hid the standard form's fourteen
 * controls on the quick screen and so gave it the standard screen's header whole: a till screen
 * with nine header fields. Split, the quick screen lays out its own compact header and footer from
 * the same {@code fx:id}s, and every rule a line or a save obeys is still written once, here or in
 * {@code features/invoice}. Any line added from either screen goes through {@link #addLine}.
 */
@Log4j2
public abstract class InvoiceScreenController<T3 extends BaseNames, T4 extends BaseAccount>
        extends BuyData<T3, T4> implements Initializable, AppSettingInterface {

    protected final InvoiceEditorViewModel<BasePurchasesAndSales> editor = new InvoiceEditorViewModel<>();
    private final Subscriptions subscriptions = new Subscriptions();
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final InvoicePrintService invoicePrintService = new InvoicePrintService();
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    protected final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final InvoicePostSaveService invoicePostSaveService;
    protected final InvoiceLineService<BasePurchasesAndSales> invoiceLineService;
    protected final InvoiceExpiryService invoiceExpiryService;
    protected final InvoiceItemSelectionService invoiceItemSelectionService;
    private final InvoiceItemPickerService invoiceItemPickerService;
    protected final InvoiceScreenMode screenMode;
    private final InvoiceLineEntry lineEntry;
    protected InvoiceLineEditService lineEditService;
    /** The invoice's price tier: the customer's, or another for whoever may change it (V84, ق-س٢). */
    protected int priceTypeByNameId = PriceTiers.FIRST;
    private final PriceTierService priceTierService = ServiceRegistry.get(PriceTierService.class);
    /** The tiers as they stood when the screen opened - the tier box and every tier's name. */
    private PriceTierCatalog priceTiers = new PriceTierCatalog(List.of());
    /** Beside the party: which tier the invoice is priced at, changeable with sales.price.tier.change. */
    private final ComboBox<PriceTier> comboPriceTier = new ComboBox<>();
    /** What the last tier change, or a line priced at tier 1, did - beside the badges; hidden when empty. */
    private final Label pricingNote = new Label();
    /** True while the screen, not the user, moves the tier box. */
    private boolean settingTier;
    /** The tier a reopened document was saved at, or null - a new document, or one saved before V84. */
    private Integer storedTier;
    /** Whether somebody chose a tier in the box since the document was opened. */
    private boolean tierChosenOnScreen;
    /**
     * The invoice stock context; kept at the legacy default until warehouse selection is exposed.
     */
    protected int invoiceStockId = DefaultStock.ID;
    /** Version of the existing document loaded by this editor, for compare-and-swap saving. */
    private LocalDateTime loadedUpdatedAt;
    /** An item changed after this invoice started; entered line prices are never overwritten silently. */
    private boolean itemCatalogChangedWhileOpen;
    private int codeAccount;
    private boolean updatingPaymentUi;
    /** Delegate names paired with ids, avoiding a database lookup whenever a pin redraws. */
    private Map<String, Integer> delegateIds = Map.of();
    /** Active till names paired with their stable ids; the selector itself still displays names. */
    private Map<String, Integer> activeTreasuryIds = Map.of();
    private StringProperty textSearchName;
    private PartySuggestionField<T3> nameSearchField;
    /**
     * What this screen's figures are typed in (V83, docs/currency-plan.md §15): the base until a party in a
     * foreign currency is chosen, or a document written in one is reopened. The services pricing a line read
     * it through a supplier, so a party chosen later reprices what they offer next.
     */
    private DocumentPricing documentPricing = DocumentPricing.BASE;
    /** The party's currency when its documents are written in the base and translated (ق-د٨), for the badge. */
    private com.hamza.account.features.currency.Currency translatedCurrency;
    /** Says which currency the figures are in, beside the title; hidden for a document in the base. */
    private final Label currencyBadge = new Label();
    private final InvoiceScreenCurrency screenCurrency = new InvoiceScreenCurrency(currencyCatalogue());
    /** True while a saved document is put back, so its own currency is not replaced by its party's. */
    private boolean restoringDocument;
    @FXML
    private Label labelNum, labelName, labelStock, labelDate, labelDelegate, labelTreasury, last1, last2, last3, last4, last5, labelNotes, labelInvoiceTotal, labelPaid, labelRemaining, labelNetAfterDiscount;
    @FXML
    @Getter
    private Button btnSave, btnPrintSave, btnNew, btnSearch, btnQuickMode,
            btnPinParty, btnPinDelegate, btnPinTreasury, btnPinStock;
    @FXML
    private Button btnReturnFromInvoice;
    @FXML
    private ComboBox<String> comboDelegate, comboTreasury;
    @FXML
    private ComboBox<Stock> comboStock;
    @FXML
    private TextField txtNum, txtOtherDiscount, txtPaid, txtRestAfterPaid, txtRestAfterDiscount;
    @FXML
    protected TableView<BasePurchasesAndSales> table;
    @FXML
    // Labels, not Text: a Text has no minimum width, so every digit a total gained widened
    // its column and moved the whole footer. .invoice-summary-figure reserves the room.
    private Label textSumCount, txtSumQuantity, txtBeforeDiscount, txtSumDiscount, txtSumTotals, textInvoiceTotal;
    @FXML
    private DatePicker date;
    @FXML
    protected StackPane stackPane;
    @FXML
    private FlowPane invoiceTopBar;
    @FXML
    private Region invoiceTopBarSpacer;
    private double dragOffsetX;
    private double dragOffsetY;
    @FXML
    private Pane boxDelegate;
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

    protected InvoiceScreenController(DataInterface<?, ?, T3, T4> dataInterface, int numInvoiceUpdate,
                                      InvoiceScreenMode screenMode) throws Exception {
        super(dataInterface, numInvoiceUpdate);
        this.screenMode = screenMode == null ? InvoiceScreenMode.STANDARD : screenMode;
        this.invoicePostSaveService = new InvoicePostSaveService(
                eventBus, dataInterface.invoiceSide());
        this.invoiceLineService = new InvoiceLineService<>(
                dataInterface.designInterface().documentType(), numInvoiceUpdate,
                dataInterface.invoiceBuy()::object_TableData, () -> documentPricing);
        // A hint said as the line is added; the save asks the permission itself (V84).
        this.invoiceLineService.undercutAllowedWhen(
                () -> AuthorizationGuard.isGranted(AppPermissions.SALES_PRICE_BELOW_LIST));
        CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
        this.invoiceExpiryService = new InvoiceExpiryService(
                dataInterface.designInterface().documentType(), numInvoiceUpdate,
                itemId -> cardItemService.expiryBalancesByItem(invoiceStockId, itemId));
        this.invoiceItemSelectionService = new InvoiceItemSelectionService(
                dataInterface.designInterface().documentType(), itemsService,
                dataInterface.invoiceBuy()::getItemsPrice, () -> documentPricing);
        this.invoiceItemPickerService = new InvoiceItemPickerService(
                dataInterface.designInterface().documentType(), itemsService,
                dataInterface.invoiceBuy()::getItemsPrice, () -> documentPricing);
        this.lineEntry = new InvoiceLineEntry(dataInterface.designInterface().documentType(),
                invoiceLineService, invoiceExpiryService,
                options -> new ChoiceItemExpireDate(options).showAndWait(),
                (item, onInvoice) -> StockLevelAlert.check(item, StockLevelAlert.remainingAfter(item, onInvoice)),
                () -> new InvoiceLineEntry.Settings(getInvoiceIncreaseItemOneTable(),
                        String.valueOf(getSettingBarcodeStart()), getSelWithoutBalance()));
    }

    static PermissionKey itemMutationPermission(String barcode) {
        return barcode == null || barcode.isBlank()
                ? AppPermissions.ITEMS_CREATE
                : AppPermissions.ITEMS_UPDATE;
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
        configureItemEntrySurface();
        configureReturnEntry();
        configureScreenMode();
        action();
        publisherData();
        disableData();
        totalSetting();
        buttonGraphic();
        configureInvoiceTopBar();
        configurePinButtons();
        installCurrencyBadge();
        installOffers();

        if (num_invoice_update > 0) {
            selectData();
        } else {
            getSavedCustomerAndDelegate();
        }
    }

    // ---- the offers (V85, docs/pricing-and-offers-plan.md ق-ع٤ and ق-ع١٤) --------------------

    private final OfferService offerService = ServiceRegistry.get(OfferService.class);
    /** Null where the engine does not run: anything but a sale, or an edition without the add-on. */
    private InvoiceOfferPreview offerPreview;
    private boolean offersPending;
    private final Label offersCaption = new Label();
    private final Label offersFigure = new Label();
    /** Under the lines: what would earn an offer - the units that complete a group, a gift not yet on it. */
    private final Label offersHint = new Label();
    /** The items the hints name, kept - a gift is usually not on the invoice yet. */
    private final java.util.Map<Integer, com.hamza.account.model.domain.ItemsModel> hintItems =
            new java.util.HashMap<>();

    /**
     * On a sale, with the add-on: the engine run after every change to the lines, the date or the tier, over
     * the till's snapshot of the offers - replaced when they change on any till. An offer column in the
     * table, and what the offers gave under the lines' count. A sales return runs nothing: its lines take
     * their source line's offer when it is saved (ق-ع١١, {@code ReturnCostResolver}).
     */
    private void installOffers() {
        if (offerService == null || !offerService.enabled() || documentType() != DocumentType.SALES) {
            return;
        }
        table.getColumns().add(InvoiceTableCoordinator.offerColumn());
        // What a limited offer has left is read afresh for each preview; the save reads it again, locked.
        offerPreview = new InvoiceOfferPreview(new InvoiceOffers.JdbcGroups(),
                (limited, exceptInvoice) -> offerService.timesLeft(limited, exceptInvoice, false));
        placeOffersHint();
        reloadOffers();
        offersCaption.setText(LanguageManager.getInstance().getString("invoice.offers.caption"));
        offersCaption.getStyleClass().add("summary-label");
        offersCaption.setMinWidth(Region.USE_PREF_SIZE);
        offersFigure.getStyleClass().addAll("text-sum", "invoice-summary-figure");
        if (txtSumDiscount.getParent() instanceof javafx.scene.layout.GridPane footer) {
            footer.add(offersCaption, 0, 2);
            footer.add(offersFigure, 1, 2);
        }
        showOffers(OfferEngine.Result.none());
        editor.totalsProperty().addListener((observable, before, now) -> scheduleOffers());
        date.valueProperty().addListener((observable, before, now) -> scheduleOffers());
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(OffersChanged.class, event -> {
                reloadOffers();
                scheduleOffers();
            }));
        }
    }

    private void reloadOffers() {
        if (offerPreview == null) {
            return;
        }
        try {
            offerPreview.setOffers(offerService.inForce(offerPreviewRecorded));
        } catch (DaoException e) {
            logError(e);
        }
    }

    private Set<Integer> offerPreviewRecorded = Set.of();

    /** A reopened sale keeps the offers its own lines carry, even once stopped (ق-ع٧). */
    private void restoreOffers(List<? extends BasePurchasesAndSales> lines) {
        if (offerPreview == null) {
            return;
        }
        offerPreviewRecorded = InvoiceOfferPreview.recordedOn(lines);
        offerPreview.setRecorded(offerPreviewRecorded);
        offerPreview.setInvoiceNumber(num_invoice_update);
        reloadOffers();
        scheduleOffers();
    }

    /** Once per pulse, after whatever changed the lines has finished changing them. */
    private void scheduleOffers() {
        if (offerPreview == null || offersPending) {
            return;
        }
        offersPending = true;
        Platform.runLater(() -> {
            offersPending = false;
            runOffers();
        });
    }

    private void runOffers() {
        if (offerPreview == null || restoringDocument) {
            return;
        }
        try {
            T3 party = selectedParty();
            boolean foreign = party != null && party.getCurrency_id() != null;
            boolean moved = foreign || date.getValue() == null
                    ? offerPreview.clear(editor.lines())
                    : offerPreview.run(editor.lines(), date.getValue(), tierForSave());
            if (moved) {
                editor.refreshTotals();
                table.refresh();
            }
            showOffers(offerPreview.last());
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void showOffers(OfferEngine.Result result) {
        boolean any = !result.isEmpty();
        offersFigure.setText(Columns.money(result.discount()));
        offersCaption.setVisible(any);
        offersFigure.setVisible(any);
        if (any) {
            StringBuilder lines = new StringBuilder();
            for (OfferEngine.OfferTotal total : result.totals()) {
                if (!lines.isEmpty()) {
                    lines.append('\n');
                }
                lines.append(total.offer().name()).append(": ").append(Columns.money(total.discount()));
            }
            Tooltip tip = new Tooltip(lines.toString());
            offersCaption.setTooltip(tip);
            offersFigure.setTooltip(tip);
        }
        showOfferHints(result.hints());
    }

    /**
     * The hint line goes straight under the lines table on both screens: into the quick screen's column, and
     * into a column made for it on the standard screen, whose table is the centre of its frame on its own.
     */
    private void placeOffersHint() {
        offersHint.getStyleClass().add("invoice-offer-hint");
        offersHint.setWrapText(true);
        offersHint.setMaxWidth(Double.MAX_VALUE);
        // Every hint, not the first cut short: the table beside it grows into all the height it is given, and a
        // label left its default least size is squeezed to one line - seen on the first picture of three hints.
        offersHint.setMinHeight(Region.USE_PREF_SIZE);
        offersHint.setVisible(false);
        offersHint.setManaged(false);
        javafx.scene.Parent parent = table.getParent();
        if (parent instanceof javafx.scene.layout.VBox column) {
            column.getChildren().add(column.getChildren().indexOf(table) + 1, offersHint);
        } else if (parent instanceof javafx.scene.layout.BorderPane frame && frame.getCenter() == table) {
            frame.setCenter(null);
            javafx.scene.layout.VBox column = new javafx.scene.layout.VBox(4, table, offersHint);
            javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
            frame.setCenter(column);
        }
    }

    private void showOfferHints(List<OfferEngine.Hint> hints) {
        List<String> sentences = List.of();
        if (!hints.isEmpty()) {
            try {
                sentences = InvoiceOfferHints.sentences(hints, this::hintItem);
            } catch (DaoException e) {
                logError(e);
            }
        }
        offersHint.setText(String.join("\n", sentences));
        offersHint.setVisible(!sentences.isEmpty());
        offersHint.setManaged(!sentences.isEmpty());
    }

    /** An item a hint names: off the invoice's own lines, else read once and kept. */
    private com.hamza.account.model.domain.ItemsModel hintItem(int itemId) throws DaoException {
        for (BasePurchasesAndSales line : editor.lines()) {
            if (line.getItems() != null && line.getItems().getId() == itemId) {
                return line.getItems();
            }
        }
        com.hamza.account.model.domain.ItemsModel item = hintItems.get(itemId);
        if (item == null) {
            item = itemsService.findItemById(itemId);
            if (item != null) {
                hintItems.put(itemId, item);
            }
        }
        return item;
    }

    /** Double-clicking the empty centre of the command bar minimizes this invoice window. */
    private void configureInvoiceTopBar() {
        invoiceTopBar.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getTarget() == invoiceTopBar) {
                Stage stage = (Stage) invoiceTopBar.getScene().getWindow();
                dragOffsetX = event.getScreenX() - stage.getX();
                dragOffsetY = event.getScreenY() - stage.getY();
            }
        });
        invoiceTopBar.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getTarget() == invoiceTopBar) {
                Stage stage = (Stage) invoiceTopBar.getScene().getWindow();
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
                event.consume();
            }
        });
        invoiceTopBar.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && (event.getTarget() == invoiceTopBar || event.getTarget() == invoiceTopBarSpacer)) {
                Stage stage = (Stage) invoiceTopBar.getScene().getWindow();
                stage.setIconified(true);
                event.consume();
            }
        });
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
        btnNew.setGraphic(AppIcon.ADD.graphic());
        btnSearch.setGraphic(AppIcon.SEARCH.graphic());
        btnSave.setGraphic(AppIcon.SAVE.graphic());
        btnPrintSave.setGraphic(AppIcon.PRINT.graphic());
        btnPinParty.setGraphic(AppIcon.PIN.graphic(14));
        btnPinDelegate.setGraphic(AppIcon.PIN.graphic(14));
        btnPinTreasury.setGraphic(AppIcon.PIN.graphic(14));
        btnPinStock.setGraphic(AppIcon.PIN.graphic(14));
    }

    private void getSavedCustomerAndDelegate() {
        applyPinnedDefaults();
        try {
            if (selectedPartyId() == 0) {
                selectPartyById(dataInterface.designInterface().showDataForCustomer()
                        ? defaultCustomerId() : 1);
            }
            if (dataInterface.designInterface().documentType().hasDelegate()
                    && selectedDelegateId() == 0) {
                comboDelegate.getSelectionModel().select(
                        SettingTabLanguageController.publishDelegate(employeeService));
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Pins are a workstation convenience, so they override this form only. The normal
     * customer setting remains the shop-wide fallback for a cash sale.
     */
    private void applyPinnedDefaults() {
        selectPartyById(pinnedValue(InvoicePinField.PARTY));
        selectPinnedDelegate();
        selectPinnedTreasury();
        selectPinnedStock();
        refreshPinButtons();
    }

    private void configurePinButtons() {
        btnPinParty.setOnAction(event -> pin(InvoicePinField.PARTY, selectedPartyId()));
        btnPinDelegate.setOnAction(event -> pin(InvoicePinField.DELEGATE, selectedDelegateId()));
        btnPinTreasury.setOnAction(event -> pin(InvoicePinField.TREASURY, selectedTreasuryId()));
        btnPinStock.setOnAction(event -> pin(InvoicePinField.STOCK, selectedStockId()));

        nameSearchField.chosenPartyProperty().addListener((observable, oldValue, newValue) -> refreshPinButtons());
        comboDelegate.valueProperty().addListener((observable, oldValue, newValue) -> refreshPinButtons());
        comboTreasury.valueProperty().addListener((observable, oldValue, newValue) -> refreshPinButtons());
        comboStock.valueProperty().addListener((observable, oldValue, newValue) -> refreshPinButtons());
        refreshPinButtons();
    }

    private void pin(InvoicePinField field, int value) {
        if (value <= 0 || !field.appliesTo(documentType())) {
            return;
        }
        setInvoicePinnedValue(documentType(), field, value);
        refreshPinButtons();
    }

    private void refreshPinButtons() {
        refreshPinButton(btnPinParty, InvoicePinField.PARTY, selectedPartyId());
        refreshPinButton(btnPinDelegate, InvoicePinField.DELEGATE, selectedDelegateId());
        refreshPinButton(btnPinTreasury, InvoicePinField.TREASURY, selectedTreasuryId());
        refreshPinButton(btnPinStock, InvoicePinField.STOCK, selectedStockId());
    }

    private void refreshPinButton(Button button, InvoicePinField field, int selectedId) {
        boolean available = field.appliesTo(documentType());
        boolean pinned = available && selectedId > 0 && selectedId == pinnedValue(field);
        button.setDisable(!available || selectedId <= 0);
        button.getStyleClass().remove("invoice-pin-active");
        if (pinned) {
            button.getStyleClass().add("invoice-pin-active");
        }
        button.setTooltip(new Tooltip(LanguageManager.getInstance().getString(
                pinned ? "invoice.pin.active" : "invoice.pin.set")));
    }

    private DocumentType documentType() {
        return dataInterface.designInterface().documentType();
    }

    private int pinnedValue(InvoicePinField field) {
        return field.appliesTo(documentType()) ? getInvoicePinnedValue(documentType(), field) : 0;
    }

    /**
     * The party on the form, or {@code null}.
     * <p>
     * The field holds it: {@code choose} sets {@code chosenParty} before {@code chosenName},
     * so a listener on the name sees the new party. Everything on this screen that wants the
     * party asks here - it used to read every customer in the database and find it by name,
     * twice per selection, which on a 147-party database measured 101 ms a read.
     * <p>
     * The name is compared because {@code textSearchName.set(name)} can put a name on the
     * field without choosing anybody, leaving a party behind that is no longer the one
     * written there.
     */
    private T3 selectedParty() {
        if (nameSearchField == null) {
            return null;
        }
        T3 chosen = nameSearchField.chosenPartyProperty().get();
        String shown = textSearchName == null ? null : textSearchName.get();
        if (chosen == null || shown == null || !shown.equals(chosen.getName())) {
            return null;
        }
        return chosen;
    }

    private int selectedPartyId() {
        T3 selected = selectedParty();
        return selected == null ? 0 : selected.getId();
    }

    private int selectedDelegateId() {
        String selected = comboDelegate == null ? null : comboDelegate.getValue();
        return selected == null ? 0 : delegateIds.getOrDefault(selected, 0);
    }

    private int selectedTreasuryId() {
        String selected = comboTreasury == null ? null : comboTreasury.getValue();
        return selected == null ? 0 : activeTreasuryIds.getOrDefault(selected, 0);
    }

    private int selectedStockId() {
        Stock selected = comboStock == null ? null : comboStock.getValue();
        return selected == null ? 0 : selected.getId();
    }

    private void selectPartyById(int partyId) {
        selectPartyById(partyId, null);
    }

    /**
     * Puts a party on the form by its id - one row, on the primary key.
     * <p>
     * It read {@code nameList()} and filtered in Java: every party in the database, each one
     * resolving its area and its price tier with a query of its own, to select one.
     * {@code AddNameController.selectData} had the same defect and was fixed this way; this
     * screen was missed, and it is the screen a cashier opens for every sale.
     *
     * @param fallbackName shown when the id resolves to nothing, so a saved document still
     *                     reopens carrying the name it was written with
     */
    private void selectPartyById(int partyId, String fallbackName) {
        if (nameSearchField == null) {
            return;
        }
        T3 party = null;
        if (partyId > 0) {
            try {
                party = nameAndAccountInterface.getNameById(partyId);
            } catch (Exception e) {
                logError(e);
            }
        }
        if (party != null) {
            nameSearchField.select(party);
        } else if (fallbackName != null && !fallbackName.isBlank()) {
            textSearchName.set(fallbackName);
        }
    }

    private void selectPinnedDelegate() {
        int delegateId = pinnedValue(InvoicePinField.DELEGATE);
        if (delegateId <= 0) {
            return;
        }
        try {
            var delegate = employeeService.delegateById(delegateId);
            if (delegate != null) {
                comboDelegate.getSelectionModel().select(delegate.getName());
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void selectPinnedTreasury() {
        int treasuryId = pinnedValue(InvoicePinField.TREASURY);
        if (treasuryId <= 0) {
            return;
        }
        activeTreasuryIds.entrySet().stream()
                .filter(entry -> entry.getValue() == treasuryId)
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(name -> comboTreasury.getSelectionModel().select(name));
    }

    private void selectPinnedStock() {
        int stockId = pinnedValue(InvoicePinField.STOCK);
        if (stockId <= 0) {
            return;
        }
        comboStock.getItems().stream()
                .filter(stock -> stock.getId() == stockId)
                .findFirst()
                .ifPresent(comboStock.getSelectionModel()::select);
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
        labelDate.setText(lang.getString("invoice.date"));
        labelTreasury.setText(lang.getString("invoice.treasury"));
        labelStock.setText(lang.getString("invoice.stock"));
        labelNotes.setText(lang.getString("invoice.notes"));

        // combo prompts - نصوص الاختيارات
        comboTreasury.setPromptText(lang.getString("invoice.treasury"));
        comboDelegate.setPromptText(lang.getString("invoice.delegate"));
        comboStock.setPromptText(lang.getString("invoice.stock"));

        // buttons - الأزرار
        btnNew.setText(lang.getString("invoice.btn.new"));
        btnSearch.setText(lang.getString("invoice.btn.search"));
        btnSave.setText(lang.getString("invoice.btn.save"));
        btnPrintSave.setText(lang.getString("invoice.btn.save.print"));
        btnQuickMode.setText(lang.getString(screenMode == InvoiceScreenMode.QUICK
                ? "invoice.screen.standard" : "invoice.screen.quick"));
    }

    /**
     * The switch to the other screen is offered only where that screen exists for this document
     * and this user: the quick screen serves new sales and purchases, and only to whoever holds
     * the document's quick-entry key, and a saved document is reopened on the standard screen
     * alone. A switch that would be refused is not a button - and it is disabled as well as
     * hidden, because F6 fires the button and {@code fire()} reads only whether it is disabled.
     */
    private void configureScreenMode() {
        boolean offered = num_invoice_update == 0 && screenMode.opposite().availableFor(documentType());
        btnQuickMode.setVisible(offered);
        btnQuickMode.setManaged(offered);
        btnQuickMode.setDisable(!offered);
        btnQuickMode.setOnAction(event -> switchScreenMode());
    }

    private void switchScreenMode() {
        try {
            if (num_invoice_update > 0) {
                throw new UserValidationException(LanguageManager.getInstance()
                        .getString("invoice.quick.switch.unsaved"));
            }
            if (hasInvoiceLines() && !AllAlerts.confirm_all(
                    LanguageManager.getInstance().getString("confirm"),
                    LanguageManager.getInstance().getString("invoice.confirm.new.invoice"))) {
                return;
            }
            // Remembered before the window opens, so the next new invoice - from the
            // dashboard as much as from here - starts in the screen just chosen.
            InvoiceScreenMode chosen = screenMode.opposite();
            chosen.rememberFor(documentType());
            new com.hamza.account.view.BuyApplication(dataInterface, 0, chosen)
                    .start(new Stage());
            ((Stage) btnQuickMode.getScene().getWindow()).close();
        } catch (Exception e) {
            logError(e);
        }
    }

    private void addTextSearchName() {
        try {
            // A party you have stopped dealing with may not be put on a new invoice.
            nameSearchField = new PartySuggestionField<>(dataInterface.nameAndAccountInterface()
                    .searchInterface(PartySearchScope.ACTIVE_ONLY));
        } catch (Exception e) {
            logError(e);
            return;
        }
        textSearchName = nameSearchField.chosenNameProperty();
        placePartyField(carriesTier() ? partyWithTier(nameSearchField) : nameSearchField);

        textSearchName.addListener((observableValue, s, string) -> {
            try {
                // No query at all: the field set chosenParty immediately before the name
                // this listener is answering. Both figures below came from reading the
                // whole party table, once each.
                T3 party = selectedParty();
                codeAccount = party == null ? 0 : party.getId();
                if (!restoringDocument) {
                    applyPartyCurrency(party);
                }
                focusItemEntry();
                if (!carriesTier()) {
                    priceTypeByNameId = t3NameData.priceId(party);
                    priceTierChanged(priceTypeByNameId);
                } else if (!restoringDocument) {
                    // The customer brings their tier, or tier 1 when theirs is switched off (V84); the
                    // lines already on the invoice follow it. A reopened document keeps its own.
                    applyTier(priceTiers.forCustomer(party == null ? PriceTiers.FIRST : t3NameData.priceId(party)),
                            false);
                }
            } catch (Exception e) {
                logError(e);
            }
        });
    }

    // ---- the price tier (V84, docs/pricing-and-offers-plan.md ق-س٢ and ق-س٣) -----------------

    /** Whether this document is priced from a tier at all: a sale and a sales return. */
    private boolean carriesTier() {
        return InvoicePriceTier.carriesTier(documentType());
    }

    /**
     * The party field with the tier box beside it. The box is shown to everybody - the cashier should
     * see that an invoice is at the wholesale price - and changed only by whoever holds
     * {@code sales.price.tier.change}; the save asks that permission itself.
     */
    private Node partyWithTier(Node partyField) {
        try {
            priceTiers = priceTierService.catalog();
        } catch (DaoException e) {
            logError(e);
        }
        comboPriceTier.setItems(FXCollections.observableArrayList(priceTiers.active()));
        priceTiers.find(PriceTiers.FIRST).ifPresent(comboPriceTier.getSelectionModel()::select);
        comboPriceTier.setDisable(!AuthorizationGuard.isGranted(AppPermissions.SALES_PRICE_TIER_CHANGE));
        comboPriceTier.setTooltip(new Tooltip(LanguageManager.getInstance().getString("invoice.tier.tooltip")));
        comboPriceTier.getStyleClass().add("invoice-price-tier");
        comboPriceTier.setMinWidth(Region.USE_PREF_SIZE);
        comboPriceTier.valueProperty().addListener((observable, before, now) -> {
            if (!settingTier && now != null && now.id() != priceTypeByNameId) {
                applyTier(now.id(), true);
            }
        });
        javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(6, partyField, comboPriceTier);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(partyField, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    /**
     * Prices the invoice at {@code tier}: the box shows it, the entry surface quotes it, and every line
     * still at its list price is restated at it - a line whose price was typed over is left, and the
     * note says how many.
     *
     * @param chosenOnScreen whether somebody chose it in the box, rather than the customer bringing it
     */
    private void applyTier(int tier, boolean chosenOnScreen) {
        priceTypeByNameId = tier;
        showTierInBox(tier);
        if (chosenOnScreen) {
            tierChosenOnScreen = true;
        }
        PriceTierRepricing.Result result = PriceTierRepricing.restate(
                editor.lines(), tier, invoiceBuy::getItemsPrice, documentPricing);
        if (result.repriced() > 0) {
            editor.refreshTotals();
        }
        table.refresh();
        priceTierChanged(tier);
        if (result.touchedAnything()) {
            var lm = LanguageManager.getInstance();
            showPricingNote(result.kept() == 0
                    ? lm.getString("invoice.tier.repriced", priceTiers.name(tier), result.repriced())
                    : lm.getString("invoice.tier.repriced.kept", priceTiers.name(tier), result.repriced(),
                            result.kept()));
        }
    }

    /** Selects {@code tier} in the box without it counting as a choice - adding it if it is switched off. */
    private void showTierInBox(int tier) {
        settingTier = true;
        try {
            if (priceTiers.find(tier).isPresent()
                    && comboPriceTier.getItems().stream().noneMatch(offered -> offered.id() == tier)) {
                comboPriceTier.setItems(FXCollections.observableArrayList(priceTiers.choicesIncluding(tier)));
            }
            comboPriceTier.getItems().stream().filter(offered -> offered.id() == tier).findFirst()
                    .ifPresent(comboPriceTier.getSelectionModel()::select);
        } finally {
            settingTier = false;
        }
    }

    /**
     * A reopened document at the tier it was saved at, with nothing repriced. One saved before V84 has
     * none: the box shows its customer's tier, and the save keeps it at none unless somebody chooses.
     */
    private void restorePriceTier(int number) {
        if (!carriesTier()) {
            return;
        }
        try {
            storedTier = InvoicePriceTier.jdbc().storedTier(documentType(), number);
        } catch (DaoException e) {
            logError(e);
            storedTier = null;
        }
        T3 party = selectedParty();
        int tier = storedTier != null ? storedTier
                : priceTiers.forCustomer(party == null ? PriceTiers.FIRST : t3NameData.priceId(party));
        priceTypeByNameId = tier;
        showTierInBox(tier);
        tierChosenOnScreen = false;
        priceTierChanged(tier);
    }

    /** The tier the save stores - none for a document from before V84 that nobody re-tiered. */
    private Integer tierForSave() {
        if (!carriesTier()) {
            return null;
        }
        if (num_invoice_update > 0 && storedTier == null && !tierChosenOnScreen) {
            return null;
        }
        return priceTypeByNameId;
    }

    /** The sentence a line priced at tier 1 in place of the invoice's tier is marked with, or null. */
    private String firstTierNote(BasePurchasesAndSales line) {
        if (!line.isFromFirstTier()) {
            return null;
        }
        return LanguageManager.getInstance().getString("invoice.line.first.tier",
                priceTiers.name(PriceTiers.FIRST), priceTiers.name(priceTypeByNameId));
    }

    /**
     * Says what pricing did without stopping anybody: the standard screen beside the badges, the
     * quick screen in its status line. A dialog would be dismissed unread by the next scan's Enter.
     */
    protected void showPricingNote(String text) {
        pricingNote.setText(text == null ? "" : text);
        pricingNote.setVisible(text != null && !text.isBlank());
    }


    /**
     * What an item is worth on this screen: the customer's tier, or the cost when buying.
     */
    protected double itemPriceForThisScreen(ItemsModel item) {
        try {
            return dataInterface.designInterface().showDataForCustomer()
                    ? invoiceBuy.getItemsPrice(item, priceTypeByNameId)
                    : item.getBuyPrice();
        } catch (Exception e) {
            return item.getBuyPrice();
        }
    }


    /**
     * mergeRepeated=false: two picked lines of the same item must stay distinct rows,
     * each keeping its own {@code sourceLineId}, rather than being folded into one row
     * that could only point at one of them.
     */

    private void configureReturnEntry() {
        returnEntry = new ReturnEntryCoordinator(
                dataInterface.designInterface().documentType(),
                new ReturnEntryCoordinator.Controls(
                        btnReturnFromInvoice, labelReturnedBadge,
                        name -> comboDelegate.getSelectionModel().select(name),
                        // By id: the coordinator reads the source invoice's party id, and
                        // this is the same one row every other party selection here takes.
                        this::selectPartyById,
                        new ReturnEntryCoordinator.HeaderDiscount() {
                            @Override
                            public double returnTotal() {
                                return editor.totals().netAmount().doubleValue();
                            }

                            @Override
                            public void show(double discount) {
                                txtOtherDiscount.setText(String.valueOf(discount));
                            }

                            @Override
                            public void lock(boolean locked) {
                                txtOtherDiscount.setEditable(!locked);
                            }
                        }),
                itemsService::findItemById,
                new ReturnEntryCoordinator.LineAppender() {
                    @Override
                    public BasePurchasesAndSales append(InvoiceLineDraft draft) throws DaoException {
                        return appendReturnLine(draft);
                    }

                    @Override
                    public boolean isEmpty() {
                        // realLines, not the rows: the quick screen's trailing entry
                        // row is never absent, so asking the table directly reported a
                        // table full of lines when nothing had been picked - and the
                        // "replace the source invoice?" confirmation appeared on an
                        // empty return.
                        return InvoiceLineTotals.realLines(table.getItems()).isEmpty();
                    }

                    @Override
                    public void clear() {
                        table.getItems().clear();
                    }
                },
                employeeService::delegateById,
                error -> AllAlerts.handleError(
                        LanguageManager.getInstance().getString("return.dialog.title"), error),
                DialogReturnReason::ask,
                new PartyStatementService()::currentBalance,
                () -> DialogReturnSourcePicker.show(
                        (number, party, limit) -> new JdbcReturnableRepository().searchSources(
                                designInterface.documentType().reverses(), number, party, limit),
                        this::logError));
        returnEntry.pricedBy(() -> documentPricing);
        returnEntry.configure();
        // A return that names an invoice gives back its share of that invoice's own
        // discount, and the share moves with the lines.
        editor.totalsProperty().addListener(
                (observable, before, now) -> returnEntry.totalsChanged());
    }

    /**
     * The party a cash sale lands on when nothing else is chosen.
     * <p>
     * The setting stores an id, and {@code publishCustomer} turned it into a name for a
     * screen that then had to find the party by that name. Falls back to party 1 exactly
     * as that method does.
     */
    private int defaultCustomerId() {
        try {
            int id = Integer.parseInt(getSettingSaveNameCustomer().trim());
            return id > 0 ? id : 1;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private BasePurchasesAndSales appendReturnLine(InvoiceLineDraft draft) throws DaoException {
        return invoiceLineService.add(
                table.getItems(), draft, false, getSelWithoutBalance()).line();
    }

    private void action() {
        btnNew.setOnAction(actionEvent -> {
            if (!hasInvoiceLines() || AllAlerts.confirm_all(
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
            SearchItemsApplication itemsApplication =
                    new SearchItemsApplication(dataInterface, priceTypeByNameId);
            itemsApplication.showAndWait(btnSearch.getScene().getWindow())
                    .ifPresent(this::addPickedItems);
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Resolves every catalog choice again in the invoice's warehouse, then sends it
     * through the same validation, expiry and merge path as barcode/name entry.
     */
    private void addPickedItems(List<ItemPickRequest> requests) {
        for (ItemPickRequest request : requests) {
            try {
                InvoiceLineDraft draft = invoiceItemPickerService.resolve(
                        request, invoiceStockId, priceTypeByNameId).orElse(null);
                if (draft == null) {
                    throw new UserValidationException(LanguageManager.getInstance().getString(
                            "search.items.error.unavailable", request.itemName()));
                }
                if (addLine(draft) == null) {
                    return;
                }
            } catch (Exception error) {
                logError(error);
                return;
            }
        }
    }

    protected int resolveSelectedPriceTier() throws Exception {
        String selectedName = textSearchName == null ? null : textSearchName.get();
        if (selectedName == null || selectedName.isBlank()) {
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("invoice.error.name.required"));
        }
        T3 party = selectedParty();
        if (party == null) {
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("invoice.error.name.not.found"));
        }
        // The invoice's tier, which the party chose when it was picked and the tier box may have
        // changed since (V84) - not the party's tier read again, which would undo that change.
        return priceTypeByNameId;
    }

    protected InvoiceItemSelectionService.ScaleBarcodeSettings scaleBarcodeSettings() {
        return new InvoiceItemSelectionService.ScaleBarcodeSettings(
                getSettingBarcodeScaleActive(), getSettingBarcodeStart(),
                getSettingBarcodeScaleCodeDigits(), ScaleBarcodeValueType.valueOf(getSettingBarcodeValueType()));
    }

    protected void handleItemEntryError(Exception error, boolean scaleBarcode) {
        if (scaleBarcode) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.scale.barcode.title"), error);
        } else {
            logError(error);
        }
    }



    /**
     * The one way a line reaches this invoice, from either screen and from the catalogue
     * picker: {@link InvoiceLineEntry}, where the validation, the expiry question, the
     * repeated-item merge and the low-stock warning are decided and tested.
     *
     * @return the row that was added or merged into, or null if the user cancelled the
     * expiry dialog - the only step that can decline without an error.
     */
    protected BasePurchasesAndSales addLine(InvoiceLineDraft draft) throws Exception {
        BasePurchasesAndSales added = lineEntry.add(editor.lines(), draft);
        // A line priced at tier 1 because the invoice's tier has no price for the item (V84, ق-س٣):
        // sold, not refused, and said - the gap is in the data, and the report of missing prices is
        // where it is mended.
        if (added != null && added.isFromFirstTier() && added.getItems() != null) {
            noteAddedLine(LanguageManager.getInstance().getString("invoice.line.first.tier.added",
                    added.getItems().getNameItem(), priceTiers.name(PriceTiers.FIRST),
                    priceTiers.name(priceTypeByNameId)));
        }
        return added;
    }

    /** Says something about the line just added - on this screen, as any pricing note is said. */
    protected void noteAddedLine(String text) {
        showPricingNote(text);
    }


    private void selectData() {
        restoringDocument = true;
        try {
            InvoiceHeaderView header = dataInterface.loadInvoiceHeader(num_invoice_update);
            invoiceStockId = header.stockId();
            selectStoredStock();
            BaseTotals dataById = header.totals();
            loadedUpdatedAt = dataById.getUpdated_at();
            int id = dataById.getId();
            InvoiceType invoiceType = dataById.getInvoiceType();
            String invoiceDate = dataById.getDate();

            date.setValue(LocalDate.parse(invoiceDate));
            selectPartyById(header.partyId(), header.partyName());
            comboDelegate.getSelectionModel().select(header.delegateName());
            selectStoredTreasury(dataById.getTreasuryModel());
            txtNum.setText(String.valueOf(id));
            codeAccount = header.partyId();
            List<? extends BasePurchasesAndSales> collection =
                    dataInterface.totalsAndPurchaseList().purchaseOrSalesList(id, id);
            editor.replaceLines(collection);
            invoiceLineService.captureOriginalLines(collection);
            invoiceExpiryService.captureOriginalLines(collection);
            restoreOffers(collection);
            radioCash.setSelected(invoiceType.equals(InvoiceType.CASH));
            radioDeffer.setSelected(invoiceType.equals(InvoiceType.DEFER));
            txtPaid.setText(String.valueOf(dataById.getPaid()));
            txtNotes.setText(dataById.getNotes());
            txtOtherDiscount.setText(String.valueOf(dataById.getDiscount()));
            // Before the return is linked back to its invoice: the invoice is shown in the currency this
            // document was written in.
            restoreDocumentCurrency(id, header.partyId(), collection, dataById.getTreasuryModel());
            // The tier it was priced at (V84), not the tier its customer is on today.
            restorePriceTier(id);
            // Before the guards can check an edit they have to know what this return
            // was linked to - without it ReturnGuard reads a source of 0 and treats the
            // whole document as a free return it has nothing to compare against.
            returnEntry.restoreSource(
                    header.sourceInvoiceNumber(), header.returnReason());
            returnEntry.showReturnedStatus(id);
        } catch (Exception e) {
            logError(e);
        } finally {
            restoringDocument = false;
        }
    }

    /**
     * Opens a saved document on the treasury it was saved on.
     * <p>
     * It used to stay on the default the screen starts with: the header was loaded, its date,
     * party, delegate and warehouse were put back, and its treasury was not. So opening an invoice
     * paid on a wallet and saving it again - to correct a note - moved its cash to the main drawer,
     * with nothing on screen saying so, and the two balances were wrong by the whole invoice.
     * <p>
     * A treasury since closed is not in the list of active ones, so it is added rather than left
     * unselected: an old document must not be re-filed under another treasury because its own was
     * retired. The same rule as {@code Add_AccountController.selectTreasury}.
     */
    private void selectStoredTreasury(Treasury stored) {
        if (stored == null || stored.getName() == null || stored.getName().isBlank()) {
            return;
        }
        if (!comboTreasury.getItems().contains(stored.getName())) {
            comboTreasury.getItems().add(stored.getName());
        }
        comboTreasury.getSelectionModel().select(stored.getName());
    }

    private void saveInvoice(boolean print) {
        if (editor.isSaving()) {
            return;
        }
        try {
            validateInvoiceForSave();

            // Why it has no invoice behind it, and whether settling it this way was meant.
            if (!returnEntry.confirmBeforeSave(
                    selectedInvoiceType() == InvoiceType.DEFER, codeAccount)) {
                return;
            }

            if (itemCatalogChangedWhileOpen && hasInvoiceLines()) {
                boolean continueWithEnteredValues = AllAlerts.confirm_all(
                        LanguageManager.getInstance().getString("invoice.catalog.changed.title"),
                        LanguageManager.getInstance().getString("invoice.catalog.changed.confirm"));
                if (!continueWithEnteredValues) {
                    return;
                }
                itemCatalogChangedWhileOpen = false;
            }

            String correctionReason = "";
            if (num_invoice_update > 0) {
                var reason = ShiftCorrectionReasonPrompt.forUpdate();
                if (reason.isEmpty()) return;
                correctionReason = reason.get();
            }

            boolean paymentTaken = paymentScreenApplies();
            if (paymentTaken) {
                if (!takePayment(print)) {
                    return;
                }
            } else if (!AllAlerts.confirmSave()) {
                return;
            }
            saveInBackground(print, paymentTaken, captureSaveCommand(correctionReason));
        } catch (InvoiceValidationException e) {
            focusValidationTarget(e.target());
            logError(e);
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * A new sale, on a computer whose settings ask for the payment screen. Not an invoice
     * being edited: it was paid for when it was made, and what is owed on it now is a matter
     * for the account, not for the drawer.
     */
    private boolean paymentScreenApplies() {
        return designInterface.showScreenPaidInInvoice()
                && getInvoiceShowScreenPaid()
                && num_invoice_update == 0;
    }

    /**
     * Shows the payment screen; false when the operator went back to the invoice, in which
     * case nothing is written. It stands in for "do you want to save?" - see
     * {@link InvoicePaymentDialog}.
     */
    private boolean takePayment(boolean print) throws InvoiceValidationException {
        updatePaymentViewModel(false);
        InvoicePaymentTerms terms = editor.requireValidPayment();
        Optional<InvoiceTender> tender = InvoicePaymentDialog.ask(
                table.getScene().getWindow(), terms, print);
        if (tender.isEmpty()) {
            return false;
        }
        if (terms.deferred()) {
            // What was handed over towards a deferred invoice is its advance payment - the
            // field it would otherwise have been typed into, so the save reads it from one
            // place whichever way it arrived. A cash invoice records its net whatever was
            // handed over: the change left the drawer again.
            txtPaid.setText(MoneyMath.text(tender.get().paid()));
        }
        return true;
    }

    /**
     * The rows that are actually being saved. The quick screen's trailing entry row is
     * a control rather than a line, so it is filtered out here - once, on the way to
     * the command - instead of being deleted from the table before validation and put
     * back if anything went wrong.
     */
    private List<BasePurchasesAndSales> linesForSave() {
        return InvoiceLineTotals.realLines(table.getItems());
    }

    private InvoiceSaveCommand captureSaveCommand(String correctionReason) throws InvoiceValidationException {
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
                List.copyOf(linesForSave()), invoiceStockId, correctionReason, loadedUpdatedAt,
                documentPricing.currencyId(), tierForSave());
    }

    private void saveInBackground(boolean print, boolean paymentTaken, InvoiceSaveCommand command) {
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
        task.setOnSucceeded(event -> afterSuccessfulSave(print, paymentTaken, command, task.getValue()));
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

    private void afterSuccessfulSave(boolean print, boolean paymentTaken, InvoiceSaveCommand command,
                                     InvoiceSaveResult result) {
        // After the payment screen the cashier has already confirmed this sale and watched
        // the change worked out; a "saved" alert on top is one more Enter per customer. The
        // screen clearing for the next sale is the answer. Without it, the alert stays.
        if (!paymentTaken) {
            AllAlerts.alertSave();
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
            invoiceLineService.validateForSave(linesForSave(), getSelWithoutBalance());
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
            case ACCOUNT -> nameSearchField::requestFocus;
            case PAYMENT_TYPE -> radioCash::requestFocus;
            case DISCOUNT -> txtOtherDiscount::requestFocus;
            case PAID -> txtPaid::requestFocus;
        };
        Platform.runLater(requestFocus);
    }

    private void handlePostSave() {
        // Both halves: what the shop asked for, and whether this machine is the one that
        // should do it. A till whose database lives on another computer takes no backup
        // after a sale - see BackupPolicy for what three cashiers doing it at once costs.
        boolean backup = getInvoiceBackupAfterSave() && BackupPolicy.mayBackupAfterEachInvoice();
        invoicePostSaveService.afterSave(backup);
    }

    private InvoicePrintRequest preparePrintRequest(boolean print,
                                                    InvoiceSaveCommand command,
                                                    InvoiceSaveResult result) {
        if (!print) {
            return null;
        }
        String printedAt = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        try {
            return invoicePrintService.prepare(command.lines(), printedAt,
                    getPrintPaperReceiptInvoice(),
                    lines -> ShowInvoiceDetails.printDocument(
                            dataInterface.loadInvoiceHeader(result.invoiceNumber()),
                            designInterface.documentType(), lines, printedAt));
        } catch (DaoException e) {
            logError(e);
            return null;
        }
    }

    /**
     * The receipt goes to the thermal printer behind the masker pane. The upright page does not:
     * it may ask where to save the file, a dialog that throws on any thread but this one, and it
     * already writes the file in the background once that is answered.
     */
    private void printInvoice(InvoicePrintRequest request) {
        if (request == null) {
            return;
        }
        if (request.receipt()) {
            maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("invoice.masker.printing"),
                    () -> invoicePrintService.print(request));
        } else {
            invoicePrintService.print(request);
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
        setTextFormatter(txtPaid, txtOtherDiscount);
        txtNum.setText(num_invoice_update > 0 ? String.valueOf(num_invoice_update) : lang.getString("invoice.number.generate"));
        // delegate data
        comboStock.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Stock stock) {
                return stock == null ? "" : stock.getName();
            }

            @Override
            public Stock fromString(String value) {
                return null;
            }
        });
        reloadStockItems();
        comboStock.getSelectionModel().selectedItemProperty().addListener((obs, oldStock, newStock) -> {
            if (newStock != null) invoiceStockId = newStock.getId();
        });
        // Counted lines, not rows: the quick screen always carries a trailing entry
        // row, which used to leave this combo disabled from the moment the screen opened.
        comboStock.disableProperty().bind(Bindings.createBooleanBinding(
                () -> editor.totals().lineCount() > 0, editor.totalsProperty()));

        // delegate data
        reloadDelegateItems();
        // treasury data
        reloadTreasuryItems();

        try {
            comboTreasury.getSelectionModel().select(treasuryService.getTreasuryById(DefaultTreasury.ID).getName());
        } catch (DaoException e) {
            logError(e);
        }
        // for name and account

        Platform.runLater(this::focusItemEntry);

    }

    @NotNull
    private List<Stock> getStocks() {
        try {
            return stockService.stocksForPicker(StockScope.ACTIVE_ONLY);
        } catch (DaoException e) {
            logError(e);
            return List.of();
        }
    }

    /**
     * (Re)reads the warehouse list, keeping the current selection - {@code invoiceStockId},
     * which {@link #selectData()} already set from a loaded invoice's own row. A
     * warehouse created after this controller was built is otherwise never offered
     * here: {@code ItemsButtons} constructs one purchase/sales controller per session,
     * well before the screen is opened, so its combo is whatever existed at login.
     */
    private void reloadStockItems() {
        comboStock.setItems(FXCollections.observableArrayList(getStocks()));
        selectStoredStock();
    }

    /**
     * Selects the warehouse a reopened document was saved in - adding it to the combo when it has
     * been switched off since (V77), exactly as {@link #selectStoredTreasury} does for a closed
     * treasury. The combo offers warehouses in use only, and a selection by id among them found
     * nothing: the document would have been re-saved into whatever the combo fell back to.
     */
    private void selectStoredStock() {
        if (comboStock == null || invoiceStockId <= 0) {
            return;
        }
        Stock stored = comboStock.getItems().stream()
                .filter(stock -> stock.getId() == invoiceStockId).findFirst().orElse(null);
        if (stored == null) {
            try {
                stored = stockService.stock(invoiceStockId);
            } catch (DaoException e) {
                logError(e);
            }
            if (stored == null) {
                return;
            }
            comboStock.getItems().add(stored);
        }
        comboStock.getSelectionModel().select(stored);
    }

    private void reloadTreasuryItems() {
        try {
            // A treasury in the base takes any document; one in a foreign currency only a document typed
            // in it (V83, docs/currency-plan.md §15 ق-د٦) - so a dollar invoice is offered the dollar drawer.
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (Treasury treasury : treasuryService.getActiveTreasuriesTaking(documentPricing.currencyId())) {
                ids.put(treasury.getName(), treasury.getId());
            }
            String selected = comboTreasury.getValue();
            activeTreasuryIds = Map.copyOf(ids);
            comboTreasury.setItems(FXCollections.observableArrayList(ids.keySet()));
            if (selected != null && ids.containsKey(selected)) {
                comboTreasury.getSelectionModel().select(selected);
            }
        } catch (DaoException e) {
            activeTreasuryIds = Map.of();
            comboTreasury.setItems(FXCollections.observableArrayList());
            logError(e);
        }
    }

    private void reset_all() {
        loadedUpdatedAt = null;
        itemCatalogChangedWhileOpen = false;
        table.getItems().clear();
        txtNum.setText(LanguageManager.getInstance().getString("invoice.number.generate"));
        txtOtherDiscount.setText("0.0");
        txtPaid.setText("0.0");
        txtNotes.clear();
        radioCash.setSelected(true);
        radioDeffer.setSelected(false);
        returnEntry.reset();
        applyPinnedDefaults();
        applyPartyCurrency(selectedParty());
        // The next invoice starts at its customer's tier, whatever the last one was changed to.
        storedTier = null;
        tierChosenOnScreen = false;
        showPricingNote(null);
        if (carriesTier()) {
            T3 party = selectedParty();
            applyTier(priceTiers.forCustomer(party == null ? PriceTiers.FIRST : t3NameData.priceId(party)), false);
        }
        resetItemEntry();
    }

    private void publisherData() {
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(EmployeesChanged.class,
                    event -> reloadDelegateItems()));
            // A warehouse created after this controller was built is otherwise never
            // offered in comboStock - see reloadStockItems.
            subscriptions.add(eventBus.subscribe(StocksChanged.class, event -> reloadStockItems()));
            subscriptions.add(eventBus.subscribe(ItemsChanged.class,
                    event -> itemCatalogChangedWhileOpen = true));
            subscriptions.add(eventBus.subscribe(ItemSaved.class,
                    event -> itemCatalogChangedWhileOpen = true));
        }
        // An invoice window is opened per invoice and closed again; the bus behind
        // it lives for the whole process.
        subscriptions.disposeWith(stackPane);
    }

    private void reloadDelegateItems() {
        try {
            Map<String, Integer> ids = new LinkedHashMap<>();
            employeeService.delegates(EmployeeScope.ACTIVE_ONLY)
                    .forEach(delegate -> ids.put(delegate.getName(), delegate.getId()));
            delegateIds = Map.copyOf(ids);
            comboDelegate.setItems(FXCollections.observableArrayList(ids.keySet()));
        } catch (DaoException e) {
            delegateIds = Map.of();
            comboDelegate.setItems(FXCollections.observableArrayList());
            logError(e);
        }
    }

    private void totalSetting() {
        // Enter walks the footer the way it is filled: the discount, what was paid, then the
        // save. On a cash invoice the paid box is disabled - it is the net - so Enter in the
        // discount goes straight to the save rather than to a box that cannot take focus.
        whenEnterPressed(txtPaid, btnSave);
        txtOtherDiscount.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                (txtPaid.isDisabled() ? btnSave : txtPaid).requestFocus();
            }
        });
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
            // Read-only answers, written as money; txtPaid is typed into, so it keeps the
            // plain form its number formatter reads back.
            txtRestAfterDiscount.setText(Columns.money(terms.netAmount()));
            if (terms.invoiceType() == InvoiceType.CASH || resetDeferredPayment) {
                txtPaid.setText(MoneyMath.text(terms.paidAmount()));
            }
            txtRestAfterPaid.setText(Columns.money(terms.remainingAmount()));
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
        // Written the way the columns they sum are written. Display only: nothing may read
        // a figure back out of these - ask editor.totals().
        txtSumQuantity.textProperty().bind(Bindings.createStringBinding(
                () -> Columns.quantity(BigDecimal.valueOf(editor.totals().quantity())),
                editor.totalsProperty()));
        txtBeforeDiscount.textProperty().bind(Bindings.createStringBinding(
                () -> Columns.money(editor.totals().grossAmount()), editor.totalsProperty()));
        txtSumDiscount.textProperty().bind(Bindings.createStringBinding(
                () -> Columns.money(editor.totals().discountAmount()), editor.totalsProperty()));
        txtSumTotals.textProperty().bind(Bindings.createStringBinding(
                () -> Columns.money(editor.totals().netAmount()), editor.totalsProperty()));
    }

    private void tableSetting() {
        DocumentType documentType = designInterface.documentType();
        InvoiceItemCatalogService catalogService = new InvoiceItemCatalogService(
                documentType, itemsService, invoiceBuy::updateItemPrice);
        lineEditService = new InvoiceLineEditService(
                documentType, catalogService, () -> invoiceStockId,
                sourceLineId -> returnEntry.sourceLineTerms(sourceLineId))
                .pricedBy(() -> documentPricing)
                .undercutAllowedWhen(() -> AuthorizationGuard.isGranted(AppPermissions.SALES_PRICE_BELOW_LIST));
        // The column menu on the lines table is the administrator's alone, which is how it has always
        // been - through CurrentUser.get().getId() == 1 written out here. It asks CurrentUser now, so
        // the one place that knows what "the administrator" means is UserSessionContext.
        new InvoiceTableCoordinator<>(table, editor.lines(), lineEditService,
                () -> priceTypeByNameId, () -> getInvoiceUpdatePrice(),
                editor::refreshTotals, getClass(), CurrentUser.isSystemAdministrator(),
                AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE),
                invoiceItemSelectionService::selectUnit)
                .notePrices(this::firstTierNote)
                .configure();
    }


    /**
     * Whether anything has been entered - the quick screen's entry row does not count.
     */
    protected boolean hasInvoiceLines() {
        return editor.totals().lineCount() > 0;
    }


    private void disableData() {
        comboDelegate.setVisible(designInterface.showDataForCustomer());
        boxDelegate.setVisible(designInterface.showDataForCustomer());
        boxDelegate.setManaged(designInterface.showDataForCustomer());

        // From the totals, not from the footer's text: that is written for a person to read,
        // and "1,050.00" is not something Double.parseDouble reads.
        BooleanBinding nonPositiveTotal = Bindings.createBooleanBinding(
                () -> editor.totals().netAmount().signum() <= 0, editor.totalsProperty());
        BooleanBinding noLines = Bindings.createBooleanBinding(
                () -> editor.totals().lineCount() == 0, editor.totalsProperty());
        BooleanBinding binding = nonPositiveTotal
                .or(noLines)
                .or(editor.invalidLinesProperty())
                .or(comboTreasury.valueProperty().isNull());

        BooleanBinding invalidPayment = Bindings.createBooleanBinding(
                this::paymentDraftInvalid,
                editor.totalsProperty(), txtOtherDiscount.textProperty(),
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
    }



    /**
     * Opens the item screen. For a new item the text already typed is carried over -
     * the barcode that was scanned and not found, or the name that was searched for -
     * so the operator does not type it a second time.
     */
    protected void addItem(int num, String typedText) {
        try {
            new AddItemApplication(num, num == 0 ? typedText : null).start(new Stage());
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

    protected void logError(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.operation.title"), e);
    }

    /** Builds the surface lines are entered on: the form above the table, or the table itself. */
    // ---- the document's currency (V83, docs/currency-plan.md §15) -------------------------------

    private static InvoiceScreenCurrency.Catalogue currencyCatalogue() {
        com.hamza.account.features.currency.CurrencyService service =
                ServiceRegistry.get(com.hamza.account.features.currency.CurrencyService.class);
        return service == null ? InvoiceScreenCurrency.Catalogue.NONE : InvoiceScreenCurrency.Catalogue.of(service);
    }

    /** The badge beside the title, placed after the returned-invoice badge in whichever bar holds it. */
    private void installCurrencyBadge() {
        currencyBadge.getStyleClass().add("neutral-button");
        currencyBadge.setWrapText(false);
        currencyBadge.managedProperty().bind(currencyBadge.visibleProperty());
        currencyBadge.setVisible(false);
        // Beside it, what pricing last did (V84): a tier change and how many lines it left, or a line
        // priced at tier 1 - said without stopping anybody.
        pricingNote.getStyleClass().add("warning-button");
        pricingNote.setWrapText(false);
        pricingNote.managedProperty().bind(pricingNote.visibleProperty());
        pricingNote.setVisible(false);
        if (labelReturnedBadge != null && labelReturnedBadge.getParent() instanceof Pane bar) {
            int at = bar.getChildren().indexOf(labelReturnedBadge);
            bar.getChildren().add(at < 0 ? bar.getChildren().size() : at + 1, currencyBadge);
            bar.getChildren().add(bar.getChildren().indexOf(currencyBadge) + 1, pricingNote);
        }
        date.valueProperty().addListener((observable, before, now) -> {
            if (!restoringDocument && documentPricing.foreign() && now != null) {
                changePricing(pricingOf(documentPricing.currency().id(), now));
            }
        });
        showCurrency();
    }

    /**
     * The party chosen: a party in a currency a document can be typed in prices this screen in it, at the
     * day's rate; anyone else in the base. Lines already on the screen are restated (§15 ق-د٧).
     */
    private void applyPartyCurrency(T3 party) {
        Integer currencyId = party == null ? null : party.getCurrency_id();
        try {
            translatedCurrency = screenCurrency.translatedCurrency(currencyId);
            changePricing(screenCurrency.forParty(currencyId, date.getValue() == null ? LocalDate.now() : date.getValue()));
        } catch (DaoException e) {
            logError(e);
        }
    }

    private DocumentPricing pricingOf(int currencyId, LocalDate day) {
        try {
            return screenCurrency.forParty(currencyId, day);
        } catch (DaoException e) {
            logError(e);
            return documentPricing;
        }
    }

    private void changePricing(DocumentPricing next) {
        DocumentPricing previous = documentPricing;
        documentPricing = next == null ? DocumentPricing.BASE : next;
        if (!java.util.Objects.equals(previous.currencyId(), documentPricing.currencyId())) {
            InvoiceScreenCurrency.restate(editor.lines(), previous, documentPricing);
            editor.refreshTotals();
            table.refresh();
            reloadTreasuryItems();
            if (comboTreasury.getValue() == null) {
                selectDefaultTreasury();
            }
            priceTierChanged(priceTypeByNameId);
        }
        showCurrency();
    }

    /**
     * A saved document put back in the currency it was written in: its lines and its discount and cash as
     * they were typed, at the rate it is stored at - never the base converted back.
     */
    private void restoreDocumentCurrency(int number, int partyId, List<? extends BasePurchasesAndSales> lines,
                                         Treasury storedTreasury) throws DaoException {
        var written = com.hamza.account.features.party.currency.PartyCurrencies.jdbc()
                .foreignHeader(documentType(), number);
        T3 party = selectedParty();
        translatedCurrency = screenCurrency.translatedCurrency(party == null ? null : party.getCurrency_id());
        if (written != null && written.written()) {
            documentPricing = screenCurrency.forStored(written.currencyId(), written.rate());
            InvoiceScreenCurrency.showTyped(lines);
            editor.refreshTotals();
            table.refresh();
            txtOtherDiscount.setText(MoneyMath.text(written.discount()));
            txtPaid.setText(MoneyMath.text(written.paid()));
        } else {
            documentPricing = DocumentPricing.BASE;
            if (written != null && translatedCurrency == null && party != null && party.getCurrency_id() != null) {
                // A V82 translation of a party whose documents are now typed in its currency: it keeps the
                // way it was written, and the badge says so.
                translatedCurrency = screenCurrency.forStored(party.getCurrency_id(), written.rate()).currency();
            }
        }
        reloadTreasuryItems();
        selectStoredTreasury(storedTreasury);
        showCurrency();
    }

    private void selectDefaultTreasury() {
        try {
            String name = treasuryService.getTreasuryById(DefaultTreasury.ID).getName();
            if (activeTreasuryIds.containsKey(name)) {
                comboTreasury.getSelectionModel().select(name);
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    /** The badge's sentence: the currency and the rate, no rate, a translation - or nothing for the base. */
    private void showCurrency() {
        var lm = LanguageManager.getInstance();
        String text = null;
        if (documentPricing.foreign()) {
            text = documentPricing.hasRate()
                    ? lm.getString("invoice.currency.badge", documentPricing.currency().name(),
                            com.hamza.account.features.currency.CurrencyFormat.rate(documentPricing.rate()))
                    : lm.getString("invoice.currency.badge.no.rate", documentPricing.currency().name());
        } else if (translatedCurrency != null) {
            text = lm.getString("invoice.currency.badge.translated", translatedCurrency.name());
        }
        currencyBadge.setText(text == null ? "" : text);
        currencyBadge.setVisible(text != null);
    }

    protected abstract void configureItemEntrySurface();

    /** Puts the party search field where this screen's layout has room for it. */
    protected abstract void placePartyField(Node partyField);

    /** Where the caret goes when the screen is ready for the next item. */
    protected abstract void focusItemEntry();

    /** Clears the entry surface after a save or a new invoice. */
    protected abstract void resetItemEntry();

    /** Told when the party's price tier changes, so an entry surface quoting prices can follow. */
    protected void priceTierChanged(int priceTier) {
    }

    /** F4: open the item the operator is on, or create one. */
    public abstract void openCurrentItem();
}
