package com.hamza.account.controller.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.model.domain.permission.UserPermission;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.permission.UserPermissionManagementService;
import com.hamza.account.service.permission.impl.UserPermissionManagementServiceImpl;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.database.DaoException;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Log4j2
@FxmlPath(pathFile = "users/user-permission.fxml")
public class UserPermissionController implements Initializable {

    @FXML
    private StackPane rootPane;

    private TableView<UserPermission> permissionsTable;
    private TextField searchField;
    private ComboBox<String> moduleFilterComboBox;
    private Label statsLabel;
    private Button saveButton;

    private final UserPermissionManagementService userPermissionService;
    private final ObservableList<UserPermission> permissionsList = FXCollections.observableArrayList();
    private Users currentUser;

    public UserPermissionController() {
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        this.userPermissionService = new UserPermissionManagementServiceImpl(daoFactory.userPermissionDao());
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupUI();
    }

    public void setUser(Users user) {
        this.currentUser = user;
        loadPermissions();
    }

    private void setupUI() {
        VBox mainContainer = new VBox(15);
        mainContainer.setStyle("-fx-padding: 20;");

        // القسم العلوي
        VBox topSection = createTopSection();

        // الجدول
        permissionsTable = createPermissionsTable();

        // القسم السفلي
        HBox bottomSection = createBottomSection();

        mainContainer.getChildren().addAll(topSection, permissionsTable, bottomSection);
        rootPane.getChildren().add(mainContainer);
    }

    private VBox createTopSection() {
        VBox vbox = new VBox(10);

        // العنوان
        Label titleLabel = new Label("صلاحيات المستخدم");
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
        selectAllButton.getStyleClass().add("app-button");

        Button deselectAllButton = new Button("إلغاء تحديد الكل");
        deselectAllButton.setOnAction(e -> selectAll(false));
        deselectAllButton.getStyleClass().add("app-button");

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

    private TableView<UserPermission> createPermissionsTable() {
        TableView<UserPermission> table = new TableView<>();
        table.setPrefHeight(500);

        // عمود التحديد
        TableColumn<UserPermission, Boolean> checkColumn = new TableColumn<>("تفعيل");
        checkColumn.setPrefWidth(80);
        checkColumn.setCellValueFactory(cellData -> {
            UserPermission perm = cellData.getValue();
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
        TableColumn<UserPermission, String> codeColumn = new TableColumn<>("الكود");
        codeColumn.setPrefWidth(250);
        codeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getPermissionCode())
        );

        // عمود الاسم
        TableColumn<UserPermission, String> nameColumn = new TableColumn<>("اسم الصلاحية");
        nameColumn.setPrefWidth(350);
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getPermissionNameAr())
        );

        // عمود الوحدة
        TableColumn<UserPermission, String> moduleColumn = new TableColumn<>("الوحدة");
        moduleColumn.setPrefWidth(150);
        moduleColumn.setCellValueFactory(cellData -> {
            String code = cellData.getValue().getPermissionCode();
            PermissionCode permCode = PermissionCode.fromCode(code);
            return new SimpleStringProperty(permCode != null ? permCode.getModule() : "");
        });

        table.getColumns().addAll(checkColumn, codeColumn, nameColumn, moduleColumn);
        table.setItems(permissionsList);
        table.setEditable(true);

        return table;
    }

    private HBox createBottomSection() {
        HBox hbox = new HBox(10);
        hbox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        saveButton = new Button("حفظ التغييرات");
        saveButton.getStyleClass().add("app-success-button");
        saveButton.setOnAction(e -> savePermissions());

        Button refreshButton = new Button("تحديث");
        refreshButton.getStyleClass().add("app-neutral-button");
        refreshButton.setOnAction(e -> loadPermissions());

        hbox.getChildren().addAll(saveButton, refreshButton);
        return hbox;
    }

    private void loadPermissions() {
        if (currentUser == null) {
            return;
        }

        try {
            permissionsList.clear();
            List<UserPermission> permissions = userPermissionService.getUserPermissions(currentUser.getId());
            permissionsList.addAll(permissions);
            updateStats();
        } catch (DaoException e) {
            log.error("خطأ في تحميل الصلاحيات", e);
            showError("خطأ في تحميل الصلاحيات: " + e.getMessage());
        }
    }

    private void filterPermissions() {
        if (currentUser == null) {
            return;
        }

        String searchText = searchField.getText().toLowerCase();
        String selectedModule = moduleFilterComboBox.getValue();

        try {
            List<UserPermission> allPermissions = userPermissionService.getUserPermissions(currentUser.getId());

            List<UserPermission> filtered = allPermissions.stream()
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
        long checkedCount = permissionsList.stream().filter(UserPermission::isChecked).count();
        long totalCount = permissionsList.size();
        statsLabel.setText(String.format("محدد: %d من %d", checkedCount, totalCount));
    }

    private void savePermissions() {
        if (currentUser == null) {
            return;
        }

        try {
            userPermissionService.saveUserPermissions(currentUser.getId(), new ArrayList<>(permissionsList));
            showSuccess("تم حفظ الصلاحيات بنجاح");
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