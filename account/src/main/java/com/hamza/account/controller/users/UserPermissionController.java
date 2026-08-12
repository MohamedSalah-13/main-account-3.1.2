package com.hamza.account.controller.users;

import com.hamza.account.features.rbac.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** RBAC editor: assigns roles to a user and manages each role's permission bundle. */
@Log4j2
@FxmlPath(pathFile = "user-permission.fxml")
public final class UserPermissionController implements AppSettingInterface {

    private final int userId;
    private final String username;
    private final RbacService rbacService;
    private final ObservableList<UserRoleRow> userRoleRows = FXCollections.observableArrayList();
    private final ObservableList<RolePermissionRow> permissionRows = FXCollections.observableArrayList();
    private final ObservableList<UserRoleRow> parentRoleRows = FXCollections.observableArrayList();
    private final ObservableList<RbacUserOverride> overrideRows = FXCollections.observableArrayList();
    private final ObservableList<RbacAccessDecision> accessRows = FXCollections.observableArrayList();
    private FilteredList<RolePermissionRow> filteredPermissions;
    private FilteredList<RbacAccessDecision> filteredAccess;
    private RbacRole editingRole;
    private boolean creatingRole;
    private boolean canManageOverrides;

    @FXML private Label labelUser;
    @FXML private TableView<UserRoleRow> tableUserRoles;
    @FXML private TableColumn<UserRoleRow, Boolean> colRoleAssigned;
    @FXML private TableColumn<UserRoleRow, String> colUserRoleName, colUserRoleCode;
    @FXML private ComboBox<RbacRole> comboRoles;
    @FXML private TextField textRoleCode, textRoleName, textPermissionSearch;
    @FXML private TextArea textRoleDescription;
    @FXML private CheckBox checkRoleActive, checkAssignNewRole;
    @FXML private Button btnNewRole, btnDeleteRole;
    @FXML private TableView<RolePermissionRow> tablePermissions;
    @FXML private TableView<UserRoleRow> tableParentRoles;
    @FXML private TableColumn<UserRoleRow, Boolean> colParentRoleInherited;
    @FXML private TableColumn<UserRoleRow, String> colParentRoleName, colParentRoleCode;
    @FXML private TableColumn<RolePermissionRow, Boolean> colPermissionGranted;
    @FXML private TableColumn<RolePermissionRow, String> colPermissionCategory, colPermissionDescription,
            colPermissionCode;
    @FXML private ComboBox<RbacPermission> comboOverridePermission;
    @FXML private ComboBox<RbacOverrideEffect> comboOverrideEffect;
    @FXML private TextField textOverrideReason, textAccessSearch;
    @FXML private DatePicker dateOverrideExpiry;
    @FXML private Button btnSaveOverride, btnDeleteOverride, btnClearOverride;
    @FXML private TableView<RbacUserOverride> tableOverrides;
    @FXML private TableColumn<RbacUserOverride, String> colOverrideEffect, colOverridePermission,
            colOverrideCode, colOverrideReason, colOverrideExpiry, colOverrideStatus;
    @FXML private TableView<RbacAccessDecision> tableEffectiveAccess;
    @FXML private TableColumn<RbacAccessDecision, String> colAccessGranted, colAccessPermission,
            colAccessCode, colAccessSource;

    public UserPermissionController(int userId, String username, RbacService rbacService) {
        this.userId = userId;
        this.username = username;
        this.rbacService = rbacService;
    }

    @FXML
    public void initialize() {
        configureTables();
        configureRoleSelector();
        btnNewRole.setOnAction(event -> beginNewRole());
        btnDeleteRole.setOnAction(event -> deleteSelectedRole());
        btnSaveOverride.setOnAction(event -> saveOverride());
        btnDeleteOverride.setOnAction(event -> deleteSelectedOverride());
        btnClearOverride.setOnAction(event -> clearOverrideForm());
        textPermissionSearch.textProperty().addListener((obs, old, value) -> filterPermissions(value));
        textAccessSearch.textProperty().addListener((obs, old, value) -> filterAccess(value));
        loadData();
    }

