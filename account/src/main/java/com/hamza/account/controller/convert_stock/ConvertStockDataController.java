package com.hamza.account.controller.convert_stock;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.StockTransferService;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Log4j2
@FxmlPath(pathFile = "stock-transfers-view.fxml")
public class ConvertStockDataController implements AppSettingInterface {

    private static final String STATUS_ALL = "كل الحالات";
    private static final String STATUS_POSTED_AR = "مرحل";
    private static final String STATUS_CANCELLED_AR = "ملغي";

    private final DaoFactory daoFactory;
    private final Publisher<String> publisherAfterInsertData = new Publisher<>();
    private final StockTransferService stockTransferService = ServiceRegistry.get(StockTransferService.class);

    private final ObservableList<StockTransfer> masterData = FXCollections.observableArrayList();
    private FilteredList<StockTransfer> filteredData;

    @FXML
    private StackPane stackPane;

    @FXML
    private Button btnNew;

    @FXML
    private Button btnDetails;

    @FXML
    private Button btnCancelTransfer;

    @FXML
    private Button btnPrint;

    @FXML
    private Button btnRefresh;

    @FXML
    private Button btnClearSearch;

    @FXML
    private TextField txtSearch;

    @FXML
    private ComboBox<String> comboStatus;

    @FXML
    private TableView<StockTransfer> tableView;

    @FXML
    private Text txtTotalTransfers;

    @FXML
    private Text txtPostedTransfers;

    @FXML
    private Text txtCancelledTransfers;

    @FXML
    private Text txtTotalQuantity;

    public ConvertStockDataController(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @FXML
    public void initialize() {
        setupStatusFilter();
        setupTable();
        setupActions();
        setupBindings();
        loadData();
    }

    private void setupStatusFilter() {
        comboStatus.setItems(FXCollections.observableArrayList(
                STATUS_ALL,
                STATUS_POSTED_AR,
                STATUS_CANCELLED_AR
        ));
        comboStatus.getSelectionModel().select(STATUS_ALL);
    }

    private void setupTable() {
        tableView.setEditable(false);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<StockTransfer, String> idColumn = new TableColumn<>("الكود");
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(cell.getValue().getId())));
        idColumn.setPrefWidth(90);

