package com.hamza.account.controller.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.permission.Role;
import com.hamza.account.service.permission.RoleService;
import com.hamza.account.service.permission.impl.RoleServiceImpl;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

@Log4j2
public class RolesManagementController implements Initializable {

    @FXML
    private TableView<Role> rolesTable;

    @FXML
    private TableColumn<Role, Number> idColumn;

    @FXML
    private TableColumn<Role, String> nameColumn;

    @FXML
    private TableColumn<Role, String> descriptionColumn;

    @FXML
    private TableColumn<Role, Boolean> activeColumn;

    @FXML
    private TextField searchField;

    @FXML
    private Button addButton;

    @FXML
    private Button editButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button permissionsButton;

    @FXML
    private Button refreshButton;

    private final RoleService roleService;
    private final ObservableList<Role> rolesList = FXCollections.observableArrayList();

    public RolesManagementController() {
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        this.roleService = new RoleServiceImpl(daoFactory.roleDao());
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupButtons();
        loadRoles();
        setupSearch();
    }

    private void setupTable() {
        // تكوين الأعمدة
        idColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getId())
        );

        nameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getName())
        );

        descriptionColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDescription())
        );

        activeColumn.setCellValueFactory(cellData -> 
            new SimpleBooleanProperty(cellData.getValue().isActive())
        );
        activeColumn.setCellFactory(CheckBoxTableCell.forTableColumn(activeColumn));

        rolesTable.setItems(rolesList);

        // عند النقر المزدوج على صف
        rolesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && rolesTable.getSelectionModel().getSelectedItem() != null) {
                handleEdit();
            }
        });
    }

    private void setupButtons() {
        addButton.setOnAction(e -> handleAdd());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());
        permissionsButton.setOnAction(e -> handlePermissions());
        refreshButton.setOnAction(e -> loadRoles());

        // تفعيل/تعطيل الأزرار بناءً على التحديد
        editButton.disableProperty().bind(
            rolesTable.getSelectionModel().selectedItemProperty().isNull()
        );
        deleteButton.disableProperty().bind(
            rolesTable.getSelectionModel().selectedItemProperty().isNull()
        );
        permissionsButton.disableProperty().bind(
            rolesTable.getSelectionModel().selectedItemProperty().isNull()
        );
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterRoles(newValue);
        });
    }

    private void loadRoles() {
        try {
            rolesList.clear();
            rolesList.addAll(roleService.getAllRoles());
        } catch (DaoException e) {
            log.error("خطأ في تحميل الأدوار", e);
            showError("خطأ في تحميل الأدوار: " + e.getMessage());
        }
    }

    private void filterRoles(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            loadRoles();
            return;
        }

        try {
            var allRoles = roleService.getAllRoles();
            var filtered = allRoles.stream()
                .filter(role -> 
                    role.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                    (role.getDescription() != null && 
                     role.getDescription().toLowerCase().contains(searchText.toLowerCase()))
                )
                .toList();

            rolesList.clear();
            rolesList.addAll(filtered);
        } catch (DaoException e) {
            log.error("خطأ في البحث", e);
        }
    }

    private void handleAdd() {
        Dialog<Role> dialog = createRoleDialog(null);
        Optional<Role> result = dialog.showAndWait();

        result.ifPresent(role -> {
            try {
                roleService.createRole(role);
                loadRoles();
                showSuccess("تم إضافة الدور بنجاح");
            } catch (DaoException e) {
                log.error("خطأ في إضافة الدور", e);
                showError("خطأ في إضافة الدور: " + e.getMessage());
            }
        });
    }

    private void handleEdit() {
        Role selectedRole = rolesTable.getSelectionModel().getSelectedItem();
        if (selectedRole == null) {
            return;
        }

        Dialog<Role> dialog = createRoleDialog(selectedRole);
        Optional<Role> result = dialog.showAndWait();

        result.ifPresent(role -> {
            try {
                role.setId(selectedRole.getId());
                roleService.updateRole(role);
                loadRoles();
                showSuccess("تم تعديل الدور بنجاح");
            } catch (DaoException e) {
                log.error("خطأ في تعديل الدور", e);
                showError("خطأ في تعديل الدور: " + e.getMessage());
            }
        });
    }

    private void handleDelete() {
        Role selectedRole = rolesTable.getSelectionModel().getSelectedItem();
        if (selectedRole == null) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("تأكيد الحذف");
        confirmation.setHeaderText("حذف الدور: " + selectedRole.getName());
        confirmation.setContentText("هل أنت متأكد من حذف هذا الدور؟");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                roleService.deactivateRole(selectedRole.getId());
                loadRoles();
                showSuccess("تم حذف الدور بنجاح");
            } catch (DaoException e) {
                log.error("خطأ في حذف الدور", e);
                showError("خطأ في حذف الدور: " + e.getMessage());
            }
        }
    }

    private void handlePermissions() {
        Role selectedRole = rolesTable.getSelectionModel().getSelectedItem();
        if (selectedRole == null) {
            return;
        }

        try {
            // فتح نافذة صلاحيات الدور
            RolePermissionsController controller = new RolePermissionsController(selectedRole);
            controller.showDialog();
        } catch (Exception e) {
            log.error("خطأ في فتح صلاحيات الدور", e);
            showError("خطأ في فتح صلاحيات الدور: " + e.getMessage());
        }
    }

    private Dialog<Role> createRoleDialog(Role existingRole) {
        Dialog<Role> dialog = new Dialog<>();
        dialog.setTitle(existingRole == null ? "إضافة دور جديد" : "تعديل دور");
        dialog.setHeaderText(existingRole == null ? "أدخل بيانات الدور الجديد" : "تعديل بيانات الدور");

        // أزرار الحوار
        ButtonType saveButtonType = new ButtonType("حفظ", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // إنشاء النموذج
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("اسم الدور");
        if (existingRole != null) {
            nameField.setText(existingRole.getName());
        }

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("وصف الدور");
        descriptionArea.setPrefRowCount(3);
        if (existingRole != null && existingRole.getDescription() != null) {
            descriptionArea.setText(existingRole.getDescription());
        }

        CheckBox activeCheckBox = new CheckBox("نشط");
        activeCheckBox.setSelected(existingRole == null || existingRole.isActive());

        grid.add(new Label("اسم الدور:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("الوصف:"), 0, 1);
        grid.add(descriptionArea, 1, 1);
        grid.add(activeCheckBox, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // التحقق من الإدخال
        javafx.scene.control.Button saveButton = 
            (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        nameField.textProperty().addListener((observable, oldValue, newValue) -> {
            saveButton.setDisable(newValue.trim().isEmpty());
        });

        // تحويل النتيجة
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Role role = new Role();
                role.setName(nameField.getText().trim());
                role.setDescription(descriptionArea.getText().trim());
                role.setActive(activeCheckBox.isSelected());
                return role;
            }
            return null;
        });

        return dialog;
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("نجاح");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("خطأ");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
