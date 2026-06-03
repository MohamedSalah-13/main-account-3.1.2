package com.hamza.account.controller.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.model.domain.permission.Role;
import com.hamza.account.service.permission.RoleService;
import com.hamza.account.service.permission.UserRoleService;
import com.hamza.account.service.permission.impl.RoleServiceImpl;
import com.hamza.account.service.permission.impl.UserRoleServiceImpl;
import com.hamza.account.database.DaoException;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public class UserRolesController {

    private final Users user;
    private final UserRoleService userRoleService;
    private final RoleService roleService;
    private final ObservableList<RoleItem> rolesList = FXCollections.observableArrayList();
    private TableView<RoleItem> rolesTable;
    private Stage dialog;

    public UserRolesController(Users user) {
        this.user = user;
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        this.userRoleService = new UserRoleServiceImpl(daoFactory.userRoleDao());
        this.roleService = new RoleServiceImpl(daoFactory.roleDao());
    }

    public void showDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("أدوار المستخدم: " + user.getUsername());

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // القسم العلوي
        VBox topSection = createTopSection();
        root.setTop(topSection);

        // الجدول
        rolesTable = createRolesTable();
        root.setCenter(rolesTable);

        // القسم السفلي
        HBox bottomSection = createBottomSection();
        root.setBottom(bottomSection);

        Scene scene = new Scene(root, 700, 500);
        dialog.setScene(scene);

        loadRoles();

        dialog.showAndWait();
    }

    private VBox createTopSection() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(0, 0, 15, 0));

        Label titleLabel = new Label("تعيين الأدوار للمستخدم: " + user.getUsername());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label infoLabel = new Label("حدد الأدوار التي تريد تعيينها للمستخدم");
        infoLabel.setStyle("-fx-text-fill: gray;");

        vbox.getChildren().addAll(titleLabel, infoLabel);
        return vbox;
    }

    private TableView<RoleItem> createRolesTable() {
        TableView<RoleItem> table = new TableView<>();

        // عمود التحديد
        TableColumn<RoleItem, Boolean> checkColumn = new TableColumn<>("تعيين");
        checkColumn.setPrefWidth(80);
        checkColumn.setCellValueFactory(cellData -> cellData.getValue().assignedProperty());
        checkColumn.setCellFactory(CheckBoxTableCell.forTableColumn(checkColumn));
        checkColumn.setEditable(true);

        // عمود اسم الدور
        TableColumn<RoleItem, String> nameColumn = new TableColumn<>("اسم الدور");
        nameColumn.setPrefWidth(250);
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole().getName())
        );

        // عمود الوصف
        TableColumn<RoleItem, String> descriptionColumn = new TableColumn<>("الوصف");
        descriptionColumn.setPrefWidth(350);
        descriptionColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole().getDescription())
        );

        table.getColumns().addAll(checkColumn, nameColumn, descriptionColumn);
        table.setItems(rolesList);
        table.setEditable(true);

        return table;
    }

    private HBox createBottomSection() {
        HBox hbox = new HBox(10);
        hbox.setPadding(new Insets(15, 0, 0, 0));
        hbox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button saveButton = new Button("حفظ");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveButton.setOnAction(e -> saveRoles());

        Button cancelButton = new Button("إلغاء");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> dialog.close());

        hbox.getChildren().addAll(saveButton, cancelButton);
        return hbox;
    }

    private void loadRoles() {
        try {
            // جلب جميع الأدوار
            List<Role> allRoles = roleService.getActiveRoles();

            // جلب أدوار المستخدم الحالية
            Set<Integer> userRoleIds = userRoleService.getUserRoles(user.getId())
                    .stream()
                    .map(ur -> ur.getRoleId())
                    .collect(Collectors.toSet());

            rolesList.clear();
            for (Role role : allRoles) {
                boolean assigned = userRoleIds.contains(role.getId());
                rolesList.add(new RoleItem(role, assigned));
            }

        } catch (DaoException e) {
            log.error("خطأ في تحميل الأدوار", e);
            showError("خطأ في تحميل الأدوار: " + e.getMessage());
        }
    }

    private void saveRoles() {
        try {
            List<Integer> selectedRoleIds = rolesList.stream()
                    .filter(RoleItem::isAssigned)
                    .map(item -> item.getRole().getId())
                    .collect(Collectors.toList());

            userRoleService.replaceUserRoles(user.getId(), selectedRoleIds);
            showSuccess("تم حفظ الأدوار بنجاح");
            dialog.close();

        } catch (DaoException e) {
            log.error("خطأ في حفظ الأدوار", e);
            showError("خطأ في حفظ الأدوار: " + e.getMessage());
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

    // Inner class لتمثيل الدور مع حالة التعيين
    public static class RoleItem {
        private final Role role;
        private final SimpleBooleanProperty assigned;

        public RoleItem(Role role, boolean assigned) {
            this.role = role;
            this.assigned = new SimpleBooleanProperty(assigned);
        }

        public Role getRole() {
            return role;
        }

        public boolean isAssigned() {
            return assigned.get();
        }

        public void setAssigned(boolean assigned) {
            this.assigned.set(assigned);
        }

        public SimpleBooleanProperty assignedProperty() {
            return assigned;
        }
    }
}
