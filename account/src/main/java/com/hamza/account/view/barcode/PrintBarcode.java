package com.hamza.account.view.barcode;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.barcodeprint.BarcodeLabelOptions;
import com.hamza.account.features.barcodeprint.BarcodeNameOverflow;
import com.hamza.account.features.barcodeprint.BarcodePrintBatch;
import com.hamza.account.features.barcodeprint.BarcodePrintLine;
import com.hamza.account.features.barcodeprint.BarcodePrintProblem;
import com.hamza.account.features.barcodeprint.BarcodePrintResult;
import com.hamza.account.features.barcodeprint.BarcodePrintService;
import com.hamza.account.features.barcodeprint.BarcodePrintValidation;
import com.hamza.account.features.barcodeprint.BarcodePrintValidationException;
import com.hamza.account.features.barcodeprint.BarcodePrinterSelection;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.reportData.JasperBarcodePrintEngine;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.TextFormat;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.print.Printer;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.hamza.account.config.PropertiesName.getBarcodeLabelHeightMm;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelNameFontSize;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelNameMaxCharacters;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelNameOverflow;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelPrintBarcode;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelPrintName;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelPrintPrice;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelShowDouble;
import static com.hamza.account.config.PropertiesName.getBarcodeLabelWidthMm;
import static com.hamza.account.config.PropertiesName.getSettingPrinterBarcode;
import static com.hamza.account.config.PropertiesName.setSettingPrinterBarcode;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/** Modern barcode-label batch screen. UI wiring stays here; validation and printing do not. */
public class PrintBarcode implements AppSettingInterface {
    private final ObservableList<PrintBarcodeModel> rows;
    private final BarcodePrintService printService;
    private List<String> availablePrinters = List.of();
    private boolean printerLoading;
    private boolean printing;
    private long previewRevision;

    @FXML private TableView<PrintBarcodeModel> tableView;
    @FXML private ComboBox<String> comboPrinter;
    @FXML private TextField txtQuantityAll;
    @FXML private CheckBox checkDoubleLabel;
    @FXML private CheckBox checkShowName;
    @FXML private CheckBox checkShowPrice;
    @FXML private CheckBox checkShowBarcode;
    @FXML private Button btnApplyQuantity;
    @FXML private Button btnRemoveSelected;
    @FXML private Button btnRefreshPrinters;
    @FXML private Button btnPreview;
    @FXML private Button btnPrint;
    @FXML private Button btnClose;
    @FXML private Label labelTitle;
    @FXML private Label labelPrinterStatus;
    @FXML private Label labelSize;
    @FXML private Label labelNamePolicy;
    @FXML private Label labelItemsCount;
    @FXML private Label labelLabelsCount;
    @FXML private Label labelPreviewStatus;
    @FXML private Label labelOperationStatus;
    @FXML private ImageView previewImage;
    @FXML private ProgressIndicator progress;

    public PrintBarcode(ObservableList<PrintBarcodeModel> rows) {
        this(rows, new BarcodePrintService(new JasperBarcodePrintEngine()));
    }

    PrintBarcode(ObservableList<PrintBarcodeModel> rows, BarcodePrintService printService) {
        this.rows = FXCollections.observableArrayList(Objects.requireNonNullElse(rows,
                FXCollections.observableArrayList()));
        this.printService = Objects.requireNonNull(printService, "printService");
    }

    @FXML
    public void initialize() {
        configureTable();
        configureOptions();
        configureActions();
        configureGraphics();
        updateSummary();
        refreshPrinters();
        if (!rows.isEmpty()) {
            tableView.getSelectionModel().selectFirst();
            requestPreview(false);
        }
    }

