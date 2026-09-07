package com.hamza.account.controller.users;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.UsersChanged;
import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.features.users.UserManagementPage;
import com.hamza.account.features.users.UserManagementQuery;
import com.hamza.account.features.users.UserStatusFilter;
import com.hamza.account.features.users.UserSummary;
import com.hamza.account.features.users.UsersManagementService;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

/** A dedicated, credential-safe user-management screen. */
@FxmlPath(pathFile = "users-management.fxml")
public final class UserController implements AppSettingInterface {

    private final UsersManagementService managementService = ServiceRegistry.get(UsersManagementService.class);
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final RbacService rbacService = ServiceRegistry.get(RbacService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));
    private UserManagementQuery query = UserManagementQuery.firstPage();
    private long loadToken;
    private boolean syncing;

    /**
     * Built here rather than in the FXML because the page factory returns it: a node has
     * one parent, so a table declared beside the Pagination is torn out of its own slot
     * the first time a page is laid out. {@code InventoryController} is the same shape.
     */
    private final TableView<UserSummary> tableUsers = new TableView<>();

    @FXML private Pane root;
    @FXML private TextField textSearch;
    @FXML private ComboBox<UserStatusFilter> comboStatus;
    @FXML private Pagination pagination;
    @FXML private Label labelTotal, labelActive, labelInactive, labelKiosk, labelStatus;
    @FXML private ProgressIndicator progress;
    @FXML private Button btnNew, btnEdit, btnToggle, btnPermissions, btnRefresh, btnClose;

    @FXML
    public void initialize() {
        configureColumns();
        configureFilter();
        configureActions();
        if (eventBus != null) subscriptions.add(eventBus.subscribe(UsersChanged.class, event -> reload()));
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        subscriptions.disposeWith(root);
        reload();
        Platform.runLater(textSearch::requestFocus);
    }

    private void configureColumns() {
        tableUsers.setId("usersManagementTable");
        tableUsers.getStyleClass().add("modern-table");
        tableUsers.getColumns().setAll(
                column("colCode", "code", 70, row -> String.valueOf(row.id())),
                column("colUsername", "user.management.column.username", 180, UserSummary::username),
                column("colRoles", "user.management.column.roles", 310, UserSummary::roleNames),
                column("colType", "user.management.column.type", 130, row -> text(row.kioskOnly()
                        ? "user.management.type.kiosk" : "user.management.type.standard")),
                column("colStatus", "user.management.column.status", 110, row -> text(row.active()
                        ? "user.management.status.active" : "user.management.status.inactive")),
                column("colAvailable", "user.management.column.availability", 110, row -> text(row.available()
                        ? "user.management.availability.online" : "user.management.availability.offline")));
        tableUsers.setPlaceholder(new Label(text("user.management.placeholder.empty")));
        tableUsers.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> updateActions());
        tableUsers.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && tableUsers.getSelectionModel().getSelectedItem() != null) editSelected();
        });
        TableSetting.tableMenuSetting(getClass(), tableUsers);
    }

    /** An id is given so {@link TableSetting} remembers a width against the column, not an index. */
    private static TableColumn<UserSummary, String> column(String id, String titleKey, double width,
                                                           java.util.function.Function<UserSummary, String> value) {
        TableColumn<UserSummary, String> column = new TableColumn<>(text(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private void configureFilter() {
        comboStatus.getItems().setAll(UserStatusFilter.values());
        comboStatus.setConverter(new StringConverter<>() {
            @Override public String toString(UserStatusFilter value) {
                if (value == null) return "";
                return switch (value) {
                    case ALL -> text("user.management.filter.all");
                    case ACTIVE -> text("user.management.filter.active");
                    case INACTIVE -> text("user.management.filter.inactive");
                };
            }
            @Override public UserStatusFilter fromString(String value) { return UserStatusFilter.ALL; }
        });
        comboStatus.setValue(UserStatusFilter.ALL);
        comboStatus.valueProperty().addListener((obs, old, value) -> {
            if (!syncing) load(query.withStatus(value));
        });
        textSearch.textProperty().addListener((obs, old, value) -> {
            searchDelay.setOnFinished(event -> load(query.withSearch(value)));
            searchDelay.playFromStart();
        });
        pagination.setPageFactory(index -> {
            if (!syncing && index != query.page()) load(query.withPage(index));
            return tableUsers;
        });
    }

    private void configureActions() {
        btnNew.setGraphic(AppIcon.ADD.graphic());
        btnEdit.setGraphic(AppIcon.EDIT.graphic());
        btnToggle.setGraphic(AppIcon.CONFIRM.graphic());
        btnPermissions.setGraphic(AppIcon.SETTINGS.graphic());
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic());
        btnClose.setGraphic(AppIcon.CLOSE.graphic());
        btnClose.setOnAction(event -> ((Stage) btnClose.getScene().getWindow()).close());
        btnNew.setOnAction(event -> openEditor(0));
        btnEdit.setOnAction(event -> editSelected());
        btnToggle.setOnAction(event -> toggleSelected());
        btnPermissions.setOnAction(event -> openPermissions());
        btnRefresh.setOnAction(event -> reload());
        updateActions();
    }

    private void editSelected() {
        UserSummary selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected != null && selected.id() != 1 && AuthorizationGuard.isGranted(AppPermissions.USERS_MANAGE)) {
            openEditor(selected.id());
        }
    }

    private void openEditor(int userId) {
        try {
            new AddForAllApplication(userId, new AddUserController(userId));
        } catch (Exception error) {
            AllAlerts.handleError(text("user.management.error.open.editor"), error);
        }
    }

    private void toggleSelected() {
        UserSummary selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null || selected.id() == 1) return;
        try {
            usersService.updateActive(selected.id(), !selected.active());
            if (eventBus != null) eventBus.publish(new UsersChanged());
            else reload();
        } catch (Exception error) {
            AllAlerts.handleError(text("user.management.error.update.status"), error);
        }
    }

    private void openPermissions() {
        UserSummary selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null || selected.id() == 1
                || !AuthorizationGuard.isGranted(AppPermissions.ROLES_MANAGE)) return;
        try {
            new OpenApplication<>(new UserPermissionController(selected.id(), selected.username(), rbacService));
        } catch (Exception error) {
            AllAlerts.handleError(text("user.management.error.open.permissions"), error);
        }
    }

    private void updateActions() {
        UserSummary selected = tableUsers == null ? null : tableUsers.getSelectionModel().getSelectedItem();
        boolean canManage = AuthorizationGuard.isGranted(AppPermissions.USERS_MANAGE);
        boolean protectedAdmin = selected != null && selected.id() == 1;
        btnNew.setDisable(!canManage);
        btnEdit.setDisable(!canManage || selected == null || protectedAdmin);
        btnToggle.setDisable(!canManage || selected == null || protectedAdmin);
        // Row 1 is refused here as it is for edit and toggle: UserSessionContext treats it
        // as the system administrator and bypasses every permission, so a role saved
        // against it changes nothing and reads as though it had.
        btnPermissions.setDisable(!AuthorizationGuard.isGranted(AppPermissions.ROLES_MANAGE)
                || selected == null || protectedAdmin);
        btnToggle.setText(text(selected != null && selected.active()
                ? "user.management.action.deactivate" : "user.management.action.activate"));
    }

    private void reload() { load(query); }

    private void load(UserManagementQuery next) {
        if (managementService == null) return;
        query = next;
        long token = ++loadToken;
        setBusy(true);
        Task<UserManagementPage> task = new Task<>() {
            @Override protected UserManagementPage call() throws DaoException { return managementService.load(next); }
        };
        task.setOnSucceeded(event -> {
            if (token != loadToken) return;
            setBusy(false);
            show(task.getValue());
        });
        task.setOnFailed(event -> {
            if (token != loadToken) return;
            setBusy(false);
            AllAlerts.handleError(text("user.management.error.load"), task.getException());
        });
        Thread thread = new Thread(task, "users-management-load-" + token);
        thread.setDaemon(true);
        thread.start();
    }

    private void show(UserManagementPage page) {
        int clamped = Math.min(query.page(), page.pageCount(query.pageSize()) - 1);
        if (clamped != query.page()) {
            load(query.withPage(clamped));
            return;
        }
        tableUsers.setItems(FXCollections.observableArrayList(page.rows()));
        labelTotal.setText(String.valueOf(page.totalRows()));
        labelActive.setText(String.valueOf(page.activeRows()));
        labelInactive.setText(String.valueOf(page.inactiveRows()));
        labelKiosk.setText(String.valueOf(page.kioskRows()));
        labelStatus.setText(text("user.management.label.page", query.page() + 1,
                page.pageCount(query.pageSize()), page.totalRows()));
        syncing = true;
        try {
            pagination.setPageCount(page.pageCount(query.pageSize()));
            pagination.setCurrentPageIndex(query.page());
            comboStatus.setValue(query.status());
        } finally {
            syncing = false;
        }
        updateActions();
    }

    /**
     * The spinner alone, as {@code InventoryController} does it. Disabling the controls
     * meant a search that fires 350ms after a keystroke took the focus out of the field
     * mid-word and dropped what was typed next, and greyed the table on every letter.
     */
    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        btnRefresh.setDisable(busy);
        if (!busy) updateActions();
    }

    @Override public Pane pane() throws Exception { return new OpenFxmlApplication(this).getPane(); }
    @Override public String title() { return text("user.management.title"); }
    @Override public boolean resize() { return true; }
    @Override public double minWidth() { return 980; }
    @Override public double minHeight() { return 650; }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }
}
