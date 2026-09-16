package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.NamesTables;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.users.ShiftCorrectionReasonPrompt;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.document.TotalsPage;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.document.TotalsSummaryRow;
import com.hamza.account.features.backup.BackupKind;
import com.hamza.account.features.documentdelete.DocumentDeletionResult;
import com.hamza.account.features.documentdelete.DocumentDeletionService;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.totals.PageJump;
import com.hamza.account.features.totals.SavedTotalsFilters;
import com.hamza.account.features.totals.TotalsDeletionPreview;
import com.hamza.account.features.totals.TotalsFilterInput;
import com.hamza.account.features.totals.TotalsSelection;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.period.PeriodLockService;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.view.BuyApplication;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.TextFormat;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;


/**
 * The list of one document family: search, paging, the row actions, deleting, and the figures
 * under the table.
 * <p>
 * It used to inherit its collaborators - {@code TotalsService extends LoadOtherData extends
 * LoadData} - which handed it eleven protected fields, four of which it never read, and made the
 * list of what the screen depends on something to be worked out rather than read. They arrive
 * through the constructor now. What it prints is {@link TotalsReports}; its named filters are
 * {@link TotalsSavedFiltersPanel}.
 */
@Log4j2
@FxmlPath(pathFile = "invoice/totals.fxml")
public class TotalsController<T3 extends BaseNames, T4 extends BaseAccount> implements Initializable {

    private static final int PAGE_SIZE = 50;
    private static final String VIEW_MODE = "columnView";
    private static final String SELECTION_COLUMN = "totals-selection";
    private static final String ACTIONS_COLUMN = "totals-actions";