    private void configureTable() {
        TableColumn<PrintBarcodeModel, String> barcode = Columns.text("barcode", PrintBarcodeModel::getBarcode);
        TableColumn<PrintBarcodeModel, String> name = Columns.text("name", PrintBarcodeModel::getName);
        TableColumn<PrintBarcodeModel, String> price = Columns.text("price",
                row -> MoneyMath.text(row.getPrice()));
        TableColumn<PrintBarcodeModel, Integer> quantity = Columns.column("quantity",
                PrintBarcodeModel::getQuantity);
        quantity.setEditable(true);
        quantity.setCellFactory(column -> new PositiveIntegerTableCell());
        quantity.setOnEditCommit(event -> {
            event.getRowValue().setQuantity(event.getNewValue());
            tableView.refresh();
            updateSummary();
        });

        tableView.getColumns().setAll(barcode, name, price, quantity);
        tableView.setItems(rows);
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> requestPreview(false));
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void configureOptions() {
        checkDoubleLabel.setSelected(getBarcodeLabelShowDouble());
        checkShowName.setSelected(getBarcodeLabelPrintName());
        checkShowPrice.setSelected(getBarcodeLabelPrintPrice());
        checkShowBarcode.setSelected(getBarcodeLabelPrintBarcode());
        labelSize.setText(text("barcode.print.label.size", getBarcodeLabelWidthMm(), getBarcodeLabelHeightMm()));
        labelNamePolicy.setText(namePolicyText());

        for (CheckBox option : List.of(checkDoubleLabel, checkShowName, checkShowPrice, checkShowBarcode)) {
            option.selectedProperty().addListener((observable, oldValue, newValue) -> {
                updateSummary();
                requestPreview(false);
            });
        }
        txtQuantityAll.setText("1");
        txtQuantityAll.setTextFormatter(TextFormat.createNumericTextFormatter());
        whenEnterPressed(txtQuantityAll, btnApplyQuantity);
    }

