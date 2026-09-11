package com.hamza.account.controller.others;

import com.hamza.account.table.PageJumpBox;
import javafx.scene.layout.HBox;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.TableAppearance;
import com.hamza.account.controller.main.DisableButtons;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.table.TableScreenProfile;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.CssToColorHelper;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

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
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();
    private final CssToColorHelper helper = new CssToColorHelper();
    private final ActionButtonToolBar<T> actionButtonToolBar;
    private final TableScreenProfile screenProfile;
    private DataTable<T> tableData;
    private final int ROWS_PER_PAGE = 50;

    private final TableView<T> tableView = new TableView<>();
    @FXML
    private Button btnNew, btnUpdate, btnDelete, btnRefresh, btnPrint;
    @FXML
    private Label labelSearch, identityTitle, identitySubtitle;
    @FXML
    private TextField txtSearch;
    @FXML
    private StackPane root;
    @FXML
    private ToggleButton btnSelected;
    @FXML
    private MenuButton btnView;
    @FXML
    private Pagination pagination;
    @FXML
    private HBox pagerBox, identityHeader, identityIconBox, rowRecordActions;

    /**
     * Type a page number, land on it.
     * <p>
     * {@code Pagination} numbers its own pages but gives no way to reach one directly, so a
     * list of thirty pages was thirty clicks from its end. Setting
     * {@code pagination.setCurrentPageIndex} is what moves it, and the page factory reloads
     * the rows - the same route a click on its own numbers takes, so there is one way pages
     * change and not two.
     */
    private final PageJumpBox pageJump =
            new PageJumpBox(page -> pagination.setCurrentPageIndex(page));

    public TableController(TableInterface<T> tableInterface) {
        this.tableInterface = tableInterface;
        this.actionButtonToolBar = tableInterface.actionButton();
        this.screenProfile = tableInterface.screenProfile();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        getTable();
        initializePagination();
        applyScreenProfile();
        otherSetting();

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
        // A screen names one or the other: the publisher it was built with, or the
        // event it has been migrated to.
        if (tableInterface.publisherTable() != null) {
            subscriptions.subscribe(tableInterface.publisherTable(), message -> updateTableView(0));
        }
        if (tableInterface.refreshOn() != null && eventBus != null) {
            subscriptions.add(eventBus.subscribe(tableInterface.refreshOn(), event -> {
                if (tableInterface.refreshFor(event)) updateTableView(0);
            }));
        }
        subscriptions.disposeWith(root);
    }

    public void initializePagination() {
        int totalItems = tableInterface.getCountItems(); // database.getCount();
        int pageCount = (totalItems / ROWS_PER_PAGE) + 1;
        pagination.setPageCount(pageCount);
        pagerBox.getChildren().setAll(pageJump);
        pageJump.showing(pagination.getCurrentPageIndex(), pageCount);
        // 3. تحديد ماذا يحدث عند تغيير الصفحة (Factory)
        pagination.setPageFactory((pageIndex) -> {
            updateTableView(pageIndex);
            // The box follows the pagination whichever way the page was changed - its own
            // numbers, or a number typed into the box - so it can never sit there naming a
            // page the table is not on.
            pageJump.showing(pageIndex, pagination.getPageCount());
            return tableView; // نعيد الجدول ليتم عرضه داخل صفحة الـ Pagination
        });


        PauseTransition pause = new PauseTransition(Duration.millis(500));
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                try {
                    loadDataFromDB(newValue); // لا يتم الاستدعاء إلا بعد التوقف عن الكتابة
                } catch (Exception e) {
                    reportUnexpected(text("error.operation.table.search"), e);
                }
            });
            pause.playFromStart();
        });
    }

    private void updateTableView(int pageIndex) {
        int offset = pageIndex * ROWS_PER_PAGE;
        // هنا الكود الحقيقي لجلب البيانات من قاعدة البيانات
        try {
            List<T> data = tableInterface.getProducts(ROWS_PER_PAGE, offset);
            tableView.setItems(FXCollections.observableArrayList(data));
            tableData.layoutColumns(tableView);
            tableView.refresh();
        } catch (Exception e) {
            // Keep the currently displayed rows. Replacing them with an empty list
            // would make a database failure look like "there is no data".
            reportUnexpected(text("error.operation.table.update"), e);
        }
    }

    private void loadDataFromDB(String newValue) throws Exception {
        var filterItems = tableInterface.getFilterItems(newValue);
        tableView.setItems(FXCollections.observableArrayList(filterItems));
        tableData.layoutColumns(tableView);
    }

    private void permButtons() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(btnNew::setDisable, tableInterface.permAdd());
        permissionDisableService.applyPermissionBasedDisable(btnUpdate::setDisable, tableInterface.permUpdate());
        permissionDisableService.applyPermissionBasedDisable(btnDelete::setDisable, tableInterface.permDelete());
    }

    private void getTable() {
        tableView.getColumns().clear();
        tableData = tableInterface.table_data();
        tableView.getColumns().addAll(tableData.columns());
        tableData.getTable(tableView);
        ColumnSetting.addSelectedColumn(tableView);
        tableData.configureColumnViews(btnView, tableView);
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        if (tableData.usesContentSizedColumns()) {
            TableAppearance.setFillAvailableWidthOverride(tableView, false);
        }
        TableAppearance.apply(tableView);
    }

    private void otherSetting() {
        labelSearch.setText(text("search"));
        labelSearch.setGraphic(AppIcon.SEARCH.graphic());
        txtSearch.setPromptText(screenProfile.searchPrompt());

        buttonSetting(btnNew, screenProfile.addButtonText(), AppIcon.ADD);
        buttonSetting(btnUpdate, text("update"), AppIcon.EDIT);
        buttonSetting(btnDelete, text("delete"), AppIcon.DELETE);
        buttonSetting(btnRefresh, text("refresh"), AppIcon.REFRESH);
        buttonSetting(btnPrint, text("print"), AppIcon.PRINT);
        buttonSetting(btnSelected, text("table.column.select"), AppIcon.SELECT_ALL);
        buttonSetting(btnView, text("party.list.view"), AppIcon.SETTINGS);
    }

    private void buttonSetting(ButtonBase button, String title, AppIcon icon) {
        button.setText(title);
        button.setGraphic(icon.graphic());
        button.setContentDisplay(ContentDisplay.RIGHT);
    }

    /** Applies feature-specific identity without coupling the shared FXML to a domain. */
    private void applyScreenProfile() {
        setShown(identityHeader, screenProfile.headerVisible());
        setShown(btnUpdate, screenProfile.updateVisible());
        setShown(btnDelete, screenProfile.deleteVisible());
        setShown(rowRecordActions,
                screenProfile.updateVisible() || screenProfile.deleteVisible());
        setShown(btnSelected, screenProfile.selectionVisible());
        setShown(btnView, tableData.supportsColumnViews());

        if (!screenProfile.rootStyleClass().isBlank()) {
            root.getStyleClass().add(screenProfile.rootStyleClass());
        }
        if (screenProfile.headerVisible()) {
            identityTitle.setText(screenProfile.title());
            identitySubtitle.setText(screenProfile.subtitle());
            identityIconBox.getChildren().setAll(screenProfile.icon().graphic(30));
            btnNew.getStyleClass().add("party-primary-button");
        }
    }

    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
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

        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                btnDelete.fire();
            }
        });

        btnNew.setOnAction(actionEvent -> {
            try {
                actionButtonToolBar.openNew();
            } catch (Exception e) {
                reportUnexpected(text("error.operation.record.open.new"), e);
            }
        });

        btnUpdate.setOnAction(actionEvent -> {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.handleError(text("error.operation.table.action"),
                        new UserValidationException(text("msg.select.row")));
                return;
            }
            try {
                actionButtonToolBar.update(tableView.getSelectionModel().getSelectedItem());
            } catch (Exception e) {
                reportUnexpected(text("error.operation.record.open.edit"), e);
            }
        });

        btnDelete.setOnAction(actionEvent -> {
            if (tableView.getSelectionModel().isEmpty()) {
                AllAlerts.handleError(text("error.operation.table.action"),
                        new UserValidationException(text("msg.select.row")));
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
                    reportDeleteFailure(e);
                }
        });

        btnRefresh.setOnAction(actionEvent -> updateTableView(0));

        btnPrint.setOnAction(actionEvent -> {
            try {
                actionButtonToolBar.print();
            } catch (Exception e) {
                reportUnexpected(text("report.error.print.table.title"), e);
            }
        });
    }

    private void reportUnexpected(String operation, Exception error) {
        AllAlerts.reportError(operation, error);
    }

    /**
     * Delete refusals still arrive through the legacy {@code throws Exception}
     * contract and carry the reason the user needs to see. They stay on the old
     * path until the table API returns {@code DeleteOutcome} directly; technical
     * failures elsewhere already use {@link AllAlerts#reportError}.
     */
    private void reportDeleteFailure(Exception error) {
        AllAlerts.handleError(text("row.action.delete"), error);
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

}
