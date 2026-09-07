package com.hamza.account.controller.others;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.audit.AuditActionFilter;
import com.hamza.account.features.audit.AuditExportFormat;
import com.hamza.account.features.audit.AuditExportResult;
import com.hamza.account.features.audit.AuditDiffRow;
import com.hamza.account.features.audit.AuditDiffKind;
import com.hamza.account.features.audit.AuditJsonDiff;
import com.hamza.account.features.audit.AuditLogEntry;
import com.hamza.account.features.audit.AuditLogExportService;
import com.hamza.account.features.audit.AuditLogOptions;
import com.hamza.account.features.audit.AuditLogPage;
import com.hamza.account.features.audit.AuditLogQuery;
import com.hamza.account.features.audit.AuditLogService;
import com.hamza.account.features.audit.AuditLogSort;
import com.hamza.account.features.audit.AuditSourceFilter;
import com.hamza.account.features.audit.AuditTableLabels;
import com.hamza.account.features.audit.AuditUserOption;
import com.hamza.account.features.audit.AuditRetentionPolicy;
import com.hamza.account.features.audit.AuditRetentionPreview;
import com.hamza.account.features.audit.AuditRetentionService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.AuditAdminEventsApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** A paged, index-backed browser for the database audit trail. */
@FxmlPath(pathFile = "process-view.fxml")
public final class ProcessesController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AuditLogService auditLogService = ServiceRegistry.get(AuditLogService.class);
    private final AuditLogExportService exportService = ServiceRegistry.get(AuditLogExportService.class);
    private final AuditRetentionService retentionService = ServiceRegistry.get(AuditRetentionService.class);
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));
    private final TableView<AuditLogEntry> tableView = new TableView<>();
    private final TableView<AuditDiffRow> diffTable = new TableView<>();
    private List<AuditDiffRow> currentDiff = List.of();
    private AuditLogQuery query = AuditLogQuery.recent(LocalDate.now());
    private long loadToken;
    private boolean syncing;
    private boolean optionsLoaded;

    @FXML private StackPane root;
    @FXML private Label labelTitle, labelTotal, labelInserts, labelUpdates, labelDeletes;
    @FXML private Label labelStatus, labelSelection;
    @FXML private TextField txtSearch;
    @FXML private DatePicker dateFrom, dateTo;
    @FXML private ComboBox<AuditUserOption> comboUser;
    @FXML private ComboBox<AuditActionFilter> comboAction;
    @FXML private ComboBox<String> comboTable;
    @FXML private ComboBox<AuditSourceFilter> comboSource;
    @FXML private ComboBox<AuditLogSort> comboSort;
    @FXML private Pagination pagination;
    @FXML private ProgressIndicator progress;
    @FXML private StackPane diffHost;
    @FXML private CheckBox chkChangedOnly;
    @FXML private Button btnApply, btnClear, btnRefresh, btnDelete, btnClose;
    @FXML private Button btnExportExcel, btnExportPdf, btnRetention, btnAdminEvents;

    @FXML
    public void initialize() {
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        configureTable();
        configureDiffTable();
        configureFilters();
        configureActions();
        dateFrom.setValue(query.from());
        dateTo.setValue(query.to());
        load(query);
    }

    private void configureDiffTable() {
        diffTable.setId("auditDiffTable");
        diffTable.getStyleClass().addAll("modern-table", "audit-diff-table");
        diffTable.getColumns().setAll(
                diffColumn("diffField", "audit.diff.column.field", 190, AuditDiffRow::field),
                diffColumn("diffBefore", "audit.diff.column.before", 330, AuditDiffRow::before),
                diffColumn("diffAfter", "audit.diff.column.after", 330, AuditDiffRow::after),
                diffColumn("diffKind", "audit.diff.column.change", 130,
                        row -> text(row.kind().labelKey())));
        diffTable.setPlaceholder(new Label(text("audit.diff.placeholder.empty")));
        diffTable.setRowFactory(ignored -> new TableRow<>() {
            @Override protected void updateItem(AuditDiffRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("audit-diff-added", "audit-diff-removed",
                        "audit-diff-changed", "audit-diff-unchanged");
                if (!empty && row != null) {
                    getStyleClass().add("audit-diff-" + row.kind().name().toLowerCase(Locale.ROOT));
                }
            }
        });
        diffHost.getChildren().setAll(diffTable);
        chkChangedOnly.setSelected(true);
        chkChangedOnly.selectedProperty().addListener((obs, old, value) -> showDiff());
    }

    private void configureTable() {
        tableView.setId("auditLogTable");
        tableView.getStyleClass().add("modern-table");
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getColumns().setAll(
                column("auditId", "audit.log.column.id", 80, row -> String.valueOf(row.id())),
                column("auditTime", "audit.log.column.time", 155, row -> DATE_TIME.format(row.actionTime())),
                column("auditActor", "audit.log.column.actor", 145, this::actorLabel),
                column("auditAction", "audit.log.column.action", 105, row -> text(row.action().labelKey())),
                column("auditTable", "audit.log.column.table", 155, row -> tableLabel(row.tableName())),
                column("auditRecord", "audit.log.column.record", 105, AuditLogEntry::recordId),
                column("auditSource", "audit.log.column.source", 125, this::sourceLabel),
                column("auditWorkstation", "audit.log.column.workstation", 145, this::workstationLabel),
                column("auditNotes", "audit.log.column.notes", 210, AuditLogEntry::notes));
        tableView.setPlaceholder(new Label(text("audit.log.placeholder.empty")));
        tableView.setRowFactory(ignored -> new TableRow<>() {
            @Override protected void updateItem(AuditLogEntry row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("audit-row-insert", "audit-row-update", "audit-row-delete");
                if (!empty && row != null) {
                    getStyleClass().add(switch (row.action()) {
                        case INSERT -> "audit-row-insert";
                        case UPDATE -> "audit-row-update";
                        case DELETE -> "audit-row-delete";
                    });
                }
            }
        });
        tableView.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<AuditLogEntry>) change -> showSelection());
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private static TableColumn<AuditLogEntry, String> column(String id, String titleKey, double width,
                                                              Function<AuditLogEntry, String> value) {
        TableColumn<AuditLogEntry, String> column = new TableColumn<>(text(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static TableColumn<AuditDiffRow, String> diffColumn(String id, String titleKey, double width,
                                                                Function<AuditDiffRow, String> value) {
        TableColumn<AuditDiffRow, String> column = new TableColumn<>(text(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Objects.toString(value.apply(cell.getValue()), "")));
        return column;
    }

    private void configureFilters() {
        comboAction.getItems().setAll(AuditActionFilter.values());
        comboAction.setConverter(converter(value -> value == null ? "" : text(value.labelKey()), AuditActionFilter.ALL));
        comboAction.setValue(AuditActionFilter.ALL);

        comboSource.getItems().setAll(AuditSourceFilter.values());
        comboSource.setConverter(converter(value -> value == null ? "" : text(value.labelKey()), AuditSourceFilter.ALL));
        comboSource.setValue(AuditSourceFilter.ALL);

        comboSort.getItems().setAll(AuditLogSort.values());
        comboSort.setConverter(converter(value -> value == null ? "" : text(value.labelKey()), AuditLogSort.NEWEST));
        comboSort.setValue(AuditLogSort.NEWEST);

        comboUser.getItems().setAll(AuditUserOption.ALL);
        comboUser.setConverter(converter(value -> value == null || value.isAll()
                ? text("audit.log.filter.all.users") : value.name(), AuditUserOption.ALL));
        comboUser.setValue(AuditUserOption.ALL);

        comboTable.getItems().setAll("");
        comboTable.setConverter(converter(value -> value == null || value.isBlank()
                ? text("audit.log.filter.all.tables") : tableLabel(value), ""));
        comboTable.setValue("");

        pagination.setPageFactory(index -> {
            if (!syncing && index != query.page()) load(query.withPage(index));
            return tableView;
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
        btnExportExcel.setGraphic(AppIcon.SPREADSHEET.graphic());
        btnExportPdf.setGraphic(AppIcon.EXPORT.graphic());
        btnRetention.setGraphic(AppIcon.SETTINGS.graphic());
        btnAdminEvents.setGraphic(AppIcon.SHOW.graphic());
        btnDelete.setGraphic(AppIcon.DELETE.graphic());
        btnClose.setGraphic(AppIcon.CLOSE.graphic());

        btnApply.setOnAction(event -> applyFilters());
        btnClear.setOnAction(event -> clearFilters());
        btnRefresh.setOnAction(event -> load(query));
        btnExportExcel.setOnAction(event -> export(AuditExportFormat.EXCEL));
        btnExportPdf.setOnAction(event -> export(AuditExportFormat.PDF));
        btnRetention.setOnAction(event -> openRetention());
        btnAdminEvents.setOnAction(event -> openAdminEvents());
        btnDelete.setOnAction(event -> deleteSelected());
        btnClose.setOnAction(event -> ((Stage) btnClose.getScene().getWindow()).close());
        btnDelete.setDisable(true);
        permissionVisibility(btnExportExcel, AppPermissions.AUDIT_EXPORT);
        permissionVisibility(btnExportPdf, AppPermissions.AUDIT_EXPORT);
        permissionVisibility(btnRetention, AppPermissions.AUDIT_RETENTION_MANAGE);
        permissionVisibility(btnAdminEvents, AppPermissions.AUDIT_ADMIN_VIEW);
    }

    private void applyFilters() {
        try {
            AuditUserOption user = comboUser.getValue();
            query = new AuditLogQuery(txtSearch.getText(), dateFrom.getValue(), dateTo.getValue(),
                    user == null ? null : user.id(), comboAction.getValue(), comboTable.getValue(),
                    comboSource.getValue(), comboSort.getValue(), 0, AuditLogQuery.DEFAULT_PAGE_SIZE);
            load(query);
        } catch (IllegalArgumentException | NullPointerException error) {
            String key = error.getMessage() != null && error.getMessage().startsWith("audit.log.")
                    ? error.getMessage() : "audit.log.validation.date.required";
            validation(key);
        }
    }

    private void clearFilters() {
        syncing = true;
        try {
            query = AuditLogQuery.recent(LocalDate.now());
            txtSearch.clear();
            dateFrom.setValue(query.from());
            dateTo.setValue(query.to());
            comboUser.setValue(AuditUserOption.ALL);
            comboAction.setValue(AuditActionFilter.ALL);
            comboTable.setValue("");
            comboSource.setValue(AuditSourceFilter.ALL);
            comboSort.setValue(AuditLogSort.NEWEST);
        } finally {
            syncing = false;
        }
        load(query);
    }

    private void load(AuditLogQuery next) {
        if (auditLogService == null) return;
        query = next;
        long token = ++loadToken;
        boolean loadOptions = !optionsLoaded;
        setBusy(true);
        Task<LoadResult> task = new Task<>() {
            @Override protected LoadResult call() throws Exception {
                AuditLogOptions options = loadOptions ? auditLogService.options() : null;
                return new LoadResult(auditLogService.load(next), options);
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
            AllAlerts.handleError(text("audit.log.error.load"), task.getException());
        });
        Thread thread = new Thread(task, "audit-log-load-" + token);
        thread.setDaemon(true);
        thread.start();
    }

    private void showOptions(AuditLogOptions options) {
        syncing = true;
        try {
            List<AuditUserOption> users = new ArrayList<>();
            users.add(AuditUserOption.ALL);
            users.addAll(options.users());
            comboUser.setItems(FXCollections.observableArrayList(users));
            comboUser.setValue(users.stream()
                    .filter(user -> Objects.equals(user.id(), query.userId()))
                    .findFirst()
                    .orElse(AuditUserOption.ALL));

            List<String> tables = new ArrayList<>();
            tables.add("");
            tables.addAll(options.tables());
            comboTable.setItems(FXCollections.observableArrayList(tables));
            comboTable.setValue(tables.contains(query.tableName()) ? query.tableName() : "");
            optionsLoaded = true;
        } finally {
            syncing = false;
        }
    }

    private void showPage(AuditLogPage page) {
        int pageCount = page.pageCount(query.pageSize());
        int clamped = Math.min(query.page(), pageCount - 1);
        if (clamped != query.page()) {
            load(query.withPage(clamped));
            return;
        }
        tableView.setItems(FXCollections.observableArrayList(page.rows()));
        labelTotal.setText(String.valueOf(page.summary().total()));
        labelInserts.setText(String.valueOf(page.summary().inserts()));
        labelUpdates.setText(String.valueOf(page.summary().updates()));
        labelDeletes.setText(String.valueOf(page.summary().deletes()));
        labelStatus.setText(text("audit.log.status.page", query.page() + 1, pageCount, page.summary().total()));
        syncing = true;
        try {
            pagination.setPageCount(pageCount);
            pagination.setCurrentPageIndex(query.page());
        } finally {
            syncing = false;
        }
        clearDetails();
    }

    private void showSelection() {
        List<AuditLogEntry> selected = tableView.getSelectionModel().getSelectedItems();
        btnDelete.setDisable(selected.isEmpty() || !AuthorizationGuard.isGranted(AppPermissions.AUDIT_DELETE));
        if (selected.isEmpty()) {
            clearDetails();
            return;
        }
        AuditLogEntry row = selected.getFirst();
        labelSelection.setText(selected.size() == 1
                ? text("audit.log.details.selected.one", row.id(), tableLabel(row.tableName()), row.recordId())
                : text("audit.log.details.selected.many", selected.size(), row.id(),
                        tableLabel(row.tableName()), row.recordId()));
        currentDiff = AuditJsonDiff.compare(row.oldData(), row.newData());
        showDiff();
    }

    private void showDiff() {
        List<AuditDiffRow> visible = chkChangedOnly.isSelected()
                ? currentDiff.stream().filter(row -> row.kind() != AuditDiffKind.UNCHANGED)
                        .toList()
                : currentDiff;
        diffTable.setItems(FXCollections.observableArrayList(visible));
    }

    private void clearDetails() {
        labelSelection.setText(text("audit.log.details.none"));
        currentDiff = List.of();
        diffTable.getItems().clear();
        btnDelete.setDisable(true);
    }

    private void deleteSelected() {
        List<Long> ids = tableView.getSelectionModel().getSelectedItems().stream()
                .map(AuditLogEntry::id).toList();
        if (ids.isEmpty() || !AuthorizationGuard.isGranted(AppPermissions.AUDIT_DELETE)) return;
        TextInputDialog reasonDialog = new TextInputDialog();
        reasonDialog.setTitle(text("audit.log.delete.title"));
        reasonDialog.setHeaderText(text("audit.log.delete.reason.header"));
        reasonDialog.setContentText(text("audit.log.reason.label"));
        reasonDialog.getEditor().setPromptText(text("audit.log.reason.prompt"));
        prepareDialog(reasonDialog);
        String reason = reasonDialog.showAndWait().orElse("").trim();
        if (reason.length() < 5 || reason.length() > 500) {
            validation("audit.log.reason.validation");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                text("audit.log.delete.confirm", ids.size()), ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle(text("audit.log.delete.title"));
        confirm.setHeaderText(text("audit.log.delete.header"));
        prepareDialog(confirm);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setBusy(true);
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws Exception { return auditLogService.delete(ids, reason); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            labelStatus.setText(text("audit.log.delete.done", task.getValue()));
            optionsLoaded = false;
            load(query);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            AllAlerts.handleError(text("audit.log.error.delete"), task.getException());
        });
        Thread thread = new Thread(task, "audit-log-delete");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        btnRefresh.setDisable(busy);
        btnApply.setDisable(busy);
        btnClear.setDisable(busy);
        btnExportExcel.setDisable(busy);
        btnExportPdf.setDisable(busy);
        btnRetention.setDisable(busy);
        btnAdminEvents.setDisable(busy);
        if (busy) btnDelete.setDisable(true);
        else {
            showSelection();
            btnExportExcel.setDisable(!AuthorizationGuard.isGranted(AppPermissions.AUDIT_EXPORT));
            btnExportPdf.setDisable(!AuthorizationGuard.isGranted(AppPermissions.AUDIT_EXPORT));
            btnRetention.setDisable(!AuthorizationGuard.isGranted(AppPermissions.AUDIT_RETENTION_MANAGE));
            btnAdminEvents.setDisable(!AuthorizationGuard.isGranted(AppPermissions.AUDIT_ADMIN_VIEW));
        }
    }

    private void openAdminEvents() {
        if (!AuthorizationGuard.isGranted(AppPermissions.AUDIT_ADMIN_VIEW)) return;
        try {
            AuditAdminEventsApplication.show(root.getScene().getWindow());
        } catch (Exception error) {
            AllAlerts.handleError(text("audit.admin.error.open"), error);
        }
    }

    private void export(AuditExportFormat format) {
        if (exportService == null || !AuthorizationGuard.isGranted(AppPermissions.AUDIT_EXPORT)) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("audit.log.export.choose"));
        chooser.setInitialFileName("audit_log_" + java.time.LocalDateTime.now().format(FILE_TIME)
                + "." + format.extension());
        String pattern = "*." + format.extension();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                text(format == AuditExportFormat.EXCEL
                        ? "audit.log.export.filter.excel" : "audit.log.export.filter.pdf"), pattern));
        File selected = chooser.showSaveDialog(root.getScene().getWindow());
        if (selected == null) return;
        selected = ensureExtension(selected, format);
        File target = selected;

        setBusy(true);
        Task<AuditExportResult> task = new Task<>() {
            @Override protected AuditExportResult call() throws Exception {
                return exportService.export(query, format, target.toPath());
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            labelStatus.setText(text("audit.log.export.done", task.getValue().rowCount(),
                    task.getValue().file().getFileName()));
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showOperationError("audit.log.error.export", task.getException());
        });
        start(task, "audit-log-export-" + format.name().toLowerCase());
    }

    private static File ensureExtension(File selected, AuditExportFormat format) {
        String extension = "." + format.extension().toLowerCase(Locale.ROOT);
        if (selected.getName().toLowerCase(Locale.ROOT).endsWith(extension)) return selected;
        return new File(selected.getParentFile(), selected.getName() + extension);
    }

    private void openRetention() {
        if (retentionService == null
                || !AuthorizationGuard.isGranted(AppPermissions.AUDIT_RETENTION_MANAGE)) return;
        setBusy(true);
        Task<AuditRetentionPolicy> task = new Task<>() {
            @Override protected AuditRetentionPolicy call() throws Exception { return retentionService.policy(); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            AuditRetentionDialog.show(task.getValue()).ifPresent(this::handleRetention);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showOperationError("audit.log.error.retention", task.getException());
        });
        start(task, "audit-retention-load");
    }

    private void handleRetention(AuditRetentionDialog.Request request) {
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 5 || reason.length() > 500) {
            validation("audit.log.reason.validation");
            return;
        }
        if (request.action() == AuditRetentionDialog.Action.SAVE && !request.enabled()) {
            saveRetention(request, reason);
            return;
        }
        previewRetention(request, reason);
    }

    private void previewRetention(AuditRetentionDialog.Request request, String reason) {
        setBusy(true);
        Task<AuditRetentionPreview> task = new Task<>() {
            @Override protected AuditRetentionPreview call() throws Exception {
                return retentionService.preview(request.days());
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            AuditRetentionPreview preview = task.getValue();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    text(request.action() == AuditRetentionDialog.Action.CLEAN_NOW
                                    ? "audit.log.retention.preview.clean" : "audit.log.retention.preview.enable",
                            preview.matchingRows(), DATE_TIME.format(preview.cutoff())),
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle(text("audit.log.retention.title"));
            confirm.setHeaderText(text("audit.log.retention.preview.header"));
            prepareDialog(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            if (request.action() == AuditRetentionDialog.Action.CLEAN_NOW) {
                cleanRetention(request.days(), reason);
            } else {
                saveRetention(request, reason);
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showOperationError("audit.log.error.retention", task.getException());
        });
        start(task, "audit-retention-preview");
    }

    private void saveRetention(AuditRetentionDialog.Request request, String reason) {
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                retentionService.save(request.enabled(), request.days(), reason);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            labelStatus.setText(request.enabled()
                    ? text("audit.log.retention.saved.enabled", request.days())
                    : text("audit.log.retention.saved.disabled"));
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showOperationError("audit.log.error.retention", task.getException());
        });
        start(task, "audit-retention-save");
    }

    private void cleanRetention(int days, String reason) {
        setBusy(true);
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws Exception { return retentionService.cleanNow(days, reason); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            labelStatus.setText(text("audit.log.retention.cleaned", task.getValue()));
            optionsLoaded = false;
            load(query);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showOperationError("audit.log.error.retention", task.getException());
        });
        start(task, "audit-retention-clean");
    }

    private void showOperationError(String titleKey, Throwable failure) {
        String message = failure == null ? "" : failure.getMessage();
        if (message != null && message.startsWith("audit.log.")) {
            validation(message);
            return;
        }
        AllAlerts.handleError(text(titleKey), failure);
    }

    private static void permissionVisibility(Button button, com.hamza.account.authorization.PermissionKey permission) {
        boolean visible = AuthorizationGuard.isGranted(permission);
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private String actorLabel(AuditLogEntry row) {
        if (!row.actorName().isBlank()) return row.actorName();
        return text("SYSTEM".equalsIgnoreCase(row.source())
                ? "audit.log.actor.system" : "audit.log.actor.unknown");
    }

    private String workstationLabel(AuditLogEntry row) {
        if (!row.workstationName().isBlank()) return row.workstationName();
        return row.workstationId().isBlank() ? text("audit.log.workstation.unknown") : row.workstationId();
    }

    private String sourceLabel(AuditLogEntry row) {
        return switch (row.source().toUpperCase()) {
            case "APP" -> text("audit.log.source.app");
            case "SYSTEM" -> text("audit.log.source.system");
            case "DATABASE" -> text("audit.log.source.database");
            default -> row.source();
        };
    }

    private static String tableLabel(String tableName) {
        String key = AuditTableLabels.keyFor(tableName);
        return key == null ? tableName : text(key);
    }

    private void validation(String key) {
        AllAlerts.handleError(text("audit.log.title"), new UserValidationException(text(key)));
    }

    private static void prepareDialog(Dialog<?> dialog) {
        dialog.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        ok.setText(text("ok"));
        cancel.setText(text("cancel"));
        ThemeManager.apply(dialog.getDialogPane().getScene());
    }

    private static <T> StringConverter<T> converter(Function<T, String> display, T fallback) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return display.apply(value); }
            @Override public T fromString(String value) { return fallback; }
        };
    }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }

    private record LoadResult(AuditLogPage page, AuditLogOptions options) {
    }
}
