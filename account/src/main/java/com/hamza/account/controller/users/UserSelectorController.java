package com.hamza.account.controller.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.function.Consumer;

@Log4j2
public class UserSelectorController {

    private final DaoFactory daoFactory;
    private final ObservableList<Users> usersList = FXCollections.observableArrayList();
    private TableView<Users> usersTable;
    private TextField searchField;
    private Stage dialog;
    private Consumer<Users> onUserSelected;

    public UserSelectorController(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    /**
     * فتح نافذة اختيار المستخدم
     *
     * @param title          عنوان النافذة
     * @param onUserSelected دالة يتم استدعاؤها عند اختيار المستخدم
     */
    public void showDialog(String title, Consumer<Users> onUserSelected) {
        this.onUserSelected = onUserSelected;

        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // القسم العلوي
        VBox topSection = createTopSection();
        root.setTop(topSection);

        // الجدول
        usersTable = createUsersTable();
        root.setCenter(usersTable);

        // القسم السفلي
        HBox bottomSection = createBottomSection();
        root.setBottom(bottomSection);

        Scene scene = new Scene(root, 600, 500);
        dialog.setScene(scene);

        loadUsers();

        dialog.showAndWait();
    }

    private VBox createTopSection() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(0, 0, 15, 0));

        Label titleLabel = new Label("اختر مستخدم");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox searchBox = new HBox(10);
        searchField = new TextField();
        searchField.setPromptText("بحث عن مستخدم...");
        searchField.setPrefWidth(400);
        searchField.textProperty().addListener((obs, old, newVal) -> filterUsers(newVal));

        searchBox.getChildren().add(searchField);

        vbox.getChildren().addAll(titleLabel, searchBox);
        return vbox;
    }

    private TableView<Users> createUsersTable() {
        TableView<Users> table = new TableView<>();

        // عمود المعرف
        TableColumn<Users, Number> idColumn = new TableColumn<>("المعرف");
        idColumn.setPrefWidth(80);
        idColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getId())
        );

        // عمود اسم المستخدم
        TableColumn<Users, String> usernameColumn = new TableColumn<>("اسم المستخدم");
        usernameColumn.setPrefWidth(200);
        usernameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUsername())
        );

        // عمود الحالة
        TableColumn<Users, String> statusColumn = new TableColumn<>("الحالة");
        statusColumn.setPrefWidth(100);
        statusColumn.setCellValueFactory(cellData -> {
            boolean active = cellData.getValue().isActive();
            return new SimpleStringProperty(active ? "نشط" : "غير نشط");
        });

        // عمود متاح
        TableColumn<Users, String> availableColumn = new TableColumn<>("متاح");
        availableColumn.setPrefWidth(100);
        availableColumn.setCellValueFactory(cellData -> {
            boolean available = cellData.getValue().isActive();
            return new SimpleStringProperty(available ? "نعم" : "لا");
        });

        table.getColumns().addAll(idColumn, usernameColumn, statusColumn, availableColumn);
        table.setItems(usersList);

        // النقر المزدوج للاختيار
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectUser();
            }
        });

        return table;
    }

    private HBox createBottomSection() {
        HBox hbox = new HBox(10);
        hbox.setPadding(new Insets(15, 0, 0, 0));
        hbox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button selectButton = new Button("اختيار");
        selectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        selectButton.setOnAction(e -> selectUser());

        Button cancelButton = new Button("إلغاء");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> dialog.close());

        hbox.getChildren().addAll(selectButton, cancelButton);
        return hbox;
    }

    private void loadUsers() {
        try {
            usersList.clear();
            List<Users> users = daoFactory.usersDao().loadAll();
            usersList.addAll(users);
        } catch (DaoException e) {
            log.error("خطأ في تحميل المستخدمين", e);
            showError("خطأ في تحميل المستخدمين: " + e.getMessage());
        }
    }

    private void filterUsers(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            loadUsers();
            return;
        }

        try {
            List<Users> allUsers = daoFactory.usersDao().loadAll();
            List<Users> filtered = allUsers.stream()
                    .filter(user ->
                            user.getUsername().toLowerCase().contains(searchText.toLowerCase())
                    )
                    .toList();

            usersList.clear();
            usersList.addAll(filtered);
        } catch (DaoException e) {
            log.error("خطأ في البحث", e);
        }
    }

    private void selectUser() {
        Users selectedUser = usersTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("الرجاء اختيار مستخدم أولاً");
            return;
        }

        if (onUserSelected != null) {
            onUserSelected.accept(selectedUser);
        }
        dialog.close();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("تنبيه");
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
