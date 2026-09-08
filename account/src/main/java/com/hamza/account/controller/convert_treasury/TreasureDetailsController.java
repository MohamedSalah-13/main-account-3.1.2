package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.features.events.TreasuryBalancesChanged;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.treasury.statement.TreasuryMovementKind;
import com.hamza.account.features.treasury.statement.TreasuryOption;
import com.hamza.account.features.treasury.statement.TreasuryStatementFilter;
import com.hamza.account.features.treasury.statement.TreasuryStatementOptions;
import com.hamza.account.features.treasury.statement.TreasuryStatementPage;
import com.hamza.account.features.treasury.statement.TreasuryStatementPrintData;
import com.hamza.account.features.treasury.statement.TreasuryStatementRow;
import com.hamza.account.features.treasury.statement.TreasuryStatementService;
import com.hamza.account.features.treasury.statement.TreasuryUserOption;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.ProcessType;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hamza.account.controller.items.CardController.dataInterface;

/** Unified, filterable and paged statement of every treasury movement. */
@FxmlPath(pathFile = "treasury/treasury-details.fxml")
public class TreasureDetailsController {
    private static final int PAGE_SIZE = 250;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final TreasuryStatementService statementService;
    private final EventBus eventBus;
    private final Subscriptions subscriptions = new Subscriptions();

    @FXML private StackPane stackPane;
    @FXML private ComboBox<TreasuryOption> comboTreasury;
    @FXML private ComboBox<MovementChoice> comboDetails;
    @FXML private ComboBox<TreasuryUserOption> comboUsers;
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Button btnRefresh;
    @FXML private Button btnSearch;
    @FXML private Button btnReset;
    @FXML private Button btnPrint;
    @FXML private Button btnPrevious;
    @FXML private Button btnNext;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Label openingBalance;
    @FXML private Label sumIncome;
    @FXML private Label sumOutput;
    @FXML private Label netMovement;
    @FXML private Label closingBalance;
    @FXML private TableView<TreasuryStatementRow> tableView;

    private MaskerPaneSetting maskerPaneSetting;
    private int currentPage;
    private boolean loading;
    private boolean pendingRefresh;
    private boolean pendingOptionsReload;

    public TreasureDetailsController(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this(daoFactory, dataPublisher, new TreasuryStatementService(), ServiceRegistry.get(EventBus.class));
    }

