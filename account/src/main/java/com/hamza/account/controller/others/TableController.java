package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ButtonGraphics;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

/**
 * Controller class for managing a TableView and its associated UI components.
 * <p>
 * This class is responsible for initializing and managing the different UI components
 * related to the TableView, including buttons, labels, text fields, and toolbar.
 * It provides functionalities such as configuring and populating the TableView,
 * animating the search bar, setting up various button actions, and updating UI elements.
 *
 * @param <T> The type of the objects contained in the TableView.
 */
@Log4j2
@FxmlPath(pathFile = "main-tableview.fxml")
public class TableController<T> implements Initializable {

    private final TableInterface<T> tableInterface;
    private final CssToColorHelper helper = new CssToColorHelper();
    private final ActionButtonToolBar<T> actionButtonToolBar;
    private final int ROWS_PER_PAGE = 50;

    private TableView<T> tableView = new TableView<>();
    @FXML
    private Button btnNew, btnUpdate, btnDelete, btnRefresh, btnPrint;
    @FXML
    private Label labelSearch, labelSum;
    @FXML
    private Text textSum;
    @FXML
    private TextField txtSearch;
    @FXML
    private HBox hBox;
    @FXML
    private StackPane root;
    @FXML
    private ToolBar toolBar;
    @FXML
    private VBox boxCenter;
    @FXML
    private ToggleButton btnSelected;
    @FXML
    private Text textData;
    @FXML
    private GridPane gridPane;
    @FXML
    private Pagination pagination;

    public TableController(TableInterface<T> tableInterface) {
        this.tableInterface = tableInterface;
        this.actionButtonToolBar = tableInterface.actionButton();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        getTable();
        initializePagination();
        otherSetting();
        sumTable();

        tableInterface.addToLastPane(gridPane, hBox, toolBar);

        if (tableInterface.styleSheet() != null) {
            root.getStylesheets().add(tableInterface.styleSheet());
        }

        tableInterface.textData(tableView, txtSearch);
        root.getChildren().add(helper);
        tableInterface.helper(helper);
        root.setPrefSize(750, 500);

        actionButton();
        permButtons();
        updateTableView(0);
        tableInterface.publisherTable().addObserver(message -> updateTableView(0));
    }

    public void initializePagination() {
        int totalItems = tableInterface.getCountItems(); // database.getCount();
        int pageCount = (totalItems / ROWS_PER_PAGE) + 1;
        pagination.setPageCount(pageCount);
        // 3. تحديد ماذا يحدث عند تغيير الصفحة (Factory)
        pagination.setPageFactory((pageIndex) -> {
            updateTableView(pageIndex);
            return tableView; // نعيد الجدول ليتم عرضه داخل صفحة الـ Pagination
        });


        PauseTransition pause = new PauseTransition(Duration.millis(500));
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                try {
                    loadDataFromDB(newValue); // لا يتم الاستدعاء إلا بعد التوقف عن الكتابة
                } catch (Exception e) {
                    log.error(this.getClass().getName(), e.getMessage());
                }
            });
            pause.playFromStart();
        });
    }

    private void updateTableView(int pageIndex) {
        int offset = pageIndex * ROWS_PER_PAGE;
        // هنا الكود الحقيقي لجلب البيانات من قاعدة البيانات
        List<T> data = null;
        try {
            data = tableInterface.getProducts(ROWS_PER_PAGE, offset);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        tableView.setItems(FXCollections.observableArrayList(data));
        tableView.refresh();
    }

    private void loadDataFromDB(String newValue) throws Exception {
        var filterItems = tableInterface.getFilterItems(newValue);
        tableView.setItems(FXCollections.observableArrayList(filterItems));
    }

    private void permButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnNew::setDisable, tableInterface.permAdd());
        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, tableInterface.permUpdate());
        permissionDisableService.applyPermissionBasedDisable(btnDelete::setDisable, tableInterface.permDelete());
    }

    private void getTable() {
        tableView.getColumns().clear();
        new TableColumnAnnotation().getTable(tableView, tableInterface.table_data().classForColumn());
        tableInterface.table_data().getTable(tableView);
        ColumnSetting.addSelectedColumn(tableView);
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void otherSetting() {
        textSum.setText("0");
        labelSearch.setText(Setting_Language.WORD_SEARCH);
        labelSum.setText(Setting_Language.WORD_TOTAL.concat(" : "));
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);
//        btnSelected.setText(Setting_Language.SELECT_ALL);

        var imageSetting = new Image_Setting();
        buttonSetting(btnNew, Setting_Language.WORD_NEW, imageSetting.add);
        buttonSetting(btnUpdate, "تعديل", imageSetting.update);
        buttonSetting(btnDelete, Setting_Language.WORD_DELETE, imageSetting.delete);
        buttonSetting(btnRefresh, Setting_Language.WORD_REFRESH, imageSetting.refresh);
        buttonSetting(btnPrint, Setting_Language.WORD_PRINT, imageSetting.print);
        btnSelected.setGraphic(createIcon(imageSetting.select));
    }

    private void sumTable() {
        textSum.setText(String.valueOf(tableView.getItems().size()));
    }

    private void buttonSetting(Button button, String title, InputStream stream) {
        button.setText(title);
//        if (LogApplication.usersVo.getId() != 1) button.setDisable(!b);

        if (stream != null) {
            ButtonGraphics.buttonGraphic(button, stream);
            button.setContentDisplay(ContentDisplay.RIGHT);
        }

        button.getStyleClass().removeAll();
    }

    private void actionButton() {
        new SelectedButton(btnSelected) {
            @Override
            public void clearSelection(boolean b) {
                for (int i = 0; i < tableView.getItems().size(); i++) {
                    T t1 = tableView.getItems().get(i);
                    tableInterface.getColumnSelected(t1).setValue(b);
                }
            }
        };

        tableView.itemsProperty().addListener((observableValue, ts, t1) -> sumTable());
        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                btnDelete.fire();
            }
        });

        btnNew.setOnAction(actionEvent -> {
            try {
                actionButtonToolBar.openNew();
            } catch (Exception e) {
                errorLog(e);
            }
        });

        btnUpdate.setOnAction(actionEvent -> {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_SELECT_ROW);
                return;
            }
            try {
                actionButtonToolBar.update(tableView.getSelectionModel().getSelectedItem());
            } catch (Exception e) {
                errorLog(e);
            }
        });

        btnDelete.setOnAction(actionEvent -> {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_SELECT_ROW);
                return;
            }
            if (AllAlerts.confirmDelete())
                try {
                    int delete1 = actionButtonToolBar.delete(tableView.getSelectionModel().getSelectedItem());
                    if (delete1 == 1) {
                        AllAlerts.alertDelete();
                        btnRefresh.fire();
                        actionButtonToolBar.afterDelete();
                    }
                } catch (Exception e) {
                    errorLog(e);
                }
        });

        btnRefresh.setOnAction(actionEvent -> updateTableView(0));

        btnPrint.setOnAction(actionEvent -> {
            try {
                actionButtonToolBar.print();
            } catch (Exception e) {
                errorLog(e);
            }
        });
    }

    private void errorLog(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
    }

}