    private final DataInterface<?, ?, T3, T4> dataInterface;
    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final NameAndAccountInterface<T3, T4> nameAndAccountInterface;
    private final TotalDesignInterface totalDesignInterface;
    private final TotalsAndPurchaseList<?, ?> totalsAndPurchaseList;
    private final TotalsDataInterface totalsDataInterface;
    private final EmployeeService employeeService;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final PeriodLockService periodLockService = ServiceRegistry.get(PeriodLockService.class);
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final Preferences preferences = Preferences.userNodeForPackage(TotalsController.class);
    private final SavedTotalsFilters savedFilters;
    private final ObservableList<BaseTotals> observableList = FXCollections.observableArrayList();
    private final String dateFromKey;
    private final String dateToKey;
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));
    private final ContentSizedColumns<BaseTotals> columnSizing = new ContentSizedColumns<>();
    private final Map<ComboBox<String>, FilterableCombo> filterableCombos = new IdentityHashMap<>();
    /** Added to every row loaded, so ticking or unticking one updates the line beside the delete button. */
    private final InvalidationListener selectionChanged = observable -> updateSelectionSummary();

    private boolean mayEditOutsideCurrentMonth = true;
    private boolean syncingFilters;
    private long searchRequest;
    private int currentPage;
    private int pageCount = 1;
    private MaskerPaneSetting maskerPaneSetting;
    private TotalsReports reports;
    private TotalsSavedFiltersPanel savedFiltersPanel;
    /**
     * The search's own task - the only one a newer search may cancel.
     * <p>
     * It used to cancel whatever the masker pane was running last, which is shared with the
     * delete, the reports and the print. An {@code InvoiceSaved} from another window, or the
     * debounce firing, cancelled a delete mid-way: no refresh, no message, and no way for the
     * operator to know whether the invoices were gone.
     */
    private Task<Void> searchTask;
    /** True while a delete runs. A search asked for meanwhile waits for it, rather than racing it. */
    private boolean deleting;
    private boolean searchAfterDelete;

    @FXML
    private TableView<BaseTotals> tableView;
    @FXML
    private TextField textSearch;
    @FXML
    private TextField textInvoiceNumber;
    @FXML
    private TextField textMinTotal;
    @FXML
    private TextField textMaxTotal;
    @FXML
    private TextField textPage;
    @FXML
    private ComboBox<String> comboName, comboDelegate, comboEnteredBy, comboSavedFilters;
    @FXML
    private Label labelScreenTitle, labelResults, labelFiltered, labelDateRange, labelDelegate,
            searchIcon, labelPage, labelSelection;
    @FXML
    private Text textSumTableSize, textSumTotals, textSumDiscount, textSumAfterDiscount, textCash, textDeffer, textProfit;
    @FXML
    private Button btnSearch, btnRefresh, btnClearFilters, btnDeleteSelected,
            btnSaveFilter, btnDeleteSavedFilter, btnPreviousPage, btnNextPage;
    @FXML
    private ToggleButton btnSelected, btnFilters, radioCash, radioDeffer, radioAll;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private StackPane stackPane;
    @FXML
    private VBox filterPane;
    @FXML
    private HBox pagerBar;
    @FXML
    private MenuButton menuButton;
    @FXML
    private MenuButton btnView;
    @FXML
    private MenuItem menuItemReportByParty, menuItemReportByDay, menuItemReportByMonth,
            menuItemReportByDelegate, menuItemReportByItem;
    @FXML
    private MenuItem menuItemPrintTotals, menuItemPrintDetailed;

    public TotalsController(DataInterface<?, ?, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher, EmployeeService employeeService) throws Exception {
        this.dataInterface = dataInterface;
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.nameAndAccountInterface = dataInterface.nameAndAccountInterface();
        this.totalDesignInterface = dataInterface.totalDesignInterface();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.totalsDataInterface = totalDesignInterface.totalsDataInterface();
        this.employeeService = employeeService;
        // Each of the four document types keeps its own remembered range - one screen's
        // date does not leak into another's.
        String prefix = dataInterface.getClass().getSimpleName();
        this.dateFromKey = prefix + ".search.dateFrom";
        this.dateToKey = prefix + ".search.dateTo";
        this.savedFilters = new SavedTotalsFilters(preferences.node(prefix).node("savedFilters"));
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        reports = new TotalsReports(dataInterface, maskerPaneSetting, new ReportsHost());
        applyTotalsIdentity();
        getTable();
        otherSetting();
        configureSearchExperience();
        action();
        showSummary(TotalsSummaryRow.EMPTY, true);
        updateSelectionSummary();
        addDataToComboName();
        savedFiltersPanel = new TotalsSavedFiltersPanel(comboSavedFilters, btnSaveFilter, btnDeleteSavedFilter,
                savedFilters, new SavedFiltersHost());
        savedFiltersPanel.install();
        subscribeToChanges();
        restrictToNumbers();
        permissionButtons();
        buttonGraphic();
        search(false);
    }

    private void subscribeToChanges() {
        if (eventBus != null) {
            // Both sides arrive here; this screen shows one of them.
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> {
                if (event.side() == dataInterface.invoiceSide()) btnRefresh.fire();
            }));
            subscriptions.add(eventBus.subscribe(EmployeesChanged.class
                    , event -> fillPicker(comboDelegate, getDelegateNames())));
            subscriptions.add(eventBus.subscribe(NameChanged.class, event -> {
                if (event.kind() == nameAndAccountInterface.partyKind()) addDataToComboName();
            }));
        }
        subscriptions.disposeWith(stackPane);
    }

    /** Gives each of the four totals lists a persistent, immediately recognizable identity. */
    private void applyTotalsIdentity() {
        stackPane.getStyleClass().removeAll(
                "totals-sales", "totals-sales-return", "totals-purchase", "totals-purchase-return");
        var type = dataInterface.designInterface().documentType();
        String identityClass = switch (type) {
            case SALES -> "totals-sales";
            case SALES_RETURN -> "totals-sales-return";
            case PURCHASE -> "totals-purchase";
            case PURCHASE_RETURN -> "totals-purchase-return";
        };
        stackPane.getStyleClass().add(identityClass);
        tableView.setId("totals-" + type.name().toLowerCase());
        labelScreenTitle.setText(type.totalText());
    }

    private void buttonGraphic() {
        searchIcon.setGraphic(AppIcon.SEARCH.graphic(18));
        btnSearch.setGraphic(AppIcon.SEARCH.graphic(16));
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic(16));
        btnClearFilters.setGraphic(AppIcon.CLEAR.graphic(16));
        btnSaveFilter.setGraphic(AppIcon.SAVE.graphic(16));
        btnDeleteSavedFilter.setGraphic(AppIcon.DELETE.graphic(16));
        btnDeleteSelected.setGraphic(AppIcon.DELETE.graphic(16));
        btnFilters.setGraphic(AppIcon.FILTER.graphic(16));
        btnSelected.setGraphic(AppIcon.SELECT_ALL.graphic(16));
        btnView.setGraphic(AppIcon.SETTINGS.graphic(16));
        btnView.setContentDisplay(ContentDisplay.RIGHT);
        menuButton.setGraphic(AppIcon.PRINT.graphic(16));

        tip(btnSearch, "invoice.tooltip.search");
        tip(btnRefresh, "invoice.tooltip.refresh");
        tip(btnClearFilters, "invoice.tooltip.clear.filters");
        tip(btnSaveFilter, "invoice.search.saved.save.tooltip");
        tip(btnDeleteSavedFilter, "invoice.search.saved.delete.tooltip");
        tip(btnFilters, "invoice.tooltip.filters");
        tip(btnSelected, "invoice.tooltip.select");
        tip(btnDeleteSelected, "invoice.tooltip.delete.selected");
        tip(menuButton, "invoice.tooltip.print");
    }

    private void permissionButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnDeleteSelected::setDisable,
                dataInterface.designInterface().delete());

        var granted = permissionDisableService.getABoolean(AppPermissions.UPDATE_DATA_BEFORE_MONTH);
        if (granted != null)
            mayEditOutsideCurrentMonth = granted;
    }

    /** The items-screen search rhythm: debounced typing and an advanced panel on demand. */
    private void configureSearchExperience() {
        searchDelay.setOnFinished(event -> search(false));
        textSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!syncingFilters) searchDelay.playFromStart();
        });
        textSearch.setOnAction(event -> search(true));

        boolean showDelegate = dataInterface.designInterface().showDataForCustomer();
        if (showDelegate) {
            whenEnterPressed(dateFrom, dateTo, comboName, comboDelegate, comboEnteredBy,
                    textInvoiceNumber, textMinTotal, textMaxTotal, btnSearch);
        } else {
            whenEnterPressed(dateFrom, dateTo, comboName, comboEnteredBy,
                    textInvoiceNumber, textMinTotal, textMaxTotal, btnSearch);
        }
        setFilterPanelVisible(false);
        keepOnePaymentSegmentSelected();
    }

    /**
     * The payment type is a segmented control, not three independent switches.
     * A {@code ToggleButton} in a group toggles itself off when clicked while
     * selected - which a {@code RadioButton} refuses - so clicking the active
     * segment would leave all three unselected and the control showing no answer.
     */
    private void keepOnePaymentSegmentSelected() {
        ToggleGroup paymentGroup = radioAll.getToggleGroup();
        paymentGroup.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null && previous != null) paymentGroup.selectToggle(previous);
        });
    }

    /**
     * Numbers only - an invoice number is a positive integer, a total is signed decimal.
     */
    private void restrictToNumbers() {
        textInvoiceNumber.setTextFormatter(TextFormat.createNumericTextFormatter());
        textMinTotal.setTextFormatter(new TextFormatter<>(TextFormat.TEXT_FORMATTER_FILTER));
        textMaxTotal.setTextFormatter(new TextFormatter<>(TextFormat.TEXT_FORMATTER_FILTER));
        // Digits only, and the same digits PageJump reads - not the general numeric filter,
        // which allows a leading '+' the parser refuses and rejects the Arabic-Indic digits
        // it accepts. A box you can type into that quietly does nothing is worse than one
        // that will not take the character.
        textPage.setTextFormatter(new TextFormatter<>(change ->
                PageJump.isTypablePageText(change.getControlNewText()) ? change : null));
    }

    /**
     * The columns every totals screen shares, reflected off {@link BaseTotals}
     * until this migrated off {@code TableColumnAnnotation} - see rule ق-ل1.
     */
    private List<TableColumn<BaseTotals, ?>> baseTotalsColumns() {
        return List.of(
                named("totals-code", Columns.number(NamesTables.CODE, BaseTotals::getId)),
                named("totals-date", Columns.text(NamesTables.DATE, BaseTotals::getDate)),
                // Money, written as Columns.money writes it - the same way the summary under
                // the table writes the totals of these very columns.
                named("totals-total", Columns.asMoney(Columns.number(NamesTables.TOTAL, BaseTotals::getTotal))),
                named("totals-discount", Columns.asMoney(Columns.number(NamesTables.DISCOUNT, BaseTotals::getDiscount))),
                named("totals-after-discount", Columns.asMoney(Columns.number(NamesTables.TOTAL_AMOUNT, BaseTotals::getTotal_after_discount))),
                named("totals-paid", Columns.asMoney(Columns.number(NamesTables.CREDITOR, BaseTotals::getPaid))),
                named("totals-rest", Columns.asMoney(Columns.number(NamesTables.REST, BaseTotals::getRest))),
                named("totals-item-count", Columns.asQuantity(Columns.number("invoice.column.item.count", BaseTotals::getItemCount))),
                named("totals-notes", Columns.text(NamesTables.NOTES, BaseTotals::getNotes))
        );
    }

    private static <V> TableColumn<BaseTotals, V> named(String id, TableColumn<BaseTotals, V> column) {
        column.setId(id);
        return column;
    }

    private void getTable() {
        tableView.getColumns().addAll(baseTotalsColumns());
        tableView.getColumns().addAll(totalDesignInterface.columns());
        totalDesignInterface.getTable(tableView);
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        ColumnSetting.addSelectedColumn(tableView);

        SortedList<BaseTotals> sortedList = new SortedList<>(observableList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        tableView.setPlaceholder(new Label(LanguageManager.getInstance().getString("invoice.search.empty")));
        assignDocumentColumnIds();
        tableView.getColumns().addFirst(rowActionsColumn());
        tableView.getColumns().add(named("totals-entered-by", Columns.text("users",
                row -> row.getUsers() == null ? "" : row.getUsers().getUsername())));
        tableView.getColumns().add(named("totals-entry-time", Columns.dateTime("column.entry.time",
                BaseTotals::getCreated_at)));
        tableView.getColumns().get(1).setId(SELECTION_COLUMN);

        TableSetting.tableMenuSetting(getClass(), tableView);
        tableView.setTableMenuButtonVisible(false);
        configureColumnViews();
        columnSizing.install(tableView);

        tableView.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(BaseTotals item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("totals-negative-row");
                if (!empty && item != null && item.getTotal() <= 0.0) {
                    getStyleClass().add("totals-negative-row");
                }
            }
        });
    }

    /** The document family adds its party/type columns itself; give those stable preferences ids. */
    private void assignDocumentColumnIds() {
        int documentColumn = 0;
        for (TableColumn<BaseTotals, ?> column : tableView.getColumns()) {
            if (column.getId() == null || column.getId().isBlank()) {
                column.setId("totals-document-" + documentColumn++);
            }
        }
    }

    /** Reuses the customer-list view model while isolating every document family's choice. */
    private void configureColumnViews() {
        String documentType = dataInterface.designInterface().documentType().name().toLowerCase();
        Preferences viewPreferences = preferences.node("columnViews").node(documentType);
        new TableColumnViews<BaseTotals>(viewPreferences, VIEW_MODE, TableColumnViews.Preset.COMPACT,
                Set.of("totals-code", "totals-date", "totals-document-0", "totals-total", "totals-rest"),
                Set.of(SELECTION_COLUMN, ACTIONS_COLUMN))
                .install(btnView, tableView);
    }

    private void otherSetting() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setValue(loadDate(dateFromKey, DateSetting.firstDateInMonth));
        dateTo.setValue(loadDate(dateToKey, LocalDate.now()));

        fillPicker(comboDelegate, getDelegateNames());
        boolean showDelegate = dataInterface.designInterface().showDataForCustomer();
        comboDelegate.setVisible(showDelegate);
        comboDelegate.setManaged(showDelegate);
        labelDelegate.setVisible(showDelegate);
        labelDelegate.setManaged(showDelegate);

        fillPicker(comboEnteredBy, getUsernames());
    }

    /**
     * The date last searched with, so reopening the screen does not reset it to today.
     */
    private LocalDate loadDate(String key, LocalDate fallback) {
        String stored = preferences.get(key, null);
        if (stored == null) return fallback;
        try {
            return LocalDate.parse(stored);
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<String> getDelegateNames() {
        try {
            return employeeService.delegateNames(EmployeeScope.ACTIVE_ONLY);
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private List<String> getUsernames() {
        try {
            return usersService.getUsersNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Fills a picker with "all" plus the names, through the typable wrapper so the whole
     * set it filters against is replaced with what it shows.
     */
    private void fillPicker(ComboBox<String> comboBox, List<String> items) {
        List<String> withAll = new ArrayList<>();
        withAll.add(LanguageManager.getInstance().getString("all"));
        withAll.addAll(items);
        filterableFor(comboBox).setEntries(withAll);
    }

    /** One wrapper per picker, created the first time that picker is filled. */
    private FilterableCombo filterableFor(ComboBox<String> comboBox) {
        return filterableCombos.computeIfAbsent(comboBox, FilterableCombo::install);
    }

    private void action() {
        menuItemPrintTotals.setOnAction(actionEvent -> reports.printListing());
        menuItemPrintDetailed.setOnAction(actionEvent -> reports.printDetailed());
        menuItemReportByParty.setOnAction(actionEvent -> reports.exportReport(DocumentTableSpec.Report.BY_PARTY));
        menuItemReportByDay.setOnAction(actionEvent -> reports.exportReport(DocumentTableSpec.Report.BY_DAY));
        menuItemReportByMonth.setOnAction(actionEvent -> reports.exportReport(DocumentTableSpec.Report.BY_MONTH));
        menuItemReportByDelegate.setOnAction(actionEvent -> reports.exportReport(DocumentTableSpec.Report.BY_DELEGATE));
        menuItemReportByItem.setOnAction(actionEvent -> reports.exportReport(DocumentTableSpec.Report.BY_ITEM));
        menuItemReportByDelegate.setVisible(dataInterface.designInterface().showDataForCustomer());
        btnRefresh.setOnAction(actionEvent -> search(false));
        btnSearch.setOnAction(actionEvent -> search(true));
        btnClearFilters.setOnAction(actionEvent -> clearFilters());
        btnFilters.setOnAction(actionEvent -> setFilterPanelVisible(btnFilters.isSelected()));
        btnPreviousPage.setOnAction(actionEvent -> {
            if (currentPage > 0) {
                currentPage--;
                loadPage(false);
            }
        });
        btnNextPage.setOnAction(actionEvent -> {
            currentPage++;
            loadPage(false);
        });
        // Enter jumps; leaving the field puts back the page actually shown, so a number
        // typed and abandoned never sits there claiming to be where the table is.
        textPage.setOnAction(actionEvent -> jumpToTypedPage());
        textPage.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) textPage.setText(String.valueOf(currentPage + 1));
        });
        btnDeleteSelected.setOnAction(actionEvent -> {
            List<BaseTotals> ticked = tickedRows();
            if (ticked.isEmpty()) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.dialog.delete.title"),
                        new UserValidationException(LanguageManager.getInstance().getString("msg.select.row")));
                return;
            }
            deleteDocuments(ticked, true);
        });
        // The keyboard and the mouse act on the focused row, the way the row's own buttons
        // do - there is no toolbar left holding "the selected one" for them to defer to.
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                BaseTotals selected = tableView.getSelectionModel().getSelectedItem();
                if (selected != null) runGuarded(() -> showInvoiceData(selected));
            }
        });
        tableView.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.DELETE)) {
                BaseTotals focused = tableView.getSelectionModel().getSelectedItem();
                if (focused != null) deleteDocuments(List.of(focused), false);
            }
            if (event.getCode().equals(KeyCode.C) && event.isControlDown()) {
                copyInvoiceDetailsToClipboard();
            }
        });

        btnSelected.selectedProperty().addListener((observableValue, wasSelected, selected) -> {
            List.copyOf(observableList).forEach(row -> row.setSelectedRow(selected));
            btnSelected.setText(LanguageManager.getInstance().getString(
                    selected ? "invoice.select.page.cancel" : "invoice.select.page"));
        });
    }

    /** The ticked rows of the page on screen, in the order the table shows them. */
    private List<BaseTotals> tickedRows() {
        return tableView.getItems().stream().filter(BaseTotals::isSelectedRow).toList();
    }

    /** "3 ticked - 12,450.00" beside the delete button, or nothing when none is. */
    private void updateSelectionSummary() {
        TotalsSelection selection = TotalsSelection.of(
                tickedRows().stream().map(row -> MoneyMath.decimal(row.getTotal())).toList());
        labelSelection.setVisible(!selection.isEmpty());
        labelSelection.setManaged(!selection.isEmpty());
        labelSelection.setText(LanguageManager.getInstance().getString("invoice.selection.summary",
                selection.count(), Columns.money(selection.total())));
    }

    /**
     * Deletes one document or many, on the one path.
     * <p>
     * A row's own delete button is the same operation on a list of one, and it must ask the same
     * confirmation, take the same backup and record the same shift-correction reason - so it is
     * one method rather than a second copy free to drift from this one.
     *
     * @param fromTickedRows true for the toolbar's delete of the ticked rows, which is the only one
     *                       whose confirmation has to say the ticks stop at this page
     */
    private void deleteDocuments(List<? extends BaseTotals> documents, boolean fromTickedRows) {
        if (deleting || documents.isEmpty()) return;
        var type = dataInterface.designInterface().documentType();
        TotalsDeletionPreview preview = TotalsDeletionPreview.of(documents.stream()
                .map(row -> new TotalsDeletionPreview.Document(row.getId(), row.getDate(),
                        totalsDataInterface.getNameData(row), row.getItemCount(), MoneyMath.decimal(row.getTotal())))
                .toList(), fromTickedRows, pageCount);
        if (!DeleteDocumentsDialog.confirm(window(), type.label(), preview)) return;

        final Optional<String> correctionReason;
        try {
            correctionReason = ShiftCorrectionReasonPrompt.forDelete();
        } catch (DaoException e) {
            exceptionHandle(e);
            return;
        }
        if (correctionReason.isEmpty()) return;

        List<Integer> ids = preview.documents().stream().map(TotalsDeletionPreview.Document::id).toList();
        // The backup is the service's to take, after its checks: a delete the rules refuse
        // costs no dump.
        var deletion = DocumentDeletionService.jdbc(daoFactory,
                () -> SaveDatabaseFile.save(BackupKind.BEFORE_DELETE, false));
        AtomicReference<DocumentDeletionResult> result = new AtomicReference<>();

        deleting = true;
        searchDelay.stop();
        maskerPaneSetting.showMaskerPane(
                LanguageManager.getInstance().getString("invoice.dialog.delete.title"),
                () -> result.set(deletion.delete(type, ids, correctionReason.get())));
        Task<Void> task = maskerPaneSetting.getVoidTask();
        task.setOnSucceeded(event -> {
            deleting = false;
            searchAfterDelete = false;
            // One refresh. This screen listens for the event it publishes, so refreshing here
            // as well ran the search twice and cancelled the first.
            if (eventBus != null) eventBus.publish(new InvoiceSaved(dataInterface.invoiceSide()));
            else search(false);
            AllAlerts.alertDeleteWithMessage(deletionMessage(result.get()));
        });
        // A failure is already reported by the masker pane; what is left is the search it held back.
        task.setOnFailed(event -> finishFailedDelete());
        task.setOnCancelled(event -> finishFailedDelete());
    }

    private void finishFailedDelete() {
        deleting = false;
        if (searchAfterDelete) {
            searchAfterDelete = false;
            search(false);
        }
    }

    /** What was actually deleted - which is not what was asked for when another till got there first. */
    private static String deletionMessage(DocumentDeletionResult result) {
        LanguageManager language = LanguageManager.getInstance();
        if (result.nothingDeleted()) {
            return language.getString("invoice.delete.done.none");
        }
        if (result.allDeleted()) {
            return language.getString("invoice.delete.done.count", result.deleted());
        }
        return language.getString("invoice.delete.done.partial", result.deleted(), result.requested());
    }

    /**
     * Open, edit and delete, on the row they act on - through {@link RowActionsColumn}, the one
     * way a table here gets buttons that act on their own row.
     * <p>
     * It was a private copy of that column. The difference that shows is deliberate: an action
     * this user may not take is left out rather than shown disabled, as on every other list, and
     * the service behind it still refuses on its own.
     */
    private TableColumn<BaseTotals, Void> rowActionsColumn() {
        var design = dataInterface.designInterface();
        List<RowAction<BaseTotals>> actions = RowAction.permitted(List.of(
                RowAction.<BaseTotals>of("invoice.tooltip.show", AppIcon.SHOW, "app-primary-button", null,
                        row -> onRow(row, () -> showInvoiceData(row))),
                RowAction.<BaseTotals>of("invoice.tooltip.update", AppIcon.EDIT, "app-neutral-button", design.update(),
                        row -> onRow(row, () -> update(row))),
                RowAction.<BaseTotals>of("invoice.tooltip.delete", AppIcon.DELETE, "app-danger-button", design.delete(),
                        row -> onRow(row, () -> deleteDocuments(List.of(row), false)))));
        TableColumn<BaseTotals, Void> column = RowActionsColumn.of("invoice.column.actions", actions);
        column.setId(ACTIONS_COLUMN);
        column.getStyleClass().add("totals-actions-column");
        return column;
    }

    /** The row the button is on is the row it acts on; selecting it keeps the keyboard pointing there too. */
    private void onRow(BaseTotals row, GuardedAction action) {
        tableView.getSelectionModel().select(row);
        runGuarded(action);
    }

    private void runGuarded(GuardedAction action) {
        try {
            action.run();
        } catch (Exception e) {
            exceptionHandle(e);
        }
    }

    @FunctionalInterface
    private interface GuardedAction {
        void run() throws Exception;
    }

    private void copyInvoiceDetailsToClipboard() {
        BaseTotals selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            var s = dataInterface.designInterface().nameTextOfInvoice();
            String content = String.format(LanguageManager.getInstance().getString("invoice.clipboard.format"),
                    s,
                    selectedItem.getId(),
                    totalsDataInterface.getNameData(selectedItem),
                    selectedItem.getTotal());
            final ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        }
    }

    /**
     * Every load of this screen's data goes through here now - the initial load and an
     * explicit search are the same query, the only difference being how many of the
     * criteria fields are filled in. There is no client-side filtering left to fall back
     * on: what the table shows is exactly what the database returned for the current
     * criteria.
     */
    private void search(boolean focusResults) {
        currentPage = 0;
        loadPage(focusResults);
    }

    /**
     * Reads one page and the totals of the whole result. The rows arrive already ordered
     * newest-first from SQL and are not re-sorted here: with a page, an ordering applied
     * afterwards would only order the fifty rows that happened to arrive.
     */
    private void loadPage(boolean focusResults) {
        searchDelay.stop();
        if (deleting) {
            searchAfterDelete = true;
            return;
        }
        long request = ++searchRequest;
        Optional<TotalsSearchCriteria> current = criteriaOrShowProblem();
        if (current.isEmpty()) return;
        TotalsSearchCriteria criteria = current.get();
        rememberDate(dateFromKey, criteria.dateFrom());
        rememberDate(dateToKey, criteria.dateTo());
        if (searchTask != null && searchTask.isRunning()) searchTask.cancel();

        int page = currentPage;
        AtomicReference<TotalsPage<? extends BaseTotals>> result =
                new AtomicReference<>(TotalsPage.empty(PAGE_SIZE));
        maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("invoice.masker.loading"), () -> {
            result.set(totalsAndPurchaseList.searchTotals(criteria, page, PAGE_SIZE));
        });
        searchTask = maskerPaneSetting.getVoidTask();
        searchTask.setOnSucceeded(event -> {
            if (request != searchRequest) return;
            TotalsPage<? extends BaseTotals> loaded = result.get();
            // New rows arrive unticked, so a "select the page" left on would claim a selection
            // nothing on this page has.
            btnSelected.setSelected(false);
            observableList.setAll(loaded.rows());
            loaded.rows().forEach(row -> row.selectedRowProperty().addListener(selectionChanged));
            updateSelectionSummary();
            columnSizing.layout(tableView);
            updatePager(loaded);
            updateFilterPresentation(criteria, loaded.totalRows());
            if (focusResults && !loaded.rows().isEmpty()) {
                tableView.getSelectionModel().selectFirst();
                tableView.requestFocus();
            }
            loadSummary(criteria, request);
        });
    }

    /**
     * The money, asked for after the rows are already on screen.
     * <p>
     * Summing a result means reading every line of every matching document: measured at
     * 3.2 seconds for an unfiltered history of 101,000 invoices, where the page itself
     * took 0.14. Asked for together, every search would cost the slower of the two; asked
     * for after, the table is usable immediately and the bar fills in behind it.
     * <p>
     * It runs on its own thread rather than through the masker pane, which belongs to the
     * search: putting the overlay back up over rows the operator can already read would
     * undo the point of splitting them. A superseded request is dropped by the same
     * sequence number the page uses.
     */
    private void loadSummary(TotalsSearchCriteria criteria, long request) {
        showSummary(TotalsSummaryRow.EMPTY, false);
        Task<TotalsSummaryRow> task = new Task<>() {
            @Override
            protected TotalsSummaryRow call() throws Exception {
                return totalsAndPurchaseList.summarizeTotals(criteria);
            }
        };
        task.setOnSucceeded(event -> {
            if (request == searchRequest) showSummary(task.getValue(), true);
        });
        AllAlerts.handleTaskFailure(
                LanguageManager.getInstance().getString("invoice.masker.loading"), task);
        Thread thread = new Thread(task, "totals-summary");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Goes where the operator typed. Out of range lands on the nearest real page rather
     * than refusing - see {@link PageJump}, which owns the arithmetic.
     */
    private void jumpToTypedPage() {
        PageJump.targetPage(textPage.getText(), pageCount).ifPresentOrElse(page -> {
            if (page != currentPage) {
                currentPage = page;
                loadPage(false);
            } else {
                textPage.setText(String.valueOf(currentPage + 1));
            }
        }, () -> textPage.setText(String.valueOf(currentPage + 1)));
    }

    /** A date that is now absent must not be remembered, or clearing it would not stick. */
    private void rememberDate(String key, LocalDate value) {
        if (value == null) preferences.remove(key);
        else preferences.put(key, value.toString());
    }

    private void updatePager(TotalsPage<? extends BaseTotals> loaded) {
        LanguageManager language = LanguageManager.getInstance();
        pageCount = loaded.pageCount();
        textPage.setText(String.valueOf(loaded.page() + 1));
        labelPage.setText(language.getString("invoice.search.page.of.total", loaded.pageCount()));
        btnPreviousPage.setDisable(!loaded.hasPrevious());
        btnNextPage.setDisable(!loaded.hasNext());
        boolean paged = loaded.pageCount() > 1;
        pagerBar.setVisible(paged);
        pagerBar.setManaged(paged);

        // Clicking a header sorts what the table holds, which is one page. On a result
        // that fits in one page that is the whole answer and is worth keeping; across
        // pages it would order fifty rows and silently claim to have ordered the search.
        // So the headers stop sorting exactly when they would start lying.
        if (paged) {
            tableView.getSortOrder().clear();
            tableView.getColumns().forEach(column -> column.setSortable(false));
        } else {
            tableView.getColumns().forEach(column -> column.setSortable(true));
        }
    }

    /**
     * The criteria on screen, or empty after telling the operator which field is wrong. Four
     * callers used to repeat this try-and-report, each free to report it differently.
     */
    private Optional<TotalsSearchCriteria> criteriaOrShowProblem() {
        try {
            return Optional.of(buildCriteria());
        } catch (TotalsFilterInput.InvalidFilterException e) {
            setFilterPanelVisible(true);
            exceptionHandle(new UserValidationException(filterErrorText(e.problem())));
            return Optional.empty();
        }
    }

    private TotalsSearchCriteria buildCriteria() throws TotalsFilterInput.InvalidFilterException {
        String partyName = selectedOrNull(comboName);
        String delegateName = dataInterface.designInterface().showDataForCustomer() ? selectedOrNull(comboDelegate) : null;
        String enteredBy = selectedOrNull(comboEnteredBy);
        InvoiceType invoiceType = radioCash.isSelected()
                ? InvoiceType.CASH
                : (radioDeffer.isSelected() ? InvoiceType.DEFER : null);

        return new TotalsFilterInput(
                dateFrom.getValue(),
                dateTo.getValue(),
                textInvoiceNumber.getText(),
                partyName,
                delegateName,
                invoiceType,
                enteredBy,
                textMinTotal.getText(),
                textMaxTotal.getText(),
                textSearch.getText()).toCriteria();
    }

    /**
     * The "all" entry is always first - a combo left on it means this field is not filtering.
     */
    private String selectedOrNull(ComboBox<String> comboBox) {
        int index = comboBox.getSelectionModel().getSelectedIndex();
        if (index <= 0) return null;
        return comboBox.getSelectionModel().getSelectedItem();
    }

    private String filterErrorText(TotalsFilterInput.Problem problem) {
        LanguageManager language = LanguageManager.getInstance();
        return switch (problem) {
            case DATE_RANGE -> language.getString("invoice.search.error.date.range");
            case INVOICE_NUMBER -> language.getString("invoice.search.error.invoice.number");
            case MIN_TOTAL -> language.getString("invoice.search.error.min.total");
            case MAX_TOTAL -> language.getString("invoice.search.error.max.total");
            case TOTAL_RANGE -> language.getString("invoice.search.error.total.range");
        };
    }

    private void setFilterPanelVisible(boolean visible) {
        filterPane.setVisible(visible);
        filterPane.setManaged(visible);
        btnFilters.setSelected(visible);
    }

    private void clearFilters() {
        syncingFilters = true;
        try {
            textSearch.clear();
            textInvoiceNumber.clear();
            textMinTotal.clear();
            textMaxTotal.clear();
            // Cleared means cleared: leaving a month in place would make "clear
            // everything" the one filter that cannot be cleared.
            dateFrom.setValue(null);
            dateTo.setValue(null);
            comboName.getSelectionModel().selectFirst();
            comboDelegate.getSelectionModel().selectFirst();
            comboEnteredBy.getSelectionModel().selectFirst();
            savedFiltersPanel.clearSelection();
            radioAll.setSelected(true);
        } finally {
            syncingFilters = false;
        }
        searchDelay.stop();
        search(false);
    }

    private void applySavedFilter(TotalsSearchCriteria saved) {
        // Keep the visible free-text lookup, just as the items screen does. A named
        // filter is a standing question; the text is what the operator seeks inside it.
        syncingFilters = true;
        try {
            dateFrom.setValue(saved.dateFrom());
            dateTo.setValue(saved.dateTo());
            textInvoiceNumber.setText(saved.invoiceNumber() == null ? "" : saved.invoiceNumber().toString());
            textMinTotal.setText(decimalText(saved.minTotal()));
            textMaxTotal.setText(decimalText(saved.maxTotal()));
            selectExistingOrAll(comboName, saved.partyName());
            selectExistingOrAll(comboDelegate, saved.delegateName());
            selectExistingOrAll(comboEnteredBy, saved.enteredByUsername());
            if (saved.invoiceType() == InvoiceType.CASH) {
                radioCash.setSelected(true);
            } else if (saved.invoiceType() == InvoiceType.DEFER) {
                radioDeffer.setSelected(true);
            } else {
                radioAll.setSelected(true);
            }
        } finally {
            syncingFilters = false;
        }
        setFilterPanelVisible(true);
        search(false);
    }

    /** Says what period is being shown, including when one or both ends are open. */
    private static String dateRangeText(TotalsSearchCriteria criteria) {
        LanguageManager language = LanguageManager.getInstance();
        LocalDate from = criteria.dateFrom();
        LocalDate to = criteria.dateTo();
        if (from == null && to == null) return language.getString("invoice.search.date.all");
        if (from == null) return language.getString("invoice.search.date.until", to);
        if (to == null) return language.getString("invoice.search.date.since", from);
        return language.getString("invoice.search.date.range", from, to);
    }

    private static String decimalText(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static void selectExistingOrAll(ComboBox<String> combo, String value) {
        if (value != null && combo.getItems().contains(value)) {
            combo.getSelectionModel().select(value);
        } else {
            combo.getSelectionModel().selectFirst();
        }
    }

    private void updateFilterPresentation(TotalsSearchCriteria criteria, int resultCount) {
        LanguageManager language = LanguageManager.getInstance();
        labelResults.setText(language.getString("invoice.search.results.count", resultCount));
        labelDateRange.setText(dateRangeText(criteria));

        int hiddenFilters = TotalsFilterInput.hiddenConditionCount(criteria);
        boolean filtered = hiddenFilters > 0;
        labelFiltered.setManaged(filtered);
        labelFiltered.setVisible(filtered);
        labelFiltered.setText(language.getString("invoice.search.filtered", hiddenFilters));
        btnFilters.setText(filtered
                ? language.getString("invoice.search.filters.count", hiddenFilters)
                : language.getString("invoice.search.filters"));
    }

    private void addDataToComboName() {
        List<String> list = List.of();
        try {
            list = nameAndAccountInterface.nameList()
                    .stream()
                    .map(BaseNames::getName)
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.error("Failed to load the party names for the totals filter", e);
        }
        fillPicker(comboName, list);
    }

    private void update(BaseTotals row) throws Exception {
        // The accounting lock decides this now, not the calendar. What was here refused
        // any invoice outside the current month: on the first of the month yesterday's
        // invoice was locked whether or not anything had been reported, everything
        // inside the current month stayed editable however much had been, and the rule
        // was invisible - it could not be seen or set by anyone. It also guarded only
        // the button that opens an invoice, leaving the delete beside it unchecked.
        //
        // The per-user restriction to the current month is still honoured: some shops rely
        // on it, and it is now the narrower of the two rather than the only one.
        LocalDate invoiceDate = LocalDate.parse(row.getDate());
        periodLockService.requireOpen(invoiceDate, dataInterface.designInterface().nameTextOfInvoice());

        if (!mayEditOutsideCurrentMonth) {
            LocalDate currentDate = LocalDate.now();
            if (invoiceDate.getYear() != currentDate.getYear()
                    || invoiceDate.getMonth() != currentDate.getMonth()) {
                throw new BusinessRuleException(LanguageManager.getInstance().getString("invoice.error.outside.current.month"));
            }
        }
        new BuyApplication(dataInterface, row.getId()).start(new Stage());
    }

    private void showInvoiceData(BaseTotals row) throws Exception {
        new ShowInvoiceApplication(dataPublisher, dataInterface, daoFactory, row.getId(),
                totalsDataInterface.getNameData(row));
    }

    /**
     * The bar under the table answers for the whole search, not for the page. It used to
     * sum {@code tableView.getItems()}, which was the same thing while a search loaded
     * every matching row; now that it loads fifty, summing them would report the page as
     * if it were the result.
     *
     * @param settled false while the figures are still being read, so a stale total is
     *                never presented as the answer to the search now on screen
     */
    private void showSummary(TotalsSummaryRow summary, boolean settled) {
        String pending = LanguageManager.getInstance().getString("invoice.search.summary.pending");
        textSumTableSize.setText(settled ? Columns.quantity(BigDecimal.valueOf(summary.count())) : pending);
        textSumTotals.setText(settled ? Columns.money(summary.total()) : pending);
        textSumDiscount.setText(settled ? Columns.money(summary.discount()) : pending);
        textSumAfterDiscount.setText(settled ? Columns.money(summary.afterDiscount()) : pending);
        textCash.setText(settled ? Columns.money(summary.paid()) : pending);
        textDeffer.setText(settled ? Columns.money(summary.remaining()) : pending);
        textProfit.setText(settled ? Columns.money(summary.profit()) : pending);
    }

    private Window window() {
        return stackPane.getScene() == null ? null : stackPane.getScene().getWindow();
    }

    private static void tip(Control control, String key) {
        control.setTooltip(new Tooltip(LanguageManager.getInstance().getString(key)));
    }

    private void exceptionHandle(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.action.title"), e);
    }

    /** The screen as the reports see it. */
    private final class ReportsHost implements TotalsReports.Host {
        @Override public Optional<TotalsSearchCriteria> currentCriteria() { return criteriaOrShowProblem(); }
        @Override public List<BaseTotals> tickedRows() { return TotalsController.this.tickedRows(); }
        @Override public String screenTitle() { return labelScreenTitle.getText(); }
        @Override public Window window() { return TotalsController.this.window(); }
        @Override public LocalDate dateFrom() { return dateFrom.getValue(); }
        @Override public LocalDate dateTo() { return dateTo.getValue(); }
    }

    /** The screen as the named-filter picker sees it. */
    private final class SavedFiltersHost implements TotalsSavedFiltersPanel.Host {
        @Override public Optional<TotalsSearchCriteria> currentCriteria() { return criteriaOrShowProblem(); }
        @Override public void apply(TotalsSearchCriteria saved) { applySavedFilter(saved); }
    }
}