    TreasureDetailsController(DaoFactory daoFactory, DataPublisher dataPublisher,
                              TreasuryStatementService statementService, EventBus eventBus) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.statementService = statementService;
        this.eventBus = eventBus;
    }

    @FXML
    private void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        dateFrom.setValue(LocalDate.now().withDayOfMonth(1));
        dateTo.setValue(LocalDate.now());
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        configureChoices();
        configureButtons();
        buildColumns();
        subscribeToChanges();
        load(true);
    }

    private void configureChoices() {
        comboTreasury.setConverter(converter(option -> option.id() == 0 ? text("all")
                : option.name() + (option.active() ? "" : " (" + text("treasury.statement.option.closed") + ")")));
        comboUsers.setConverter(converter(option -> option.id() == 0 ? text("all") : option.name()));
        comboDetails.setConverter(converter(choice -> choice.kind() == null
                ? text("all") : text(choice.kind().labelKey())));
        comboDetails.setItems(FXCollections.observableArrayList(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(new MovementChoice(null)),
                        java.util.Arrays.stream(TreasuryMovementKind.values()).map(MovementChoice::new)).toList()));
        comboDetails.getSelectionModel().selectFirst();
    }

    private <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : label.apply(value); }
            @Override public T fromString(String value) { return null; }
        };
    }

    private void configureButtons() {
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic());
        btnSearch.setGraphic(AppIcon.SEARCH.graphic());
        btnReset.setGraphic(AppIcon.CLEAR.graphic());
        btnPrint.setGraphic(AppIcon.PRINT.graphic());
    }

    private void buildColumns() {
        TableColumn<TreasuryStatementRow, TreasuryStatementRow> action =
                Columns.column("treasury.statement.column.action", row -> row);
        action.setCellFactory(ignored -> new InvoiceActionCell());
        action.setSortable(false);
        action.setPrefWidth(72);

        tableView.getColumns().setAll(
                Columns.number("treasury.statement.column.reference", TreasuryStatementRow::referenceId),
                Columns.date("treasury.statement.column.date", TreasuryStatementRow::movementDate),
                Columns.text("treasury.statement.column.time", row -> row.recordedAt() == null
                        ? "" : row.recordedAt().format(TIME_FORMAT)),
                Columns.text("treasury.statement.column.movement", row -> text(row.kind().labelKey())),
                Columns.text("treasury.statement.column.treasury", TreasuryStatementRow::treasuryName),
                Columns.number("treasury.statement.column.income", TreasuryStatementRow::income),
                Columns.number("treasury.statement.column.output", TreasuryStatementRow::output),
                Columns.number("treasury.statement.column.balance", TreasuryStatementRow::runningBalance),
                Columns.text("treasury.statement.column.user", TreasuryStatementRow::username), action);
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void subscribeToChanges() {
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(TreasuryMovementRecorded.class, event -> load(false)));
            subscriptions.add(eventBus.subscribe(TreasuryBalancesChanged.class, event -> load(false)));
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> load(false)));
            subscriptions.add(eventBus.subscribe(TreasuriesChanged.class, event -> load(true)));
        }
        subscriptions.disposeWith(stackPane);
    }

    @FXML private void search() { currentPage = 0; load(false); }
    @FXML private void refresh() { load(false); }

    @FXML
    private void resetFilters() {
        dateFrom.setValue(LocalDate.now().withDayOfMonth(1));
        dateTo.setValue(LocalDate.now());
        comboTreasury.getSelectionModel().selectFirst();
        comboDetails.getSelectionModel().selectFirst();
        comboUsers.getSelectionModel().selectFirst();
        currentPage = 0;
        load(false);
    }

    @FXML private void previousPage() { if (currentPage > 0) { currentPage--; load(false); } }
    @FXML private void nextPage() { currentPage++; load(false); }

    @FXML
    private void printStatement() {
        TreasuryStatementFilter filter = readFilter();
        if (filter == null || loading) return;
        AtomicReference<TreasuryStatementPrintData> result = new AtomicReference<>();
        setLoading(true);
        maskerPaneSetting.showMaskerPane(text("treasury.statement.operation.print"),
                () -> result.set(statementService.forPrint(filter)));
        maskerPaneSetting.getVoidTask().setOnSucceeded(event -> {
            setLoading(false);
            TreasuryStatementPrintData data = result.get();
            if (data.truncated()) AllAlerts.alertError(text("treasury.statement.error.print.limit"));
            else if (data.rows().isEmpty()) AllAlerts.alertError(text("treasury.statement.error.print.empty"));
            else print(data, filter);
            runPendingRefresh();
        });
        maskerPaneSetting.getVoidTask().setOnFailed(event -> { setLoading(false); runPendingRefresh(); });
    }

    private void load(boolean reloadOptions) {
        if (loading) {
            pendingRefresh = true;
            pendingOptionsReload |= reloadOptions;
            return;
        }
        TreasuryStatementFilter filter = readFilter();
        if (filter == null) return;
        AtomicReference<TreasuryStatementOptions> options = new AtomicReference<>();
        AtomicReference<TreasuryStatementPage> page = new AtomicReference<>();
        setLoading(true);
        maskerPaneSetting.showMaskerPane(text("treasury.statement.operation.load"), () -> {
            if (reloadOptions) options.set(statementService.options());
            page.set(statementService.search(filter));
        });
        maskerPaneSetting.getVoidTask().setOnSucceeded(event -> {
            if (options.get() != null) applyOptions(options.get());
            applyPage(page.get());
            setLoading(false);
            runPendingRefresh();
        });
        maskerPaneSetting.getVoidTask().setOnFailed(event -> { setLoading(false); runPendingRefresh(); });
    }

    private TreasuryStatementFilter readFilter() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) {
            AllAlerts.alertError(text("treasury.statement.error.period.required"));
            return null;
        }
        if (from.isAfter(to)) {
            AllAlerts.alertError(text("treasury.statement.error.period.reversed"));
            return null;
        }
        TreasuryOption treasury = comboTreasury.getValue();
        TreasuryUserOption user = comboUsers.getValue();
        MovementChoice movement = comboDetails.getValue();
        return new TreasuryStatementFilter(from, to,
                treasury == null || treasury.id() == 0 ? null : treasury.id(),
                movement == null ? null : movement.kind(),
                user == null || user.id() == 0 ? null : user.id(), currentPage, PAGE_SIZE);
    }

    private void applyOptions(TreasuryStatementOptions options) {
        int treasuryId = comboTreasury.getValue() == null ? 0 : comboTreasury.getValue().id();
        int userId = comboUsers.getValue() == null ? 0 : comboUsers.getValue().id();
        comboTreasury.setItems(FXCollections.observableArrayList(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(new TreasuryOption(0, "", true)),
                        options.treasuries().stream()).toList()));
        comboUsers.setItems(FXCollections.observableArrayList(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(new TreasuryUserOption(0, "")),
                        options.users().stream()).toList()));
        selectTreasury(treasuryId);
        selectUser(userId);
    }

    private void selectTreasury(int id) {
        comboTreasury.getItems().stream().filter(value -> value.id() == id).findFirst()
                .ifPresentOrElse(comboTreasury::setValue, () -> comboTreasury.getSelectionModel().selectFirst());
    }

    private void selectUser(int id) {
        comboUsers.getItems().stream().filter(value -> value.id() == id).findFirst()
                .ifPresentOrElse(comboUsers::setValue, () -> comboUsers.getSelectionModel().selectFirst());
    }

    private void applyPage(TreasuryStatementPage page) {
        tableView.setItems(FXCollections.observableArrayList(page.rows()));
        currentPage = page.page();
        btnPrevious.setDisable(!page.hasPrevious());
        btnNext.setDisable(!page.hasNext());
        pageLabel.setText(MessageFormat.format(text("treasury.statement.page"), currentPage + 1));
        statusLabel.setText(MessageFormat.format(text("treasury.statement.status"), page.rows().size()));
        openingBalance.setText(money(page.summary().openingBalance()));
        sumIncome.setText(money(page.summary().totalIncome()));
        sumOutput.setText(money(page.summary().totalOutput()));
        netMovement.setText(money(page.summary().netMovement()));
        closingBalance.setText(money(page.summary().closingBalance()));
    }

    private void setLoading(boolean value) {
        loading = value;
        btnSearch.setDisable(value); btnRefresh.setDisable(value); btnReset.setDisable(value); btnPrint.setDisable(value);
        if (value) {
            btnPrevious.setDisable(true); btnNext.setDisable(true);
            statusLabel.setText(text("treasury.statement.status.loading"));
        }
    }

    private void runPendingRefresh() {
        if (pendingRefresh) {
            boolean reloadOptions = pendingOptionsReload;
            pendingRefresh = false;
            pendingOptionsReload = false;
            load(reloadOptions);
        }
    }

    private void print(TreasuryStatementPrintData data, TreasuryStatementFilter filter) {
        List<TreasuryBalance> legacyRows = data.rows().stream().map(this::toPrintRow).toList();
        var summary = data.summary();
        new Print_Reports().printAccountStatements(legacyRows, filter.from().toString(), filter.to().toString(),
                summary.totalIncome().doubleValue(), summary.totalOutput().doubleValue(),
                summary.closingBalance().doubleValue());
    }

    private TreasuryBalance toPrintRow(TreasuryStatementRow row) {
        TreasuryBalance legacy = new TreasuryBalance();
        legacy.setId(row.referenceId()); legacy.setDate(row.movementDate());
        legacy.setInformation(text(row.kind().labelKey())); legacy.setName(row.treasuryName());
        legacy.setTotal_income(row.income().doubleValue()); legacy.setTotal_output(row.output().doubleValue());
        legacy.setBalance(row.runningBalance().doubleValue()); legacy.setUser_id(row.userId());
        legacy.setUser_name(row.username()); legacy.setTreasury_id(row.treasuryId());
        return legacy;
    }

    private void openInvoice(TreasuryStatementRow row) {
        try {
            ProcessType type = switch (row.kind()) {
                case SALES -> ProcessType.SALES;
                case SALES_RETURN -> ProcessType.SALES_RETURN;
                case PURCHASE -> ProcessType.PURCHASE;
                case PURCHASE_RETURN -> ProcessType.PURCHASE_RETURN;
                default -> null;
            };
            if (type != null) new ShowInvoiceApplication(dataPublisher,
                    dataInterface(type, daoFactory, dataPublisher), daoFactory, row.referenceId(), "");
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.statement.operation.open.invoice"), e);
        }
    }

    private String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private String text(String key) { return LanguageManager.getInstance().getString(key); }

    private record MovementChoice(TreasuryMovementKind kind) { }

    private final class InvoiceActionCell extends TableCell<TreasuryStatementRow, TreasuryStatementRow> {
        private final Button button = new Button();

        private InvoiceActionCell() {
            button.setGraphic(AppIcon.SHOW.graphic(16));
            button.getStyleClass().add("app-neutral-button");
            button.setTooltip(new Tooltip(text("treasury.statement.open.invoice")));
            button.setOnAction(event -> openInvoice(getItem()));
        }

        @Override protected void updateItem(TreasuryStatementRow row, boolean empty) {
            super.updateItem(row, empty);
            setGraphic(empty || row == null || !row.kind().isInvoice() ? null : button);
        }
    }
}