    private void configureActions() {
        btnApplyQuantity.setOnAction(event -> applyQuantityToAll());
        btnRemoveSelected.setOnAction(event -> removeSelected());
        btnRefreshPrinters.setOnAction(event -> refreshPrinters());
        btnPreview.setOnAction(event -> requestPreview(true));
        btnPrint.setOnAction(event -> printBatch());
        btnClose.setOnAction(event -> btnClose.getScene().getWindow().hide());
        comboPrinter.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null && !value.isBlank()) {
                setSettingPrinterBarcode(value);
            }
            updatePrinterStatus();
        });
        rows.addListener((javafx.collections.ListChangeListener<PrintBarcodeModel>) change -> updateSummary());
    }

    private void configureGraphics() {
        labelTitle.setGraphic(AppIcon.BARCODE.graphic(22));
        btnApplyQuantity.setGraphic(AppIcon.CONFIRM.graphic(15));
        btnRemoveSelected.setGraphic(AppIcon.DELETE.graphic(15));
        btnRefreshPrinters.setGraphic(AppIcon.REFRESH.graphic(15));
        btnPreview.setGraphic(AppIcon.SHOW.graphic(16));
        btnPrint.setGraphic(AppIcon.PRINT.graphic(17));
        btnClose.setGraphic(AppIcon.CLOSE.graphic(16));
    }

    private void applyQuantityToAll() {
        try {
            int quantity = Integer.parseInt(txtQuantityAll.getText());
            if (quantity < 1 || quantity > BarcodePrintValidation.MAX_COPIES_PER_LINE) {
                showProblem(BarcodePrintProblem.row(BarcodePrintProblem.Type.INVALID_COPIES, 0));
                return;
            }
            rows.forEach(row -> row.setQuantity(quantity));
            tableView.refresh();
            updateSummary();
        } catch (NumberFormatException ignored) {
            showProblem(BarcodePrintProblem.row(BarcodePrintProblem.Type.INVALID_COPIES, 0));
        }
    }

    private void removeSelected() {
        var selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        rows.removeAll(selected);
        if (!rows.isEmpty()) {
            tableView.getSelectionModel().selectFirst();
        } else {
            previewImage.setImage(null);
            labelPreviewStatus.setText(text("barcode.print.preview.empty"));
        }
        updateSummary();
    }

    private void refreshPrinters() {
        printerLoading = true;
        labelPrinterStatus.setText(text("barcode.print.printer.loading"));
        setStatusStyle(labelPrinterStatus, "printer-ready", "printer-missing", false);
        updateActionState();
        String configured = comboPrinter.getValue() == null ? getSettingPrinterBarcode() : comboPrinter.getValue();
        Thread.ofVirtual().name("barcode-printer-discovery").start(() -> {
            try {
                List<String> found = Printer.getAllPrinters().stream()
                        .map(Printer::getName)
                        .sorted()
                        .toList();
                Platform.runLater(() -> applyPrinters(found, configured));
            } catch (RuntimeException failure) {
                Platform.runLater(() -> {
                    printerLoading = false;
                    updateActionState();
                    AllAlerts.handleError(text("barcode.print.printer.context"), failure);
                });
            }
        });
    }

    private void applyPrinters(List<String> found, String configured) {
        printerLoading = false;
        availablePrinters = List.copyOf(found);
        comboPrinter.getItems().setAll(found);
        if (configured != null && !configured.isBlank() && !found.contains(configured)) {
            comboPrinter.getItems().add(configured);
        }
        comboPrinter.setValue(configured);
        updatePrinterStatus();
    }

    private void updatePrinterStatus() {
        String selected = comboPrinter.getValue();
        boolean ready = selected != null && availablePrinters.contains(selected);
        if (ready) {
            labelPrinterStatus.setText(text("barcode.print.printer.ready", selected));
            labelPrinterStatus.setGraphic(AppIcon.CONFIRM.graphic(14));
        } else {
            labelPrinterStatus.setText(availablePrinters.isEmpty()
                    ? text("barcode.print.printer.none")
                    : text("barcode.print.printer.missing"));
            labelPrinterStatus.setGraphic(AppIcon.WARNING.graphic(14));
        }
        setStatusStyle(labelPrinterStatus, "printer-ready", "printer-missing", ready);
        updateActionState();
    }

    private void printBatch() {
        BarcodePrintBatch batch = batch(rows);
        List<BarcodePrintProblem> problems = BarcodePrintValidation.forPrint(batch);
        if (!problems.isEmpty()) {
            showProblem(problems.getFirst());
            return;
        }
        if (!printerReady()) {
            AllAlerts.handleError(text("barcode.print.printer.context"),
                    new UserValidationException(text("barcode.print.validation.printer.unavailable")));
            return;
        }

        printing = true;
        labelOperationStatus.setText(text("barcode.print.status.preparing"));
        updateActionState();
        Task<BarcodePrintResult> task = new Task<>() {
            @Override
            protected BarcodePrintResult call() throws Exception {
                return printService.print(batch);
            }
        };
        task.setOnSucceeded(event -> {
            printing = false;
            BarcodePrintResult result = task.getValue();
            labelOperationStatus.setText(text("barcode.print.status.sent", result.labels(), result.printerName()));
            updateActionState();
        });
        task.setOnFailed(event -> {
            printing = false;
            updateActionState();
            handleFailure(task.getException());
        });
        Thread.ofVirtual().name("barcode-print-batch").start(task);
    }

    private void requestPreview(boolean reportFailure) {
        PrintBarcodeModel row = tableView == null ? null : tableView.getSelectionModel().getSelectedItem();
        if (row == null && !rows.isEmpty()) {
            row = rows.getFirst();
        }
        if (row == null) {
            return;
        }
        BarcodePrintBatch batch = batch(List.of(row));
        List<BarcodePrintProblem> problems = BarcodePrintValidation.forPreview(batch);
        if (!problems.isEmpty()) {
            labelPreviewStatus.setText(text("barcode.print.preview.invalid"));
            if (reportFailure) {
                showProblem(problems.getFirst());
            }
            return;
        }

        long revision = ++previewRevision;
        labelPreviewStatus.setText(text("barcode.print.preview.loading"));
        Task<byte[]> task = new Task<>() {
            @Override
            protected byte[] call() throws Exception {
                return printService.preview(batch);
            }
        };
        task.setOnSucceeded(event -> {
            if (revision != previewRevision) {
                return;
            }
            previewImage.setImage(new Image(new ByteArrayInputStream(task.getValue())));
            labelPreviewStatus.setText(text("barcode.print.preview.ready"));
        });
        task.setOnFailed(event -> {
            if (revision != previewRevision) {
                return;
            }
            previewImage.setImage(null);
            labelPreviewStatus.setText(text("barcode.print.preview.failed"));
            if (reportFailure) {
                handleFailure(task.getException());
            }
        });
        Thread.ofVirtual().name("barcode-label-preview").start(task);
    }

    private BarcodePrintBatch batch(List<PrintBarcodeModel> source) {
        List<BarcodePrintLine> lines = source.stream()
                .map(row -> new BarcodePrintLine(row.getBarcode(), row.getName(), row.getPrice(), row.getQuantity()))
                .toList();
        return new BarcodePrintBatch(lines, comboPrinter == null ? "" : comboPrinter.getValue(), currentOptions());
    }

    private BarcodeLabelOptions currentOptions() {
        return new BarcodeLabelOptions(getBarcodeLabelWidthMm(), getBarcodeLabelHeightMm(),
                checkDoubleLabel.isSelected(), checkShowName.isSelected(), checkShowPrice.isSelected(),
                checkShowBarcode.isSelected(), BarcodeNameOverflow.fromSetting(getBarcodeLabelNameOverflow()),
                getBarcodeLabelNameMaxCharacters(), getBarcodeLabelNameFontSize());
    }

    private void updateSummary() {
        long copies = rows.stream().mapToLong(PrintBarcodeModel::getQuantity).sum();
        long labels = copies * (checkDoubleLabel != null && checkDoubleLabel.isSelected() ? 2 : 1);
        labelItemsCount.setText(text("barcode.print.summary.items", rows.size()));
        labelLabelsCount.setText(text("barcode.print.summary.labels", labels));
        updateActionState();
    }

    private void updateActionState() {
        if (btnPrint == null) {
            return;
        }
        btnPrint.setDisable(printing || rows.isEmpty() || !printerReady());
        btnPreview.setDisable(printing || rows.isEmpty());
        btnApplyQuantity.setDisable(printing || rows.isEmpty());
        btnRemoveSelected.setDisable(printing || rows.isEmpty());
        btnRefreshPrinters.setDisable(printing || printerLoading);
        comboPrinter.setDisable(printing || printerLoading);
        progress.setVisible(printing);
        progress.setManaged(printing);
    }

    private boolean printerReady() {
        return comboPrinter != null
                && BarcodePrinterSelection.isAvailable(availablePrinters, comboPrinter.getValue());
    }

    private void handleFailure(Throwable failure) {
        if (failure instanceof BarcodePrintValidationException validation
                && !validation.problems().isEmpty()) {
            showProblem(validation.problems().getFirst());
            return;
        }
        Exception exception = failure instanceof Exception value
                ? value : new IllegalStateException(failure);
        AllAlerts.handleError(text("barcode.print.error.context"), exception);
        labelOperationStatus.setText(text("barcode.print.status.failed"));
    }

    private void showProblem(BarcodePrintProblem problem) {
        String message = switch (problem.type()) {
            case EMPTY_BATCH -> text("barcode.print.validation.empty");
            case MISSING_PRINTER -> text("barcode.print.validation.printer.required");
            case MISSING_BARCODE -> text("barcode.print.validation.barcode", problem.rowNumber());
            case INVALID_COPIES -> problem.rowNumber() > 0
                    ? text("barcode.print.validation.copies", problem.rowNumber(), BarcodePrintValidation.MAX_COPIES_PER_LINE)
                    : text("barcode.print.validation.copies.all", BarcodePrintValidation.MAX_COPIES_PER_LINE);
            case INVALID_PRICE -> text("barcode.print.validation.price", problem.rowNumber());
            case INVALID_LABEL_SIZE -> text("barcode.print.validation.size");
            case INVALID_NAME_SETTINGS -> text("barcode.print.validation.name.settings");
        };
        AllAlerts.handleError(text("barcode.print.validation.context"), new UserValidationException(message));
    }

    private String namePolicyText() {
        String policy = switch (BarcodeNameOverflow.fromSetting(getBarcodeLabelNameOverflow())) {
            case ELLIPSIS -> text("settings.barcode.nameOverflow.ellipsis");
            case SHRINK -> text("settings.barcode.nameOverflow.shrink");
            case HIDE -> text("settings.barcode.nameOverflow.hide");
        };
        return text("barcode.print.name.policy", policy);
    }

    private void setStatusStyle(Label label, String readyClass, String missingClass, boolean ready) {
        label.getStyleClass().removeAll(readyClass, missingClass);
        label.getStyleClass().add(ready ? readyClass : missingClass);
    }

    private String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    @Override
    public Pane pane() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("print_barcode.fxml"),
                LanguageManager.getInstance().getResourceBundle());
        loader.setControllerFactory(type -> this);
        return loader.load();
    }

    @Override
    public String title() {
        return text("barcode.print.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public double minHeight() {
        return 540;
    }

    @Override
    public double minWidth() {
        return 860;
    }
}
