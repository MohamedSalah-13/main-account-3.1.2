package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.NamesTables;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsPage;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.document.TotalsSummaryRow;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.totals.TotalsFilterInput;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.totals.PageJump;
import com.hamza.account.features.totals.SavedTotalsFilters;
import com.hamza.account.features.totals.TotalsDocumentRow;
import com.hamza.account.features.totals.TotalsFilterDescription;
import com.hamza.account.features.totals.TotalsReportLayout;
import com.hamza.account.features.totals.TotalsReportService;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.period.PeriodLockService;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TotalsService;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.view.BuyApplication;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.TextFormat;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import com.itextpdf.kernel.geom.PageSize;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;


@Log4j2
@FxmlPath(pathFile = "invoice/totals.fxml")
public class TotalsController<T3 extends BaseNames, T4 extends BaseAccount>
        extends TotalsService<T3, T4> implements Initializable {

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final PeriodLockService periodLockService = ServiceRegistry.get(PeriodLockService.class);
    private final EmployeeService employeeService;
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final Preferences preferences = Preferences.userNodeForPackage(TotalsController.class);
    private final SavedTotalsFilters savedFilters;
    private final ObservableList<BaseTotals> observableList;
    private final String dateFromKey;
    private final String dateToKey;
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));
    private static final int PAGE_SIZE = 50;
    private static final String VIEW_MODE = "columnView";
    private static final String SELECTION_COLUMN = "totals-selection";
    private static final String ACTIONS_COLUMN = "totals-actions";
    private final ContentSizedColumns<BaseTotals> columnSizing = new ContentSizedColumns<>();
    private boolean update_data = true;
    private boolean syncingFilters;
    private long searchRequest;
    private final Map<ComboBox<String>, FilterableCombo> filterableCombos = new IdentityHashMap<>();
    private final TotalsReportService reportService = new TotalsReportService();
    private int currentPage;
    private int pageCount = 1;
    private MaskerPaneSetting maskerPaneSetting;
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
            searchIcon, labelPage;
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
            , DataPublisher dataPublisher, EmployeeService employeeService
            ) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.employeeService = employeeService;
        this.observableList = FXCollections.observableArrayList();
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
        applyTotalsIdentity();
        getTable();
        otherSetting();
        configureSearchExperience();
        action();
        showSummary(TotalsSummaryRow.EMPTY, true);
        addDataToComboName();
        configureSavedFilters();
        // publisher data
        // Both sides arrive here; this screen shows one of them.
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> {
                if (event.side() == dataInterface.invoiceSide()) btnRefresh.fire();
            }));
        }
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(EmployeesChanged.class
                    , event -> comboDelegateSetting(comboDelegate, getDelegateNames())));
        }
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(NameChanged.class, event -> {
                if (event.kind() == nameAndAccountInterface.partyKind()) addDataToComboName();
            }));
        }
        subscriptions.disposeWith(stackPane);
        restrictToNumbers();
        permissionButtons();
        buttonGraphic();
        search(false);
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

        var aBoolean = permissionDisableService.getABoolean(AppPermissions.UPDATE_DATA_BEFORE_MONTH);
        if (aBoolean != null)
            update_data = aBoolean;
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
                named("totals-total", Columns.number(NamesTables.TOTAL, BaseTotals::getTotal)),
                named("totals-discount", Columns.number(NamesTables.DISCOUNT, BaseTotals::getDiscount)),
                named("totals-after-discount", Columns.number(NamesTables.TOTAL_AMOUNT, BaseTotals::getTotal_after_discount)),
                named("totals-paid", Columns.number(NamesTables.CREDITOR, BaseTotals::getPaid)),
                named("totals-rest", Columns.number(NamesTables.REST, BaseTotals::getRest)),
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
        tableView.getColumns().add( named("totals-entered-by", Columns.text("users",
                row -> row.getUsers() == null ? "" : row.getUsers().getUsername())));
        tableView.getColumns().add(named("totals-entry-time", Columns.text("column.entry.time",
                row -> row.getCreated_at() == null ? "" : row.getCreated_at().toString())));
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
        // date setting
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setValue(loadDate(dateFromKey, DateSetting.firstDateInMonth));
        dateTo.setValue(loadDate(dateToKey, LocalDate.now()));

        comboDelegateSetting(comboDelegate, getDelegateNames());
        boolean showDelegate = dataInterface.designInterface().showDataForCustomer();
        comboDelegate.setVisible(showDelegate);
        comboDelegate.setManaged(showDelegate);
        labelDelegate.setVisible(showDelegate);
        labelDelegate.setManaged(showDelegate);

        comboDelegateSetting(comboEnteredBy, getUsernames());
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
            return employeeService.getDelegateNames();
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
    private void comboDelegateSetting(ComboBox<String> comboBox, List<String> items) {
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
        menuItemPrintTotals.setOnAction(actionEvent -> print());
        menuItemPrintDetailed.setOnAction(actionEvent -> printDetailed());
        menuItemReportByParty.setOnAction(actionEvent -> exportReport(DocumentTableSpec.Report.BY_PARTY));
        menuItemReportByDay.setOnAction(actionEvent -> exportReport(DocumentTableSpec.Report.BY_DAY));
        menuItemReportByMonth.setOnAction(actionEvent -> exportReport(DocumentTableSpec.Report.BY_MONTH));
        menuItemReportByDelegate.setOnAction(actionEvent -> exportReport(DocumentTableSpec.Report.BY_DELEGATE));
        menuItemReportByItem.setOnAction(actionEvent -> exportReport(DocumentTableSpec.Report.BY_ITEM));
        boolean hasDelegate = dataInterface.designInterface().showDataForCustomer();
        menuItemReportByDelegate.setVisible(hasDelegate);
        btnRefresh.setOnAction(actionEvent -> search(false));
        btnSearch.setOnAction(actionEvent -> search(true));
        btnClearFilters.setOnAction(actionEvent -> clearFilters());
        btnSaveFilter.setOnAction(actionEvent -> saveCurrentFilter());
        btnDeleteSavedFilter.setOnAction(actionEvent -> deleteSelectedFilter());
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
            var list = tableView.getItems().stream().filter(BaseTotals::isSelectedRow).toList();
            if (list.isEmpty()) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.dialog.delete.title"),
                        new UserValidationException(LanguageManager.getInstance().getString("msg.select.row")));
                return;
            }
            deleteDocuments(list);
        });
        // The keyboard and the mouse act on the focused row, the way the row's own buttons
        // do - there is no toolbar left holding "the selected one" for them to defer to.
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                try {
                    showInvoiceData(requireSelectedRow());
                } catch (Exception e) {
                    exceptionHandle(e);
                }
            }
        });
        tableView.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.DELETE)) {
                BaseTotals focused = tableView.getSelectionModel().getSelectedItem();
                if (focused != null) deleteDocuments(List.of(focused));
            }

            if (event.getCode().equals(KeyCode.C) && event.isControlDown()) {
                copyInvoiceDetailsToClipboard();
            }

        });

        btnSelected.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            List<BaseTotals> list = tableView.getItems().stream().toList();
            list.forEach(t2 -> t2.setSelectedRow(t1));

            if (t1) btnSelected.setText(LanguageManager.getInstance().getString("common.cancel.select.all"));
            else btnSelected.setText(LanguageManager.getInstance().getString("common.select.all"));
        });
    }

    /**
     * Deletes one document or many, on the one path.
     * <p>
     * It was written inline in the toolbar's handler, which is where it had to stay while
     * the toolbar was the only way to delete. A row's own delete button is the same
     * operation on a list of one, and it must ask the same confirmation, take the same
     * backup and record the same shift-correction reason - so it is a method now rather
     * than a second copy free to drift from this one.
     */
    private void deleteDocuments(List<? extends BaseTotals> documents) {
        if (documents.isEmpty() || !AllAlerts.confirmDelete()) return;
        final Optional<String> correctionReason;
        try {
            correctionReason = com.hamza.account.controller.users.ShiftCorrectionReasonPrompt.forDelete();
        } catch (DaoException e) {
            exceptionHandle(e);
            return;
        }
        if (correctionReason.isEmpty()) return;
        maskerPaneSetting.showMaskerPane(
                LanguageManager.getInstance().getString("invoice.dialog.delete.title"), () -> {
                    SaveDatabaseFile.saveBeforeClose(false);
                    dataInterface.totalDesignInterface().deleteMultiData(correctionReason.get(),
                            documents.stream().map(BaseTotals::getId).toArray(Integer[]::new));
                });
        maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
            btnRefresh.fire();
            if (eventBus != null) eventBus.publish(new InvoiceSaved(dataInterface.invoiceSide()));
            AllAlerts.alertDelete();
        });
    }

    /**
     * Open, edit and delete, on the row they act on.
     * <p>
     * The toolbar above keeps all three, and deliberately: delete there works on every
     * ticked row at once, which is a different operation from deleting the one in front of
     * you. What the toolbar cannot do is say <em>which</em> row it means without the
     * operator first selecting it and then travelling to the top of the screen - so the
     * single-row half of each action is here as well, where the row is.
     * <p>
     * The permissions are the toolbar's own, checked per button rather than for the column,
     * so a user who may look but not edit gets a row he can open and nothing else.
     */
    private TableColumn<BaseTotals, Void> rowActionsColumn() {
        TableColumn<BaseTotals, Void> column = new TableColumn<>(
                LanguageManager.getInstance().getString("invoice.column.actions"));
        column.setId(ACTIONS_COLUMN);
        column.setSortable(false);
        column.setReorderable(false);
        column.setMinWidth(112);
        column.setPrefWidth(112);
        column.setCellFactory(ignored -> new RowActionsCell());
        return column;
    }

    /** The three buttons one row carries. One instance per cell, reused as rows scroll. */
    private final class RowActionsCell extends TableCell<BaseTotals, Void> {
        private final HBox buttons = new HBox(4);

        private RowActionsCell() {
            Button show = rowButton(AppIcon.SHOW, "invoice.tooltip.show", "primary-button",
                    row -> showInvoiceData(row));
            Button edit = rowButton(AppIcon.EDIT, "invoice.tooltip.update", "warning-button",
                    row -> update(row));
            Button delete = rowButton(AppIcon.DELETE, "invoice.tooltip.delete", "danger-button",
                    this::deleteRow);
            // The same service the toolbar's buttons go through, so a row button and the
            // button above it can never disagree about what this user may do.
            var permissions = new DisableButtons.PermissionDisableService();
            permissions.applyPermissionBasedDisable(edit::setDisable,
                    dataInterface.designInterface().update());
            permissions.applyPermissionBasedDisable(delete::setDisable,
                    dataInterface.designInterface().delete());
            buttons.setAlignment(Pos.CENTER);
            buttons.getChildren().addAll(show, edit, delete);
            buttons.getStyleClass().add("totals-row-actions");
        }

        private Button rowButton(AppIcon icon, String tooltipKey, String styleClass,
                                 RowAction action) {
            Button button = new Button();
            button.setGraphic(icon.graphic(14));
            button.getStyleClass().addAll("icon-button", styleClass);
            tip(button, tooltipKey);
            button.setOnAction(event -> {
                BaseTotals row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) return;
                // The row the button is on is the row it acts on; selecting it as well keeps
                // the toolbar and the keyboard pointing at the same document.
                tableView.getSelectionModel().select(row);
                try {
                    action.run(row);
                } catch (Exception e) {
                    exceptionHandle(e);
                }
            });
            return button;
        }

        /** Deleting one row goes through the same confirmation the toolbar's delete uses. */
        private void deleteRow(BaseTotals row) {
            deleteDocuments(List.of(row));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null
                    ? null : buttons);
        }
    }

    @FunctionalInterface
    private interface RowAction {
        void run(BaseTotals row) throws Exception;
    }

    private BaseTotals requireSelectedRow() throws UserValidationException {
        BaseTotals selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new UserValidationException(LanguageManager.getInstance().getString("msg.select.row"));
        }
        return selected;
    }

    /**
     * Runs one of the grouped summaries over the criteria now on screen and writes it to a
     * PDF, with the filter it ran under printed under the title.
     * <p>
     * That subtitle is the point of the exercise. The same report over one month, over one
     * customer, or over the whole history prints identically, so a page filed away or
     * handed to an accountant otherwise carries no way of knowing what it counted - and two
     * of them that disagree cannot be told apart. It is built from the criteria the query
     * ran with, not from the controls, which may have been edited since.
     */
    private void exportReport(DocumentTableSpec.Report report) {
        TotalsSearchCriteria criteria;
        try {
            criteria = buildCriteria();
        } catch (TotalsFilterInput.InvalidFilterException e) {
            setFilterPanelVisible(true);
            exceptionHandle(new UserValidationException(filterErrorText(e.problem())));
            return;
        }
        LanguageManager language = LanguageManager.getInstance();
        DocumentTableSpec spec = DocumentTableSpec.of(dataInterface.designInterface().documentType());

        File target = reportTarget(language.getString(reportTitleKey(report)));
        if (target == null) return;

        AtomicReference<TotalsReportService.TotalsReport> result = new AtomicReference<>();
        maskerPaneSetting.showMaskerPane(language.getString("invoice.masker.loading"), () ->
                result.set(reportService.run(spec, report, criteria,
                        dataInterface.designInterface().show_totals(),
                        language.getString("invoice.report.unsupported"))));
        maskerPaneSetting.getVoidTask().setOnSucceeded(event ->
                writeReport(result.get(), report, criteria, target));
    }

    private void writeReport(TotalsReportService.TotalsReport report,
                             DocumentTableSpec.Report kind,
                             TotalsSearchCriteria criteria, File target) {
        LanguageManager language = LanguageManager.getInstance();
        if (report == null || report.isEmpty()) {
            AllAlerts.handleError(language.getString(reportTitleKey(kind)),
                    new UserValidationException(language.getString("invoice.report.empty")));
            return;
        }
        String subtitle = TotalsFilterDescription.describe(criteria, language::getString);
        if (report.truncated()) {
            subtitle = subtitle + language.getString("invoice.report.filter.separator")
                    + language.getString("invoice.report.truncated", DocumentTableSpec.REPORT_ROW_LIMIT);
        }
        TotalsReportLayout layout = TotalsReportLayout.of(report, kind, language::getString);
        // The same separator the subtitle uses. A plain hyphen between two Arabic phrases
        // is a bidi-neutral character the report font has no glyph for, and it printed as
        // an empty box in the title - the em dash renders.
        String separator = language.getString("invoice.report.filter.separator");
        boolean written = new PdfExportService().exportGroupedReport(
                target.getAbsolutePath(),
                language.getString(reportTitleKey(kind)) + separator + labelScreenTitle.getText(),
                subtitle,
                layout.headers(),
                layout.columnWidths(),
                layout.rows(),
                layout.totals(),
                PageSize.A4.rotate());
        if (written) {
            AllAlerts.alertSaveWithMessage(language.getString("invoice.report.saved")
                    + System.lineSeparator() + target.getAbsolutePath());
        } else {
            AllAlerts.handleError(language.getString(reportTitleKey(kind)),
                    new UserValidationException(language.getString("invoice.report.failed")));
        }
    }

    private static String reportTitleKey(DocumentTableSpec.Report report) {
        return switch (report) {
            case BY_PARTY -> "invoice.report.by.party";
            case BY_DAY -> "invoice.report.by.day";
            case BY_MONTH -> "invoice.report.by.month";
            case BY_DELEGATE -> "invoice.report.by.delegate";
            case BY_ITEM -> "invoice.report.by.item";
        };
    }

    /** Where the operator wants it. A report they cannot find again is not a report. */
    private File reportTarget(String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(LanguageManager.getInstance().getString("invoice.report.save.title"));
        chooser.setInitialFileName(suggestedName.replaceAll("[\\\\/:*?\"<>|]", " ").trim() + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        return chooser.showSaveDialog(stackPane.getScene().getWindow());
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
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            clipboard.setContent(clipboardContent);
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
        long request = ++searchRequest;
        TotalsSearchCriteria criteria;
        try {
            criteria = buildCriteria();
        } catch (TotalsFilterInput.InvalidFilterException e) {
            setFilterPanelVisible(true);
            exceptionHandle(new UserValidationException(filterErrorText(e.problem())));
            return;
        }
        rememberDate(dateFromKey, criteria.dateFrom());
        rememberDate(dateToKey, criteria.dateTo());
        Task<Void> previous = maskerPaneSetting.getVoidTask();
        if (previous != null && previous.isRunning()) previous.cancel();

        int page = currentPage;
        AtomicReference<TotalsPage<? extends BaseTotals>> result =
                new AtomicReference<>(TotalsPage.empty(PAGE_SIZE));
        maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("invoice.masker.loading"), () -> {
            result.set(totalsAndPurchaseList.searchTotals(criteria, page, PAGE_SIZE));
        });
        maskerPaneSetting.getVoidTask().setOnSucceeded(event -> {
            if (request != searchRequest) return;
            TotalsPage<? extends BaseTotals> loaded = result.get();
            observableList.setAll(loaded.rows());
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
            comboSavedFilters.getSelectionModel().clearSelection();
            radioAll.setSelected(true);
        } finally {
            syncingFilters = false;
        }
        searchDelay.stop();
        search(false);
    }

    // -------------------------------------------------------------------------
    // Saved filters
    // -------------------------------------------------------------------------

    private void configureSavedFilters() {
        reloadSavedFilters(null);
        comboSavedFilters.valueProperty().addListener((observable, oldName, name) -> {
            btnDeleteSavedFilter.setDisable(name == null);
            if (syncingFilters || name == null) return;
            TotalsSearchCriteria saved = savedFilters.get(name);
            if (saved != null) applySavedFilter(saved);
        });
        btnDeleteSavedFilter.setDisable(comboSavedFilters.getValue() == null);
    }

    private void reloadSavedFilters(String selectedName) {
        syncingFilters = true;
        try {
            comboSavedFilters.setItems(FXCollections.observableArrayList(savedFilters.names()));
            if (selectedName != null && comboSavedFilters.getItems().contains(selectedName)) {
                comboSavedFilters.setValue(selectedName);
            } else {
                comboSavedFilters.getSelectionModel().clearSelection();
            }
        } finally {
            syncingFilters = false;
        }
    }

    private void saveCurrentFilter() {
        TotalsSearchCriteria criteria;
        try {
            criteria = buildCriteria();
        } catch (TotalsFilterInput.InvalidFilterException e) {
            setFilterPanelVisible(true);
            exceptionHandle(new UserValidationException(filterErrorText(e.problem())));
            return;
        }

        LanguageManager language = LanguageManager.getInstance();
        TextInputDialog dialog = new TextInputDialog(comboSavedFilters.getValue());
        dialog.setTitle(language.getString("invoice.search.saved.save.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(language.getString("invoice.search.saved.save.prompt"));
        localizeDialog(dialog);
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get().isBlank()) return;

        String name = answer.get().trim();
        if (name.length() > Preferences.MAX_KEY_LENGTH) {
            exceptionHandle(new UserValidationException(
                    language.getString("invoice.search.saved.name.too.long", Preferences.MAX_KEY_LENGTH)));
            return;
        }
        savedFilters.save(name, criteria);
        reloadSavedFilters(name);
        btnDeleteSavedFilter.setDisable(false);
    }

    /**
     * A dialog built in code, rather than from an FXML loaded with a bundle, carries
     * JavaFX's own English button labels and the platform's left-to-right orientation -
     * while every other window on this screen is Arabic and right-to-left.
     */
    private static void localizeDialog(Dialog<?> dialog) {
        LanguageManager language = LanguageManager.getInstance();
        DialogPane pane = dialog.getDialogPane();
        pane.setNodeOrientation(language.getNodeOrientation());
        for (ButtonType type : pane.getButtonTypes()) {
            String key = switch (type.getButtonData()) {
                case OK_DONE -> "ok";
                case CANCEL_CLOSE -> "cancel";
                default -> null;
            };
            if (key != null && pane.lookupButton(type) instanceof Button button) {
                button.setText(language.getString(key));
            }
        }
    }

    private void deleteSelectedFilter() {
        String name = comboSavedFilters.getValue();
        if (name == null) return;
        savedFilters.delete(name);
        reloadSavedFilters(null);
        btnDeleteSavedFilter.setDisable(true);
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

    /** One end of the period as a report header shows it, or blank when it is open. */
    private static String boundText(LocalDate value) {
        return value == null ? "" : value.toString();
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
        comboDelegateSetting(comboName, list);
    }

    private void update(BaseTotals t2) throws Exception {
        int i = t2.getId();

        // The accounting lock decides this now, not the calendar. What was here refused
        // any invoice outside the current month: on the first of the month yesterday's
        // invoice was locked whether or not anything had been reported, everything
        // inside the current month stayed editable however much had been, and the rule
        // was invisible - it could not be seen or set by anyone. It also guarded only
        // the button that opens an invoice, leaving the delete beside it unchecked.
        //
        // update_data is still honoured: it is a per-user restriction to the current
        // month, which some shops rely on, and it is now the narrower of the two rather
        // than the only one.
        LocalDate invoiceDate = LocalDate.parse(t2.getDate());
        periodLockService.requireOpen(invoiceDate, dataInterface.designInterface().nameTextOfInvoice());

        if (!update_data) {
            LocalDate currentDate = LocalDate.now();
            if (invoiceDate.getYear() != currentDate.getYear()
                    || invoiceDate.getMonth() != currentDate.getMonth()) {
                throw new BusinessRuleException(LanguageManager.getInstance().getString("invoice.error.outside.current.month"));
            }
        }
        BuyApplication buyApp = new BuyApplication(dataInterface, i);
        buyApp.start(new Stage());
    }

    /**
     * Prints the list itself: one line per invoice, as a PDF that says what it covers.
     *
     * <p>It was a Jasper report, and moving it is not a change of library for its own
     * sake - the report it produced could not say which filter it was run under, which is
     * exactly the thing that makes a filed page arguable. Everything it lists is a table
     * of figures rather than a document handed to a customer, so it belongs with the four
     * summaries beside it in the menu; the detailed print, which puts real invoices on
     * paper and has a receipt-printer layout, stays where it is.</p>
     *
     * <p>Two things it does that the Jasper one did not, and both were defects rather
     * than choices. It prints the <b>whole</b> result when nothing is ticked, instead of
     * an empty page - and it has to, because with a paged list "the ticked rows" are only
     * ever the ticked rows of the page in front of you. And it no longer reads
     * {@code dateFrom.getValue().toString()}, which is a NullPointerException now that a
     * search may legitimately carry no dates at all.</p>
     */
    private void print() {
        TotalsSearchCriteria criteria;
        try {
            criteria = buildCriteria();
        } catch (TotalsFilterInput.InvalidFilterException e) {
            setFilterPanelVisible(true);
            exceptionHandle(new UserValidationException(filterErrorText(e.problem())));
            return;
        }
        LanguageManager language = LanguageManager.getInstance();
        File target = reportTarget(language.getString("invoice.btn.print.totals"));
        if (target == null) return;

        List<BaseTotals> ticked = tableView.getItems().stream()
                .filter(BaseTotals::isSelectedRow).toList();
        AtomicReference<List<? extends BaseTotals>> documents = new AtomicReference<>(ticked);
        if (ticked.isEmpty()) {
            maskerPaneSetting.showMaskerPane(language.getString("invoice.masker.loading"), () ->
                    documents.set(totalsAndPurchaseList
                            .searchTotals(criteria, 0, DocumentTableSpec.REPORT_ROW_LIMIT).rows()));
            maskerPaneSetting.getVoidTask().setOnSucceeded(event ->
                    writeDocumentListing(documents.get(), criteria, target));
        } else {
            writeDocumentListing(ticked, criteria, target);
        }
    }

    private void writeDocumentListing(List<? extends BaseTotals> documents,
                                      TotalsSearchCriteria criteria, File target) {
        LanguageManager language = LanguageManager.getInstance();
        if (documents.isEmpty()) {
            AllAlerts.handleError(language.getString("invoice.btn.print.totals"),
                    new UserValidationException(language.getString("invoice.report.empty")));
            return;
        }
        DocumentTableSpec spec = DocumentTableSpec.of(dataInterface.designInterface().documentType());
        var profit = totalsDataInterface.getTotalProfit();
        List<TotalsDocumentRow> rows = documents.stream()
                .map(document -> new TotalsDocumentRow(
                        document.getId(),
                        document.getDate(),
                        totalsDataInterface.getNameData(document),
                        document.getInvoiceType() == null ? "" : document.getInvoiceType().getType(),
                        MoneyMath.decimal(document.getTotal()),
                        MoneyMath.decimal(document.getDiscount()),
                        MoneyMath.decimal(document.getPaid()),
                        spec.hasProfit() ? MoneyMath.decimal(profit.applyAsDouble(document))
                                : java.math.BigDecimal.ZERO))
                .toList();

        String subtitle = TotalsFilterDescription.describe(criteria, language::getString);
        if (documents.size() >= DocumentTableSpec.REPORT_ROW_LIMIT) {
            subtitle = subtitle + language.getString("invoice.report.filter.separator")
                    + language.getString("invoice.report.truncated", DocumentTableSpec.REPORT_ROW_LIMIT);
        }
        TotalsReportLayout layout = TotalsReportLayout.ofDocuments(
                rows, spec.hasProfit(), language::getString);
        String separator = language.getString("invoice.report.filter.separator");
        boolean written = new PdfExportService().exportGroupedReport(
                target.getAbsolutePath(),
                language.getString("invoice.btn.print.totals") + separator + labelScreenTitle.getText(),
                subtitle, layout.headers(), layout.columnWidths(), layout.rows(),
                layout.totals(), PageSize.A4.rotate());
        if (written) {
            AllAlerts.alertSaveWithMessage(language.getString("invoice.report.saved")
                    + System.lineSeparator() + target.getAbsolutePath());
        } else {
            AllAlerts.handleError(language.getString("invoice.btn.print.totals"),
                    new UserValidationException(language.getString("invoice.report.failed")));
        }
    }

    private void printDetailed() {
        try {
            List<BaseTotals> items = new ArrayList<>();
            for (int i = 0; i < tableView.getItems().size(); i++) {
                if (tableView.getItems().get(i).isSelectedRow()) {
                    items.add(tableView.getItems().get(i));
                }
            }
            if (items.isEmpty()) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("print"),
                        new UserValidationException(
                                LanguageManager.getInstance().getString("msg.select.row")));
                return;
            }
            List<PrintPurchaseWithName> printPurchaseWithNames = new ArrayList<>();
            dataInterface.addList(items, printPurchaseWithNames);
            // A bound that is not set is a blank on the header, not a NullPointerException:
            // a search may now legitimately carry no dates at all.
            printReports.printMultiInvoice(printPurchaseWithNames,
                    dataInterface.designInterface().nameTextOfTotal(),
                    boundText(dateFrom.getValue()), boundText(dateTo.getValue()), null);
        } catch (DaoException e) {
            exceptionHandle(e);
        }
    }

    private void showInvoiceData(BaseTotals t2) throws Exception {
        int id = t2.getId();
        String name = totalsDataInterface.getNameData(t2);
        new ShowInvoiceApplication(dataPublisher, dataInterface, daoFactory, id, name);
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
        textSumTableSize.setText(settled ? String.valueOf(summary.count()) : pending);
        textSumTotals.setText(settled ? MoneyMath.text(summary.total()) : pending);
        textSumDiscount.setText(settled ? MoneyMath.text(summary.discount()) : pending);
        textSumAfterDiscount.setText(settled ? MoneyMath.text(summary.afterDiscount()) : pending);
        textCash.setText(settled ? MoneyMath.text(summary.paid()) : pending);
        textDeffer.setText(settled ? MoneyMath.text(summary.remaining()) : pending);
        textProfit.setText(settled ? MoneyMath.text(summary.profit()) : pending);
    }

    private static void tip(Control control, String key) {
        control.setTooltip(new Tooltip(LanguageManager.getInstance().getString(key)));
    }

    private void exceptionHandle(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("invoice.error.action.title"), e);
    }

}