        TableColumn<StockTransfer, String> dateColumn = new TableColumn<>("التاريخ");
        dateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatDate(cell.getValue())));
        dateColumn.setPrefWidth(130);

        TableColumn<StockTransfer, String> fromStockColumn = new TableColumn<>("من مخزن");
        fromStockColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getFromStockName(cell.getValue())));
        fromStockColumn.setPrefWidth(180);

        TableColumn<StockTransfer, String> toStockColumn = new TableColumn<>("إلى مخزن");
        toStockColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getToStockName(cell.getValue())));
        toStockColumn.setPrefWidth(180);

        TableColumn<StockTransfer, String> itemsCountColumn = new TableColumn<>("عدد الأصناف");
        itemsCountColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(getItemsCount(cell.getValue()))));
        itemsCountColumn.setPrefWidth(110);
        itemsCountColumn.setStyle("-fx-alignment: CENTER;");

        TableColumn<StockTransfer, String> totalQuantityColumn = new TableColumn<>("إجمالي الكمية");
        totalQuantityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatNumber(getTotalQuantity(cell.getValue()))));
        totalQuantityColumn.setPrefWidth(130);
        totalQuantityColumn.setStyle("-fx-alignment: CENTER;");

        TableColumn<StockTransfer, String> statusColumn = new TableColumn<>("الحالة");
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getArabicStatus(cell.getValue().getStatus())));
        statusColumn.setPrefWidth(110);
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(status);
                setAlignment(Pos.CENTER);

                if (STATUS_CANCELLED_AR.equals(status)) {
                    setStyle("""
                            -fx-text-fill: #991b1b;
                            -fx-font-weight: bold;
                            -fx-background-color: #fee2e2;
                            """);
                } else {
                    setStyle("""
                            -fx-text-fill: #065f46;
                            -fx-font-weight: bold;
                            -fx-background-color: #d1fae5;
                            """);
                }
            }
        });

        TableColumn<StockTransfer, String> cancelReasonColumn = new TableColumn<>("سبب الإلغاء");
        cancelReasonColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getCancelReason() == null ? "" : cell.getValue().getCancelReason()
        ));
        cancelReasonColumn.setPrefWidth(220);

        tableView.getColumns().setAll(
                idColumn,
                dateColumn,
                fromStockColumn,
                toStockColumn,
                itemsCountColumn,
                totalQuantityColumn,
                statusColumn,
                cancelReasonColumn
        );

        tableView.setRowFactory(tv -> {
            TableRow<StockTransfer> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSelectedTransferDetails();
                }
            });

            return row;
        });

        filteredData = new FilteredList<>(masterData, transfer -> true);
        tableView.setItems(filteredData);
    }

    private void setupActions() {
        btnNew.setOnAction(event -> openNewTransferScreen());

        btnDetails.setOnAction(event -> showSelectedTransferDetails());

        btnCancelTransfer.setOnAction(event -> cancelSelectedTransfer());

        btnPrint.setOnAction(event -> printSelectedTransfer());

        btnRefresh.setOnAction(event -> loadData());

        btnClearSearch.setOnAction(event -> {
            txtSearch.clear();
            comboStatus.getSelectionModel().select(STATUS_ALL);
            applyFilter();
        });

        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());

        comboStatus.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());

        masterData.addListener((ListChangeListener<StockTransfer>) change -> updateSummary());
    }

    private void setupBindings() {
        btnDetails.disableProperty().bind(tableView.getSelectionModel().selectedItemProperty().isNull());

        btnPrint.disableProperty().bind(tableView.getSelectionModel().selectedItemProperty().isNull());

        btnCancelTransfer.disableProperty().bind(
                tableView.getSelectionModel().selectedItemProperty().isNull()
                        .or(Bindings.createBooleanBinding(
                                () -> {
                                    StockTransfer selected = tableView.getSelectionModel().getSelectedItem();
                                    return selected == null || !"POSTED".equalsIgnoreCase(selected.getStatus());
                                },
                                tableView.getSelectionModel().selectedItemProperty()
                        ))
        );
    }

    private void loadData() {
        try {
            List<StockTransfer> transfers = stockTransferService.getStockTransferList();
            masterData.setAll(transfers);
            applyFilter();
            updateSummary();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void applyFilter() {
        String searchText = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String selectedStatus = comboStatus.getSelectionModel().getSelectedItem();

        filteredData.setPredicate(transfer -> {
            boolean matchesSearch = searchText.isBlank()
                    || String.valueOf(transfer.getId()).contains(searchText)
                    || containsIgnoreCase(formatDate(transfer), searchText)
                    || containsIgnoreCase(getFromStockName(transfer), searchText)
                    || containsIgnoreCase(getToStockName(transfer), searchText)
                    || containsIgnoreCase(getArabicStatus(transfer.getStatus()), searchText)
                    || containsIgnoreCase(transfer.getCancelReason(), searchText);

            boolean matchesStatus = selectedStatus == null
                    || STATUS_ALL.equals(selectedStatus)
                    || selectedStatus.equals(getArabicStatus(transfer.getStatus()));

            return matchesSearch && matchesStatus;
        });

        updateSummary();
    }

    private void openNewTransferScreen() {
        try {
            new OpenApplication<>(new ConvertStockMainController(daoFactory, publisherAfterInsertData, 0));
            publisherAfterInsertData.addObserver(message -> loadData());
        } catch (Exception e) {
            logError(e);
        }
    }

    private void showSelectedTransferDetails() {
        StockTransfer selected = tableView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            AllAlerts.alertError("يجب اختيار تحويل أولاً");
            return;
        }

        try {
            StockTransfer fullTransfer = stockTransferService.getStockTransfersById(selected.getId());
            new ShowDataTransfer<>(new ShowDataListStock(fullTransfer));
        } catch (Exception e) {
            logError(e);
        }
    }

    private void cancelSelectedTransfer() {
        StockTransfer selected = tableView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            AllAlerts.alertError("يجب اختيار تحويل أولاً");
            return;
        }

        if (!"POSTED".equalsIgnoreCase(selected.getStatus())) {
            AllAlerts.alertError("لا يمكن إلغاء تحويل ملغي مسبقًا");
            return;
        }

        if (!AllAlerts.confirmDelete()) {
            return;
        }

        Optional<String> reason = showCancelReasonDialog(selected);

        if (reason.isEmpty() || reason.get().trim().isEmpty()) {
            AllAlerts.alertError("يجب إدخال سبب الإلغاء");
            return;
        }

        try {
            int userId = 1;
            int result = stockTransferService.cancelTransfer(selected.getId(), userId, reason.get().trim());

            if (result >= 1) {
                AllAlerts.alertSave();
                loadData();
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private Optional<String> showCancelReasonDialog(StockTransfer transfer) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("إلغاء تحويل مخزني");
        dialog.setHeaderText("إلغاء التحويل رقم: " + transfer.getId());
        dialog.setContentText("سبب الإلغاء:");

        return dialog.showAndWait();
    }

    private void printSelectedTransfer() {
        StockTransfer selected = tableView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            AllAlerts.alertError("يجب اختيار تحويل أولاً");
            return;
        }

        try {
            new Print_Reports().printStockTransfer(selected.getId(), selected.getId());
        } catch (Exception e) {
            logError(e);
        }
    }

    private void updateSummary() {
        List<StockTransfer> currentItems;

        if (filteredData == null) {
            currentItems = masterData;
        } else {
            currentItems = filteredData;
        }

        long total = currentItems.size();

        long posted = currentItems.stream()
                .filter(transfer -> "POSTED".equalsIgnoreCase(transfer.getStatus()))
                .count();

        long cancelled = currentItems.stream()
                .filter(transfer -> "CANCELLED".equalsIgnoreCase(transfer.getStatus()))
                .count();

        double totalQuantity = currentItems.stream()
                .mapToDouble(this::getTotalQuantity)
                .sum();

        txtTotalTransfers.setText(String.valueOf(total));
        txtPostedTransfers.setText(String.valueOf(posted));
        txtCancelledTransfers.setText(String.valueOf(cancelled));
        txtTotalQuantity.setText(formatNumber(totalQuantity));
    }

    private String getFromStockName(StockTransfer transfer) {
        if (transfer == null || transfer.getStockFrom() == null) {
            return "";
        }

        return transfer.getStockFrom().getName();
    }

    private String getToStockName(StockTransfer transfer) {
        if (transfer == null || transfer.getStockTo() == null) {
            return "";
        }

        return transfer.getStockTo().getName();
    }

    private String formatDate(StockTransfer transfer) {
        if (transfer == null || transfer.getDate() == null) {
            return "";
        }

        return transfer.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private int getItemsCount(StockTransfer transfer) {
        if (transfer == null || transfer.getTransferListItems() == null) {
            return 0;
        }

        return transfer.getTransferListItems().size();
    }

    private double getTotalQuantity(StockTransfer transfer) {
        if (transfer == null || transfer.getTransferListItems() == null) {
            return 0;
        }

        return transfer.getTransferListItems()
                .stream()
                .mapToDouble(StockTransferListItems::getQuantity)
                .sum();
    }

    private String getArabicStatus(String status) {
        if ("CANCELLED".equalsIgnoreCase(status)) {
            return STATUS_CANCELLED_AR;
        }

        return STATUS_POSTED_AR;
    }

    private boolean containsIgnoreCase(String text, String searchText) {
        return text != null && text.toLowerCase().contains(searchText);
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        return String.format("%.3f", value);
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return "عرض تحويلات المخازن";
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}