package com.hamza.account.controller.others;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.audit.AuditAdminEvent;
import com.hamza.account.features.audit.AuditAdminEventPage;
import com.hamza.account.features.audit.AuditAdminEventQuery;
import com.hamza.account.features.audit.AuditAdminEventService;
import com.hamza.account.features.audit.AuditAdminOptions;
import com.hamza.account.features.audit.AuditAdminSort;
import com.hamza.account.features.audit.AuditJsonFormatter;
import com.hamza.account.features.audit.AuditSourceFilter;
import com.hamza.account.features.audit.AuditUserOption;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Read-only, paged browser for operations performed on the audit trail itself. */
@FxmlPath(pathFile = "audit-admin-events-view.fxml")
public final class AuditAdminEventsController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AuditAdminEventService service = ServiceRegistry.get(AuditAdminEventService.class);
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));
    private final TableView<AuditAdminEvent> table = new TableView<>();
    private AuditAdminEventQuery query = AuditAdminEventQuery.recent(LocalDate.now());
    private long loadToken;
    private boolean syncing;
    private boolean optionsLoaded;

    @FXML private StackPane root;
    @FXML private Label labelTitle, labelTotal, labelExports, labelDeletes, labelPolicies, labelCleanups;
    @FXML private Label labelStatus, labelSelection;
    @FXML private TextField txtSearch;
    @FXML private DatePicker dateFrom, dateTo;
    @FXML private ComboBox<AuditUserOption> comboUser;
    @FXML private ComboBox<String> comboEvent;
    @FXML private ComboBox<AuditSourceFilter> comboSource;
    @FXML private ComboBox<AuditAdminSort> comboSort;
    @FXML private Pagination pagination;
    @FXML private ProgressIndicator progress;
    @FXML private TextArea txtReason, txtDetails;
    @FXML private Button btnApply, btnClear, btnRefresh, btnClose;

    @FXML
    public void initialize() {
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        configureTable();
        configureFilters();
        configureActions();
        dateFrom.setValue(query.from());
        dateTo.setValue(query.to());
        load(query);
    }

    private void configureTable() {
        table.setId("auditAdminEventTable");
        table.getStyleClass().add("modern-table");
        table.getColumns().setAll(
                column("adminId", "audit.admin.column.id", 80, row -> String.valueOf(row.id())),
                column("adminTime", "audit.admin.column.time", 155, row -> DATE_TIME.format(row.occurredAt())),
                column("adminEvent", "audit.admin.column.event", 170, row -> eventLabel(row.eventType())),
                column("adminActor", "audit.admin.column.actor", 145, this::actorLabel),
                column("adminSource", "audit.admin.column.source", 120, this::sourceLabel),
                column("adminWorkstation", "audit.admin.column.workstation", 150, this::workstationLabel),
                column("adminAffected", "audit.admin.column.affected", 110,
                        row -> String.valueOf(row.affectedRows())),
                column("adminReason", "audit.admin.column.reason", 260, AuditAdminEvent::reason));
        table.setPlaceholder(new Label(text("audit.admin.placeholder.empty")));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> showSelection(value));
        TableSetting.tableMenuSetting(getClass(), table);
    }

    private static TableColumn<AuditAdminEvent, String> column(String id, String titleKey, double width,
                                                                Function<AuditAdminEvent, String> value) {
        TableColumn<AuditAdminEvent, String> column = new TableColumn<>(text(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Objects.toString(value.apply(cell.getValue()), "")));
        return column;
    }

    private void configureFilters() {
        comboUser.getItems().setAll(AuditUserOption.ALL);
        comboUser.setConverter(converter(value -> value == null || value.isAll()
                ? text("audit.log.filter.all.users") : value.name(), AuditUserOption.ALL));
        comboUser.setValue(AuditUserOption.ALL);

        comboEvent.getItems().setAll("");
        comboEvent.setConverter(converter(value -> value == null || value.isBlank()
                ? text("audit.admin.filter.all.events") : eventLabel(value), ""));
        comboEvent.setValue("");

        comboSource.getItems().setAll(AuditSourceFilter.values());
        comboSource.setConverter(converter(value -> value == null ? "" : text(value.labelKey()),
                AuditSourceFilter.ALL));
        comboSource.setValue(AuditSourceFilter.ALL);

        comboSort.getItems().setAll(AuditAdminSort.values());
        comboSort.setConverter(converter(value -> value == null ? "" : text(value.labelKey()),
                AuditAdminSort.NEWEST));
        comboSort.setValue(AuditAdminSort.NEWEST);

        pagination.setPageFactory(index -> {
            if (!syncing && index != query.page()) load(query.withPage(index));
            return table;
        });
        searchDelay.setOnFinished(event -> applyFilters());
        txtSearch.textProperty().addListener((obs, old, value) -> {
            if (!syncing) searchDelay.playFromStart();
        });
    }

    private void configureActions() {
        labelTitle.setGraphic(AppIcon.REPORT.graphic(24));
        btnApply.setGraphic(AppIcon.FILTER.graphic());
        btnClear.setGraphic(AppIcon.CLEAR.graphic());
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic());
        btnClose.setGraphic(AppIcon.CLOSE.graphic());
        btnApply.setOnAction(event -> applyFilters());
        btnClear.setOnAction(event -> clearFilters());
        btnRefresh.setOnAction(event -> load(query));
        btnClose.setOnAction(event -> ((Stage) btnClose.getScene().getWindow()).close());
    }

    private void applyFilters() {
        try {
            AuditUserOption user = comboUser.getValue();
            query = new AuditAdminEventQuery(txtSearch.getText(), dateFrom.getValue(), dateTo.getValue(),
                    user == null ? null : user.id(), comboEvent.getValue(), comboSource.getValue(),
                    comboSort.getValue(), 0, AuditAdminEventQuery.DEFAULT_PAGE_SIZE);
            load(query);
        } catch (IllegalArgumentException | NullPointerException error) {
            String key = error.getMessage() != null && error.getMessage().startsWith("audit.log.")
                    ? error.getMessage() : "audit.log.validation.date.required";
            AllAlerts.handleError(text("audit.admin.title"), new UserValidationException(text(key)));
        }
    }

    private void clearFilters() {
        syncing = true;
        try {
            query = AuditAdminEventQuery.recent(LocalDate.now());
            txtSearch.clear();
            dateFrom.setValue(query.from());
            dateTo.setValue(query.to());
            comboUser.setValue(AuditUserOption.ALL);
            comboEvent.setValue("");
            comboSource.setValue(AuditSourceFilter.ALL);
            comboSort.setValue(AuditAdminSort.NEWEST);
        } finally {
            syncing = false;
        }
        load(query);
    }

    private void load(AuditAdminEventQuery next) {
        if (service == null) return;
        query = next;
        long token = ++loadToken;
        boolean loadOptions = !optionsLoaded;
        setBusy(true);
        Task<LoadResult> task = new Task<>() {
            @Override protected LoadResult call() throws Exception {
                AuditAdminOptions options = loadOptions ? service.options() : null;
                return new LoadResult(service.load(next), options);
            }
        };
        task.setOnSucceeded(event -> {
            if (token != loadToken) return;
            setBusy(false);
            LoadResult result = task.getValue();
            if (result.options() != null) showOptions(result.options());
            showPage(result.page());
        });
        task.setOnFailed(event -> {
            if (token != loadToken) return;
            setBusy(false);
            AllAlerts.handleError(text("audit.admin.error.load"), task.getException());
        });
        start(task, "audit-admin-load-" + token);
    }

    private void showOptions(AuditAdminOptions options) {
        syncing = true;
        try {
            List<AuditUserOption> users = new ArrayList<>();
            users.add(AuditUserOption.ALL);
            users.addAll(options.users());
            comboUser.setItems(FXCollections.observableArrayList(users));
            comboUser.setValue(users.stream().filter(user -> Objects.equals(user.id(), query.userId()))
                    .findFirst().orElse(AuditUserOption.ALL));

            List<String> types = new ArrayList<>();
            types.add("");
            types.addAll(options.eventTypes());
            comboEvent.setItems(FXCollections.observableArrayList(types));
            comboEvent.setValue(types.contains(query.eventType()) ? query.eventType() : "");
            optionsLoaded = true;
        } finally {
            syncing = false;
        }
    }

    private void showPage(AuditAdminEventPage page) {
        int pageCount = page.pageCount(query.pageSize());
        int clamped = Math.min(query.page(), pageCount - 1);
        if (clamped != query.page()) {
            load(query.withPage(clamped));
            return;
        }
        table.setItems(FXCollections.observableArrayList(page.rows()));
        labelTotal.setText(String.valueOf(page.summary().total()));
        labelExports.setText(String.valueOf(page.summary().exports()));
        labelDeletes.setText(String.valueOf(page.summary().deletions()));
        labelPolicies.setText(String.valueOf(page.summary().retentionChanges()));
        labelCleanups.setText(String.valueOf(page.summary().cleanups()));
        labelStatus.setText(text("audit.admin.status.page", query.page() + 1, pageCount,
                page.summary().total()));
        syncing = true;
        try {
            pagination.setPageCount(pageCount);
            pagination.setCurrentPageIndex(query.page());
        } finally {
            syncing = false;
        }
        table.getSelectionModel().clearSelection();
        showSelection(null);
    }

    private void showSelection(AuditAdminEvent row) {
        if (row == null) {
            labelSelection.setText(text("audit.admin.details.none"));
            txtReason.clear();
            txtDetails.clear();
            return;
        }
        labelSelection.setText(text("audit.admin.details.selected", row.id(), eventLabel(row.eventType())));
        txtReason.setText(Objects.toString(row.reason(), ""));
        txtDetails.setText(AuditJsonFormatter.display(row.details()));
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        btnApply.setDisable(busy);
        btnClear.setDisable(busy);
        btnRefresh.setDisable(busy);
    }

    private String actorLabel(AuditAdminEvent row) {
        if (row.actorName() != null && !row.actorName().isBlank()) return row.actorName();
        return row.actorUserId() == null ? text("audit.log.actor.system")
                : text("audit.log.actor.id", row.actorUserId());
    }

    private String workstationLabel(AuditAdminEvent row) {
        if (row.workstationName() != null && !row.workstationName().isBlank()) return row.workstationName();
        return row.workstationId() == null || row.workstationId().isBlank() ? "" : row.workstationId();
    }

    private String sourceLabel(AuditAdminEvent row) {
        return switch (Objects.toString(row.source(), "").toUpperCase()) {
            case "APP" -> text("audit.log.source.app");
            case "SYSTEM" -> text("audit.log.source.system");
            case "DATABASE" -> text("audit.log.source.database");
            default -> Objects.toString(row.source(), "");
        };
    }

    private static String eventLabel(String eventType) {
        return switch (Objects.toString(eventType, "").toUpperCase()) {
            case "EXPORT" -> text("audit.admin.event.export");
            case "DELETE_SELECTED" -> text("audit.admin.event.delete.selected");
            case "RETENTION_POLICY" -> text("audit.admin.event.retention.policy");
            case "RETENTION_CLEANUP" -> text("audit.admin.event.retention.cleanup");
            default -> Objects.toString(eventType, "");
        };
    }

    private static <T> StringConverter<T> converter(Function<T, String> display, T emptyValue) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return display.apply(value); }
            @Override public T fromString(String value) { return emptyValue; }
        };
    }

    private static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }

    private record LoadResult(AuditAdminEventPage page, AuditAdminOptions options) {
    }
}