    private void configureTables() {
        tableUserRoles.setEditable(true);
        colRoleAssigned.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        colRoleAssigned.setCellFactory(CheckBoxTableCell.forTableColumn(colRoleAssigned));
        colUserRoleName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().role().name()));
        colUserRoleCode.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().role().code()));
        tableUserRoles.setItems(userRoleRows);

        tableParentRoles.setEditable(true);
        colParentRoleInherited.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        colParentRoleInherited.setCellFactory(CheckBoxTableCell.forTableColumn(colParentRoleInherited));
        colParentRoleName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().role().name()));
        colParentRoleCode.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().role().code()));
        tableParentRoles.setItems(parentRoleRows);

        tablePermissions.setEditable(true);
        colPermissionGranted.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        colPermissionGranted.setCellFactory(CheckBoxTableCell.forTableColumn(colPermissionGranted));
        colPermissionCategory.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(categoryLabel(cell.getValue().permission().category())));
        colPermissionDescription.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().permission().description()));
        colPermissionCode.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().permission().code()));
        filteredPermissions = new FilteredList<>(permissionRows, row -> true);
        tablePermissions.setItems(filteredPermissions);

        comboOverridePermission.setCellFactory(list -> permissionCell());
        comboOverridePermission.setButtonCell(permissionCell());
        comboOverrideEffect.setItems(FXCollections.observableArrayList(RbacOverrideEffect.values()));
        comboOverrideEffect.setCellFactory(list -> overrideEffectCell());
        comboOverrideEffect.setButtonCell(overrideEffectCell());

        colOverrideEffect.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(overrideEffectLabel(cell.getValue().effect())));
        colOverridePermission.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().permissionDescription()));
        colOverrideCode.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().permissionCode()));
        colOverrideReason.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().reason()));
        colOverrideExpiry.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDate(cell.getValue().expiresAt())));
        colOverrideStatus.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActiveAt(LocalDateTime.now()) ? "نشط" : "منتهي"));
        tableOverrides.setItems(overrideRows);
        tableOverrides.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null) showOverride(value);
        });

        colAccessGranted.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().granted() ? "مسموح" : "مرفوض"));
        colAccessPermission.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().permission().description()));
        colAccessCode.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().permission().code()));
        colAccessSource.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().explanation()));
        filteredAccess = new FilteredList<>(accessRows, row -> true);
        tableEffectiveAccess.setItems(filteredAccess);
    }

    private void configureRoleSelector() {
        comboRoles.setCellFactory(list -> roleCell());
        comboRoles.setButtonCell(roleCell());
        comboRoles.valueProperty().addListener((obs, old, role) -> {
            if (role != null) showRole(role);
        });
    }

    private ListCell<RbacRole> roleCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(RbacRole role, boolean empty) {
                super.updateItem(role, empty);
                setText(empty || role == null ? null : role.displayName());
            }
        };
    }

    private void loadData() {
        try {
            labelUser.setText("الأدوار المسندة إلى: " + username);
            Set<Integer> assigned = rbacService.roleIdsForUser(userId);
            var roles = rbacService.roles();
            userRoleRows.setAll(roles.stream()
                    .filter(role -> userId == 1 || !role.systemRole())
                    .map(role ->
                    new UserRoleRow(role, assigned.contains(role.id()))).toList());
            var permissions = rbacService.permissions();
            permissionRows.setAll(permissions.stream()
                    .map(permission -> new RolePermissionRow(permission, false)).toList());
            comboOverridePermission.setItems(FXCollections.observableArrayList(permissions));
            comboRoles.setItems(FXCollections.observableArrayList(roles));

            boolean canManage = AuthorizationGuard.isGranted(AppPermissions.ROLES_MANAGE);
            canManageOverrides = canManage && userId != 1;
            tableUserRoles.setDisable(!canManage || userId == 1);
            btnNewRole.setDisable(!canManage);
            setOverrideEditorDisabled(!canManageOverrides);
            loadUserSecurityDetails();
            if (!roles.isEmpty()) comboRoles.getSelectionModel().selectFirst();
            else beginNewRole();
        } catch (DaoException e) {
            report(e);
        }
    }

    private void loadUserSecurityDetails() throws DaoException {
        overrideRows.setAll(rbacService.userOverrides(userId));
        accessRows.setAll(rbacService.accessDecisionsForUser(userId));
        clearOverrideForm();
    }

    private ListCell<RbacPermission> permissionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(RbacPermission permission, boolean empty) {
                super.updateItem(permission, empty);
                setText(empty || permission == null ? null
                        : permission.description() + " (" + permission.code() + ")");
            }
        };
    }

    private ListCell<RbacOverrideEffect> overrideEffectCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(RbacOverrideEffect effect, boolean empty) {
                super.updateItem(effect, empty);
                setText(empty || effect == null ? null : overrideEffectLabel(effect));
            }
        };
    }

    private void showOverride(RbacUserOverride override) {
        comboOverridePermission.getItems().stream()
                .filter(permission -> permission.id() == override.permissionId())
                .findFirst().ifPresent(comboOverridePermission::setValue);
        comboOverrideEffect.setValue(override.effect());
        textOverrideReason.setText(override.reason());
        dateOverrideExpiry.setValue(override.expiresAt() == null ? null : override.expiresAt().toLocalDate());
        btnDeleteOverride.setDisable(!canManageOverrides);
    }

    private void clearOverrideForm() {
        tableOverrides.getSelectionModel().clearSelection();
        comboOverridePermission.getSelectionModel().clearSelection();
        comboOverrideEffect.setValue(RbacOverrideEffect.DENY);
        textOverrideReason.clear();
        dateOverrideExpiry.setValue(null);
        btnDeleteOverride.setDisable(true);
    }

    private void setOverrideEditorDisabled(boolean disabled) {
        comboOverridePermission.setDisable(disabled);
        comboOverrideEffect.setDisable(disabled);
        textOverrideReason.setDisable(disabled);
        dateOverrideExpiry.setDisable(disabled);
        btnSaveOverride.setDisable(disabled);
        btnClearOverride.setDisable(disabled);
        btnDeleteOverride.setDisable(true);
    }

    private void saveOverride() {
        RbacPermission permission = comboOverridePermission.getValue();
        if (permission == null) {
            AllAlerts.alertError("حدد الصلاحية أولاً");
            return;
        }
        LocalDateTime expiresAt = dateOverrideExpiry.getValue() == null
                ? null
                : dateOverrideExpiry.getValue().atTime(LocalTime.MAX);
        try {
            rbacService.saveUserOverride(userId, permission.id(), comboOverrideEffect.getValue(),
                    textOverrideReason.getText(), expiresAt);
            AllAlerts.alertSaveWithMessage("تم حفظ الاستثناء وتحديث الوصول الفعلي");
            loadUserSecurityDetails();
        } catch (DaoException e) {
            report(e);
        }
    }

    private void deleteSelectedOverride() {
        RbacUserOverride selected = tableOverrides.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError("حدد استثناءً للحذف");
            return;
        }
        if (!AllAlerts.confirmDelete()) return;
        try {
            if (rbacService.deleteUserOverride(userId, selected.permissionId()) > 0) {
                AllAlerts.alertDelete();
                loadUserSecurityDetails();
            }
        } catch (DaoException e) {
            report(e);
        }
    }

    private void filterAccess(String value) {
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        filteredAccess.setPredicate(decision -> query.isEmpty()
                || decision.permission().code().toLowerCase(Locale.ROOT).contains(query)
                || decision.permission().description().toLowerCase(Locale.ROOT).contains(query)
                || decision.explanation().toLowerCase(Locale.ROOT).contains(query));
    }

    private String overrideEffectLabel(RbacOverrideEffect effect) {
        return effect == RbacOverrideEffect.ALLOW ? "سماح استثنائي" : "منع استثنائي";
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "دائم" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private void showRole(RbacRole role) {
        try {
            creatingRole = false;
            editingRole = role;
            textRoleCode.setText(role.code());
            textRoleName.setText(role.name());
            textRoleDescription.setText(role.description());
            checkRoleActive.setSelected(role.active());
            checkAssignNewRole.setSelected(false);
            checkAssignNewRole.setDisable(true);

            Set<Integer> granted = rbacService.permissionIdsForRole(role.id());
            permissionRows.forEach(row -> row.setSelected(granted.contains(row.permission().id())));
            Set<Integer> inherited = rbacService.parentRoleIds(role.id());
            parentRoleRows.setAll(rbacService.roles().stream()
                    .filter(candidate -> candidate.id() != role.id() && !candidate.systemRole() && candidate.active())
                    .map(candidate -> new UserRoleRow(candidate, inherited.contains(candidate.id())))
                    .toList());
            setRoleEditorDisabled(role.systemRole());
        } catch (DaoException e) {
            report(e);
        }
    }

    private void beginNewRole() {
        creatingRole = true;
        editingRole = null;
        comboRoles.getSelectionModel().clearSelection();
        textRoleCode.clear();
        textRoleName.clear();
        textRoleDescription.clear();
        checkRoleActive.setSelected(true);
        checkAssignNewRole.setDisable(userId == 1);
        checkAssignNewRole.setSelected(userId != 1);
        permissionRows.forEach(row -> row.setSelected(false));
        parentRoleRows.setAll(comboRoles.getItems().stream()
                .filter(role -> !role.systemRole() && role.active())
                .map(role -> new UserRoleRow(role, false)).toList());
        setRoleEditorDisabled(false);
        textRoleCode.requestFocus();
    }

    private void setRoleEditorDisabled(boolean systemRole) {
        boolean disabled = systemRole || !AuthorizationGuard.isGranted(AppPermissions.ROLES_MANAGE);
        textRoleCode.setDisable(disabled);
        textRoleName.setDisable(disabled);
        textRoleDescription.setDisable(disabled);
        checkRoleActive.setDisable(disabled);
        tablePermissions.setDisable(disabled);
        tableParentRoles.setDisable(disabled);
        btnDeleteRole.setDisable(disabled || editingRole == null);
    }

    private void deleteSelectedRole() {
        RbacRole role = editingRole;
        if (role == null) {
            AllAlerts.alertError("حدد دورًا للحذف");
            return;
        }
        if (!AllAlerts.confirmDelete()) return;
        try {
            if (rbacService.deleteRole(role) == 1) {
                AllAlerts.alertDelete();
                loadData();
            }
        } catch (DaoException e) {
            report(e);
        }
    }

    private void filterPermissions(String value) {
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        filteredPermissions.setPredicate(row -> query.isEmpty()
                || row.permission().code().toLowerCase(Locale.ROOT).contains(query)
                || row.permission().description().toLowerCase(Locale.ROOT).contains(query)
                || categoryLabel(row.permission().category()).contains(query));
    }

    @Override
    public int save() throws DaoException {
        RbacRole role = roleDraft();
        Set<Integer> permissionIds = new LinkedHashSet<>();
        if (role != null) {
            permissionRows.stream().filter(RolePermissionRow::isSelected)
                    .map(row -> row.permission().id()).forEach(permissionIds::add);
        }

        Set<Integer> assignedRoleIds = new LinkedHashSet<>();
        userRoleRows.stream().filter(UserRoleRow::isSelected)
                .map(row -> row.role().id()).forEach(assignedRoleIds::add);

        Set<Integer> parentRoleIds = new LinkedHashSet<>();
        parentRoleRows.stream().filter(UserRoleRow::isSelected)
                .map(row -> row.role().id()).forEach(parentRoleIds::add);

        return rbacService.saveConfiguration(userId, role, permissionIds, parentRoleIds, assignedRoleIds,
                creatingRole && checkAssignNewRole.isSelected());
    }

    private RbacRole roleDraft() {
        if (!creatingRole && (editingRole == null || editingRole.systemRole())) return null;
        int id = creatingRole ? 0 : editingRole.id();
        return new RbacRole(id, textRoleCode.getText(), textRoleName.getText(),
                textRoleDescription.getText(), false, checkRoleActive.isSelected());
    }

    private String categoryLabel(String category) {
        if (category == null) return "عام";
        return switch (category) {
            case "PURCHASES" -> "المشتريات";
            case "SALES" -> "المبيعات";
            case "PARTIES" -> "العملاء والموردون";
            case "INVENTORY" -> "الأصناف والمخزون";
            case "TREASURY" -> "الخزينة";
            case "REPORTS" -> "التقارير";
            case "SETTINGS" -> "الإعدادات";
            case "SECURITY" -> "المستخدمون والأمان";
            default -> "عام";
        };
    }

    private void report(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return "إدارة الأدوار والصلاحيات / " + username;
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public boolean addLastPane() {
        return true;
    }

    @Override
    public double minWidth() {
        return 980;
    }

    @Override
    public double minHeight() {
        return 650;
    }

    public static final class UserRoleRow {
        private final RbacRole role;
        private final BooleanProperty selected;

        private UserRoleRow(RbacRole role, boolean selected) {
            this.role = role;
            this.selected = new SimpleBooleanProperty(selected);
        }

        public RbacRole role() { return role; }
        public boolean isSelected() { return selected.get(); }
        public BooleanProperty selectedProperty() { return selected; }
    }

    public static final class RolePermissionRow {
        private final RbacPermission permission;
        private final BooleanProperty selected;

        private RolePermissionRow(RbacPermission permission, boolean selected) {
            this.permission = permission;
            this.selected = new SimpleBooleanProperty(selected);
        }

        public RbacPermission permission() { return permission; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
    }
}
