package com.hamza.account.controller.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.permission.Role;
import com.hamza.account.model.domain.permission.RolePermission;
import com.hamza.account.service.permission.RolePermissionService;
import com.hamza.account.service.permission.impl.RolePermissionServiceImpl;
import com.hamza.account.type.PermissionCode;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class RolePermissionsController {

    private final Role role;
    private final RolePermissionService rolePermissionService;
    private final ObservableList<RolePermission> permissionsList = FXCollections.observableArrayList();
    private TableView<RolePermission> permissionsTable;
    private TextField searchField;
    private ComboBox<String> moduleFilterComboBox;
    private Label statsLabel;
    private Stage dialog;

    public RolePermissionsController(Role role) {
        this.role = role;
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        this.rolePermissionService = new RolePermissionServiceImpl(daoFactory.rolePermissionDao());
    }

    public void showDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("صلاحيات الدور: " + role.getName());

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // القسم العلوي
        VBox topSection = createTopSection();
        root.setTop(topSection);

        // الجدول
        permissionsTable = createPermissionsTable();
        root.setCenter(permissionsTable);

        // القسم السفلي (الأزرار)
        HBox bottomSection = createBottomSection();
        root.setBottom(bottomSection);

        Scene scene = new Scene(root, 1000, 700);
        dialog.setScene(scene);

        loadPermissions();

        dialog.showAndWait();
    }

    private VBox createTopSection() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(0, 0, 15, 0));

        // العنوان
        Label titleLabel = new Label("إدارة صلاحيات الدور: " + role.getName());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // شريط البحث والتصفية
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("بحث في الصلاحيات...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, old, newVal) -> filterPermissions());

        moduleFilterComboBox = new ComboBox<>();
        moduleFilterComboBox.setPromptText("تصفية حسب الوحدة");
        moduleFilterComboBox.setPrefWidth(200);
        moduleFilterComboBox.getItems().add("الكل");
        moduleFilterComboBox.getItems().addAll(PermissionCode.getAllModules());
        moduleFilterComboBox.setValue("الكل");
        moduleFilterComboBox.setOnAction(e -> filterPermissions());

        Button selectAllButton = new Button("تحديد الكل");
        selectAllButton.setOnAction(e -> selectAll(true));

        Button deselectAllButton = new Button("إلغاء تحديد الكل");
        deselectAllButton.setOnAction(e -> selectAll(false));

        statsLabel = new Label();
        statsLabel.setStyle("-fx-font-weight: bold;");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        searchBox.getChildren().addAll(
            searchField,
            moduleFilterComboBox,
            selectAllButton,
            deselectAllButton,
            spacer,
            statsLabel
        );

        vbox.getChildren().addAll(titleLabel, searchBox);
        return vbox;
    }

    private TableView<RolePermission> createPermissionsTable() {
        TableView<RolePermission> table = new TableView<>();

        // عمود التحديد
        TableColumn<RolePermission, Boolean> checkColumn = new TableColumn<>("تفعيل");
        checkColumn.setPrefWidth(80);
        checkColumn.setCellValueFactory(cellData -> {
            RolePermission perm = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(perm.isChecked());
            property.addListener((obs, oldVal, newVal) -> {
                perm.setChecked(newVal);
                updateStats();
            });
            return property;
        });
        checkColumn.setCellFactory(CheckBoxTableCell.forTableColumn(checkColumn));
        checkColumn.setEditable(true);

        // عمود الكود
        TableColumn<RolePermission, String> codeColumn = new TableColumn<>("الكود");
        codeColumn.setPrefWidth(250);
        codeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getPermissionCode())
        );

        // عمود الاسم
        TableColumn<RolePermission, String> nameColumn = new TableColumn<>("اسم الصلاحية");
        nameColumn.setPrefWidth(350);
        nameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getPermissionNameAr())
        );

        // عمود الوحدة
        TableColumn<RolePermission, String> moduleColumn = new TableColumn<>("الوحدة");
        moduleColumn.setPrefWidth(150);
        moduleColumn.setCellValueFactory(cellData -> {
            String code = cellData.getValue().getPermissionCode();
            PermissionCode permCode = PermissionCode.fromCode(code);
            return new SimpleStringProperty(permCode != null ? permCode.getModule() : "");
        });

        // عمود الإجراء
        TableColumn<RolePermission, String> actionColumn = new TableColumn<>("الإجراء");
        actionColumn.setPrefWidth(120);
        actionColumn.setCellValueFactory(cellData -> {
            String code = cellData.getValue().getPermissionCode();
            PermissionCode permCode = PermissionCode.fromCode(code);
            return new SimpleStringProperty(permCode != null ? permCode.getAction() : "");
        });

        table.getColumns().addAll(checkColumn, codeColumn, nameColumn, moduleColumn, actionColumn);
        table.setItems(permissionsList);
        table.setEditable(true);

        return table;
    }

    private HBox createBottomSection() {
        HBox hbox = new HBox(10);
        hbox.setPadding(new Insets(15, 0, 0, 0));
        hbox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button saveButton = new Button("حفظ التغييرات");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px;");
        saveButton.setOnAction(e -> savePermissions());

        Button cancelButton = new Button("إلغاء");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 14px;");
        cancelButton.setOnAction(e -> dialog.close());

        hbox.getChildren().addAll(saveButton, cancelButton);
        return hbox;
    }

    private void loadPermissions() {
        try {
            permissionsList.clear();
            List<RolePermission> permissions = rolePermissionService.getRolePermissions(role.getId());
            permissionsList.addAll(permissions);
            updateStats();
        } catch (DaoException e) {
            log.error("خطأ في تحميل الصلاحيات", e);
            showError("خطأ في تحميل الصلاحيات: " + e.getMessage());
        }
    }

    private void filterPermissions() {
        String searchText = searchField.getText().toLowerCase();
        String selectedModule = moduleFilterComboBox.getValue();

        try {
            List<RolePermission> allPermissions = rolePermissionService.getRolePermissions(role.getId());

            List<RolePermission> filtered = allPermissions.stream()
                .filter(perm -> {
                    boolean matchesSearch = searchText.isEmpty() ||
                        perm.getPermissionCode().toLowerCase().contains(searchText) ||
                        perm.getPermissionNameAr().contains(searchText);

                    boolean matchesModule = selectedModule.equals("الكل") ||
                        perm.getPermissionCode().startsWith(selectedModule);

                    return matchesSearch && matchesModule;
                })
                .collect(Collectors.toList());

            permissionsList.clear();
            permissionsList.addAll(filtered);
            updateStats();

        } catch (DaoException e) {
            log.error("خطأ في التصفية", e);
        }
    }

    private void selectAll(boolean select) {
        permissionsList.forEach(perm -> perm.setChecked(select));
        permissionsTable.refresh();
        updateStats();
    }

    private void updateStats() {
        long checkedCount = permissionsList.stream().filter(RolePermission::isChecked).count();
        long totalCount = permissionsList.size();
        statsLabel.setText(String.format("محدد: %d من %d", checkedCount, totalCount));
    }

    private void savePermissions() {
        try {
            rolePermissionService.saveRolePermissions(role.getId(), new ArrayList<>(permissionsList));
            showSuccess("تم حفظ الصلاحيات بنجاح");
            dialog.close();
        } catch (DaoException e) {
            log.error("خطأ في حفظ الصلاحيات", e);
            showError("خطأ في حفظ الصلاحيات: " + e.getMessage());
        }
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
